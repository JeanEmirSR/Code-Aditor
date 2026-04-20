package com.jeansr.androideditor

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {

    /**
     * Ejecuta un comando en la terminal oculta de Android.
     * Ejemplo de uso: val resultado = ShellExecutor.runCommand("ls -la")
     */
    fun runCommand(command: String): ShellResult {
        return try {
            // Inicia un nuevo proceso en el sistema operativo
            val process = Runtime.getRuntime().exec(command)

            // Lee lo que el proceso responde (El output "bueno")
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }

            // Lee si el proceso lanzó algún error
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }

            // Espera a que termine y obtiene el código (0 significa éxito)
            val exitCode = process.waitFor()

            ShellResult(
                success = exitCode == 0,
                output = output.trim(),
                error = error.trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Error desconocido", -1)
        }
    }
}

// Clase para guardar los resultados del comando
data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)