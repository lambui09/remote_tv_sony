package com.remotelg.remotelg.ui.remote.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.MainVM
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.FragmentKeyboardBinding
import com.remotelg.remotelg.lgcontrol.RemoteControlKey
import com.remotelg.remotelg.utils.mapTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardFragment : Fragment(R.layout.fragment_keyboard) {
  private val binding by viewBinding<FragmentKeyboardBinding>()
  private val mainVM by viewModels<MainVM>(ownerProducer = { requireActivity() })

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.tag("<<<").d("$this::onCreate")
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Timber.tag("<<<").d("$this::onViewCreated")

    val numberControlBinding = binding.viewListNumberControl
    (
      arrayOf<Pair<View, RemoteControlKey>>(
        binding.ivBack to RemoteControlKey.back,
        binding.ivExit to RemoteControlKey.exit,
        binding.ivSource to RemoteControlKey.source,
        binding.ivGuide to RemoteControlKey.guide,
      ) + arrayOf(
        numberControlBinding.passCode0,
        numberControlBinding.passCode1,
        numberControlBinding.passCode2,
        numberControlBinding.passCode3,
        numberControlBinding.passCode4,
        numberControlBinding.passCode5,
        numberControlBinding.passCode6,
        numberControlBinding.passCode7,
        numberControlBinding.passCode8,
        numberControlBinding.passCode9,
      ).mapIndexed { index, passCodeView -> passCodeView to RemoteControlKey.number(index.toUByte()) } +
        arrayOf(
          numberControlBinding.ivPre to RemoteControlKey.numberDelete,
          numberControlBinding.ivPause to RemoteControlKey.numberNone,
        )

      ).map { (view, key) -> view.clicks().mapTo(key) }
      .merge()
      .onEach { mainVM.sendKey(it) }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    Timber.tag("<<<").d("$this::onDestroyView")
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.tag("<<<").d("$this::onDestroy")
  }
}
