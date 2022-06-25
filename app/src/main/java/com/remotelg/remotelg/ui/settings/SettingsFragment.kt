package com.remotelg.remotelg.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.MainVM
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.FragmentSettingsBinding
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.lgName
import com.remotelg.remotelg.utils.collectIn
import com.remotelg.remotelg.utils.flatMapFirst
import com.remotelg.remotelg.utils.showAlertDialogSuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

@ExperimentalCoroutinesApi
class SettingsFragment : Fragment(R.layout.fragment_settings) {
  private val binding by viewBinding<FragmentSettingsBinding>()
  private val mainVM by viewModels<MainVM>(ownerProducer = { requireActivity() })

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    mainVM.controlStateFlow.collectIn(this) {
      binding.tvNameTV.text = if (it?.first == LgState.Connected) {
        it.second.device.lgName
      } else {
        getString(R.string.text_connect_your_tv)
      }
    }
    binding
      .btnReconnect
      .clicks()
      .flatMapFirst {
        flow {
          requireActivity().showAlertDialogSuspend(
            tag = "disconnect",
            negativeText = null,
          ) {
            title("Disconnect")
            message("Do you want to disconnect from the device")
            cancelable(true)
          }.let { emit(it) }
        }
      }
      .filterNotNull()
      .onEach { mainVM.disconnect() }
      .launchIn(viewLifecycleOwner.lifecycleScope)
  }
}
