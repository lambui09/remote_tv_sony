package com.remotelg.remotelg.ui.apps

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.base.Optional
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.FragmentAppsBinding
import com.remotelg.remotelg.ui.apps.adapter.AppsAdapter
import com.remotelg.remotelg.utils.Lce
import com.remotelg.remotelg.utils.collectIn
import com.remotelg.remotelg.utils.dpToPx
import com.remotelg.remotelg.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.widget.textChanges
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AppsFragment : Fragment(R.layout.fragment_apps) {
  private val binding by viewBinding<FragmentAppsBinding> {
    recyclerView.adapter = null
  }
  private val vm by viewModels<AppsVM>()
  private val appAdapter = AppsAdapter(
    { vm.launch(it) },
    { vm.toggle(it) }
  )

  @[Inject AppsSpaceInDp]
  lateinit var spaceInDp: Optional<Int>

  @[Inject AppsSpanCount]
  lateinit var spanCount: Optional<Int>

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpViews()
    bindVM()
  }

  private fun bindVM() {
    vm.appsListStateFlow.collectIn(this) { lce ->
      Timber.d(lce.toString())
      when (lce) {
        is Lce.Content -> {
          binding.progressBar.isGone = true
          appAdapter.submitList(lce.content)
          lce.content.forEach { Timber.d(">>>> $it") }
        }
        is Lce.Error -> {
          binding.progressBar.isGone = true
          appAdapter.submitList(emptyList())
        }
        Lce.Loading -> {
          binding.progressBar.isVisible = true
          appAdapter.submitList(emptyList())
        }
      }
    }
    vm.singleEventFlow.collectIn(this) {
      when (it) {
        is AppsSingleEvent.Error -> {
          requireContext().toast("Error: ${it.throwable.message}")
        }
        is AppsSingleEvent.LaunchAppSuccess -> {
          requireContext().toast("Launch '${it.appModel.name}' successfully")
        }
        AppsSingleEvent.NotConnected -> {
          requireContext().toast("Not connected!")
        }
      }
    }

    binding.edtSearch
      .textChanges()
      .map { it.toString().trim() }
      .onEach { vm.search(it) }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }

  private fun setUpViews() {
    val spanCount = spanCount.get()

    binding.run {
      recyclerView.run {
        setHasFixedSize(true)
        layoutManager = GridLayoutManager(context, spanCount)
        adapter = appAdapter
      }

      val space = requireContext().dpToPx(spaceInDp.get())
      recyclerView.addItemDecoration(
        object : RecyclerView.ItemDecoration() {
          override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
          ) {
            val column = parent.getChildAdapterPosition(view) % spanCount
            outRect.right = space * (column + 1) / spanCount
            outRect.left = space - space * column / spanCount
            outRect.top = 0
            outRect.bottom = space
          }
        }
      )
    }
  }
}
