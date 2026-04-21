package com.jeansr.androideditor

import SyntaxColorInfo
import SyntaxHighlighter
import TabAdapter

import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.PopupWindow
import android.view.ViewGroup.LayoutParams
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
// ── NUEVO GIT: imports adicionales ──
import androidx.appcompat.app.AlertDialog

class EditorActivity : AppCompatActivity() {

    private val TAG = "EditorCompiler"

    // =========================================================================
    // STATE AND PROPERTIES
    // =========================================================================

    private enum class RenderMode { ORIGINAL, LINEAR, RELATIVE }
    private var currentRenderMode = RenderMode.LINEAR
    private var currentZeroDpBehavior = "wrap_content"
    private var controlPanel: LinearLayout? = null

    private lateinit var editorCodeArea: LineNumberEditText
    private lateinit var recyclerFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var explorerPanel: LinearLayout
    private lateinit var panelDivider: View
    private lateinit var codeScrollContainer: ScrollView
    private lateinit var previewContainer: PreviewXml
    private lateinit var rvTabs: RecyclerView
    private lateinit var tabAdapter: TabAdapter
    private lateinit var quickSymbolBar: LinearLayout
    private lateinit var tvConsole: TextView

    private lateinit var projectRoot: File
    private val expandedFolders = mutableSetOf<String>()
    private val openTabs = mutableListOf<File>()
    private var fileCurrentlyOpen: File? = null
    private val fileContentsMemory = mutableMapOf<String, String>()
    private var isShowingPreview = false

    private lateinit var aapt2File: File
    private lateinit var androidJarFile: File
    private lateinit var materialLibFile: File

    private lateinit var gitPanel: LinearLayout
    private lateinit var gitStatusBar: LinearLayout
    private lateinit var tvGitBranch: TextView
    private lateinit var tvGitChanges: TextView
    private lateinit var tvToolbarBranch: TextView
    private lateinit var dotGitPending: View
    private lateinit var tvGitPanelBranch: TextView
    private lateinit var tvGitPanelStatus: TextView
    private lateinit var etCommitMessage: EditText
    private lateinit var recyclerGitItems: RecyclerView
    private lateinit var tabGit: TabLayout
    private lateinit var btnCloseGitPanel: ImageButton
    private lateinit var btnGitPull: Button
    private lateinit var btnGitPush: Button
    private lateinit var btnGitSync: Button
    private lateinit var btnGitCommit: Button
    private lateinit var fragment_container: FrameLayout

    private lateinit var gitManager: GitManager
    private var gitRepoInfo: GitManager.RepoInfo? = null
    private var gitCurrentTab = 0
    private lateinit var gitItemAdapter: GitItemAdapter

    private val highlighter = SyntaxHighlighter()
    private val highlightingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var highlightingRunnable: Runnable? = null
    private lateinit var highlightingTextWatcher: android.text.TextWatcher

    // State variables to prevent multiple menus and actions from opening
    private var fileOnClipboard: File? = null
    private var clipboardAccion: String = "" // Guardará "copiar" o "cortar"

    private var mainPopup: PopupWindow? = null
    private var newPopup: PopupWindow? = null


    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        explorerPanel = findViewById(R.id.explorerPanel)
        panelDivider = findViewById(R.id.panelDivider)
        editorCodeArea = findViewById(R.id.editorCodeArea)
        recyclerFiles = findViewById(R.id.recyclerFiles)
        codeScrollContainer = findViewById(R.id.codeScrollContainer)
        previewContainer = findViewById(R.id.previewContainer)
        rvTabs = findViewById(R.id.rvTabs)
        setupTabsRecyclerView()
        quickSymbolBar = findViewById(R.id.quickSymbolBar)
        fragment_container = findViewById(R.id.fragment_container)

        configureConsole()

        val projectPath = intent.getStringExtra("PROJECT_PATH") ?: return
        projectRoot = File(projectPath)

        findViewById<ImageButton>(R.id.btnToggleExplorer).setOnClickListener {
            val visible = explorerPanel.visibility == View.VISIBLE
            explorerPanel.visibility = if (visible) View.GONE else View.VISIBLE
            panelDivider.visibility = if (visible) View.GONE else View.VISIBLE
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveActualFile() }
        findViewById<ImageButton>(R.id.btnPreview).setOnClickListener { switchPreview() }

        configureSymbolBar()
        setupTabsRecyclerView()

        fileAdapter = FileAdapter(
            onClick = { file -> handleFileTreeClick(file) },
            onLongClick = { view, file -> handleFileTreeLongClick(view, file) }
        )

        recyclerFiles.layoutManager = LinearLayoutManager(this)
        recyclerFiles.adapter = fileAdapter

        refreshFileTree()
        inicializarGit()
        setupSyntaxHighlighting()
        setupScrollListener()

        lifecycleScope.launch(Dispatchers.IO) { setupCompiler() }

        // --- PREVIEW CONTAINER ---
        previewContainer.onModeChanged = { modo ->
            currentRenderMode = when(modo) {
                "ORIGINAL" -> RenderMode.ORIGINAL
                "RELATIVE" -> RenderMode.RELATIVE
                else -> RenderMode.LINEAR
            }
            if (isShowingPreview) {
                compilePreviewWithResources()
            }
        }


    }



    private fun setupTabsRecyclerView() {
        tabAdapter = TabAdapter(
            onTabClick = { openFileInTab(it) },
            onTabClose = { colseTab(it) }
        )

        rvTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvTabs.adapter = tabAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                tabAdapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                openTabs.clear()
                openTabs.addAll(tabAdapter.openTabs)
            }
        })

        itemTouchHelper.attachToRecyclerView(rvTabs)
    }

    // =========================================================================
    // RENDERING ENGINE
    // =========================================================================
    private fun log(mensaje: String, esError: Boolean = false) {

        if (esError) Log.e(TAG, mensaje) else Log.d(TAG, mensaje)

        runOnUiThread { tvConsole.append("$mensaje\n") }

    }


    private fun compilePreviewWithResources() {
        if (!aapt2File.exists() || !androidJarFile.exists()) return
        val rawXml = editorCodeArea.text.toString()

        log("\n--- [STARTING COMPILATION] ---")

        lifecycleScope.launch(Dispatchers.IO) {
            val tiempoInicio = System.currentTimeMillis()
            val workspace = File(cacheDir, "build_workspace").apply { deleteRecursively(); mkdirs() }
            val buildResDir = File(workspace, "res")
            val projResDir = File(projectRoot, "app/src/main/res")

            if (projResDir.exists()) {
                projResDir.copyRecursively(buildResDir, overwrite = true)
            } else { buildResDir.mkdirs() }

            val processedXml = processXmlForPreview(rawXml)
            File(buildResDir, "layout").apply { mkdirs() }
            File(buildResDir, "layout/preview_layout.xml").writeText(processedXml)

            val manifestFile = File(workspace, "AndroidManifest.xml")
            manifestFile.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.material">
                
                <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />
                <application android:theme="@style/Theme.Material3.DayNight.NoActionBar" />

            </manifest>
            """.trimIndent())

            val outputApk = File(workspace, "preview.apk")
            val compiledZip = File(workspace, "compiled.zip")

            val resCompile = runShell("${aapt2File.absolutePath} compile --dir ${buildResDir.absolutePath} -o ${compiledZip.absolutePath}")
            if (!resCompile.success) { log("[ERROR COMPILE]\n${resCompile.error}", true); return@launch }

            val linkCmd = "${aapt2File.absolutePath} link -I ${androidJarFile.absolutePath} -I ${materialLibFile.absolutePath} -o ${outputApk.absolutePath} --manifest ${manifestFile.absolutePath} ${compiledZip.absolutePath} --auto-add-overlay --allow-reserved-package-id --no-static-lib-packages"
            val resLink = runShell(linkCmd)
            if (!resLink.success) { log("[ERROR LINK]\n${resLink.error}", true); return@launch }

            log("> [SUCCESS] Mini-APK generated in ${System.currentTimeMillis() - tiempoInicio}ms.")
            withContext(Dispatchers.Main) { injectAndDraw(outputApk.absolutePath) }
        }
    }

    private fun injectAndDraw(apkPath: String) {
        try {
            log("> Injecting APK...")
            val customAssetManager = AssetManager::class.java.getConstructor().newInstance()
            val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            addAssetPathMethod.invoke(customAssetManager, apkPath)
            addAssetPathMethod.invoke(customAssetManager, androidJarFile.absolutePath)
            addAssetPathMethod.invoke(customAssetManager, materialLibFile.absolutePath)

            val customResources = Resources(customAssetManager, resources.displayMetrics, resources.configuration)
            val resId = customResources.getIdentifier("preview_layout", "layout", "com.google.android.material")
            if (resId == 0) { log("[ERROR] Layout not found.", true); return }

            val customContext = object : ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar) {
                override fun getResources() = customResources
                override fun getAssets() = customAssetManager
                override fun getTheme(): Resources.Theme {
                    val t = super.getTheme()
                    val themeId = customResources.getIdentifier("Theme.Material3.Light.NoActionBar", "style", "com.google.android.material")
                    if (themeId != 0) t.applyStyle(themeId, true)
                    return t
                }
            }

            runOnUiThread {
                try {
                    // 1. Clear the custom component (PreviewXml)
                    previewContainer.clear()

                    val inflater = LayoutInflater.from(customContext).cloneInContext(customContext)

                    inflater.factory2 = object : LayoutInflater.Factory2 {
                        override fun onCreateView(parent: View?, name: String, context: Context, attrs: android.util.AttributeSet): View? {
                            if (name == "androidx.camera.view.PreviewView" || name == "SurfaceView" || name == "TextureView") {
                                return TextView(context).apply { text = "📷 Vista de Cámara"; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE); gravity = Gravity.CENTER }
                            }
                            if (name == "fragment" || name == "androidx.fragment.app.FragmentContainerView") {
                                return TextView(context).apply { text = "🧩 Fragmento"; setBackgroundColor(Color.LTGRAY); setTextColor(Color.DKGRAY); gravity = Gravity.CENTER }
                            }

                            return when (name) {
                                "ImageView", "ImageButton", "com.google.android.material.floatingactionbutton.FloatingActionButton", "androidx.appcompat.widget.AppCompatImageView", "androidx.appcompat.widget.AppCompatImageButton" -> {
                                    val isBtn = name == "ImageButton" || name == "androidx.appcompat.widget.AppCompatImageButton" || name.contains("Floating")
                                    val view = if (isBtn) ImageButton(context, attrs) else ImageView(context, attrs)
                                    val srcCompat = attrs.getAttributeValue("http://schemas.android.com/apk/res-auto", "srcCompat")
                                    if (srcCompat != null) {
                                        try {
                                            val cResId = context.resources.getIdentifier(srcCompat.replace("@", ""), null, null)
                                            if (cResId != 0) {
                                                view.setImageDrawable(androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, cResId, context.theme))
                                                if (name.contains("Floating")) view.scaleType = ImageView.ScaleType.CENTER_INSIDE
                                            }
                                        } catch (e: Exception) {}
                                    }
                                    if (name.contains("Floating")) {
                                        view.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#4285F4")) }
                                    }
                                    view
                                }

                                "Button", "com.google.android.material.button.MaterialButton", "androidx.appcompat.widget.AppCompatButton" -> {
                                    Button(context, attrs).apply {
                                        val tint = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "backgroundTint") ?: attrs.getAttributeValue("http://schemas.android.com/apk/res-auto", "backgroundTint")
                                        val shape = android.graphics.drawable.GradientDrawable().apply {
                                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                                            cornerRadius = dpToPx(24f).toFloat()
                                            if (tint != null) {
                                                try {
                                                    if (tint.startsWith("#")) setColor(Color.parseColor(tint))
                                                    else {
                                                        val cResId = context.resources.getIdentifier(tint.replace("@", ""), null, null)
                                                        if (cResId != 0) setColor(context.getColor(cResId)) else setColor(Color.LTGRAY)
                                                    }
                                                } catch (e: Exception) { setColor(Color.LTGRAY) }
                                            } else {
                                                val tv = android.util.TypedValue()
                                                if (context.theme.resolveAttribute(context.resources.getIdentifier("colorPrimary", "attr", "com.google.android.material"), tv, true)) setColor(tv.data) else setColor(Color.LTGRAY)
                                            }
                                        }
                                        background = shape
                                        gravity = Gravity.CENTER
                                        isAllCaps = false
                                        setPadding(dpToPx(16f), 0, dpToPx(16f), 0)

                                        val textColor = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "textColor")
                                        if (textColor != null) {
                                            try {
                                                if (textColor.startsWith("#")) setTextColor(Color.parseColor(textColor))
                                                else {
                                                    val cResId = context.resources.getIdentifier(textColor.replace("@", ""), null, null)
                                                    if (cResId != 0) setTextColor(context.getColor(cResId))
                                                }
                                            } catch (e: Exception) {}
                                        } else {
                                            val tv = android.util.TypedValue()
                                            if (context.theme.resolveAttribute(context.resources.getIdentifier("colorOnPrimary", "attr", "com.google.android.material"), tv, true)) setTextColor(tv.data) else setTextColor(Color.WHITE)
                                        }
                                    }
                                }
                                "TextView", "com.google.android.material.textview.MaterialTextView", "androidx.appcompat.widget.AppCompatTextView" -> TextView(context, attrs)
                                "EditText", "com.google.android.material.textfield.TextInputEditText", "androidx.appcompat.widget.AppCompatEditText" -> EditText(context, attrs)
                                else -> null
                            }
                        }
                        override fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): View? = null
                    }


                    val rootView = inflater.inflate(resId, previewContainer.contentArea, false)
                    rootView.layoutParams = FrameLayout.LayoutParams(-1, -1) // -1 es Match Parent

                    if (rootView is ScrollView || rootView is androidx.core.widget.NestedScrollView) {
                        rootView.isVerticalScrollBarEnabled = false
                        rootView.overScrollMode = View.OVER_SCROLL_NEVER
                    }

                    // Pass the final view to the component
                    previewContainer.setPreviewView(rootView)

                    // Force PreviewXml to recalculate device size
                    previewContainer.requestLayout()
                    previewContainer.invalidate()

                    log("> [SUCCESS] Interface rendered and adjusted.")
                } catch (e: Exception) { log("[[ERROR] Inflation failed] ${e.message}", true) }
            }
        } catch (e: Exception) { log("[INJECTION ERROR] ${e.message}", true) }
    }


    private fun processXmlForPreview(rawXml: String): String {
        if (currentRenderMode == RenderMode.ORIGINAL || !rawXml.contains("ConstraintLayout")) return rawXml
        var xml = rawXml
        if (currentRenderMode == RenderMode.LINEAR) {
            xml = xml.replace("<androidx.constraintlayout.widget.ConstraintLayout", "<LinearLayout android:orientation=\"vertical\"")
            xml = xml.replace("</androidx.constraintlayout.widget.ConstraintLayout>", "</LinearLayout>")
        } else if (currentRenderMode == RenderMode.RELATIVE) {
            xml = xml.replace("androidx.constraintlayout.widget.ConstraintLayout", "RelativeLayout")
        }
        xml = xml.replace("""android:layout_width="0dp"""", """android:layout_width="$currentZeroDpBehavior"""")
        xml = xml.replace("""android:layout_height="0dp"""", """android:layout_height="$currentZeroDpBehavior"""")
        xml = xml.replace(Regex("""app:layout_constraint[a-zA-Z0-9_]+=".*?""""), "")
        return xml
    }

    private fun switchPreview() {
        isShowingPreview = !isShowingPreview
        codeScrollContainer.visibility = if (isShowingPreview) View.GONE else View.VISIBLE
        previewContainer.visibility = if (isShowingPreview) View.VISIBLE else View.GONE
        tvConsole.visibility = if (isShowingPreview) View.VISIBLE else View.GONE
        if (isShowingPreview) {
            compilePreviewWithResources()
        }
    }

    // =========================================================================
    // FILE MANAGEMENT, GIT, AND UTILITIES
    // =========================================================================

    private var fullFileColorMap: List<SyntaxColorInfo>? = null
   // private var backgroundJob: kotlinx.coroutines.Job? = null

    /*
    private fun iniciarResaltadoHibrido(content: String, ext: String) {
        // 1. PINTADO RÁPIDO (Lo que el usuario ve ya mismo)
        ejecutarResaltadoViewport()

        // 2. ESCANEO TOTAL EN SEGUNDO PLANO
        backgroundJob?.cancel()
        backgroundJob = lifecycleScope.launch(Dispatchers.Default) {
            val nuevoMapa = highlighter.generateColorMap(content, ext)
            withContext(Dispatchers.Main) {
                fullFileColorMap = nuevoMapa
                log("> Jarvis Engine: Mapa de sintaxis completo.")
            }
        }
    }

     */

    private fun setupSyntaxHighlighting() {
        highlightingTextWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                highlightingRunnable?.let { highlightingHandler.removeCallbacks(it) }
                highlightingRunnable = Runnable { s?.let { runViewportHighlighting() } }
                highlightingHandler.postDelayed(highlightingRunnable!!, 150)
            }
        }
        editorCodeArea.addTextChangedListener(highlightingTextWatcher)
    }

    private fun runViewportHighlighting() {
        val layout = editorCodeArea.layout ?: return
        val content = editorCodeArea.text ?: return
        val ext = fileCurrentlyOpen?.extension ?: "txt"

        val scrollY = codeScrollContainer.scrollY
        val height = codeScrollContainer.height
        val firstLine = layout.getLineForVertical(scrollY)
        val lastLine = layout.getLineForVertical(scrollY + height)

        val startOffset = layout.getLineStart((firstLine - 10).coerceAtLeast(0))
        val endOffset = layout.getLineEnd((lastLine + 50).coerceAtMost(editorCodeArea.lineCount - 1))

        editorCodeArea.removeTextChangedListener(highlightingTextWatcher)

        if (fullFileColorMap != null) {

            val visibleColors = fullFileColorMap!!.filter { it.start >= startOffset && it.end <= endOffset }

            val oldSpans = content.getSpans(startOffset, endOffset, ForegroundColorSpan::class.java)
            oldSpans.forEach { content.removeSpan(it) }

            for (info in visibleColors) {
                content.setSpan(ForegroundColorSpan(info.color), info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {

            highlighter.applyHighlighting(content, ext, startOffset, endOffset)
        }

        editorCodeArea.addTextChangedListener(highlightingTextWatcher)
    }

    private fun setupScrollListener() {
        var lastScrollY = -1

        codeScrollContainer.viewTreeObserver.addOnScrollChangedListener {
            val currentScrollY = codeScrollContainer.scrollY


            if (Math.abs(currentScrollY - lastScrollY) > 20) {
                lastScrollY = currentScrollY

                highlightingHandler.removeCallbacks(highlightingRunnable ?: Runnable {})
                highlightingRunnable = Runnable {
                    if (!isShowingPreview) runViewportHighlighting()
                }


                highlightingHandler.postDelayed(highlightingRunnable!!, 100)
            }
        }
    }

    private fun switchFocusToTab(file: File) {
        fileCurrentlyOpen = file
        val contenido = fileContentsMemory[file.absolutePath] ?: ""
        editorCodeArea.setText(contenido)

        // Syntax highlighting
        editorCodeArea.post { runViewportHighlighting() }

        // Update adapter visual state (Active tab)
        tabAdapter.updateData(openTabs, file)
        val index = openTabs.indexOf(file)
        if (index != -1) rvTabs.smoothScrollToPosition(index)

        // Sync preview
        if (isShowingPreview) {
            if (file.extension == "xml") compilePreviewWithResources()
            else previewContainer.clear()
        }
    }

    private fun saveActualFile() {
        fileCurrentlyOpen?.let { file ->
            val text = editorCodeArea.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                file.writeText(text)
                withContext(Dispatchers.Main) { Toast.makeText(this@EditorActivity, "Guardado", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun refreshFileTree() {
        val lista = buildTree(projectRoot, 0)
        fileAdapter.updateFiles(lista, expandedFolders)
    }

    private fun buildTree(dir: File, depth: Int): List<Pair<File, Int>> {
        val lista = mutableListOf<Pair<File, Int>>()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return lista
        for (f in files) {
            lista.add(f to depth)
            if (f.isDirectory && expandedFolders.contains(f.absolutePath)) lista.addAll(buildTree(f, depth + 1))
        }
        return lista
    }

    private fun handleFileTreeClick(file: File) {
        if (file.isDirectory) {
            if (expandedFolders.contains(file.absolutePath)) expandedFolders.remove(file.absolutePath)
            else expandedFolders.add(file.absolutePath)
            refreshFileTree()
        } else openFileInTab(file)
    }
    //////////////////////////////////////////////////
    //------------------------------------------//

    private fun runFileAction(action: String, destinyFile: File) {
        when (action) {
            "copy", "cut" -> {
                fileOnClipboard = destinyFile
                clipboardAccion = action
                if (action == "copy") {
                    log("Copied to clipboard ${destinyFile.name}")
                    Toast.makeText(this, "${getString(R.string.Copied)}: ${destinyFile.name}", Toast.LENGTH_SHORT).show()
                }else{
                    log("Cut to clipboard ${destinyFile.name}")
                    Toast.makeText(this, "${getString(R.string.Cut)}: ${destinyFile.name}", Toast.LENGTH_SHORT).show()
                }

            }

            "paste" -> {
                val origen = fileOnClipboard
                if (origen == null || !origen.exists()) {
                    log("Clipboard is empty or the original file no longer exists.", true)
                    return
                }

                val fatherDirectory = if (destinyFile.isDirectory) destinyFile else destinyFile.parentFile
                val newFile = File(fatherDirectory, origen.name)

                if (newFile.exists()) {
                    log("A file with the name '${origen.name}' already exists in this folder.", true)
                    return
                }

                Toast.makeText(this,getString(R.string.Paste), Toast.LENGTH_SHORT).show()

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (clipboardAccion == "copy") {
                            if (origen.isDirectory) {
                                origen.copyRecursively(newFile, true)
                            } else {
                                origen.copyTo(newFile, true)
                            }
                            withContext(Dispatchers.Main) {
                                log("Successfully copied to: ${fatherDirectory.name}")
                                refreshFileTree()
                            }
                        }
                        else if (clipboardAccion == "cut") {
                            val fastMoveElement = origen.renameTo(newFile)
                            if (!fastMoveElement) {
                                if (origen.isDirectory) {
                                    origen.copyRecursively(newFile, true)
                                    origen.deleteRecursively()
                                } else {
                                    origen.copyTo(newFile, true)
                                    origen.delete()
                                }
                            }
                            withContext(Dispatchers.Main) {
                                log("Successfully moved to: ${fatherDirectory.name}")
                                fileOnClipboard = null
                                clipboardAccion = ""
                                refreshFileTree()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            log("Error pasting: ${e.message}", true)
                        }
                    }
                }
            }

            "Delete" -> {
                val tipo = if (destinyFile.isDirectory) "directory" else "file"

                android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.Delete))
                    .setMessage("${getString(R.string.Deletesureelement)} $tipo '${destinyFile.name}'?\n${getString(R.string.Actionundo)}")
                    .setPositiveButton(getString(R.string.Delete)) { _, _ ->

                        Toast.makeText(this,getString(R.string.Deleting), Toast.LENGTH_SHORT).show()

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val successful = if (destinyFile.isDirectory) {
                                    destinyFile.deleteRecursively()
                                } else {
                                    destinyFile.delete()

                                }
                                withContext(Dispatchers.Main) {
                                    if (successful) {
                                        log("$tipo '${destinyFile.name}' has been deleted.")
                                        refreshFileTree()
                                    } else {
                                        log("Error: Could not delete $tipo.", true)
                                    }
                                }
                                // ── TAB PROTECTION (Centralized) ──

                                // 1. Filter which open files have just been deleted from disk
                                val closeFiles = openTabs.filter {
                                    it.absolutePath == destinyFile.absolutePath ||
                                            it.absolutePath.startsWith(destinyFile.absolutePath + "/")
                                }

                                // 2. Iterate and delegate the work to colseTab function
                                closeFiles.forEach { archivo ->
                                    colseTab(archivo)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Error trying to delete: ${e.message}", true)
                                }
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.Deleteop), null)
                    .show()
            }
        }
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this)
        input.setText(file.name)
        input.selectAll()
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.Rename))
            .setView(input)
            .setPositiveButton(getString(R.string.Saveop)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    val newFile = File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        log("Rename to: $newName")
                        refreshFileTree()
                    } else {
                        log("Error renaming file.", true)
                    }
                }
            }
            .setNegativeButton(getString(R.string.Cancelop), null)
            .show()
    }
    //-----------------------------------------//


    private fun handleFileTreeLongClick(anchorView: View, archivoDestino: File) {
        newPopup?.dismiss()
        mainPopup?.dismiss()

        val inflater = layoutInflater
        val menuView = inflater.inflate(R.layout.layout_contex_menu, null)

        mainPopup = PopupWindow(
            menuView,
            resources.getDimensionPixelSize(R.dimen.menu_ancho), // Define en dimens.xml ej. 200dp
            LayoutParams.WRAP_CONTENT,
            true // true = se cierra al tocar fuera
        )
        mainPopup?.elevation = 16f

        // --- LÓGICA DE DIRECTORIOS ---
        val destinyDirectory = if (archivoDestino.isDirectory) archivoDestino else archivoDestino.parentFile
        // --- EVENTOS DEL MENÚ PRINCIPAL ---
        // 1. Desplegar el submenú "Nuevo"
        menuView.findViewById<View>(R.id.itemMenuNuevo).setOnClickListener { viewNuevo ->
            showNewSubMenu(viewNuevo, destinyDirectory)
        }
        // 2. Acciones estándar
        menuView.findViewById<View>(R.id.itemMenuCortar).setOnClickListener {
            mainPopup?.dismiss()
            runFileAction("cut", archivoDestino)
        }
        menuView.findViewById<View>(R.id.itemMenuCopiar).setOnClickListener {
            mainPopup?.dismiss()
            runFileAction("copy", archivoDestino)
        }
        menuView.findViewById<View>(R.id.itemMenuPegar).setOnClickListener {
            mainPopup?.dismiss()
            runFileAction("paste", archivoDestino)
        }
        menuView.findViewById<View>(R.id.itemMenuRenombrar).setOnClickListener {
            mainPopup?.dismiss()
            showRenameDialog(archivoDestino)
        }
        menuView.findViewById<View>(R.id.itemMenuEliminar).setOnClickListener {
            mainPopup?.dismiss()
            runFileAction("Delete", archivoDestino)
        }

        // Mostrar menú pegado al ítem del RecyclerView
        mainPopup?.showAsDropDown(anchorView, 20, -anchorView.height / 2)
    }

    private fun showNewSubMenu(newAnchor: View, destinyDirectory: File) {
        newPopup?.dismiss()

        val newView = layoutInflater.inflate(R.layout.layout_new_menu, null)

        newPopup = PopupWindow(
            newView,
            resources.getDimensionPixelSize(R.dimen.submenu_ancho), // ej. 220dp
            LayoutParams.WRAP_CONTENT,
            true
        )
        newPopup?.elevation = 20f

        newPopup?.setOnDismissListener { mainPopup?.dismiss() }

        // ---GENERATION EVENTS---
        newView.findViewById<View>(R.id.itemNuevoKotlin).setOnClickListener {
            promptNameAndGenerate("kotlin", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoJava).setOnClickListener {
            promptNameAndGenerate("java", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoLayout).setOnClickListener {
            promptNameAndGenerate("layout", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoDirectorio).setOnClickListener {
            promptNameAndGenerate("dir", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoArchivo).setOnClickListener {
            promptNameAndGenerate("file", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoActivity).setOnClickListener {
            promptNameAndGenerate("activity", destinyDirectory)
        }
        newView.findViewById<View>(R.id.itemNuevoFragment).setOnClickListener {
            promptNameAndGenerate("fragment", destinyDirectory)
        }

        // Show to the right of the 'New' option in the main menu
        newPopup?.showAsDropDown(newAnchor, newAnchor.width, -newAnchor.height)
    }

    private fun promptNameAndGenerate(tipo: String, directorioPadre: File) {
        newPopup?.dismiss()
        mainPopup?.dismiss()

        // Using an EditText Dialog here for name input
        val input = android.widget.EditText(this)
        input.hint = "Ej: MainActivity"

        android.app.AlertDialog.Builder(this)
            .setTitle(if (tipo == "dir") getString(R.string.Newfolder) else getString(R.string.Newfile))
            .setView(input)
            .setPositiveButton(getString(R.string.Create)) { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    generateFileFromTemplate(tipo, directorioPadre, nombre)
                }
            }
            .setNegativeButton(getString(R.string.Cancelop), null)
            .show()
    }

    private fun generateFileFromTemplate(type: String, fatherDirectory: File, nombre: String) {
        try {
            when (type) {
                "kotlin" -> {
                    val file = File(fatherDirectory, "$nombre.kt")
                    file.writeText("package your.package.here\n\nclass $nombre {\n    // TODO: Implement logic\n}")
                    openFileInTab(file)
                }
                "java" -> {
                    val file = File(fatherDirectory, "$nombre.java")
                    file.writeText("package your.package.here;\n\npublic class $nombre {\n    public $nombre() {\n    }\n}")
                    openFileInTab(file)
                }
                "layout" -> {
                    val file = File(fatherDirectory, "${nombre.lowercase()}.xml")
                    file.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n    \n</LinearLayout>")
                    openFileInTab(file)
                }
                "activity" -> {
                    val file = File(fatherDirectory, "${nombre}Activity.kt")
                    file.writeText("import android.os.Bundle\nimport androidx.appcompat.app.AppCompatActivity\n\nclass ${nombre}Activity : AppCompatActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}")
                    openFileInTab(file)
                }
                "fragment" -> {
                    val file = File(fatherDirectory, "${nombre}Fragment.kt")
                    file.writeText("import android.os.Bundle\nimport android.view.View\nimport androidx.fragment.app.Fragment\n\nclass ${nombre}Fragment : Fragment() {\n    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {\n        super.onViewCreated(view, savedInstanceState)\n    }\n}")
                    openFileInTab(file)
                }
                "file" -> {
                    val file = File(fatherDirectory, nombre)
                    file.createNewFile()
                    openFileInTab(file)
                }
                "dir" -> {
                    File(fatherDirectory, nombre).mkdirs()
                }
            }
            refreshFileTree() // Tu función para recargar el RecyclerView
        } catch (e: Exception) {
            log("Error creating: ${e.message}", true)
        }
    }
    ////////////////////////////////////////
    private fun openFileInTab(file: File) {
        if (!editorCodeArea.isEnabled) editorCodeArea.isEnabled = true
        fileCurrentlyOpen?.let { fileContentsMemory[it.absolutePath] = editorCodeArea.text.toString() }

        val index = openTabs.indexOf(file)
        if (index == -1) {
            openTabs.add(file)
            lifecycleScope.launch(Dispatchers.IO) {
                val content = try { file.readText() } catch (e: Exception) { "" }
                withContext(Dispatchers.Main) {
                    fileContentsMemory[file.absolutePath] = content
                    fileCurrentlyOpen = file
                    tabAdapter.updateData(openTabs, file)
                    switchFocusToTab(file)
                }
            }
        } else {
            switchFocusToTab(file)
        }
    }

    private fun colseTab(file: File) {
        val index = openTabs.indexOf(file)
        if (index == -1) return

        openTabs.removeAt(index)
        fileContentsMemory.remove(file.absolutePath)

        if (fileCurrentlyOpen == file) {
            if (openTabs.isNotEmpty()) {
                val nextIndex = if (index < openTabs.size) index else openTabs.size - 1
                switchFocusToTab(openTabs[nextIndex])
            } else {
                fileCurrentlyOpen = null
                editorCodeArea.setText("")
                editorCodeArea.isEnabled = false
                tabAdapter.updateData(openTabs, null)
            }
        } else {
            tabAdapter.updateData(openTabs, fileCurrentlyOpen)
        }
    }

    private fun inicializarGit() {
        gitManager = GitManager(projectRoot)
        gitPanel = findViewById(R.id.gitPanel)
        gitStatusBar = findViewById(R.id.gitStatusBar)
        tvGitBranch = findViewById(R.id.tvGitBranch)
        tvGitChanges = findViewById(R.id.tvGitChanges)
        tvToolbarBranch = findViewById(R.id.tvToolbarBranch)
        dotGitPending = findViewById(R.id.dotGitPending)
        tvGitPanelBranch = findViewById(R.id.tvGitPanelBranch)
        tvGitPanelStatus = findViewById(R.id.tvGitPanelStatus)
        etCommitMessage = findViewById(R.id.etCommitMessage)
        recyclerGitItems = findViewById(R.id.recyclerGitItems)
        tabGit = findViewById(R.id.tabGit)
        btnCloseGitPanel = findViewById(R.id.btnCloseGitPanel)
        btnGitPull = findViewById(R.id.btnGitPull)
        btnGitPush = findViewById(R.id.btnGitPush)
        btnGitSync = findViewById(R.id.btnGitSync)
        btnGitCommit = findViewById(R.id.btnGitCommit)

        gitItemAdapter = GitItemAdapter(
            onFileClick = { item ->
                if (item is GitItemAdapter.GitItem.FileItem) {
                    val f = File(projectRoot, item.file.path)
                    if (f.exists()) openFileInTab(f)
                }
            },
            onFileStage = { item ->
                if (item is GitItemAdapter.GitItem.FileItem) {
                    lifecycleScope.launch {
                        val res = gitManager.stageFile(item.file.path)
                        if (!res.success) log("[Git] Error indexing: ${res.error}", true)
                        refreshGitStatus()
                    }
                }
            }
        )

        recyclerGitItems.layoutManager = LinearLayoutManager(this)
        recyclerGitItems.adapter = gitItemAdapter

        tabGit.addTab(tabGit.newTab().setText(getString(R.string.Changes)))
        tabGit.addTab(tabGit.newTab().setText(getString(R.string.History)))
        tabGit.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                gitCurrentTab = tab?.position ?: 0
                refreshGitList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<LinearLayout>(R.id.gitOpcions).setOnClickListener { toggleGitPanel() }
        btnCloseGitPanel.setOnClickListener { closeGitPanel() }

        // Connecting buttons with execution functions
        btnGitCommit.setOnClickListener { executeCommit() }
        btnGitPull.setOnClickListener { executePull() }
        btnGitPush.setOnClickListener { executePush() }
        btnGitSync.setOnClickListener { executeSync() }

        lifecycleScope.launch { cargarInfoRepoYActualizar() }
    }

    private fun toggleGitPanel() { if (gitPanel.visibility == View.VISIBLE) closeGitPanel() else openGitPanel() }
    private fun openGitPanel() { gitPanel.visibility = View.VISIBLE; gitPanel.alpha = 0f; gitPanel.animate().alpha(1f).setDuration(180).start(); refreshGitStatus() }
    private fun closeGitPanel() { gitPanel.animate().alpha(0f).setDuration(150).withEndAction { gitPanel.visibility = View.GONE }.start() }


    private fun refreshGitStatus() {
        lifecycleScope.launch {
            val cambios = gitManager.getChangedFiles()
            withContext(Dispatchers.Main) {
                tvGitChanges.text = "${cambios.size} ${getString(R.string.Changes)}"
                dotGitPending.visibility = if (cambios.isNotEmpty()) View.VISIBLE else View.GONE
                refreshGitList()
            }
        }
    }
    private fun executePull() {
        val info = gitRepoInfo ?: return

        // Retrieve the token saved in SharedPreferences
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        if (token.isBlank()) {
            log("[Git] Error: No GitHub Token configured.", true)
            showTokenDialog()
            return
        }

        lifecycleScope.launch {
            log("> [Git] Starting Pull from origin/${info.branch}...")
            runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpulltxt)} ${info.branch}...", Toast.LENGTH_SHORT).show() }

            val res = gitManager.pull(token, info.owner, info.repoName, info.branch)

            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Pull done.\n${res.output}")
                    runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpulldone)}.\n${res.output}", Toast.LENGTH_SHORT).show() }
                    refreshFileTree()
                    refreshGitStatus()
                } else {
                    log("[Git] Error on Pull: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpullerror)} ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                    handlePullResult(res)
                }
            }
        }
    }

    private fun handlePullResult(result: GitManager.GitResult) {
        if (!result.success && result.error.contains("conflict", ignoreCase = true)) {
            lifecycleScope.launch {
                val conflictos = gitManager.getConflictingFiles()
                if (conflictos.isNotEmpty()) {
                    showConflictSelectionDialog(conflictos)
                }
            }
        }
    }

    private fun showConflictSelectionDialog(lista: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.Conflictdetected))
            .setItems(lista.toTypedArray()) { _, which ->
                val file = lista[which]
                openFragmentMerge(file)
            }
            .setNegativeButton(getString(R.string.Cancelop), null)
            .show()
    }

    private fun openFragmentMerge(path: String) {
        val frag = MergeResolverFragment().apply {
            arguments = Bundle().apply { putString("file_path", path) }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag)
            .addToBackStack(null)
            .commit()
    }

    private fun refreshGitList() {
        lifecycleScope.launch {
            val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

            if (gitCurrentTab == 0) {
                // Tab "Changes"
                val files = gitManager.getChangedFiles()
                withContext(Dispatchers.Main) {
                    gitItemAdapter.showFiles(files)
                }
            } else {
                // Tab "History"
                val commits = if (gitRepoInfo != null && gitRepoInfo!!.isGitRepo && token.isNotBlank()) {
                    gitManager.getGitHubCommits(gitRepoInfo!!.owner, gitRepoInfo!!.repoName, token, gitRepoInfo!!.branch)
                } else {
                    gitManager.getLocaleCommits()
                }
                withContext(Dispatchers.Main) {
                    gitItemAdapter.showCommits(commits)
                }
            }
        }
    }

    private fun executeCommit() {
        val msg = etCommitMessage.text.toString().trim()
        if (msg.isEmpty()) return
        saveActualFile()

        lifecycleScope.launch {
            log("> [Git] Executing Commit...")
            runOnUiThread { Toast.makeText(this@EditorActivity, getString(R.string.Commitexecute), Toast.LENGTH_SHORT).show() }
            val res = gitManager.commit(msg)
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Commit Done")
                    etCommitMessage.setText("")
                    refreshGitStatus()
                    runOnUiThread { Toast.makeText(this@EditorActivity, getString(R.string.Commitdone), Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Commit Error: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity,"${getString(R.string.Commiterror)} ${res.error}\n${res.output}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun executePush() {
        val info = gitRepoInfo ?: return
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        lifecycleScope.launch {
            log("[Git] Starting Push from origin/${info.branch}...")
            val res = gitManager.push(token, info.owner, info.repoName, info.branch)
            runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpushtxt)}${info.branch}...", Toast.LENGTH_SHORT).show() }
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Push done\n${res.output}")
                    refreshGitStatus()
                    runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpushdone)}\n${res.output}", Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Push Error: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpusherror)} ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private fun executeSync() {
        val info = gitRepoInfo ?: return
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        lifecycleScope.launch {
            log("> [Git] Starting Synchronization (Pull + Push)...")
            runOnUiThread { Toast.makeText(this@EditorActivity,getString(R.string.Gitpushpull), Toast.LENGTH_SHORT).show() }
            val res = gitManager.sync(token, info.owner, info.repoName, info.branch)
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Synchronization done")
                    refreshFileTree()
                    refreshGitStatus()
                    runOnUiThread { Toast.makeText(this@EditorActivity,getString(R.string.Gitpushpulldone), Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Synchronization Error: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "${getString(R.string.Gitpushpullerror)} ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private suspend fun cargarInfoRepoYActualizar() {
        gitRepoInfo = gitManager.obtenerInfoRepo()
        withContext(Dispatchers.Main) {
            val info = gitRepoInfo ?: return@withContext
            if (!info.isGitRepo) {
                tvToolbarBranch.text = "no-git"
                return@withContext
            }
            tvToolbarBranch.text = info.branch
            tvGitBranch.text = "⎇ ${info.branch}"
            tvGitPanelBranch.text = "⎇ ${info.branch}"
            gitStatusBar.visibility = View.VISIBLE

            val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

            if (token.isNotBlank()) {
                // 1. Retrieve the GitHub user
                val user = gitManager.obtenerUsuarioGitHub(token)

                if (user.isNotEmpty()) {
                    tvGitPanelStatus.text = "✓ @$user"

                    // 2. Optional: Retrieve the primary email from the API
                    // If the API doesn't provide the email (e.g., if it's private), we use a default one
                    val correoAsociado = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_EMAIL", "$user@users.noreply.github.com")
                    // 3. Applying the identity to the repository!
                    lifecycleScope.launch {
                        val configRes = gitManager.configurarIdentidad(user, correoAsociado!!)
                        if (!configRes.success) {
                            log("Warning: Could not configure commit signing.", true)
                        }
                    }
                } else {
                    tvGitPanelStatus.text = "⚠️ ${getString(R.string.Tokeninvalid)}"
                }
            } else {
                showTokenDialog()
            }
        }
        refreshGitStatus()
    }

    private fun showTokenDialog() {
        val input = EditText(this).apply { hint = "ghp_xxxxxxxxxxxx" }
        android.app.AlertDialog.Builder(this).setTitle("Token GitHub").setView(input).setPositiveButton("OK") { _, _ ->
            val t = input.text.toString(); if (t.isNotBlank()) { getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("GITHUB_TOKEN", t).apply(); lifecycleScope.launch { cargarInfoRepoYActualizar() } }
        }.show()
    }

    private fun configureConsole() {
        tvConsole = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(150f))
            setBackgroundColor(Color.parseColor("#0C0C0C"))
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 10f; setPadding(16, 16, 16, 16)
            text = "> Engine: Done.\n"; visibility = View.GONE
        }
        val mainContainer = codeScrollContainer.parent as ViewGroup
        mainContainer.addView(tvConsole, mainContainer.indexOfChild(codeScrollContainer) + 1)
    }

    private fun configureSymbolBar() {
        listOf("{", "}", "<", ">", "/", "=", "\"", "(", ")", ";").forEach { s ->
            val b = TextView(this).apply { text = s; textSize = 18f; setPadding(30, 10, 30, 10); setTextColor(Color.WHITE); setOnClickListener { editorCodeArea.text?.insert(editorCodeArea.selectionStart, s) } }
            quickSymbolBar.addView(b)
        }
    }


    private suspend fun setupCompiler() {
        aapt2File = File(applicationInfo.nativeLibraryDir, "libaapt2.so")
        androidJarFile = File(filesDir, "android_framework_api33.jar")
        materialLibFile = File(filesDir, "m3_shared_library.apk")
        try {
            if (!androidJarFile.exists()) assets.open("android_framework_api33.jar").use { it.copyTo(FileOutputStream(androidJarFile)) }
            if (!materialLibFile.exists()) assets.open("m3_shared_library.apk").use { it.copyTo(FileOutputStream(materialLibFile)) }
        } catch (e: Exception) { log("> [ERROR] Environment: ${e.message}", true) }
    }

    private fun runShell(comando: String): ShellResult {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", comando), arrayOf("LD_LIBRARY_PATH=${filesDir.absolutePath}:${applicationInfo.nativeLibraryDir}"))
            ShellResult(p.waitFor() == 0, p.inputStream.bufferedReader().readText(), p.errorStream.bufferedReader().readText())
        } catch (e: Exception) { ShellResult(false, "", e.message ?: "Error Shell") }
    }




    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    data class ShellResult(val success: Boolean, val output: String, val error: String)
}

// File Adapter
class FileAdapter(private val onClick: (File) -> Unit, private val onLongClick: (View, File) -> Unit) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private var files = listOf<Pair<File, Int>>()
    private var expandedFolders = setOf<String>()

    fun updateFiles(newFiles: List<Pair<File, Int>>, expandedFolders: Set<String>) {
        this.files = newFiles
        this.expandedFolders = expandedFolders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(-2, -2)
            setPadding(16, 12, 32, 12)
        }
        val arrowIcon = ImageView(parent.context).apply { layoutParams = LinearLayout.LayoutParams(40, 40); setPadding(0, 0, 8, 0) }
        val mainIcon = ImageView(parent.context).apply { layoutParams = LinearLayout.LayoutParams(50, 50); setPadding(0, 0, 16, 0) }
        val textView = TextView(parent.context).apply { textSize = 13f; setTextColor(Color.parseColor("#CCCCCC")) }

        layout.addView(arrowIcon); layout.addView(mainIcon); layout.addView(textView)
        return ViewHolder(layout, arrowIcon, mainIcon, textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (file, depth) = files[position]
        holder.itemView.setPadding(16 + (depth * 20), 12, 32, 12)
        holder.textView.text = file.name

        if (file.isDirectory) {
            holder.arrowIcon.visibility = View.VISIBLE
            if (expandedFolders.contains(file.absolutePath)) {
                holder.arrowIcon.setImageResource(R.drawable.ic_arrowexpanded)
                holder.mainIcon.setImageResource(R.drawable.ic_folderopen)
            } else {
                holder.arrowIcon.setImageResource(R.drawable.ic_arrowexpand)
                holder.mainIcon.setImageResource(R.drawable.ic_folder)
            }
            holder.arrowIcon.setColorFilter(Color.parseColor("#FFFFFF"))
            holder.mainIcon.setColorFilter(Color.parseColor("#DCB67A"))
        } else {
            holder.arrowIcon.visibility = View.INVISIBLE
            holder.mainIcon.setImageResource(R.drawable.ic_file)
            holder.mainIcon.setColorFilter(Color.parseColor("#519ABA"))
        }

        holder.itemView.setOnClickListener { onClick(file) }

        // ── CORREGIDO: Capturamos 'view' y se la pasamos a la función ──
        holder.itemView.setOnLongClickListener { view ->
            onLongClick(view, file)
            true
        }
    }

    override fun getItemCount() = files.size

    class ViewHolder(view: View, val arrowIcon: ImageView, val mainIcon: ImageView, val textView: TextView) : RecyclerView.ViewHolder(view)
}
// --- CLASE ADAPTER COMPLETE ---
class TabAdapter(private val onTabClick: (File) -> Unit, private val onTabClose: (File) -> Unit) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() { // <--- Esta línea es la clave

    var openTabs = mutableListOf<File>()
    var activeFile: File? = null

    fun updateData(newList: List<File>, current: File?) {
        this.openTabs = newList.toMutableList()
        this.activeFile = current
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabAdapter.TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabAdapter.TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabAdapter.TabViewHolder, position: Int) {
        val file = openTabs[position]
        holder.tvName.text = file.name
        val isSelected = file.absolutePath == activeFile?.absolutePath

        holder.layoutRoot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (isSelected) "#1E2E4A" else "#142033")
        )
        holder.layoutRoot.alpha = if (isSelected) 1.0f else 0.7f

        holder.itemView.setOnClickListener { onTabClick(file) }
        holder.btnClose.setOnClickListener { onTabClose(file) }
    }

    override fun getItemCount(): Int = openTabs.size

    fun moveItem(from: Int, to: Int) {
        java.util.Collections.swap(openTabs, from, to)
        notifyItemMoved(from, to)
    }

    class TabViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val layoutRoot: View = v.findViewById(R.id.alltab)
        val tvName: TextView = v.findViewById(R.id.namefile)
        val btnClose: ImageView = v.findViewById(R.id.close)
    }
}