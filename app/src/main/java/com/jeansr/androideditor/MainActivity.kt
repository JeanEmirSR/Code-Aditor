package com.jeansr.androideditor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerProjects: RecyclerView
    private lateinit var projectAdapter: SimpleProjectAdapter

    // Launcher para importar carpetas
    private val importProjectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importarProyectoDesdeUri(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar motor de archivos en segundo plano
        Thread { CompilerSetup.initEngineFiles(this) }.start()

        recyclerProjects = findViewById(R.id.recyclerProjects)

        // CONFIGURACIÓN DEL ADAPTADOR (Pasa la vista para el Popup)
        projectAdapter = SimpleProjectAdapter(
            onProjectClick = { abrirProyecto(it) },
            onProjectLongClick = { proyecto, vista -> mostrarMenuOpciones(proyecto, vista) }
        )

        recyclerProjects.layoutManager = LinearLayoutManager(this)
        recyclerProjects.adapter = projectAdapter

        findViewById<View>(R.id.btnGitHubLogin).setOnClickListener { mostrarDialogoGitHub() }
        findViewById<View>(R.id.btnNuevoProyecto).setOnClickListener {
            importProjectLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }

        cargarProyectos()
    }

    override fun onResume() {
        super.onResume()
        cargarProyectos()
    }

    // =========================================================================
    // LÓGICA DEL MENÚ FLOTANTE (POPUPWINDOW)
    // =========================================================================

    private fun mostrarMenuOpciones(proyecto: File, anchorView: View) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.item_listopcion, null)

        // Crear el Popup (WRAP_CONTENT para que se ajuste a tu diseño)
        val popup = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Permitir que se cierre al tocar fuera y añadir sombra
        popup.elevation = 30f
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Configurar clics usando tus IDs
        view.findViewById<View>(R.id.Renameop).setOnClickListener {
            popup.dismiss()
            mostrarDialogoRenombrar(proyecto)
        }

        view.findViewById<View>(R.id.Deleteop).setOnClickListener {
            popup.dismiss()
            confirmarEliminar(proyecto)
        }

        // Mostrar "al ladito" (X offset para mover a la derecha, Y offset para centrar)
        popup.showAsDropDown(anchorView, 200, -anchorView.height / 4)
    }

    private fun mostrarDialogoRenombrar(proyecto: File) {
        val input = EditText(this).apply {
            setText(proyecto.name)
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Renombrar")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = input.text.toString().trim()
                if (nuevoNombre.isNotEmpty()) {
                    val nuevoFile = File(proyecto.parent, nuevoNombre)
                    if (proyecto.renameTo(nuevoFile)) cargarProyectos()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarEliminar(proyecto: File) {
        AlertDialog.Builder(this)
            .setTitle("Borrar Proyecto")
            .setMessage("¿Estás seguro de eliminar permanentemente ${proyecto.name}?")
            .setPositiveButton("Sí, borrar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (proyecto.deleteRecursively()) {
                        withContext(Dispatchers.Main) { cargarProyectos() }
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    // =========================================================================
    // GESTIÓN DE ARCHIVOS
    // =========================================================================

    private fun cargarProyectos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val savePath = getExternalFilesDir("Projects") ?: return@launch
            val proyectos = savePath.listFiles { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
            withContext(Dispatchers.Main) {
                projectAdapter.actualizarDatos(proyectos)
            }
        }
    }

    private fun abrirProyecto(proyecto: File) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("PROJECT_PATH", proyecto.absolutePath)
        }
        startActivity(intent)
    }

    private fun importarProyectoDesdeUri(uri: Uri) {
        val sourceDir = DocumentFile.fromTreeUri(this, uri) ?: return
        val destinationDir = File(getExternalFilesDir("Projects"), sourceDir.name ?: "NuevoProyecto")

        if (destinationDir.exists()) {
            Toast.makeText(this, "El proyecto ya existe", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Importando...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                copiarDirectorio(sourceDir, destinationDir)
                withContext(Dispatchers.Main) { cargarProyectos() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun copiarDirectorio(source: DocumentFile, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles().forEach { copiarDirectorio(it, File(dest, it.name)) }
        } else {
            contentResolver.openInputStream(source.uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun mostrarDialogoGitHub() {
        val sharedPref = getSharedPreferences("CodeAssistPrefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("GITHUB_TOKEN", "")
        if (!token.isNullOrEmpty()) {
            startActivity(Intent(this, GithubControlActivity::class.java))
        } else {
            val input = EditText(this).apply { hint = "Pega tu Token aquí" }
            AlertDialog.Builder(this)
                .setTitle("Conexión GitHub")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    sharedPref.edit().putString("GITHUB_TOKEN", input.text.toString()).apply()
                    startActivity(Intent(this, GithubControlActivity::class.java))
                }.show()

        }
    }
}

// =============================================================================
// ADAPTADOR COMPACTO
// =============================================================================

class SimpleProjectAdapter(
    private val onProjectClick: (File) -> Unit,
    private val onProjectLongClick: (File, View) -> Unit
) : RecyclerView.Adapter<SimpleProjectAdapter.ViewHolder>() {

    private var proyectos = listOf<File>()

    fun actualizarDatos(n: List<File>) {
        proyectos = n
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = TextView(parent.context).apply {
            textSize = 15f
            setPadding(45, 30, 45, 30) // Padding compacto
            setTextColor(Color.WHITE)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folderproject, 0, 0, 0)
            compoundDrawablePadding = 35

            // Efecto de selección (Ripple)
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)

            layoutParams = ViewGroup.LayoutParams(-1, -2)
            isClickable = true
            isFocusable = true
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(h: ViewHolder, p: Int) {
        val proj = proyectos[p]
        h.textView.text = proj.name
        h.itemView.setOnClickListener { onProjectClick(proj) }
        h.itemView.setOnLongClickListener {
            onProjectLongClick(proj, it) // Pasa la vista para el Popup
            true
        }
    }

    override fun getItemCount() = proyectos.size
    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}