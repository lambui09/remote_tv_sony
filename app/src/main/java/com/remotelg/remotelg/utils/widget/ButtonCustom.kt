package com.remotelg.remotelg.utils.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.ViewButtonCustomBinding

class ButtonCustom @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : RelativeLayout(context, attrs, defStyle, defStyleRes) {

  private val viewBinding: ViewButtonCustomBinding =
    ViewButtonCustomBinding.inflate(LayoutInflater.from(context), this, true)

  init {
    attrs?.let {
      val typedArray = context.obtainStyledAttributes(
        it,
        R.styleable.ButtonCustom, 0, 0
      )
      val drawableId = typedArray.getResourceId(R.styleable.ButtonCustom_icon, -1)
      viewBinding.ivIcon.setImageResource(drawableId)
      typedArray.recycle()
    }
  }
}
