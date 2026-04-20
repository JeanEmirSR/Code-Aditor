package com.jeansr.androideditor

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class MergeResolverFragment : Fragment(R.layout.fragment_merge_resolver) {

    private lateinit var gitManager: GitManager
    private var relativePath: String = ""

    // Referencias a los EditText del XML anterior
    private lateinit var etLocal: EditText
    private lateinit var etRemote: EditText
    private lateinit var etResult: EditText
    private lateinit var btnApply: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar vistas
        etLocal = view.findViewById(R.id.etMergeLocal)
        etRemote = view.findViewById(R.id.etMergeRemote)
        etResult = view.findViewById(R.id.etMergeResult)
        btnApply = view.findViewById(R.id.btnApplyMerge)

        // Supongamos que pasas la ruta del archivo por Arguments
        relativePath = arguments?.getString("file_path") ?: ""
        val projectDir = File(requireContext().getExternalFilesDir("Projects"), "TuProyectoActual")
        gitManager = GitManager(projectDir)

        cargarVersiones()

        // Botón "Usar Local" -> Copia el texto de la izquierda al resultado
        view.findViewById<Button>(R.id.btnAcceptLocal).setOnClickListener {
            etResult.setText(etLocal.text)
        }

        // Botón "Usar Remoto" -> Copia el texto de la derecha al resultado
        view.findViewById<Button>(R.id.btnAcceptRemote).setOnClickListener {
            etResult.setText(etRemote.text)
        }

        // Botón "RESOLVER" -> Guarda y hace git add
        btnApply.setOnClickListener {
            aplicarResolucion()
        }
    }

    private fun cargarVersiones() {
        lifecycleScope.launch {
            val versiones = gitManager.obtenerVersionesConflicto(relativePath)
            etLocal.setText(versiones.first)
            etRemote.setText(versiones.second)

            // Por defecto, ponemos una mezcla o el local en el resultado para empezar
            etResult.setText(versiones.first)
        }
    }

    private fun aplicarResolucion() {
        val contenidoFinal = etResult.text.toString()
        lifecycleScope.launch {
            val res = gitManager.resolverConflicto(relativePath, contenidoFinal)
            if (res.success) {
                Toast.makeText(context, "Conflicto resuelto", Toast.LENGTH_SHORT).show()
                // Volver a la pantalla anterior
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(context, "Error: ${res.error}", Toast.LENGTH_LONG).show()
            }
        }
    }
}