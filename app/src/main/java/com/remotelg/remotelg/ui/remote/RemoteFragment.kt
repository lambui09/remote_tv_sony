package com.remotelg.remotelg.ui.remote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.MainVM
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.FragmentRemoteBinding
import com.remotelg.remotelg.databinding.ViewControlSettingBinding
import com.remotelg.remotelg.lgcontrol.RemoteControlKey
import com.remotelg.remotelg.ui.remote.fragments.ControlFragment
import com.remotelg.remotelg.ui.remote.fragments.KeyboardFragment
import com.remotelg.remotelg.ui.remote.fragments.TouchpadFragment
import com.remotelg.remotelg.ui.scan.ScanActivity
import com.remotelg.remotelg.ui.scan.ScanActivity.Companion.DeviceType.WEB_OS
import com.remotelg.remotelg.utils.mapTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFragment : Fragment(R.layout.fragment_remote) {
  private val binding by viewBinding<FragmentRemoteBinding>()
  private val bottomBinding by viewBinding<ViewControlSettingBinding>()
  private val mainVM by viewModels<MainVM>(ownerProducer = { requireActivity() })

  private var cachedView: View? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.tag("<<<").d("$this::onCreate")
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ) = cachedView ?: super.onCreateView(inflater, container, savedInstanceState)!!
    .also { cachedView = it }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    Timber.tag("<<<").d("$this::onViewCreated")
    setupViews()
    bindVM()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    Timber.tag("<<<").d("$this::onDestroyView")
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.tag("<<<").d("$this::onDestroy")
  }

  private fun bindVM() {
    arrayOf(
      binding.ivPower to RemoteControlKey.power,
      bottomBinding.btnVolDown to RemoteControlKey.volumeDown,
      bottomBinding.btnVolUp to RemoteControlKey.volumeUp,
      bottomBinding.btnMute to RemoteControlKey.mute,
      bottomBinding.btnHome to RemoteControlKey.home,
      bottomBinding.btnRecord to RemoteControlKey.pause,
      bottomBinding.btnPlay to RemoteControlKey.play,
      bottomBinding.btnRaise to RemoteControlKey.chUp,
      bottomBinding.btnDown to RemoteControlKey.chDown,
      bottomBinding.btnPrev to RemoteControlKey.previous,
      bottomBinding.btnNext to RemoteControlKey.next,
      bottomBinding.btnVoice to RemoteControlKey.voice,
    ).map { (view, key) -> view.clicks().mapTo(key) }
      .merge()
      .onEach { mainVM.sendKey(it) }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }

  private fun setupViews() {
    binding.run {
      viewPagerRemote.run {
        adapter = RemotePageAdapter(this@RemoteFragment)
        isUserInputEnabled = false
        isSaveEnabled = false
        isSaveFromParentEnabled = false
      }
      TabLayoutMediator(tabs, viewPagerRemote) { tab, position ->
        tab.text = when (position) {
          0 -> "Control"
          1 -> "Touchpad"
          2 -> "Keyboard"
          else -> error("TabLayoutMediator: invalid position $position")
        }
      }.attach()
    }

    binding.ivScan.setOnClickListener {
      startActivity(ScanActivity.makeIntent(it.context, WEB_OS))
    }
  }
}

class RemotePageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  override fun getItemCount(): Int = 3

  override fun createFragment(position: Int): Fragment = when (position) {
    0 -> ControlFragment()
    1 -> TouchpadFragment()
    2 -> KeyboardFragment()
    else -> error("createFragment: invalid position $position")
  }
}
