package com.remotelg.remotelg.utils.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.ViewPassCodeBinding

class PassCodeView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : RelativeLayout(context, attrs, defStyle, defStyleRes) {

  private val viewBinding: ViewPassCodeBinding =
    ViewPassCodeBinding.inflate(LayoutInflater.from(context), this, true)

  init {
    attrs?.let {
      val typedArray = context.obtainStyledAttributes(
        it,
        R.styleable.PassCodeView, 0, 0
      )

      val content = resources.getText(
        typedArray
          .getResourceId(
            R.styleable
              .PassCodeView_passViewNumber,
            R.string.empty
          )
      )

      viewBinding.content.text = content
      typedArray.recycle()
    }
  }
}
