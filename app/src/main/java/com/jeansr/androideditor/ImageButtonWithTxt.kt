package com.jeansr.androideditor

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes

class ImageButtonWithTxt @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val imageButton = ImageButton(context)
    private val textView = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        imageButton.setBackgroundColor(Color.TRANSPARENT)
        textView.setTextColor(Color.WHITE)
        textView.gravity = Gravity.CENTER

        addView(imageButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))


        attrs?.let {
            context.withStyledAttributes(it, R.styleable.ImageButtonWithTxt, 0, 0) {
                val srcResId = getResourceId(R.styleable.ImageButtonWithTxt_buttonSrc, -1)
                if (srcResId != -1) {
                    imageButton.setImageResource(srcResId)
                }

                val text = getString(R.styleable.ImageButtonWithTxt_buttonText)
                val textcolor= getColor(R.styleable.ImageButtonWithTxt_buttonTextColor, Color.WHITE)
                textView.text = text ?: ""
                textView.setTextColor(textcolor)
                textView.setTextSize(12F)
            }
        }
    }
}