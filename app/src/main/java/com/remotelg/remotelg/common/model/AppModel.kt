package com.remotelg.remotelg.common.model

import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.DiffUtil

data class AppModel(
  val id: String,
  val name: String,
  val image: Drawable,
  val hasFavorite: Boolean = false
) {
  fun isSameExceptFavorite(other: AppModel): Boolean = id == other.id &&
    name == other.name &&
    image == other.image &&
    hasFavorite != other.hasFavorite
}

val APPS_MODEL_DIFF = AppsModelItemBack()

class AppsModelItemBack : DiffUtil.ItemCallback<AppModel>() {
  override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
    return oldItem == newItem
  }

  override fun getChangePayload(oldItem: AppModel, newItem: AppModel): Any? {
    return if (newItem.isSameExceptFavorite(oldItem)) {
      newItem.hasFavorite
    } else {
      null
    }
  }
}
