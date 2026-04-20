package com.jeansr.androideditor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GitManager(
    private val projectRoot: File
) {
    private val TAG = "GitManagerJGit"

    // --- MODELOS DE DATOS ---
    data class GitCommit(val sha: String, val shortSha: String, val message: String, val author: String, val date: String, val isLocal: Boolean = false)
    data class GitChangedFile(val path: String, val status: GitFileStatus)
    enum class GitFileStatus { MODIFIED, ADDED, DELETED, RENAMED, UNKNOWN }
    data class GitResult(val success: Boolean, val output: String, val error: String = "")
    data class RepoInfo(val branch: String, val remoteUrl: String, val owner: String, val repoName: String, val isGitRepo: Boolean)

    // --- FUNCIONES CORE ---

    suspend fun obtenerInfoRepo(): RepoInfo = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext RepoInfo("sin-git", "", "", "", false)

            Git.open(projectRoot).use { git ->
                val repo = git.repository
                val branch = repo.branch ?: "main"
                val remoteUrl = repo.config.getString("remote", "origin", "url") ?: ""

                var owner = ""
                var repoName = ""
                if (remoteUrl.contains("github.com")) {
                    val clean = remoteUrl.replace("https://github.com/", "")
                        .replace("git@github.com:", "")
                        .removeSuffix(".git").trim()
                    val parts = clean.split("/")
                    if (parts.size >= 2) { owner = parts[0]; repoName = parts[1] }
                }
                RepoInfo(branch, remoteUrl, owner, repoName, true)
            }
        } catch (e: Exception) {
            RepoInfo("error", "", "", "", false)
        }
    }

    suspend fun clonarRepo(
        token: String,
        owner: String,
        repo: String,
        destino: File,
        rama: String? = null // <-- 1. Nuevo parámetro opcional
    ): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "Token vacío")

        if (destino.exists()) destino.deleteRecursively()
        destino.mkdirs()

        try {
            val logRama = if (rama != null) " (rama: $rama)" else ""
            Log.d(TAG, "> [Git] Iniciando clonado puro de $owner/$repo$logRama...")
            val cloneUrl = "https://github.com/$owner/$repo.git"

            // 2. Preparamos el comando de JGit
            val cloneCommand = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(destino)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))

            // 3. Si el usuario eligió una rama, le decimos a JGit que solo traiga esa
            if (rama != null) {
                cloneCommand.setBranch("refs/heads/$rama")
                cloneCommand.setBranchesToClone(listOf("refs/heads/$rama"))
            }

            // 4. Ejecutamos el comando
            cloneCommand.call().use {
                Log.d(TAG, "> [Git] Clonado exitoso.")
            }

            GitResult(true, "Clonado exitoso de $owner/$repo$logRama")
        } catch (e: GitAPIException) {
            Log.e(TAG, "Error clonando: ${e.message}")
            destino.deleteRecursively()
            GitResult(false, "", e.message ?: "Error desconocido al clonar")
        } catch (e: Exception) {
            Log.e(TAG, "Error sistema: ${e.message}")
            destino.deleteRecursively()
            GitResult(false, "", e.message ?: "Error del sistema de archivos")
        }
    }

    suspend fun obtenerArchivosCambiados(): List<GitChangedFile> = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext emptyList()

            Git.open(projectRoot).use { git ->
                val status = git.status().call()
                val cambios = mutableListOf<GitChangedFile>()

                status.modified.forEach { cambios.add(GitChangedFile(it, GitFileStatus.MODIFIED)) }
                status.added.forEach { cambios.add(GitChangedFile(it, GitFileStatus.ADDED)) }
                status.untracked.forEach { cambios.add(GitChangedFile(it, GitFileStatus.ADDED)) }
                status.missing.forEach { cambios.add(GitChangedFile(it, GitFileStatus.DELETED)) }
                status.removed.forEach { cambios.add(GitChangedFile(it, GitFileStatus.DELETED)) }

                cambios
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun stageFile(path: String): GitResult = withContext(Dispatchers.IO) {
        try {
            Git.open(projectRoot).use { git ->
                git.add().addFilepattern(path).call()
            }
            GitResult(true, "Archivo $path preparado")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error al preparar archivo")
        }
    }

    suspend fun commit(message: String): GitResult = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext GitResult(false, "", "Mensaje vacío")
        try {
            Git.open(projectRoot).use { git ->
                // git add -A
                git.add().addFilepattern(".").call()
                // git commit -m
                /*
                git.commit()
                    .setMessage(message)
                    .setAuthor("Jarvis Engine", "jarvis@engine.local")
                    .call()

                 */
                git.commit().setMessage(message).call()
            }
            GitResult(true, "Commit guardado.")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error al hacer commit")
        }
    }

    suspend fun pull(token: String, owner: String, repo: String, branch: String = "main"): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "Token vacío")
        try {
            Git.open(projectRoot).use { git ->
                val pullResult = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(branch)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                    .call()

                if (pullResult.isSuccessful) {
                    GitResult(true, "Pull exitoso.")
                } else {
                    GitResult(false, "", "El pull finalizó con conflictos o errores.")
                }
            }
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error al hacer pull")
        }
    }

    suspend fun push(token: String, owner: String, repo: String, branch: String = "main"): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "Token vacío")
        try {
            Git.open(projectRoot).use { git ->
                git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                    .call()
            }
            GitResult(true, "Push exitoso hacia origin/$branch")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error al hacer push")
        }
    }

    suspend fun obtenerCommitsLocales(limite: Int = 20): List<GitCommit> = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext emptyList()

            Git.open(projectRoot).use { git ->
                val logs = git.log().setMaxCount(limite).call()
                val commits = mutableListOf<GitCommit>()

                // Formateador para que la fecha se vea limpia
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                for (rev in logs) {
                    val sha = rev.name
                    val author = rev.authorIdent.name
                    val dateStr = sdf.format(java.util.Date(rev.commitTime * 1000L))
                    val msg = rev.shortMessage

                    commits.add(GitCommit(sha, sha.take(7), msg, author, dateStr, true))
                }
                commits
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo commits locales: ${e.message}")
            emptyList()
        }
    }
    suspend fun sync(token: String, owner: String, repo: String, branch: String): GitResult = withContext(Dispatchers.IO) {
        // 1. Primero intentamos traer los cambios
        val pullRes = pull(token, owner, repo, branch)

        // Si el pull falla rotundamente (y no es solo porque ya está actualizado), abortamos
        if (!pullRes.success && !pullRes.output.contains("Up-to-date") && !pullRes.error.contains("up to date")) {
            return@withContext pullRes
        }

        // 2. Si el pull fue bien, subimos nuestros cambios
        push(token, owner, repo, branch)
    }

    suspend fun obtenerCommitsGitHub(owner: String, repo: String, token: String, branch: String = "main", limite: Int = 20): List<GitCommit> = withContext(Dispatchers.IO) {
        try {
            // El secreto está aquí: &sha=$branch le dice a la API qué rama leer
            val url = URL("https://api.github.com/repos/$owner/$repo/commits?per_page=$limite&sha=$branch")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != 200) return@withContext emptyList()

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(response)
            val commits = mutableListOf<GitCommit>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sha = obj.optString("sha", "")
                val commitObj = obj.optJSONObject("commit") ?: continue
                val message = commitObj.optString("message", "").lines().first()
                val authorObj = commitObj.optJSONObject("author")

                commits.add(
                    GitCommit(
                        sha = sha,
                        shortSha = sha.take(7),
                        message = message,
                        author = authorObj?.optString("name", "") ?: "",
                        date = authorObj?.optString("date", "")?.take(10) ?: "",
                        isLocal = false
                    )
                )
            }
            commits
        } catch (e: Exception) {
            Log.e(TAG, "Error en API GitHub commits: ${e.message}")
            emptyList()
        }
    }

    suspend fun obtenerUsuarioGitHub(token: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(response).optString("login", "")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en API GitHub user: ${e.message}")
            ""
        }
    }

    // Lee las ramas directamente desde GitHub sin descargar el repositorio
    suspend fun obtenerRamasRemotas(token: String, owner: String, repoName: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/$owner/$repoName.git"
            val refs = Git.lsRemoteRepository()
                .setRemote(url)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                .setHeads(true) // Solo queremos las ramas (heads), no los tags
                .call()

            // Transformamos "refs/heads/main" a "main"
            refs.map { it.name.removePrefix("refs/heads/") }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ramas: ${e.message}")
            emptyList()
        }
    }

    suspend fun configurarIdentidad(nombre: String, correo: String): GitResult = withContext(Dispatchers.IO) {
        if (nombre.isBlank() || correo.isBlank()) {
            return@withContext GitResult(false, "", "El nombre y correo no pueden estar vacíos")
        }

        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) {
                return@withContext GitResult(false, "", "No hay un repositorio Git inicializado en esta carpeta.")
            }

            // Abrimos el repositorio con JGit
            Git.open(projectRoot).use { git ->
                val config = git.repository.config

                // Escribimos los valores en la sección [user]
                config.setString("user", null, "name", nombre)
                config.setString("user", null, "email", correo)

                // Guardamos los cambios en el archivo .git/config
                config.save()
            }

            Log.d(TAG, "> [Git] Identidad configurada: $nombre <$correo>")
            GitResult(true, "Identidad configurada exitosamente.")

        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar identidad: ${e.message}")
            GitResult(false, "", "Error al guardar la identidad: ${e.message}")
        }
    }

    /**
     * Obtiene las tres versiones de un archivo en conflicto:
     * Pair.first = Contenido LOCAL (OURS)
     * Pair.second = Contenido REMOTO (THEIRS)
     */
    /**
     * Extrae las versiones LOCAL y REMOTA analizando las marcas de conflicto
     * que JGit deja en el archivo físico.
     */
    suspend fun obtenerVersionesConflicto(relativeFilePath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val archivoFisico = File(projectRoot, relativeFilePath)
            if (!archivoFisico.exists()) return@withContext Pair("Archivo no encontrado", "")

            val contenidoCompleto = archivoFisico.readText()

            // Usamos Regex para extraer lo que hay entre las marcas de Git
            // <<<<<<< HEAD (Local)
            // ======= (Separador)
            // >>>>>>> [SHA/Branch] (Remoto)

            val localMatch = Regex("<<<<<<< HEAD\\n([\\s\\S]*?)\\n=======").find(contenidoCompleto)
            val remoteMatch = Regex("=======\\n([\\s\\S]*?)\\n>>>>>>>").find(contenidoCompleto)

            val local = localMatch?.groupValues?.get(1) ?: "No se detectaron cambios locales o el archivo ya se procesó."
            val remote = remoteMatch?.groupValues?.get(1) ?: "No se detectaron cambios remotos."

            Pair(local, remote)
        } catch (e: Exception) {
            Log.e("GitManager", "Error parseando conflicto: ${e.message}")
            Pair("Error al leer conflicto", "")
        }
    }

    /**
     * Esta función es la que llama el botón "RESOLVER" del Fragment.
     */
    suspend fun resolverConflicto(relativeFilePath: String, contenidoFinal: String): GitResult = withContext(Dispatchers.IO) {
        try {
            val file = File(projectRoot, relativeFilePath)

            // 1. Escribimos la decisión final del usuario en el disco
            file.writeText(contenidoFinal)

            // 2. IMPORTANTE: git add <archivo> para marcarlo como resuelto
            Git.open(projectRoot).use { git ->
                git.add().addFilepattern(relativeFilePath).call()

                // Verificamos si ya no quedan conflictos en el repo
                val status = git.status().call()
                if (status.conflicting.isEmpty()) {
                    // Si ya no hay conflictos, podrías hacer el commit automático,
                    // pero es mejor dejar que el usuario lo haga después.
                    Log.d("GitManager", "Todos los conflictos resueltos.")
                }
            }
            GitResult(true, "Archivo resuelto con éxito.")
        } catch (e: Exception) {
            Log.e("GitManager", "Error al resolver: ${e.message}")
            GitResult(false, "", e.message ?: "Error desconocido")
        }
    }
    suspend fun obtenerArchivosEnConflicto(): List<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(projectRoot).use { git ->
                val status = git.status().call()
                // status.conflicting devuelve un Set<String> con las rutas relativas
                status.conflicting.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}