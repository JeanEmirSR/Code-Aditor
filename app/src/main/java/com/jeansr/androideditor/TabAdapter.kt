import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeansr.androideditor.R
import java.io.File
import java.util.Collections

class TabAdapter(
    private val onTabClick: (File) -> Unit,
    private val onTabClose: (File) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    var openTabs = mutableListOf<File>()
    var activeFile: File? = null

    fun updateData(newList: List<File>, current: File?) {
        this.openTabs = newList.toMutableList()
        this.activeFile = current
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val file = openTabs[position]
        holder.tvName.text = file.name

        // Lógica de selección: resaltar la pestaña activa
        val isSelected = file.absolutePath == activeFile?.absolutePath

        // Cambiamos el tinte del fondo (alltab) según el estado
        holder.layoutRoot.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (isSelected) "#1E2E4A" else "#142033")
        )
        holder.layoutRoot.alpha = if (isSelected) 1.0f else 0.7f

        holder.itemView.setOnClickListener { onTabClick(file) }
        holder.btnClose.setOnClickListener { onTabClose(file) }
    }

    override fun getItemCount() = openTabs.size

    // Función necesaria para el reordenamiento físico
    fun moveItem(from: Int, to: Int) {
        Collections.swap(openTabs, from, to)
        notifyItemMoved(from, to)
    }

    class TabViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val layoutRoot: View = v.findViewById(R.id.alltab)
        val tvName: TextView = v.findViewById(R.id.namefile)
        val btnClose: ImageView = v.findViewById(R.id.close)
    }
}