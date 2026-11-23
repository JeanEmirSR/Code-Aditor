package com.jeansr.androideditor.libs

import com.android.apksig.ApkSigner
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class FullApkBuilder(
    private val aapt2File: File,
    private val androidJarFile: File,
    private val runShell: (String) -> ShellResult,
    private val log: (String, Boolean) -> Unit,
) {
    data class ShellResult(val success: Boolean, val output: String, val error: String)

    data class BuildInput(
        val workspace: File,
        val manifestFile: File,
        val resDir: File,
        val extraLibApks: List<File>,
        val projectDex: File?,
        val libDexes: List<File>,
        val keystoreFile: File,
        val keystorePassword: String,
        val outputApk: File,
    )

    fun build(input: BuildInput): Boolean {
        return try {
            log("> [FULL BUILD] Compiling resources...")
            val compiledZip = File(input.workspace, "full_compiled.zip")
            val compile = runShell("${aapt2File.absolutePath} compile --dir ${input.resDir.absolutePath} -o ${compiledZip.absolutePath}")
            if (!compile.success) { log("[AAPT2 COMPILE]\n${compile.error}", true); return false }

            val unsignedApk = File(input.workspace, "unsigned.apk")
            val includes = input.extraLibApks.joinToString(" ") { "-I ${it.absolutePath}" }
            val link = runShell(
                "${aapt2File.absolutePath} link -I ${androidJarFile.absolutePath} $includes " +
                    "-o ${unsignedApk.absolutePath} --manifest ${input.manifestFile.absolutePath} " +
                    "--min-sdk-version 26 --target-sdk-version 33 --auto-add-overlay " +
                    "--allow-reserved-package-id ${compiledZip.absolutePath}"
            )
            if (!link.success) { log("[AAPT2 LINK]\n${link.error}", true); return false }

            log("> [FULL BUILD] Merging dex...")
            val dexInputs = listOfNotNull(input.projectDex) + input.libDexes
            val mergedDir = File(input.workspace, "merged_dex").apply { deleteRecursively(); mkdirs() }
            if (dexInputs.isNotEmpty()) {
                D8.run(
                    D8Command.builder()
                        .addProgramFiles(dexInputs.map { it.toPath() })
                        .setMinApiLevel(26)
                        .setOutput(mergedDir.toPath(), OutputMode.DexIndexed)
                        .build()
                )
            }
            val withDex = File(input.workspace, "with_dex.apk")
            injectDex(unsignedApk, File(mergedDir, "classes.dex"), withDex)

            log("> [FULL BUILD] Signing APK...")
            sign(withDex, input)
            log("> [SUCCESS] Installable APK: ${input.outputApk.absolutePath}")
            true
        } catch (e: Exception) {
            log("[FULL BUILD ERROR] ${e.message}", true)
            false
        }
    }

    private fun injectDex(src: File, dex: File, dst: File) {
        ZipOutputStream(dst.outputStream()).use { out ->
            ZipFile(src).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.name.startsWith("META-INF")) return@forEach
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            if (dex.exists()) {
                out.putNextEntry(ZipEntry("classes.dex"))
                dex.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    private fun sign(src: File, input: BuildInput) {
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(input.keystoreFile).use { keyStore.load(it, input.keystorePassword.toCharArray()) }
        val alias = keyStore.aliases().nextElement()
        val entry = keyStore.getEntry(alias, KeyStore.PasswordProtection(input.keystorePassword.toCharArray())) as KeyStore.PrivateKeyEntry
        val signerConfig = ApkSigner.SignerConfig.Builder("debug", entry.privateKey, listOf(entry.certificate)).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(src)
            .setOutputApk(input.outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()
            .sign()
    }
}
