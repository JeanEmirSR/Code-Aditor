package com.jeansr.androideditor.libs

import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File
import java.io.PrintStream

class ProjectSourceCompiler(
    private val androidJarFile: File,
    private val log: (String, Boolean) -> Unit,
) {
    fun compile(sourceRoots: List<File>, classpathJars: List<File>, workDir: File): File? {
        val sourceFiles = sourceRoots
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }

        if (sourceFiles.isEmpty()) return null

        val classesOut = File(workDir, "project_classes").apply { deleteRecursively(); mkdirs() }
        val classpath = (classpathJars + androidJarFile).joinToString(File.pathSeparator) { it.absolutePath }

        val compilerArgs = mutableListOf<String>().apply {
            addAll(sourceFiles.map { it.absolutePath })
            add("-cp"); add(classpath)
            add("-d"); add(classesOut.absolutePath)
            add("-jvm-target"); add("1.8")
            add("-no-stdlib")
            add("-no-reflect")
        }

        val logFile = File(workDir, "kotlinc.log")
        val exitCode = PrintStream(logFile.outputStream()).use { stream ->
            K2JVMCompiler().exec(stream, *compilerArgs.toTypedArray())
        }

        if (exitCode.code != 0) {
            log("[KOTLINC] compilation failed: ${logFile.readText().take(2000)}", true)
            return null
        }

        return dexClasses(classesOut, File(workDir, "project_dex"))
    }

    private fun dexClasses(classesDir: File, outputDir: File): File? {
        return try {
            outputDir.mkdirs()
            D8.run(
                D8Command.builder()
                    .addProgramFiles(classesDir.toPath())
                    .addLibraryFiles(androidJarFile.toPath())
                    .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
                    .setMinApiLevel(26)
                    .build()
            )
            File(outputDir, "classes.dex").takeIf { it.exists() }
        } catch (e: Exception) {
            log("[D8] failed to dex project sources: ${e.message}", true)
            null
        }
    }
}