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

    private val paddingLeftCustom = 80 // Numbers padding left

    init {
        setPadding(paddingLeftCustom, paddingTop, paddingRight, paddingBottom)

        // --- ENCAPSULATED EDITOR SETTINGS ---
        // Disable spell checker and smart suggestions natively inside the component.
        // This ensures maximum typing performance anywhere this view is used.
        this.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    override fun onDraw(canvas: Canvas) {
        val currentLayout = layout ?: run {
            super.onDraw(canvas)
            return
        }

        // 1. Draw the gutter background anchored to the current scroll position.
        // Using scrollY and scrollX ensures the background doesn't disappear when scrolling!
        canvas.drawRect(
            scrollX.toFloat(),
            scrollY.toFloat(),
            (scrollX + paddingLeftCustom - 10).toFloat(),
            (scrollY + height).toFloat(),
            backgroundPaint
        )

        // 2. Get ONLY the visible lines on the screen
        val currentScrollY = scrollY
        val firstVisibleLine = currentLayout.getLineForVertical(currentScrollY)
        val lastVisibleLine = currentLayout.getLineForVertical(currentScrollY + height)

        // 3. Calculate the REAL line number for the first visible line (ignoring word-wraps)
        val startOffset = currentLayout.getLineStart(firstVisibleLine)
        var realLineNumber = 1

        val textSequence = text ?: ""
        // This loop is ultra-fast and creates zero memory garbage
        for (i in 0 until startOffset) {
            if (textSequence[i] == '\n') {
                realLineNumber++
            }
        }

        // 4. Draw the numbers ONLY for the visible block (e.g., 30 iterations instead of 5,000)
        for (i in firstVisibleLine..lastVisibleLine) {
            val lineStart = currentLayout.getLineStart(i)

            // Draw the number only if it's the actual start of a line (not a word-wrap break)
            if (lineStart == 0 || textSequence[lineStart - 1] == '\n') {
                val baseline = currentLayout.getLineBaseline(i).toFloat()

                canvas.drawText(
                    realLineNumber.toString(),
                    (scrollX + paddingLeftCustom - 25).toFloat(),
                    baseline,
                    linePaint
                )
                realLineNumber++
            }
        }

        super.onDraw(canvas)
    }
}