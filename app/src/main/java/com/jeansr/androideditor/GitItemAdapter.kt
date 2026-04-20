package com.jeansr.androideditor

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter unificado para mostrar tanto archivos cambiados como commits en el panel git.
 */
class GitItemAdapter(
    private val onFileClick: (GitItem) -> Unit,
    private val onFileStage: (GitItem) -> Unit
) : RecyclerView.Adapter<GitItemAdapter.GitViewHolder>() {

    sealed class GitItem {
        data class FileItem(val file: GitManager.GitChangedFile) : GitItem()
        data class CommitItem(val commit: GitManager.GitCommit) : GitItem()
    }

    private val items = mutableListOf<GitItem>()

    fun mostrarArchivos(archivos: List<GitManager.GitChangedFile>) {
        items.clear()
        items.addAll(archivos.map { GitItem.FileItem(it) })
        notifyDataSetChanged()
    }

    fun mostrarCommits(commits: List<GitManager.GitCommit>) {
        items.clear()
        items.addAll(commits.map { GitItem.CommitItem(it) })
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GitViewHolder {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -2)
            setPadding(16, 14, 16, 14)
        }
        return GitViewHolder(row)
    }

    override fun onBindViewHolder(holder: GitViewHolder, position: Int) {
        val item = items[position]
        val row = holder.itemView as LinearLayout
        row.removeAllViews()

        when (item) {
            is GitItem.FileItem -> bindFile(row, item)
            is GitItem.CommitItem -> bindCommit(row, item)
        }

        row.setOnClickListener { onFileClick(item) }
    }

    private fun bindFile(row: LinearLayout, item: GitItem.FileItem) {
        val ctx = row.context

        // Indicador de estado (letra coloreada)
        val tvStatus = TextView(ctx).apply {
            val (letra, color) = when (item.file.status) {
                GitManager.GitFileStatus.MODIFIED -> "M" to "#E2C08D"
                GitManager.GitFileStatus.ADDED    -> "A" to "#73C991"
                GitManager.GitFileStatus.DELETED  -> "D" to "#F14C4C"
                GitManager.GitFileStatus.RENAMED  -> "R" to "#4EC9B0"
                GitManager.GitFileStatus.UNKNOWN  -> "?" to "#9E9E9E"
            }
            text = letra
            setTextColor(Color.parseColor(color))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(28, -2)
            gravity = Gravity.CENTER
        }

        // Nombre del archivo
        val tvName = TextView(ctx).apply {
            text = item.file.path.substringAfterLast("/")
            setTextColor(Color.parseColor("#D4D4D4"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        // Ruta padre (dimmer)
        val tvPath = TextView(ctx).apply {
            text = item.file.path.substringBeforeLast("/", "")
            setTextColor(Color.parseColor("#666666"))
            textSize = 11f
        }

        // Botón stage/unstage
        val tvStage = TextView(ctx).apply {
            text = "+"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 16f
            setPadding(12, 0, 4, 0)
            setOnClickListener { onFileStage(item) }
        }

        row.addView(tvStatus)
        row.addView(tvName)
        row.addView(tvPath)
        row.addView(tvStage)
    }

    private fun bindCommit(row: LinearLayout, item: GitItem.CommitItem) {
        val ctx = row.context

        // SHA corto
        val tvSha = TextView(ctx).apply {
            text = item.commit.shortSha
            setTextColor(Color.parseColor("#4EC9B0"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(60, -2)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        // Columna central: mensaje + autor
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvMsg = TextView(ctx).apply {
            text = item.commit.message
            setTextColor(Color.parseColor("#D4D4D4"))
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val tvAuthor = TextView(ctx).apply {
            text = "${item.commit.author}  ·  ${item.commit.date}"
            setTextColor(Color.parseColor("#666666"))
            textSize = 11f
        }
        col.addView(tvMsg)
        col.addView(tvAuthor)

        row.addView(tvSha)
        row.addView(col)
    }

    class GitViewHolder(view: View) : RecyclerView.ViewHolder(view)
}