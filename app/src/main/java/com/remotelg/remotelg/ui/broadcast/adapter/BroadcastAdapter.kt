package com.remotelg.remotelg.ui.broadcast.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.remotelg.remotelg.common.model.BROAD_CAST_DIFF
import com.remotelg.remotelg.common.model.BroadcastModel
import com.remotelg.remotelg.databinding.ItemViewApplicationBroadcastBinding
import com.remotelg.remotelg.utils.debounceClick

class BroadcastAdapter(private val onClick: (Int) -> Unit) :
  ListAdapter<BroadcastModel, BroadcastVH>(BROAD_CAST_DIFF) {
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BroadcastVH {

    return BroadcastVH(
      ItemViewApplicationBroadcastBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false
      ),
      onClick
    )
  }

  override fun onBindViewHolder(holder: BroadcastVH, position: Int) {
    holder.bindData(getItem(position))
  }
}

class BroadcastVH(
  private val view: ItemViewApplicationBroadcastBinding,
  private val onClick: (Int) -> Unit
) :
  RecyclerView.ViewHolder(view.root) {
  fun bindData(broadcastModel: BroadcastModel) {
    with(view) {
      broadcastModel.let {
        imgApplication.setImageDrawable(it.image)
        tvTitle.text = it.name
      }
      containerView.debounceClick {
        onClick.invoke(adapterPosition)
      }
    }
  }
}
