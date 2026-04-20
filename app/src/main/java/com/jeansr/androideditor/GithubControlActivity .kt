package com.jeansr.androideditor // <-- Asegúrate de que coincida con tu paquete

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Modelo de datos
data class GithubRepo(val name: String, val description: String, val cloneUrl: String)

class GithubControlActivity : AppCompatActivity() {

    private lateinit var recyclerRepos: RecyclerView
    private lateinit var progressBar: ProgressBar
    private var githubToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_github_control)

        recyclerRepos = findViewById(R.id.recyclerRepos)
        progressBar = findViewById(R.id.progressBar)
        val toolbar = findViewById<MaterialToolbar>(R.id.githubToolbar)

        toolbar.setNavigationOnClickListener { finish() }
        recyclerRepos.layoutManager = LinearLayoutManager(this)

        val sharedPref = getSharedPreferences("CodeAssistPrefs", Context.MODE_PRIVATE)
        githubToken = sharedPref.getString("GITHUB_TOKEN", "") ?: ""

        if (githubToken.isEmpty()) {
            Toast.makeText(this, "Error: No hay Token", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            cargarRepositorios()
        }
    }

    private fun cargarRepositorios() {
        progressBar.visibility = ProgressBar.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/user/repos?sort=updated")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $githubToken")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(response)
                    val repos = mutableListOf<GithubRepo>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        repos.add(
                            GithubRepo(
                                name = obj.getString("name"),
                                description = if (!obj.isNull("description")) obj.getString("description") else "Sin descripción",
                                cloneUrl = obj.getString("clone_url")
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        // AQUÍ CONECTAMOS LOS NUEVOS CLICS
                        recyclerRepos.adapter = RepoAdapter(
                            repos,
                            onCloneClick = { repo -> clonarRepositorio(repo) },
                            onItemClick = { repo -> mostrarDetalles(repo) }
                        )
                        progressBar.visibility = ProgressBar.GONE
                    }
                } else {
                    mostrarError("Error API: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                mostrarError(e.message ?: "Error de red")
            }
        }
    }

    // NUEVO: Cuadro de diálogo para ver los detalles completos
    private fun mostrarDetalles(repo: GithubRepo) {
        AlertDialog.Builder(this)
            .setTitle(repo.name)
            .setMessage("Descripción:\n${repo.description}\n\nURL de clonación:\n${repo.cloneUrl}")
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Clonar a local") { _, _ ->
                clonarRepositorio(repo)
            }
            .show()
    }

    private fun clonarRepositorio(repo: GithubRepo) {
        val destinationDir = File(getExternalFilesDir("Projects"), repo.name)

        if (destinationDir.exists()) {
            Toast.makeText(this, "El proyecto ya existe localmente", Toast.LENGTH_SHORT).show()
            return
        }

        val gitManager = GitManager(destinationDir)

        // Extraemos owner y repoName
        val parts = repo.cloneUrl
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .split("/")
        val owner = parts.getOrElse(0) { "" }
        val repoName = parts.getOrElse(1) { repo.name }

        // 1. Mostrar progreso mientras leemos las ramas
        progressBar.visibility = ProgressBar.VISIBLE
        Toast.makeText(this, "Buscando ramas...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            // Pedimos la lista de ramas a GitHub
            val ramas = gitManager.obtenerRamasRemotas(githubToken, owner, repoName)

            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE

                if (ramas.isEmpty()) {
                    Toast.makeText(this@GithubControlActivity, "No se encontraron ramas o hubo un error de red.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                // 2. Lógica de decisión
                if (ramas.size == 1) {
                    // Si solo hay una (ej. main), la clonamos directamente sin molestar al usuario
                    ejecutarClonadoReal(gitManager, owner, repoName, destinationDir, ramas.first())
                } else {
                    // Si hay varias, mostramos un diálogo nativo
                    val opciones = ramas.toTypedArray()
                    android.app.AlertDialog.Builder(this@GithubControlActivity)
                        .setTitle("Selecciona la rama a clonar")
                        .setItems(opciones) { _, which ->
                            val ramaSeleccionada = opciones[which]
                            ejecutarClonadoReal(gitManager, owner, repoName, destinationDir, ramaSeleccionada)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
        }
    }

    // Función auxiliar para hacer el trabajo pesado
    private fun ejecutarClonadoReal(gitManager: GitManager, owner: String, repoName: String, destinationDir: File, rama: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        Toast.makeText(this, "Clonando rama '$rama'... esto puede tardar", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val res = gitManager.clonarRepo(githubToken, owner, repoName, destinationDir, rama)

            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                if (res.success) {
                    Toast.makeText(this@GithubControlActivity, "¡Clonado con éxito!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    destinationDir.deleteRecursively()
                    Toast.makeText(this@GithubControlActivity, "Error: ${res.error.take(120)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun mostrarError(mensaje: String) {
        withContext(Dispatchers.Main) {
            progressBar.visibility = ProgressBar.GONE
            Toast.makeText(this@GithubControlActivity, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    // --- EL ADAPTADOR ACTUALIZADO PARA USAR EL XML ---
    class RepoAdapter(
        private val repos: List<GithubRepo>,
        private val onCloneClick: (GithubRepo) -> Unit,
        private val onItemClick: (GithubRepo) -> Unit
    ) : RecyclerView.Adapter<RepoAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Inflamos el archivo XML que acabas de crear
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_github_repo, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val repo = repos[position]
            holder.txtName.text = repo.name
            holder.txtName.setTextColor(holder.itemView.context.getColor(R.color.gray))
            holder.txtDesc.text = repo.description
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.gray_ob))

            // 1. Clic en toda la tarjeta (Abre detalles)
            holder.itemView.setOnClickListener { onItemClick(repo) }

            // 2. Clic en los 3 puntitos (Abre menú Popup)
            holder.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add("Clonar a Local")
                popup.setOnMenuItemClickListener { item ->
                    if (item.title == "Clonar a Local") {
                        onCloneClick(repo)
                    }
                    true
                }
                popup.show()
            }
        }

        override fun getItemCount() = repos.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtName: TextView = view.findViewById(R.id.txtRepoName)
            val txtDesc: TextView = view.findViewById(R.id.txtRepoDesc)
            val btnMenu: ImageButton = view.findViewById(R.id.btnRepoMenu)
        }
    }
}