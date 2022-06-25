package com.remotelg.remotelg.ui.scan

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.ServiceItemLayoutBinding
import com.remotelg.remotelg.lgcontrol.lgName
import timber.log.Timber

class DeviceAdapter(
  private val onSelectServiceItem: (DeviceItem) -> Unit,
) :
  ListAdapter<DeviceItem, DeviceAdapter.VH>(object : DiffUtil.ItemCallback<DeviceItem>() {

    override fun areItemsTheSame(oldItem: DeviceItem, newItem: DeviceItem) =
      oldItem.device.id == newItem.device.id

    override fun areContentsTheSame(oldItem: DeviceItem, newItem: DeviceItem) =
      oldItem == newItem

    override fun getChangePayload(oldItem: DeviceItem, newItem: DeviceItem) =
      if (newItem.isSameExceptSelected(oldItem)) {
        newItem.selected
      } else {
        null
      }
  }) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
    ServiceItemLayoutBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
  )

  override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
    if (payloads.isEmpty()) {
      return onBindViewHolder(holder, position)
    }
    payloads.forEach {
      if (it is Boolean) {
        Timber.d("onBindViewHolder payload=$it")
        holder.updateSelected(it)
      }
    }
  }

  override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

  inner class VH(private val binding: ServiceItemLayoutBinding) :
    RecyclerView.ViewHolder(binding.root) {

    init {
      binding.serviceButton.run {
        setOnClickListener {
          adapterPosition
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let { getItem(it) }
            ?.let(onSelectServiceItem)
        }
        setTextColor(Color.WHITE)
      }
    }

    fun bind(item: DeviceItem) {
      binding.run {
        @SuppressLint("SetTextI18n")
        serviceButton.text = item.device.lgName
        updateSelected(item.selected)
      }
    }

    fun updateSelected(selected: Boolean) {
      binding.serviceButton.setBackgroundColor(
        if (selected) ContextCompat.getColor(itemView.context, R.color.colorPrimary)
        else ContextCompat.getColor(itemView.context, R.color.colorSecondary)
      )
    }
  }
}
