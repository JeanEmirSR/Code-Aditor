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

    // --- MODEL DATA ---
    data class GitCommit(val sha: String, val shortSha: String, val message: String, val author: String, val date: String, val isLocal: Boolean = false)
    data class GitChangedFile(val path: String, val status: GitFileStatus)
    enum class GitFileStatus { MODIFIED, ADDED, DELETED, RENAMED, UNKNOWN }
    data class GitResult(val success: Boolean, val output: String, val error: String = "")
    data class RepoInfo(val branch: String, val remoteUrl: String, val owner: String, val repoName: String, val isGitRepo: Boolean)


    suspend fun getRepoInfo(): RepoInfo = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext RepoInfo("no-git", "", "", "", false)

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

    suspend fun cloneRepo(
        token: String,
        owner: String,
        repo: String,
        destination: File,
        branch: String? = null
    ): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "empty token")

        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()

        try {
            val branchLog = if (branch != null) " (rama: $branch)" else ""
            Log.d(TAG, "> [Git] Starting bare clone of $owner/$repo$branchLog...")
            val cloneUrl = "https://github.com/$owner/$repo.git"


            val cloneCommand = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(destination)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))

            if (branch != null) {
                cloneCommand.setBranch("refs/heads/$branch")
                cloneCommand.setBranchesToClone(listOf("refs/heads/$branch"))
            }

            cloneCommand.call().use {
                Log.d(TAG, "> [Git] Successfully cloned.")
            }

            GitResult(true, "Successfully cloned $owner/$repo$branchLog")
        } catch (e: GitAPIException) {
            Log.e(TAG, "Error: ${e.message}")
            destination.deleteRecursively()
            GitResult(false, "", e.message ?: "Unknown error while cloning")
        } catch (e: Exception) {
            Log.e(TAG, "System error: ${e.message}")
            destination.deleteRecursively()
            GitResult(false, "", e.message ?: "Error on system files")
        }
    }

    suspend fun getChangedFiles(): List<GitChangedFile> = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext emptyList()

            Git.open(projectRoot).use { git ->
                val status = git.status().call()
                val changes = mutableListOf<GitChangedFile>()

                status.modified.forEach { changes.add(GitChangedFile(it, GitFileStatus.MODIFIED)) }
                status.added.forEach { changes.add(GitChangedFile(it, GitFileStatus.ADDED)) }
                status.untracked.forEach { changes.add(GitChangedFile(it, GitFileStatus.ADDED)) }
                status.missing.forEach { changes.add(GitChangedFile(it, GitFileStatus.DELETED)) }
                status.removed.forEach { changes.add(GitChangedFile(it, GitFileStatus.DELETED)) }

                changes
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
            GitResult(true, "File $path ready")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error on get ready file")
        }
    }

    suspend fun commit(message: String): GitResult = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext GitResult(false, "", "empty message")
        try {
            Git.open(projectRoot).use { git ->
                // git add -A
                git.add().addFilepattern(".").call()

                git.commit().setMessage(message).call()
            }
            GitResult(true, "Commit Save.")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error on commit")
        }
    }

    suspend fun pull(token: String, owner: String, repo: String, branch: String = "main"): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "Empty Token")
        try {
            Git.open(projectRoot).use { git ->
                val pullResult = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(branch)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                    .call()

                if (pullResult.isSuccessful) {
                    GitResult(true, "Pull Done.")
                } else {
                    GitResult(false, "", "The pull finished with conflicts or errors.")
                }
            }
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error on pull")
        }
    }

    suspend fun push(token: String, owner: String, repo: String, branch: String = "main"): GitResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext GitResult(false, "", "Empty Token")
        try {
            Git.open(projectRoot).use { git ->
                git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                    .call()
            }
            GitResult(true, "Push Done to origin/$branch")
        } catch (e: Exception) {
            GitResult(false, "", e.message ?: "Error on push")
        }
    }

    suspend fun getLocaleCommits(limite: Int = 20): List<GitCommit> = withContext(Dispatchers.IO) {
        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) return@withContext emptyList()

            Git.open(projectRoot).use { git ->
                val logs = git.log().setMaxCount(limite).call()
                val commits = mutableListOf<GitCommit>()
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
            Log.e(TAG, "Error getting local commits : ${e.message}")
            emptyList()
        }
    }
    suspend fun sync(token: String, owner: String, repo: String, branch: String): GitResult = withContext(Dispatchers.IO) {

        val pullRes = pull(token, owner, repo, branch)

        if (!pullRes.success && !pullRes.output.contains("Up-to-date") && !pullRes.error.contains("up to date")) {
            return@withContext pullRes
        }
        push(token, owner, repo, branch)
    }

    suspend fun getGitHubCommits(owner: String, repo: String, token: String, branch: String = "main", limite: Int = 20): List<GitCommit> = withContext(Dispatchers.IO) {
        try {

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
            Log.e(TAG, "Error on API GitHub commits: ${e.message}")
            emptyList()
        }
    }

    suspend fun getGitHubUser(token: String): String = withContext(Dispatchers.IO) {
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
            Log.e(TAG, "Error on API GitHub user: ${e.message}")
            ""
        }
    }

    suspend fun getRemoteBranches(token: String, owner: String, repoName: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/$owner/$repoName.git"
            val refs = Git.lsRemoteRepository()
                .setRemote(url)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                .setHeads(true)
                .call()

            refs.map { it.name.removePrefix("refs/heads/") }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting branches: ${e.message}")
            emptyList()
        }
    }

    suspend fun setupGitIdentity(nombre: String, correo: String): GitResult = withContext(Dispatchers.IO) {
        if (nombre.isBlank() || correo.isBlank()) {
            return@withContext GitResult(false, "", "Name and email cannot be empty.")
        }

        try {
            val gitDir = File(projectRoot, ".git")
            if (!gitDir.exists()) {
                return@withContext GitResult(false, "", "There is no Git repository initialized in this folder.")
            }
            Git.open(projectRoot).use { git ->
                val config = git.repository.config

                config.setString("user", null, "name", nombre)
                config.setString("user", null, "email", correo)

                config.save()
            }

            Log.d(TAG, "> [Git] Identity configured: $nombre <$correo>")
            GitResult(true, "Identity configured successfully.")

        } catch (e: Exception) {
            Log.e(TAG, "Error on configuring identity: ${e.message}")
            GitResult(false, "", "Error on Save identity: ${e.message}")
        }
    }

    /**
     * Gets the three versions of a file in conflict:
     * Pair.first = LOCAL content (OURS)
     * Pair.second = REMOTE content (THEIRS)
     */
    /**
     * Extracts the LOCAL and REMOTE versions by analyzing the conflict markers
     * that JGit leaves in the physical file.
     */
    suspend fun getConflictVersions(relativeFilePath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val conflictingFile = File(projectRoot, relativeFilePath)
            if (!conflictingFile.exists()) return@withContext Pair("Archivo no encontrado", "")

            val fileContent = conflictingFile.readText()


            val localMatch = Regex("<<<<<<< HEAD\\n([\\s\\S]*?)\\n=======").find(fileContent)
            val remoteMatch = Regex("=======\\n([\\s\\S]*?)\\n>>>>>>>").find(fileContent)

            val local = localMatch?.groupValues?.get(1) ?: "No local changes detected or the file has already been processed."
            val remote = remoteMatch?.groupValues?.get(1) ?: "No remote changes detected."

            Pair(local, remote)
        } catch (e: Exception) {
            Log.e("GitManager", "Error parsing conflict: ${e.message}")
            Pair("Error reading conflict", "")
        }
    }

    /**
     * This function is called by the "RESOLVE" button in the Fragment.
     */
    suspend fun resolveConflict(relativeFilePath: String, finalContent: String): GitResult = withContext(Dispatchers.IO) {
        try {
            val file = File(projectRoot, relativeFilePath)

            // 1. Write the user's final decision to disk
            file.writeText(finalContent)

            // 2. IMPORTANT: git add <file> to mark it as resolved
            Git.open(projectRoot).use { git ->
                git.add().addFilepattern(relativeFilePath).call()

                // Verify if there are no remaining conflicts in the repo
                val status = git.status().call()
                if (status.conflicting.isEmpty()) {
                    // If there are no more conflicts, you could do an automatic commit,
                    // but it is better to let the user do it later.
                    Log.d("GitManager", "All conflicts resolved.")
                }
            }
            GitResult(true, "File successfully resolved.")
        } catch (e: Exception) {
            Log.e("GitManager", "Error resolving: ${e.message}")
            GitResult(false, "", e.message ?: "Unknown error")
        }
    }
    suspend fun getConflictingFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(projectRoot).use { git ->
                val status = git.status().call()
                // status.conflicting returns a Set<String> with the relative paths
                status.conflicting.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}