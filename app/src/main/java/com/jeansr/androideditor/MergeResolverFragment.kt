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


    private lateinit var etLocal: EditText
    private lateinit var etRemote: EditText
    private lateinit var etResult: EditText
    private lateinit var btnApply: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        etLocal = view.findViewById(R.id.etMergeLocal)
        etRemote = view.findViewById(R.id.etMergeRemote)
        etResult = view.findViewById(R.id.etMergeResult)
        btnApply = view.findViewById(R.id.btnApplyMerge)


        relativePath = arguments?.getString("file_path") ?: ""
        val projectDir = File(requireContext().getExternalFilesDir("Projects"), "Your Actual Project")
        gitManager = GitManager(projectDir)

        loadVersions()

        // "Use Local" button -> Copies the text from the left to the result
        view.findViewById<Button>(R.id.btnAcceptLocal).setOnClickListener {
            etResult.setText(etLocal.text)
        }

        // "Use Remote" button -> Copies the text from the right to the result
        view.findViewById<Button>(R.id.btnAcceptRemote).setOnClickListener {
            etResult.setText(etRemote.text)
        }

        // "RESOLVE" button -> Saves and runs git add
        btnApply.setOnClickListener {
            applyResolution()
        }
    }

    private fun loadVersions() {
        lifecycleScope.launch {
            val version = gitManager.getConflictVersions(relativePath)
            etLocal.setText(version.first)
            etRemote.setText(version.second)
            etResult.setText(version.first)
        }
    }

    private fun applyResolution() {
        val contenidoFinal = etResult.text.toString()
        lifecycleScope.launch {
            val res = gitManager.resolveConflict(relativePath, contenidoFinal)
            if (res.success) {
                Toast.makeText(context, "Conflict resolved", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(context, "Error: ${res.error}", Toast.LENGTH_LONG).show()
            }
        }
    }
}