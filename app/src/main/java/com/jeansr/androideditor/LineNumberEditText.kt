package com.jeansr.androideditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.toColorInt

class LineNumberEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#606060".toColorInt() // Gray numbers
        textSize = 15f
        textAlign = Paint.Align.RIGHT
    }

    private val backgroundPaint = Paint().apply {
        color = "#1E1E1E".toColorInt() // Margins background
    }

    private val paddingLeftCustom = 50 // Numbers padding left

    init {

        setPadding(paddingLeftCustom, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, paddingLeftCustom.toFloat() - 10f, height.toFloat(), backgroundPaint)

        var baseline = baseline.toFloat()
        val lineCount = lineCount
        val layout = layout

        for (i in 0 until lineCount) {
            val lineNumber = (i + 1).toString()

            // Solo dibujamos el número si es el inicio de una línea real (no un wrap)
            if (layout.getLineStart(i) == 0 || text?.get(layout.getLineStart(i) - 1) == '\n') {
                canvas.drawText(lineNumber, paddingLeftCustom.toFloat() - 25f, baseline, linePaint)
            }

            baseline += lineHeight
        }

        super.onDraw(canvas)
    }
}