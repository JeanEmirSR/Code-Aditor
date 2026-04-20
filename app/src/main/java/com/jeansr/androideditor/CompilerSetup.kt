package com.jeansr.androideditor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CompilerSetup {

    // Extrae los archivos de assets a la memoria interna
    fun initEngineFiles(context: Context) {
        val engineFolder = File(context.filesDir, "compiler_engine")

        // Creamos la carpeta si no existe
        if (!engineFolder.exists()) {
            engineFolder.mkdirs()
        }

        val filesToExtract = listOf("m3_shared_library.apk", "android_framework_api33.jar")

        for (fileName in filesToExtract) {
            val outFile = File(engineFolder, fileName)

            // LA CLAVE: Solo extraer si no existe.
            // Esto hace que tu app abra al instante después de la primera instalación.
            if (!outFile.exists()) {
                try {
                    val inputStream: InputStream = context.assets.open(fileName)
                    val outputStream = FileOutputStream(outFile)

                    // copyTo es una función mágica de Kotlin que maneja los buffers por ti
                    inputStream.copyTo(outputStream)

                    inputStream.close()
                    outputStream.close()
                    println("Jarvis: Extraído exitosamente -> $fileName")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Jarvis: Error crítico al extraer -> $fileName")
                }
            }
        }
    }

    // Funciones de conveniencia para obtener las rutas absolutas exactas
    fun getFrameworkPath(context: Context): String {
        return File(context.filesDir, "compiler_engine/android_framework_api33.jar").absolutePath
    }

    fun getMaterialLibraryPath(context: Context): String {
        return File(context.filesDir, "compiler_engine/m3_super_library.apk").absolutePath
    }
}