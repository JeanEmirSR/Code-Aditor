package com.jeansr.androideditor

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {

    /**
     * Executes a command in the hidden Android terminal.
     * Usage example: val result = ShellExecutor.runCommand("ls -la")
     */
    fun runCommand(command: String): ShellResult {
        return try {
            // Starts a new process in the operating system
            val process = Runtime.getRuntime().exec(command)

            // Reads what the process returns (The "good" output)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }

            // Reads if the process threw any error
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }

            // Waits for it to finish and gets the code (0 means success)
            val exitCode = process.waitFor()

            ShellResult(
                success = exitCode == 0,
                output = output.trim(),
                error = error.trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Unknown error", -1)
        }
    }
}

// Class to store the command results
data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)