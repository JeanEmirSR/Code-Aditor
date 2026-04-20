package com.jeansr.androideditor

import SyntaxColorInfo
import SyntaxHighlighter
import TabAdapter
import android.content.res.ColorStateList
import java.util.Collections

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import android.widget.PopupWindow
import android.view.ViewGroup.LayoutParams
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
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
import org.eclipse.jgit.util.FileUtils.mkdirs
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import com.jeansr.androideditor.R
import java.io.InputStreamReader
import java.nio.file.Files.exists
// ── NUEVO GIT: imports adicionales ──
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContentProviderCompat.requireContext

import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.tabs.TabLayout as MaterialTabLayout

class EditorActivity : AppCompatActivity() {

    private val TAG = "EditorCompiler"

    // =========================================================================
    // ESTADO Y PROPIEDADES
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
    private val handlerResaltado = android.os.Handler(android.os.Looper.getMainLooper())
    private var runnableResaltado: Runnable? = null
    private lateinit var textWatcherResaltado: android.text.TextWatcher

    /// Variables globales en tu Activity para controlar que no se abran múltiples menús y acciones
    private var archivoEnPortapapeles: File? = null
    private var accionPortapapeles: String = "" // Guardará "copiar" o "cortar"

    private var popupPrincipal: PopupWindow? = null
    private var popupNuevo: PopupWindow? = null


    // =========================================================================
    // CICLO DE VIDA
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
        rvTabs = findViewById(R.id.rvTabs) // Asegúrate que este ID esté en tu activity_editor.xml
        configurarTabsRecyclerView()
        quickSymbolBar = findViewById(R.id.quickSymbolBar)
        fragment_container = findViewById(R.id.fragment_container)

        configurarConsola()

        val projectPath = intent.getStringExtra("PROJECT_PATH") ?: return
        projectRoot = File(projectPath)

        findViewById<ImageButton>(R.id.btnToggleExplorer).setOnClickListener {
            val visible = explorerPanel.visibility == View.VISIBLE
            explorerPanel.visibility = if (visible) View.GONE else View.VISIBLE
            panelDivider.visibility = if (visible) View.GONE else View.VISIBLE
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { guardarArchivoActual() }
        findViewById<ImageButton>(R.id.btnPreview).setOnClickListener { alternarVistaPrevia() }

        configurarBarraDeSimbolos()
        configurarTabsRecyclerView()

        fileAdapter = FileAdapter(
            onClick = { file -> manejarClicEnArbol(file) },
            onLongClick = { view, file -> manejarLongClickEnArbol(view, file) }
        )

        recyclerFiles.layoutManager = LinearLayoutManager(this)
        recyclerFiles.adapter = fileAdapter

        refrescarArbolArchivos()
        inicializarGit()
        configurarSyntaxHighlighting()
        configurarScrollListener()

        lifecycleScope.launch(Dispatchers.IO) { prepararCompilador() }

        // --- CONEXIÓN CON EL COMPONENTE CUSTOM ---
        previewContainer.onModeChanged = { modo ->
            currentRenderMode = when(modo) {
                "ORIGINAL" -> RenderMode.ORIGINAL
                "RELATIVE" -> RenderMode.RELATIVE
                else -> RenderMode.LINEAR
            }
            if (isShowingPreview) {
                compilarVistaPreviaConRecursos()
            }
        }


    }



    private fun configurarTabsRecyclerView() {
        tabAdapter = TabAdapter(
            onTabClick = { abrirArchivoEnPestana(it) },
            onTabClose = { cerrarPestana(it) }
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
                // Sincronizamos tu lista global openTabs con el nuevo orden del adaptador
                openTabs.clear()
                openTabs.addAll(tabAdapter.openTabs)
            }
        })

        itemTouchHelper.attachToRecyclerView(rvTabs)
    }

    // =========================================================================
    // MOTOR DE RENDERIZADO
    // =========================================================================
    private fun log(mensaje: String, esError: Boolean = false) {

        if (esError) Log.e(TAG, mensaje) else Log.d(TAG, mensaje)

        runOnUiThread { tvConsole.append("$mensaje\n") }

    }


    private fun compilarVistaPreviaConRecursos() {
        if (!aapt2File.exists() || !androidJarFile.exists()) return
        val xmlCrudo = editorCodeArea.text.toString()

        log("\n--- [INICIANDO COMPILACIÓN] ---")

        lifecycleScope.launch(Dispatchers.IO) {
            val tiempoInicio = System.currentTimeMillis()
            val workspace = File(cacheDir, "build_workspace").apply { deleteRecursively(); mkdirs() }
            val buildResDir = File(workspace, "res")
            val projResDir = File(projectRoot, "app/src/main/res")

            if (projResDir.exists()) {
                projResDir.copyRecursively(buildResDir, overwrite = true)
            } else { buildResDir.mkdirs() }

            val xmlProcesado = procesarXmlParaPreview(xmlCrudo)
            File(buildResDir, "layout").apply { mkdirs() }
            File(buildResDir, "layout/preview_layout.xml").writeText(xmlProcesado)

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

            val resCompile = ejecutarShell("${aapt2File.absolutePath} compile --dir ${buildResDir.absolutePath} -o ${compiledZip.absolutePath}")
            if (!resCompile.success) { log("[ERROR COMPILE]\n${resCompile.error}", true); return@launch }

            val linkCmd = "${aapt2File.absolutePath} link -I ${androidJarFile.absolutePath} -I ${materialLibFile.absolutePath} -o ${outputApk.absolutePath} --manifest ${manifestFile.absolutePath} ${compiledZip.absolutePath} --auto-add-overlay --allow-reserved-package-id --no-static-lib-packages"
            val resLink = ejecutarShell(linkCmd)
            if (!resLink.success) { log("[ERROR LINK]\n${resLink.error}", true); return@launch }

            log("> [ÉXITO] Mini-APK generado en ${System.currentTimeMillis() - tiempoInicio}ms.")
            withContext(Dispatchers.Main) { inyectarYDibujar(outputApk.absolutePath) }
        }
    }

    private fun inyectarYDibujar(apkPath: String) {
        try {
            log("> Inyectando APK...")
            val customAssetManager = AssetManager::class.java.getConstructor().newInstance()
            val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            addAssetPathMethod.invoke(customAssetManager, apkPath)
            addAssetPathMethod.invoke(customAssetManager, androidJarFile.absolutePath)
            addAssetPathMethod.invoke(customAssetManager, materialLibFile.absolutePath)

            val customResources = Resources(customAssetManager, resources.displayMetrics, resources.configuration)
            val resId = customResources.getIdentifier("preview_layout", "layout", "com.google.android.material")
            if (resId == 0) { log("[ERROR] Layout no encontrado.", true); return }

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
                    // 1. Limpiamos el componente personalizado (tu PreviewXml)
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

                    // --- CAMBIO 1: Inflamos pero con MATCH_PARENT para el contenedor ---
                    val rootView = inflater.inflate(resId, previewContainer.contentArea, false)
                    rootView.layoutParams = FrameLayout.LayoutParams(-1, -1) // -1 es Match Parent

                    if (rootView is ScrollView || rootView is androidx.core.widget.NestedScrollView) {
                        rootView.isVerticalScrollBarEnabled = false
                        rootView.overScrollMode = View.OVER_SCROLL_NEVER
                    }

                    // 3. Mandamos la vista final al componente
                    previewContainer.setPreviewView(rootView)

                    // --- CAMBIO 2: Forzamos al PreviewXml a recalcular el tamaño del teléfono ---
                    previewContainer.requestLayout()
                    previewContainer.invalidate()

                    log("> [ÉXITO] Interfaz renderizada y ajustada.")
                } catch (e: Exception) { log("[ERROR INFLADO] ${e.message}", true) }
            }
        } catch (e: Exception) { log("[ERROR INYECCIÓN] ${e.message}", true) }
    }


    private fun procesarXmlParaPreview(xmlCrudo: String): String {
        if (currentRenderMode == RenderMode.ORIGINAL || !xmlCrudo.contains("ConstraintLayout")) return xmlCrudo
        var xml = xmlCrudo
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

    private fun alternarVistaPrevia() {
        isShowingPreview = !isShowingPreview
        codeScrollContainer.visibility = if (isShowingPreview) View.GONE else View.VISIBLE
        previewContainer.visibility = if (isShowingPreview) View.VISIBLE else View.GONE
        tvConsole.visibility = if (isShowingPreview) View.VISIBLE else View.GONE
        if (isShowingPreview) {
            compilarVistaPreviaConRecursos()
        }
    }

    // =========================================================================
    // GESTIÓN DE ARCHIVOS, GIT Y UTILIDADES (MANTENIDO)
    // =========================================================================

    private var fullFileColorMap: List<SyntaxColorInfo>? = null
   // private var backgroundJob: kotlinx.coroutines.Job? = null

    /*
    private fun iniciarResaltadoHibrido(content: String, ext: String) {
        // 1. PINTADO RÁPIDO (Lo que el usuario ve ya mismo)
        ejecutarResaltadoViewport()

        // 2. ESCANEO TOTAL EN SEGUNDO PLANO
        backgroundJob?.cancel() // Cancelar escaneos anteriores
        backgroundJob = lifecycleScope.launch(Dispatchers.Default) {
            // Generamos el mapa de colores sin tocar la UI
            val nuevoMapa = highlighter.generateColorMap(content, ext)

            withContext(Dispatchers.Main) {
                fullFileColorMap = nuevoMapa
                // Una vez que tenemos el mapa, si el usuario hace scroll,
                // el resaltado será instantáneo porque ya sabemos los índices.
                log("> Jarvis Engine: Mapa de sintaxis completo.")
            }
        }
    }

     */

    private fun configurarSyntaxHighlighting() {
        textWatcherResaltado = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                runnableResaltado?.let { handlerResaltado.removeCallbacks(it) }
                runnableResaltado = Runnable { s?.let { ejecutarResaltadoViewport() } }
                handlerResaltado.postDelayed(runnableResaltado!!, 150)
            }
        }
        editorCodeArea.addTextChangedListener(textWatcherResaltado)
    }

    private fun ejecutarResaltadoViewport() {
        val layout = editorCodeArea.layout ?: return
        val content = editorCodeArea.text ?: return
        val ext = fileCurrentlyOpen?.extension ?: "txt"

        val scrollY = codeScrollContainer.scrollY
        val height = codeScrollContainer.height
        val firstLine = layout.getLineForVertical(scrollY)
        val lastLine = layout.getLineForVertical(scrollY + height)

        val startOffset = layout.getLineStart((firstLine - 10).coerceAtLeast(0))
        val endOffset = layout.getLineEnd((lastLine + 50).coerceAtMost(editorCodeArea.lineCount - 1))

        editorCodeArea.removeTextChangedListener(textWatcherResaltado)

        if (fullFileColorMap != null) {
            // USA EL MAPA (Ultra rápido, no hay Regex aquí)
            val visibleColors = fullFileColorMap!!.filter { it.start >= startOffset && it.end <= endOffset }

            // Limpiar spans viejos solo en el rango visible
            val oldSpans = content.getSpans(startOffset, endOffset, ForegroundColorSpan::class.java)
            oldSpans.forEach { content.removeSpan(it) }

            for (info in visibleColors) {
                content.setSpan(ForegroundColorSpan(info.color), info.start, info.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            // MODO EMERGENCIA: Si el mapa no está listo, usa el highlighter normal para el viewport
            highlighter.applyHighlighting(content, ext, startOffset, endOffset)
        }

        editorCodeArea.addTextChangedListener(textWatcherResaltado)
    }

    private fun configurarScrollListener() {
        var lastScrollY = -1

        codeScrollContainer.viewTreeObserver.addOnScrollChangedListener {
            val currentScrollY = codeScrollContainer.scrollY

            // Solo recalculamos si el scroll cambió significativamente (> 20px)
            if (Math.abs(currentScrollY - lastScrollY) > 20) {
                lastScrollY = currentScrollY

                handlerResaltado.removeCallbacks(runnableResaltado ?: Runnable {})
                runnableResaltado = Runnable {
                    if (!isShowingPreview) ejecutarResaltadoViewport()
                }

                // Aumentamos a 100ms para dar respiro al procesador en archivos como json.hpp
                handlerResaltado.postDelayed(runnableResaltado!!, 100)
            }
        }
    }

    private fun cambiarFocoAPestana(file: File) {
        fileCurrentlyOpen = file
        val contenido = fileContentsMemory[file.absolutePath] ?: ""
        editorCodeArea.setText(contenido)

        // Resaltado de sintaxis
        editorCodeArea.post { ejecutarResaltadoViewport() }

        // Actualizar visual del adaptador (Pestaña activa)
        tabAdapter.updateData(openTabs, file)
        val index = openTabs.indexOf(file)
        if (index != -1) rvTabs.smoothScrollToPosition(index)

        // Sincronizar Preview
        if (isShowingPreview) {
            if (file.extension == "xml") compilarVistaPreviaConRecursos()
            else previewContainer.clear()
        }
    }

    private fun guardarArchivoActual() {
        fileCurrentlyOpen?.let { file ->
            val text = editorCodeArea.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                file.writeText(text)
                withContext(Dispatchers.Main) { Toast.makeText(this@EditorActivity, "Guardado", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun refrescarArbolArchivos() {
        val lista = construirArbol(projectRoot, 0)
        fileAdapter.actualizarArchivos(lista, expandedFolders)
    }

    private fun construirArbol(dir: File, depth: Int): List<Pair<File, Int>> {
        val lista = mutableListOf<Pair<File, Int>>()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return lista
        for (f in files) {
            lista.add(f to depth)
            if (f.isDirectory && expandedFolders.contains(f.absolutePath)) lista.addAll(construirArbol(f, depth + 1))
        }
        return lista
    }

    private fun manejarClicEnArbol(file: File) {
        if (file.isDirectory) {
            if (expandedFolders.contains(file.absolutePath)) expandedFolders.remove(file.absolutePath)
            else expandedFolders.add(file.absolutePath)
            refrescarArbolArchivos()
        } else abrirArchivoEnPestana(file)
    }
    //////////////////////////////////////////////////
    //------------------------------------------//

    private fun ejecutarAccionArchivo(accion: String, fileDestino: File) {
        when (accion) {
            "copiar", "cortar" -> {
                archivoEnPortapapeles = fileDestino
                accionPortapapeles = accion
                val verbo = if (accion == "copiar") "Copiado" else "Cortado"
                log("$verbo al portapapeles: ${fileDestino.name}")
                Toast.makeText(this, "$verbo: ${fileDestino.name}", Toast.LENGTH_SHORT).show()
            }

            "pegar" -> {
                val origen = archivoEnPortapapeles
                if (origen == null || !origen.exists()) {
                    log("El portapapeles está vacío o el archivo original ya no existe.", true)
                    return
                }

                val directorioPadre = if (fileDestino.isDirectory) fileDestino else fileDestino.parentFile
                val archivoNuevo = File(directorioPadre, origen.name)

                if (archivoNuevo.exists()) {
                    log("Ya existe un archivo con el nombre '${origen.name}' en esta carpeta.", true)
                    return
                }

                Toast.makeText(this, "Pegando...", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (accionPortapapeles == "copiar") {
                            if (origen.isDirectory) {
                                origen.copyRecursively(archivoNuevo, true)
                            } else {
                                origen.copyTo(archivoNuevo, true)
                            }
                            withContext(Dispatchers.Main) {
                                log("Copiado con éxito en: ${directorioPadre.name}")
                                refrescarArbolArchivos()
                            }
                        }
                        else if (accionPortapapeles == "cortar") {
                            val movidoRapido = origen.renameTo(archivoNuevo)
                            if (!movidoRapido) {
                                if (origen.isDirectory) {
                                    origen.copyRecursively(archivoNuevo, true)
                                    origen.deleteRecursively()
                                } else {
                                    origen.copyTo(archivoNuevo, true)
                                    origen.delete()
                                }
                            }
                            withContext(Dispatchers.Main) {
                                log("Movido con éxito a: ${directorioPadre.name}")
                                archivoEnPortapapeles = null
                                accionPortapapeles = ""
                                refrescarArbolArchivos()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            log("Error al pegar: ${e.message}", true)
                        }
                    }
                }
            }

            "eliminar" -> {
                val tipo = if (fileDestino.isDirectory) "la carpeta" else "el archivo"

                android.app.AlertDialog.Builder(this)
                    .setTitle("Eliminar")
                    .setMessage("¿Estás seguro de que deseas eliminar permanentemente $tipo '${fileDestino.name}'?\nEsta acción no se puede deshacer.")
                    .setPositiveButton("Eliminar") { _, _ ->

                        Toast.makeText(this, "Eliminando...", Toast.LENGTH_SHORT).show()

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val exito = if (fileDestino.isDirectory) {
                                    fileDestino.deleteRecursively()
                                } else {
                                    fileDestino.delete()

                                }
                                withContext(Dispatchers.Main) {
                                    if (exito) {
                                        log("$tipo '${fileDestino.name}' ha sido eliminado.")
                                        refrescarArbolArchivos()
                                    } else {
                                        log("Error: No se pudo eliminar $tipo.", true)
                                    }
                                }
                                // ── PROTECCIÓN DE PESTAÑAS (Centralizada) ──

// 1. Filtramos cuáles archivos abiertos acaban de ser eliminados del disco
                                val archivosCerrar = openTabs.filter {
                                    it.absolutePath == fileDestino.absolutePath ||
                                            it.absolutePath.startsWith(fileDestino.absolutePath + "/")
                                }

// 2. Iteramos y le delegamos el trabajo a tu función principal
                                archivosCerrar.forEach { archivo ->
                                    cerrarPestana(archivo)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Error al eliminar: ${e.message}", true)
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun mostrarDialogoRenombrar(file: File) {
        val input = EditText(this)
        input.setText(file.name)
        // Seleccionamos el texto para que sea fácil de borrar
        input.selectAll()

        android.app.AlertDialog.Builder(this)
            .setTitle("Renombrar")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoNombre = input.text.toString().trim()
                if (nuevoNombre.isNotEmpty() && nuevoNombre != file.name) {
                    val nuevoArchivo = File(file.parent, nuevoNombre)
                    if (file.renameTo(nuevoArchivo)) {
                        log("Renombrado a: $nuevoNombre")
                        refrescarArbolArchivos()
                    } else {
                        log("Error al renombrar el archivo.", true)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    //-----------------------------------------//


    private fun manejarLongClickEnArbol(anchorView: View, archivoDestino: File) {
        popupNuevo?.dismiss()
        popupPrincipal?.dismiss()

        val inflater = layoutInflater
        val vistaMenu = inflater.inflate(R.layout.layout_menu_contextual, null)

        popupPrincipal = PopupWindow(
            vistaMenu,
            resources.getDimensionPixelSize(R.dimen.menu_ancho), // Define en dimens.xml ej. 200dp
            LayoutParams.WRAP_CONTENT,
            true // true = se cierra al tocar fuera
        )
        popupPrincipal?.elevation = 16f

        // --- LÓGICA DE DIRECTORIOS ---
        // Si el usuario hace clic largo en un archivo, el submenú "Nuevo" debe crear el archivo
        // en la carpeta padre de ese archivo. Si es un directorio, lo crea directamente ahí.
        val directorioDestino = if (archivoDestino.isDirectory) archivoDestino else archivoDestino.parentFile

        // --- EVENTOS DEL MENÚ PRINCIPAL ---

        // 1. Desplegar el submenú "Nuevo"
        vistaMenu.findViewById<View>(R.id.itemMenuNuevo).setOnClickListener { viewNuevo ->
            mostrarSubMenuNuevo(viewNuevo, directorioDestino)
        }

        // 2. Acciones estándar
        vistaMenu.findViewById<View>(R.id.itemMenuCortar).setOnClickListener {
            popupPrincipal?.dismiss()
            ejecutarAccionArchivo("cortar", archivoDestino)
        }
        vistaMenu.findViewById<View>(R.id.itemMenuCopiar).setOnClickListener {
            popupPrincipal?.dismiss()
            ejecutarAccionArchivo("copiar", archivoDestino)
        }
        vistaMenu.findViewById<View>(R.id.itemMenuPegar).setOnClickListener {
            popupPrincipal?.dismiss()
            ejecutarAccionArchivo("pegar", archivoDestino)
        }
        vistaMenu.findViewById<View>(R.id.itemMenuRenombrar).setOnClickListener {
            popupPrincipal?.dismiss()
            mostrarDialogoRenombrar(archivoDestino) // Usa tu función existente
        }
        vistaMenu.findViewById<View>(R.id.itemMenuEliminar).setOnClickListener {
            popupPrincipal?.dismiss()
            ejecutarAccionArchivo("eliminar", archivoDestino)
        }

        // Mostrar menú pegado al ítem del RecyclerView
        popupPrincipal?.showAsDropDown(anchorView, 20, -anchorView.height / 2)
    }

    /**
     * Muestra el submenú desplazado hacia la derecha para crear archivos
     */
    private fun mostrarSubMenuNuevo(anchorNuevo: View, directorioDestino: File) {
        popupNuevo?.dismiss()

        val vistaNuevo = layoutInflater.inflate(R.layout.layout_menu_nuevo, null)

        popupNuevo = PopupWindow(
            vistaNuevo,
            resources.getDimensionPixelSize(R.dimen.submenu_ancho), // ej. 220dp
            LayoutParams.WRAP_CONTENT,
            true
        )
        popupNuevo?.elevation = 20f

        popupNuevo?.setOnDismissListener { popupPrincipal?.dismiss() }

        // --- EVENTOS DE GENERACIÓN ---
        vistaNuevo.findViewById<View>(R.id.itemNuevoKotlin).setOnClickListener {
            pedirNombreYGenerar("kotlin", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoJava).setOnClickListener {
            pedirNombreYGenerar("java", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoLayout).setOnClickListener {
            pedirNombreYGenerar("layout", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoDirectorio).setOnClickListener {
            pedirNombreYGenerar("dir", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoArchivo).setOnClickListener {
            pedirNombreYGenerar("file", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoActivity).setOnClickListener {
            pedirNombreYGenerar("activity", directorioDestino)
        }
        vistaNuevo.findViewById<View>(R.id.itemNuevoFragment).setOnClickListener {
            pedirNombreYGenerar("fragment", directorioDestino)
        }

        // Mostrar a la derecha de la opción "Nuevo" del menú principal
        popupNuevo?.showAsDropDown(anchorNuevo, anchorNuevo.width, -anchorNuevo.height)
    }

    private fun pedirNombreYGenerar(tipo: String, directorioPadre: File) {
        popupNuevo?.dismiss()
        popupPrincipal?.dismiss()

        // Aquí usamos un EditText Dialog para que ingreses el nombre
        val input = android.widget.EditText(this)
        input.hint = "Ej: MainActivity"

        android.app.AlertDialog.Builder(this)
            .setTitle(if (tipo == "dir") "Nueva Carpeta" else "Nuevo Archivo")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isNotEmpty()) {
                    generarArchivoDesdePlantilla(tipo, directorioPadre, nombre)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun generarArchivoDesdePlantilla(tipo: String, directorioPadre: File, nombre: String) {
        try {
            when (tipo) {
                "kotlin" -> {
                    val file = File(directorioPadre, "$nombre.kt")
                    file.writeText("package tu.paquete.aqui\n\nclass $nombre {\n    // TODO: Implementar lógica\n}")
                    abrirArchivoEnPestana(file) // Opcional: abrirlo de inmediato
                }
                "java" -> {
                    val file = File(directorioPadre, "$nombre.java")
                    file.writeText("package tu.paquete.aqui;\n\npublic class $nombre {\n    public $nombre() {\n    }\n}")
                    abrirArchivoEnPestana(file)
                }
                "layout" -> {
                    val file = File(directorioPadre, "${nombre.lowercase()}.xml")
                    file.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n    \n</LinearLayout>")
                    abrirArchivoEnPestana(file)
                }
                "activity" -> {
                    val file = File(directorioPadre, "${nombre}Activity.kt")
                    file.writeText("import android.os.Bundle\nimport androidx.appcompat.app.AppCompatActivity\n\nclass ${nombre}Activity : AppCompatActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}")
                    abrirArchivoEnPestana(file)
                }
                "fragment" -> {
                    val file = File(directorioPadre, "${nombre}Fragment.kt")
                    file.writeText("import android.os.Bundle\nimport android.view.View\nimport androidx.fragment.app.Fragment\n\nclass ${nombre}Fragment : Fragment() {\n    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {\n        super.onViewCreated(view, savedInstanceState)\n    }\n}")
                    abrirArchivoEnPestana(file)
                }
                "file" -> {
                    val file = File(directorioPadre, nombre)
                    file.createNewFile()
                    abrirArchivoEnPestana(file)
                }
                "dir" -> {
                    File(directorioPadre, nombre).mkdirs()
                }
            }
            refrescarArbolArchivos() // Tu función para recargar el RecyclerView
        } catch (e: Exception) {
            log("Error creando: ${e.message}", true)
        }
    }
    ////////////////////////////////////////
    private fun abrirArchivoEnPestana(file: File) {
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
                    cambiarFocoAPestana(file)
                }
            }
        } else {
            cambiarFocoAPestana(file)
        }
    }

    private fun cerrarPestana(file: File) {
        val index = openTabs.indexOf(file)
        if (index == -1) return

        openTabs.removeAt(index)
        fileContentsMemory.remove(file.absolutePath)

        if (fileCurrentlyOpen == file) {
            if (openTabs.isNotEmpty()) {
                val nextIndex = if (index < openTabs.size) index else openTabs.size - 1
                cambiarFocoAPestana(openTabs[nextIndex])
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
                    if (f.exists()) abrirArchivoEnPestana(f)
                }
            },
            onFileStage = { item ->
                if (item is GitItemAdapter.GitItem.FileItem) {
                    lifecycleScope.launch {
                        val res = gitManager.stageFile(item.file.path)
                        if (!res.success) log("[Git] Error al indexar: ${res.error}", true)
                        refrescarEstadoGit()
                    }
                }
            }
        )

        recyclerGitItems.layoutManager = LinearLayoutManager(this)
        recyclerGitItems.adapter = gitItemAdapter

        tabGit.addTab(tabGit.newTab().setText("Cambios"))
        tabGit.addTab(tabGit.newTab().setText("Historial"))
        tabGit.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                gitCurrentTab = tab?.position ?: 0
                refrescarListaGit()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<LinearLayout>(R.id.gitOpcions).setOnClickListener { alternarPanelGit() }
        btnCloseGitPanel.setOnClickListener { cerrarPanelGit() }

        // Conexión de botones con las funciones de ejecución
        btnGitCommit.setOnClickListener { ejecutarCommit() }
        btnGitPull.setOnClickListener { ejecutarPull() }
        btnGitPush.setOnClickListener { ejecutarPush() }
        btnGitSync.setOnClickListener { ejecutarSync() }

        lifecycleScope.launch { cargarInfoRepoYActualizar() }
    }

    private fun alternarPanelGit() { if (gitPanel.visibility == View.VISIBLE) cerrarPanelGit() else abrirPanelGit() }
    private fun abrirPanelGit() { gitPanel.visibility = View.VISIBLE; gitPanel.alpha = 0f; gitPanel.animate().alpha(1f).setDuration(180).start(); refrescarEstadoGit() }
    private fun cerrarPanelGit() { gitPanel.animate().alpha(0f).setDuration(150).withEndAction { gitPanel.visibility = View.GONE }.start() }


    private fun refrescarEstadoGit() {
        lifecycleScope.launch {
            val cambios = gitManager.obtenerArchivosCambiados()
            withContext(Dispatchers.Main) {
                tvGitChanges.text = "${cambios.size} cambios"
                dotGitPending.visibility = if (cambios.isNotEmpty()) View.VISIBLE else View.GONE
                refrescarListaGit()
            }
        }
    }
    private fun ejecutarPull() {
        val info = gitRepoInfo ?: return

        // Obtenemos el token guardado en las preferencias (SharedPreferences)
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        if (token.isBlank()) {
            log("[Git] Error: No hay un Token de GitHub configurado.", true)
            mostrarDialogoToken() // Llama a tu función para pedir el token si no existe
            return
        }

        lifecycleScope.launch {
            log("> [Git] Iniciando Pull desde origin/${info.branch}...")
            runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Iniciando Pull desde origin/${info.branch}...", Toast.LENGTH_SHORT).show() }

            // Ejecutamos el pull usando el GitManager nativo
            val res = gitManager.pull(token, info.owner, info.repoName, info.branch)

            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Pull completado.\n${res.output}")
                    runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Pull completado.\n${res.output}", Toast.LENGTH_SHORT).show() }

                    // Refrescamos la interfaz para mostrar los nuevos archivos o cambios
                    refrescarArbolArchivos()
                    refrescarEstadoGit()
                } else {
                    log("[Git] Error en Pull: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "[Git] Error en Pull: ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                    manejarResultadoPull(res)
                }
            }
        }
    }

    private fun manejarResultadoPull(resultado: GitManager.GitResult) {
        if (!resultado.success && resultado.error.contains("conflict", ignoreCase = true)) {
            lifecycleScope.launch {
                val conflictos = gitManager.obtenerArchivosEnConflicto()
                if (conflictos.isNotEmpty()) {
                    // Opción A: Mostrar un diálogo con la lista de archivos
                    mostrarDialogoSeleccionConflictos(conflictos)
                }
            }
        }
    }

    private fun mostrarDialogoSeleccionConflictos(lista: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Conflictos detectados")
            .setItems(lista.toTypedArray()) { _, which ->
                val archivo = lista[which]
                abrirFragmentMerge(archivo)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirFragmentMerge(path: String) {
        val frag = MergeResolverFragment().apply {
            arguments = Bundle().apply { putString("file_path", path) }
        }

        // Lo cargamos sobre el editor o en un contenedor de fragments
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag) // Asegúrate de tener un FrameLayout para esto
            .addToBackStack(null)
            .commit()
    }

    private fun refrescarListaGit() {
        lifecycleScope.launch {
            val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

            if (gitCurrentTab == 0) {
                // Pestaña "Cambios"
                val archivos = gitManager.obtenerArchivosCambiados()
                withContext(Dispatchers.Main) {
                    gitItemAdapter.mostrarArchivos(archivos)
                }
            } else {
                // Pestaña "Historial"
                val commits = if (gitRepoInfo != null && gitRepoInfo!!.isGitRepo && token.isNotBlank()) {
                    gitManager.obtenerCommitsGitHub(gitRepoInfo!!.owner, gitRepoInfo!!.repoName, token, gitRepoInfo!!.branch)
                } else {
                    gitManager.obtenerCommitsLocales()
                }
                withContext(Dispatchers.Main) {
                    gitItemAdapter.mostrarCommits(commits)
                }
            }
        }
    }

    private fun ejecutarCommit() {
        val msg = etCommitMessage.text.toString().trim()
        if (msg.isEmpty()) return
        guardarArchivoActual()

        lifecycleScope.launch {
            log("> [Git] Ejecutando Commit...")
            runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Ejecutando Commit...", Toast.LENGTH_SHORT).show() }
            val res = gitManager.commit(msg)
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Commit exitoso.")
                    etCommitMessage.setText("")
                    refrescarEstadoGit()
                    runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Commit exitoso.", Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Error en Commit: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity,"[Git] Error en Commit: ${res.error}\n${res.output}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun ejecutarPush() {
        val info = gitRepoInfo ?: return
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        lifecycleScope.launch {
            log("> [Git] Iniciando Push hacia origin/${info.branch}...")
            val res = gitManager.push(token, info.owner, info.repoName, info.branch)
            runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Iniciando Push hacia origin/${info.branch}...", Toast.LENGTH_SHORT).show() }
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Push completado.\n${res.output}")
                    refrescarEstadoGit()
                    runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Push completado.\n${res.output}", Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Error en Push: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "[Git] Error en Push: ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private fun ejecutarSync() {
        val info = gitRepoInfo ?: return
        val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

        lifecycleScope.launch {
            log("> [Git] Iniciando Sincronización (Pull + Push)...")
            runOnUiThread { Toast.makeText(this@EditorActivity,"> [Git] Iniciando Sincronización (Pull + Push)...", Toast.LENGTH_SHORT).show() }
            val res = gitManager.sync(token, info.owner, info.repoName, info.branch)
            withContext(Dispatchers.Main) {
                if (res.success) {
                    log("> [Git] Sincronización completa.")
                    refrescarArbolArchivos()
                    refrescarEstadoGit()
                    runOnUiThread { Toast.makeText(this@EditorActivity, "> [Git] Sincronización completa.", Toast.LENGTH_SHORT).show() }
                } else {
                    log("[Git] Error en Sync: ${res.error}\n${res.output}", true)
                    runOnUiThread { Toast.makeText(this@EditorActivity, "[Git] Error en Sync: ${res.error}\n${res.output}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private suspend fun cargarInfoRepoYActualizar() {
        gitRepoInfo = gitManager.obtenerInfoRepo()
        withContext(Dispatchers.Main) {
            val info = gitRepoInfo ?: return@withContext
            if (!info.isGitRepo) {
                tvToolbarBranch.text = "sin-git"
                return@withContext
            }
            tvToolbarBranch.text = info.branch
            tvGitBranch.text = "⎇ ${info.branch}"
            tvGitPanelBranch.text = "⎇ ${info.branch}"
            gitStatusBar.visibility = View.VISIBLE

            val token = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_TOKEN", "") ?: ""

            if (token.isNotBlank()) {
                // 1. Obtenemos el usuario de GitHub
                val user = gitManager.obtenerUsuarioGitHub(token)

                if (user.isNotEmpty()) {
                    tvGitPanelStatus.text = "✓ @$user"

                    // 2. Opcional: Obtener el correo primario de la API
                    // Si la API no te da el correo (porque es privado), puedes usar uno por defecto
                    val correoAsociado = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("GITHUB_EMAIL", "$user@users.noreply.github.com")

                    // 3. ¡Aplicamos la identidad al repositorio!
                    // Usamos lifecycleScope porque no podemos bloquear el hilo Main con operaciones IO
                    lifecycleScope.launch {
                        val configRes = gitManager.configurarIdentidad(user, correoAsociado!!)
                        if (!configRes.success) {
                            log("Advertencia: No se pudo configurar la firma de commits.", true)
                        }
                    }
                } else {
                    tvGitPanelStatus.text = "⚠️ Token inválido"
                }
            } else {
                mostrarDialogoToken()
            }
        }
        refrescarEstadoGit()
    }

    private fun mostrarDialogoToken() {
        val input = EditText(this).apply { hint = "ghp_xxxxxxxxxxxx" }
        android.app.AlertDialog.Builder(this).setTitle("Token GitHub").setView(input).setPositiveButton("OK") { _, _ ->
            val t = input.text.toString(); if (t.isNotBlank()) { getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("GITHUB_TOKEN", t).apply(); lifecycleScope.launch { cargarInfoRepoYActualizar() } }
        }.show()
    }

    private fun configurarConsola() {
        tvConsole = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(150f))
            setBackgroundColor(Color.parseColor("#0C0C0C"))
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 10f; setPadding(16, 16, 16, 16)
            text = "> Jarvis Engine: Listo.\n"; visibility = View.GONE
        }
        val mainContainer = codeScrollContainer.parent as ViewGroup
        mainContainer.addView(tvConsole, mainContainer.indexOfChild(codeScrollContainer) + 1)
    }

    private fun configurarBarraDeSimbolos() {
        listOf("{", "}", "<", ">", "/", "=", "\"", "(", ")", ";").forEach { s ->
            val b = TextView(this).apply { text = s; textSize = 18f; setPadding(30, 10, 30, 10); setTextColor(Color.WHITE); setOnClickListener { editorCodeArea.text?.insert(editorCodeArea.selectionStart, s) } }
            quickSymbolBar.addView(b)
        }
    }


    private suspend fun prepararCompilador() {
        aapt2File = File(applicationInfo.nativeLibraryDir, "libaapt2.so")
        androidJarFile = File(filesDir, "android_framework_api33.jar")
        materialLibFile = File(filesDir, "m3_shared_library.apk")
        try {
            if (!androidJarFile.exists()) assets.open("android_framework_api33.jar").use { it.copyTo(FileOutputStream(androidJarFile)) }
            if (!materialLibFile.exists()) assets.open("m3_shared_library.apk").use { it.copyTo(FileOutputStream(materialLibFile)) }
        } catch (e: Exception) { log("> [ERROR] Entorno: ${e.message}", true) }
    }

    private fun ejecutarShell(comando: String): ShellResult {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", comando), arrayOf("LD_LIBRARY_PATH=${filesDir.absolutePath}:${applicationInfo.nativeLibraryDir}"))
            ShellResult(p.waitFor() == 0, p.inputStream.bufferedReader().readText(), p.errorStream.bufferedReader().readText())
        } catch (e: Exception) { ShellResult(false, "", e.message ?: "Error Shell") }
    }




    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    data class ShellResult(val success: Boolean, val output: String, val error: String)
}

// Adaptador de Archivos
class FileAdapter(private val onClick: (File) -> Unit, private val onLongClick: (View, File) -> Unit) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private var files = listOf<Pair<File, Int>>()
    private var expandedFolders = setOf<String>()

    fun actualizarArchivos(newFiles: List<Pair<File, Int>>, expandedFolders: Set<String>) {
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
}// --- CLASE ADAPTER COMPLETA ---
// --- CLASE ADAPTER DEFINITIVA ---
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

        // Resaltado visual usando tu ID 'alltab'
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

    // El ViewHolder DEBE estar dentro de la clase para evitar errores de tipo
    class TabViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val layoutRoot: View = v.findViewById(R.id.alltab)
        val tvName: TextView = v.findViewById(R.id.namefile)
        val btnClose: ImageView = v.findViewById(R.id.close)
    }
}