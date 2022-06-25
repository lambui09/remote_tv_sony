package com.remotelg.remotelg.common.model

import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.DiffUtil

data class BroadcastModel(
  val id: String? = null,
  val name: String? = null,
  val image: Drawable? = null
)
val BROAD_CAST_DIFF = BroadcastDiff()
class BroadcastDiff : DiffUtil.ItemCallback<BroadcastModel>() {
  override fun areItemsTheSame(oldItem: BroadcastModel, newItem: BroadcastModel): Boolean {
    return oldItem == newItem
  }

  override fun areContentsTheSame(oldItem: BroadcastModel, newItem: BroadcastModel): Boolean {
    return oldItem == newItem
  }
}
