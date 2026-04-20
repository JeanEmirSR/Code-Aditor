package com.jeansr.androideditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class LineNumberEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#606060") // Gris para los números
        textSize = 15f // Ajusta según tu editor
        textAlign = Paint.Align.RIGHT
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#1E1E1E") // Fondo del margen
    }

    private val paddingLeftCustom = 100 // Espacio para los números

    init {
        // Reservamos el espacio a la izquierda
        setPadding(paddingLeftCustom, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Dibujar el fondo del margen de números
        canvas.drawRect(0f, 0f, paddingLeftCustom.toFloat() - 10f, height.toFloat(), backgroundPaint)

        // 2. Dibujar los números de línea
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