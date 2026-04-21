package com.jeansr.androideditor

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
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Model data class for GitHub repository
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
            Toast.makeText(this, "Error: Token didn't exist", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            setupRepository()
        }
    }

    private fun setupRepository() {
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

                        recyclerRepos.adapter = RepoAdapter(
                            repos,
                            onCloneClick = { repo -> cloneRepository(repo) },
                            onItemClick = { repo -> showRepoDetails(repo) }
                        )
                        progressBar.visibility = ProgressBar.GONE
                    }
                } else {
                    showError("Error API: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                showError(e.message ?: "Error network")
            }
        }
    }


    private fun showRepoDetails(repo: GithubRepo) {
        AlertDialog.Builder(this)
            .setTitle(repo.name)
            .setMessage("${getString(R.string.Description)}\n${repo.description}\n\n${getString(R.string.Cloneurl)}\n${repo.cloneUrl}")
            .setPositiveButton(getString(R.string.Close), null)
            .setNeutralButton(getString(R.string.Clonelocal)) { _, _ ->
                cloneRepository(repo)
            }
            .show()
    }

    private fun cloneRepository(repo: GithubRepo) {
        val destinationDir = File(getExternalFilesDir("Projects"), repo.name)

        if (destinationDir.exists()) {
            Toast.makeText(this, getString(R.string.Projectalreadyexist), Toast.LENGTH_SHORT).show()
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

        progressBar.visibility = ProgressBar.VISIBLE
        Toast.makeText(this, getString(R.string.Searchbranch), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {

            val branches = gitManager.getRemoteBranches(githubToken, owner, repoName)

            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                if (branches.isEmpty()) {
                    Toast.makeText(this@GithubControlActivity, getString(R.string.Nobranches), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                if (branches.size == 1) {
                    runRealClone(gitManager, owner, repoName, destinationDir, branches.first())
                } else {
                    val options = branches.toTypedArray()
                    android.app.AlertDialog.Builder(this@GithubControlActivity)
                        .setTitle(getString(R.string.Selectclonebranch))
                        .setItems(options) { _, which ->
                            val ramaSeleccionada = options[which]
                            runRealClone(gitManager, owner, repoName, destinationDir, ramaSeleccionada)
                        }
                        .setNegativeButton(getString(R.string.Cancelop), null)
                        .show()
                }
            }
        }
    }

    // Helper function to handle the heavy lifting
    private fun runRealClone(gitManager: GitManager, owner: String, repoName: String, destinationDir: File, rama: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        Toast.makeText(this, "${getString(R.string.Cloningbranch)} '$rama'${getString(R.string.Thiscantakeawhile)}", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val res = gitManager.clonarRepo(githubToken, owner, repoName, destinationDir, rama)

            withContext(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
                if (res.success) {
                    Toast.makeText(this@GithubControlActivity, getString(R.string.Clonedone), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    destinationDir.deleteRecursively()
                    Toast.makeText(this@GithubControlActivity, "${getString(R.string.Error)} ${res.error.take(120)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            progressBar.visibility = ProgressBar.GONE
            Toast.makeText(this@GithubControlActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // --- ADAPTER UPDATE TO USE ON XML ---
    class RepoAdapter(
        private val repos: List<GithubRepo>,
        private val onCloneClick: (GithubRepo) -> Unit,
        private val onItemClick: (GithubRepo) -> Unit
    ) : RecyclerView.Adapter<RepoAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_github_repo, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val repo = repos[position]
            holder.txtName.text = repo.name
            holder.txtName.setTextColor(holder.itemView.context.getColor(R.color.gray))
            holder.txtDesc.text = repo.description
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.gray_ob))

            holder.itemView.setOnClickListener { onItemClick(repo) }

            holder.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)

                popup.menu.add(R.string.Clonelocal.toString())
                popup.setOnMenuItemClickListener { item ->
                    if (item.title == R.string.Clonelocal.toString()) {
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