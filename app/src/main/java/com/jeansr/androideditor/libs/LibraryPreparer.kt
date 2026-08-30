package com.jeansr.androideditor.libs

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import java.io.File
import java.util.zip.ZipFile

data class PreparedLibrary(
    val coord: MavenResolver.Coordinate,
    val staticResApk: File?,
    val dexFile: File?,
)

class LibraryPreparer(
    private val aapt2File: File,
    private val androidJarFile: File,
    private val runShell: (String) -> ShellResultLike,
    private val log: (String, Boolean) -> Unit,
) {
    data class ShellResultLike(val success: Boolean, val output: String, val error: String)

    fun prepare(artifact: MavenResolver.ResolvedArtifact, coord: MavenResolver.Coordinate, workspace: File): PreparedLibrary {
        val libWorkDir = File(workspace, "libs/${coord.artifactId}").apply { mkdirs() }

        if (!artifact.isAar) {
            val dex = dexClassesJar(artifact.file, File(libWorkDir, "dex"))
            return PreparedLibrary(coord, null, dex)
        }

        val extractDir = File(libWorkDir, "extracted").apply { mkdirs() }
        extractAar(artifact.file, extractDir)

        val classesJar = File(extractDir, "classes.jar").takeIf { it.exists() }
        val resDir = File(extractDir, "res").takeIf { it.exists() && it.listFiles()?.isNotEmpty() == true }
        val manifest = File(extractDir, "AndroidManifest.xml")

        val staticApk = if (resDir != null && manifest.exists()) {
            compileStaticResLib(coord, resDir, manifest, libWorkDir)
        } else null

        val dex = classesJar?.let { dexClassesJar(it, File(libWorkDir, "dex")) }

        return PreparedLibrary(coord, staticApk, dex)
    }

    private fun extractAar(aarFile: File, destDir: File) {
        ZipFile(aarFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun compileStaticResLib(coord: MavenResolver.Coordinate, resDir: File, manifest: File, workDir: File): File? {
        val compiledZip = File(workDir, "compiled.zip")
        val outApk = File(workDir, "${coord.artifactId}_static.apk")

        val compile = runShell("${aapt2File.absolutePath} compile --dir ${resDir.absolutePath} -o ${compiledZip.absolutePath}")
        if (!compile.success) {
            log("[LIB ${coord.artifactId}] resource compile failed: ${compile.error}", true)
            return null
        }

        val link = runShell(
            "${aapt2File.absolutePath} link --static-lib " +
                "-I ${androidJarFile.absolutePath} " +
                "--manifest ${manifest.absolutePath} " +
                "-o ${outApk.absolutePath} ${compiledZip.absolutePath} " +
                "--auto-add-overlay --allow-reserved-package-id --non-constant-id"
        )
        if (!link.success) {
            log("[LIB ${coord.artifactId}] resource link failed: ${link.error}", true)
            return null
        }
        return outApk
    }

    private fun dexClassesJar(classesJar: File, outputDir: File): File? {
        return try {
            outputDir.mkdirs()
            D8.run(
                D8Command.builder()
                    .addProgramFiles(classesJar.toPath())
                    .addLibraryFiles(androidJarFile.toPath())
                    .setOutput(outputDir.toPath(), OutputMode.DexIndexed)
                    .setMinApiLevel(26)
                    .setMode(CompilationMode.DEBUG)
                    .build()
            )
            File(outputDir, "classes.dex").takeIf { it.exists() }
        } catch (e: Exception) {
            log("[D8] failed to dex ${classesJar.name}: ${e.message}", true)
            null
        }
    }
}