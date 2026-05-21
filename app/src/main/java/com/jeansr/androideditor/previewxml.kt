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
import androidx.core.graphics.toColorInt

class PreviewXml @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val phoneCard = CardView(context)
    val contentArea = FrameLayout(context) // Público para que la Activity lo use
    private val sideBar = LinearLayout(context)

    var onModeChanged: ((modo: String) -> Unit)? = null

    init {
        setBackgroundColor("#181818".toColorInt())

        // 1. Phone Body (Card)
        phoneCard.apply {
            radius = dpToPx(12f).toFloat()
            cardElevation = dpToPx(15f).toFloat()
            setCardBackgroundColor(Color.WHITE)
            preventCornerOverlap = true
            layoutParams = LayoutParams(0, 0).apply {
                gravity = Gravity.CENTER
                marginEnd = dpToPx(40f)
            }
        }

        // 2. Side Palette
        setupSideBar()
        phoneCard.addView(contentArea)
        addView(phoneCard)
        addView(sideBar)
    }

    private fun setupSideBar() {
        sideBar.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor("#2D2D30".toColorInt())
            elevation = dpToPx(20f).toFloat() // Always on top
            layoutParams = LayoutParams(dpToPx(45f), LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
            setPadding(0, dpToPx(20f), 0, 0)
        }

        sideBar.addView(createModeButton("ORIG", "ORIGINAL"))
        sideBar.addView(createModeButton("LIN", "LINEAR"))
        sideBar.addView(createModeButton("REL", "RELATIVE"))
    }

    private fun createModeButton(label: String, realMode: String) = Button(context).apply {
        text = label
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor("#9E9E9E".toColorInt())
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f)).apply {
            setMargins(0, 4, 0, 4)
        }
        setOnClickListener {
            onModeChanged?.invoke(realMode)
            updateButton(this)
        }
    }

    private fun updateButton(active: Button) {
        for (i in 0 until sideBar.childCount) {
            val btn = sideBar.getChildAt(i) as? Button ?: continue
            if (btn == active) {
                btn.setTextColor("#4285F4".toColorInt())
                btn.setBackgroundColor("#3A3A3B".toColorInt())
            } else {
                btn.setTextColor("#9E9E9E".toColorInt())
                btn.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)

        // Automatically calculate 9:16 scale
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
        sideBar.bringToFront()
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}