package com.jeansr.androideditor.libs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MavenResolver {

    private val REPOS = listOf(
        "https://dl.google.com/dl/android/maven2",
        "https://repo1.maven.org/maven2"
    )

    data class Coordinate(val groupId: String, val artifactId: String, val version: String) {
        val path: String get() = "${groupId.replace('.', '/')}/$artifactId/$version"
        val id: String get() = "$groupId:$artifactId:$version"
    }

    data class ResolvedArtifact(val file: File, val isAar: Boolean)

    suspend fun resolve(coord: Coordinate, cacheDir: File): ResolvedArtifact? = withContext(Dispatchers.IO) {
        val destDir = File(cacheDir, coord.path).apply { mkdirs() }

        for (repo in REPOS) {
            for (ext in listOf("aar", "jar")) {
                val fileName = "${coord.artifactId}-${coord.version}.$ext"
                val dest = File(destDir, fileName)

                if (dest.exists() && dest.length() > 0) {
                    return@withContext ResolvedArtifact(dest, ext == "aar")
                }

                val url = "$repo/${coord.path}/$fileName"
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        requestMethod = "GET"
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        return@withContext ResolvedArtifact(dest, ext == "aar")
                    }
                } catch (e: Exception) {
                }
            }
        }
        null
    }
}