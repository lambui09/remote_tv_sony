package com.remotelg.remotelg.ui.apps.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.remotelg.remotelg.R
import com.remotelg.remotelg.common.model.APPS_MODEL_DIFF
import com.remotelg.remotelg.common.model.AppModel
import com.remotelg.remotelg.databinding.ItemViewAppsBinding

class AppsAdapter(
  private val onClick: (AppModel) -> Unit,
  private val onToggleFavorite: (AppModel) -> Unit,
) : ListAdapter<AppModel, AppsAdapter.AppsVH>(APPS_MODEL_DIFF) {

  inner class AppsVH(val view: ItemViewAppsBinding) : RecyclerView.ViewHolder(view.root) {
    init {
      itemView.setOnClickListener {
        adapterPosition
          .takeIf { it != RecyclerView.NO_POSITION }
          ?.let(::getItem)
          ?.let(onClick)
      }

      view.appCompatImageView.setOnClickListener {
        adapterPosition
          .takeIf { it != RecyclerView.NO_POSITION }
          ?.let(::getItem)
          ?.let(onToggleFavorite)
      }
    }

    internal fun bindData(appModel: AppModel) {
      with(view) {
        imgIconApp.setImageDrawable(appModel.image)
        tvTitle.text = appModel.name
        updateFavoriteStatus(appModel.hasFavorite)
      }
    }

    internal fun updateFavoriteStatus(hasFavorite: Boolean) {
      view.appCompatImageView.setImageDrawable(
        ContextCompat.getDrawable(
          itemView.context,
          if (hasFavorite) R.drawable.ic_favorite_fill
          else R.drawable.ic_favorite
        )
      )
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsVH {
    return AppsVH(
      ItemViewAppsBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false
      )
    )
  }

  override fun onBindViewHolder(holder: AppsVH, position: Int, payloads: List<Any>) {
    if (payloads.isEmpty()) {
      return onBindViewHolder(holder, position)
    }
    payloads.forEach { payload ->
      if (payload is Boolean) {
        holder.updateFavoriteStatus(payload)
      }
    }
  }

  override fun onBindViewHolder(holder: AppsVH, position: Int) {
    holder.bindData(getItem(position))
  }
}
