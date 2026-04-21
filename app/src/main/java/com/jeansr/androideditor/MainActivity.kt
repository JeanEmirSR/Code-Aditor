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


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerProjects: RecyclerView
    private lateinit var projectAdapter: SimpleProjectAdapter

    // Launcher to import directories
    private val importProjectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { setupProjectFromUri(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize file engine in the background
        Thread { CompilerSetup.initEngineFiles(this) }.start()

        recyclerProjects = findViewById(R.id.recyclerProjects)

        // ADAPTER CONFIGURATION (Pass the view for the Popup)
        projectAdapter = SimpleProjectAdapter(
            onProjectClick = { openProject(it) },
            onProjectLongClick = { proyecto, vista -> showOptionsMenu(proyecto, vista) }
        )

        recyclerProjects.layoutManager = LinearLayoutManager(this)
        recyclerProjects.adapter = projectAdapter

        findViewById<View>(R.id.btnGitHubLogin).setOnClickListener { showGitHubDialog() }
        findViewById<View>(R.id.btnNuevoProyecto).setOnClickListener {
            importProjectLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }

        loadProjects()
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    // =========================================================================
    // FLOATING MENU LOGIC (PopupWindow)
    // =========================================================================

    private fun showOptionsMenu(project: File, anchorView: View) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.item_listopcion, null)

        // Create the Popup Window
        val popup = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Dismiss on outside touch and add shadow
        popup.elevation = 30f
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))


        view.findViewById<View>(R.id.Renameop).setOnClickListener {
            popup.dismiss()
            showRenameDialog(project)
        }

        view.findViewById<View>(R.id.Deleteop).setOnClickListener {
            popup.dismiss()
            requestDeleteProject(project)
        }


        popup.showAsDropDown(anchorView, 200, -anchorView.height / 4)
    }

    private fun showRenameDialog(project: File) {
        val input = EditText(this).apply {
            setText(project.name)
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.Renameop))
            .setView(input)
            .setPositiveButton(getString(R.string.Saveop)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(project.parent, newName)
                    if (project.renameTo(newFile)) loadProjects()
                }
            }
            .setNegativeButton(getString(R.string.Cancelop), null)
            .show()
    }

    private fun requestDeleteProject(proyecto: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.Deleteop))
            .setMessage("${getString(R.string.Deletesure)} ${proyecto.name}?")
            .setPositiveButton(getString(R.string.ConfirmDeleteop)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (proyecto.deleteRecursively()) {
                        withContext(Dispatchers.Main) { loadProjects() }
                    }
                }
            }
            .setNegativeButton(getString(R.string.No), null)
            .show()
    }

    // =========================================================================
    // FILE MANAGEMENT
    // =========================================================================

    private fun loadProjects() {
        lifecycleScope.launch(Dispatchers.IO) {
            val savePath = getExternalFilesDir("Projects") ?: return@launch
            val projects = savePath.listFiles { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
            withContext(Dispatchers.Main) {
                projectAdapter.updateProjects(projects)
            }
        }
    }

    private fun openProject(proyecto: File) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("PROJECT_PATH", proyecto.absolutePath)
        }
        startActivity(intent)
    }

    private fun setupProjectFromUri(uri: Uri) {
        val sourceDir = DocumentFile.fromTreeUri(this, uri) ?: return
        val destinationDir = File(getExternalFilesDir("Projects"), sourceDir.name ?: "New Project")

        if (destinationDir.exists()) {
            Toast.makeText(this, getString(R.string.Projectalreadyexist), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.ImportProject), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                copyDirectory(sourceDir, destinationDir)
                withContext(Dispatchers.Main) { loadProjects() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun copyDirectory(source: DocumentFile, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles().forEach { copyDirectory(it, File(dest, it.name)) }
        } else {
            contentResolver.openInputStream(source.uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun showGitHubDialog() {
        val sharedPref = getSharedPreferences("CodeAssistPrefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("GITHUB_TOKEN", "")
        if (!token.isNullOrEmpty()) {
            startActivity(Intent(this, GithubControlActivity::class.java))
        } else {
            val input = EditText(this).apply { hint = getString(R.string.Pastetoken) }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.Githubconection))
                .setView(input)
                .setPositiveButton(getString(R.string.Saveop)) { _, _ ->
                    sharedPref.edit().putString("GITHUB_TOKEN", input.text.toString()).apply()
                    startActivity(Intent(this, GithubControlActivity::class.java))
                }.show()

        }
    }
}

// =============================================================================
// COMPACT ADAPTER
// =============================================================================

class SimpleProjectAdapter(
    private val onProjectClick: (File) -> Unit,
    private val onProjectLongClick: (File, View) -> Unit
) : RecyclerView.Adapter<SimpleProjectAdapter.ViewHolder>() {

    private var projects = listOf<File>()

    fun updateProjects(n: List<File>) {
        projects = n
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = TextView(parent.context).apply {
            textSize = 15f
            setPadding(45, 30, 45, 30)
            setTextColor(Color.WHITE)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folderproject, 0, 0, 0)
            compoundDrawablePadding = 35

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
        val proj = projects[p]
        h.textView.text = proj.name
        h.itemView.setOnClickListener { onProjectClick(proj) }
        h.itemView.setOnLongClickListener {
            onProjectLongClick(proj, it)
            true
        }
    }

    override fun getItemCount() = projects.size
    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}