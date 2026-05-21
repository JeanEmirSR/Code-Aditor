package com.jeansr.androideditor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CompilerSetup {

    fun initEngineFiles(context: Context) {
        val engineFolder = File(context.filesDir, "compiler_engine")
        if (!engineFolder.exists()) {
            engineFolder.mkdirs()
        }

        val filesToExtract = listOf("m3_shared_library.apk", "android_framework_api33.jar")

        for (fileName in filesToExtract) {
            val outFile = File(engineFolder, fileName)

            if (!outFile.exists()) {
                try {
                    val inputStream: InputStream = context.assets.open(fileName)
                    val outputStream = FileOutputStream(outFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    println("compiler_engine Successfully extracted -> $fileName")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("compiler_engine Critical error during extraction -> $fileName")
                }
            }
        }
    }

    // Convenience functions to get exact absolute paths
    fun getFrameworkPath(context: Context): String {
        return File(context.filesDir, "compiler_engine/android_framework_api33.jar").absolutePath
    }

    fun getMaterialLibraryPath(context: Context): String {
        return File(context.filesDir, "compiler_engine/m3_super_library.apk").absolutePath
    }
}