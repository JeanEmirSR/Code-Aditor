package com.jeansr.androideditor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.cardview.widget.CardView

class PreviewXml @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val phoneCard = CardView(context)
    val contentArea = FrameLayout(context) // Público para que la Activity lo use
    private val sideBar = LinearLayout(context)

    var onModeChanged: ((modo: String) -> Unit)? = null

    init {
        // Fondo gris oscuro estilo Studio
        setBackgroundColor(Color.parseColor("#181818"))

        // 1. Cuerpo del Teléfono
        phoneCard.apply {
            radius = dpToPx(12f).toFloat()
            cardElevation = dpToPx(15f).toFloat()
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            layoutParams = LayoutParams(0, 0).apply {
                gravity = Gravity.CENTER
                marginEnd = dpToPx(40f) // Espacio para la barra lateral
            }
        }

        // 2. Barra Lateral (Side Palette)
        setupSideBar()

        phoneCard.addView(contentArea)
        addView(phoneCard)
        addView(sideBar)
    }

    private fun setupSideBar() {
        sideBar.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#2D2D30"))
            elevation = dpToPx(20f).toFloat() // Siempre encima de todo
            layoutParams = LayoutParams(dpToPx(45f), LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
            setPadding(0, dpToPx(20f), 0, 0)
        }

        sideBar.addView(crearBotonModo("ORIG", "ORIGINAL"))
        sideBar.addView(crearBotonModo("LIN", "LINEAR"))
        sideBar.addView(crearBotonModo("REL", "RELATIVE"))
    }

    private fun crearBotonModo(label: String, modoReal: String) = Button(context).apply {
        text = label
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#9E9E9E"))
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f)).apply {
            setMargins(0, 4, 0, 4)
        }
        setOnClickListener {
            onModeChanged?.invoke(modoReal)
            actualizarBotones(this)
        }
    }

    private fun actualizarBotones(activo: Button) {
        for (i in 0 until sideBar.childCount) {
            val btn = sideBar.getChildAt(i) as? Button ?: continue
            if (btn == activo) {
                btn.setTextColor(Color.parseColor("#4285F4"))
                btn.setBackgroundColor(Color.parseColor("#3A3A3B"))
            } else {
                btn.setTextColor(Color.parseColor("#9E9E9E"))
                btn.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)

        // Calculamos escala 9:16 automática
        val targetHeight = (h * 0.85).toInt()
        val targetWidth = (targetHeight * 0.562).toInt()

        val lp = phoneCard.layoutParams
        lp.width = targetWidth
        lp.height = targetHeight

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun clear() = contentArea.removeAllViews()

    fun setPreviewView(view: View) {
        clear()
        contentArea.addView(view, LayoutParams(-1, -1))
        sideBar.bringToFront() // MANTENER BOTONES SIEMPRE VISIBLES
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}