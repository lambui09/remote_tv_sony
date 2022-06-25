package com.remotelg.remotelg.ui.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.connectsdk.device.ConnectableDevice
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.R
import com.remotelg.remotelg.databinding.ActivityScanBinding
import com.remotelg.remotelg.databinding.PairingEditTextBinding
import com.remotelg.remotelg.lgcontrol.LgEvent
import com.remotelg.remotelg.lgcontrol.lgName
import com.remotelg.remotelg.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class ScanActivity : AppCompatActivity(R.layout.activity_scan) {
  private val vm by viewModels<ScanVM>()
  private val viewBinding by viewBinding<ActivityScanBinding>()

  private val requirePairingChannel = Channel<ConnectableDevice>()
  private val connectedChannel = Channel<ConnectableDevice>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    viewBinding.closeButton.setOnClickListener {
      finish()
    }
    viewBinding.buttonConnect.setOnClickListener {
      vm.connect()
    }

    val deviceAdapter = DeviceAdapter {
      vm.changeSelectedId(it.device)
    }
    viewBinding.recyclerView.run {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      adapter = deviceAdapter
    }

    vm.servicesStateFlow
      .onEach { result ->
        viewBinding.run {
          when (result) {
            is Lce.Error -> {
              Timber.d("Error ${result.exception}")
              loadingGroup.isVisible = true
              progressBar.isGone = true
              textScanning.text = "Error occurred"
            }
            Lce.Loading -> {
              loadingGroup.isVisible = true
              textScanning.text = getString(R.string.scanning)
            }
            is Lce.Content -> {
              loadingGroup.isGone = true
              deviceAdapter.submitList(result.content)
            }
          }
        }
      }
      .launchIn(lifecycleScope)

    vm.singleEventFlow.collectIn(this) {
      Timber.tag("scan").d("Event $it")

      when (it) {
        ScanSingleEvent.MustSelectTV -> toast("Must select a TV!")
        is ScanSingleEvent.LgEvent -> {
          val name = it.device.lgName

          when (it.event) {
            LgEvent.Connecting -> Unit
            LgEvent.PairingRequired -> {
              requirePairingChannel.trySend(it.device)
            }
            is LgEvent.ConnectFailed -> {
              toast("Failed to connect to '$name'. Try connect again!")
            }
            LgEvent.Connected -> {
              connectedChannel.trySend(it.device)
            }
            LgEvent.DisconnectedManual -> Unit
            LgEvent.Disconnected -> {
              toast("Disconnected from '$name'. Try connect again!")
            }
          }
        }
      }
    }

    requirePairingChannel
      .consumeAsFlow()
      .flatMapFirst { device ->
        flow {
          val editBinding = PairingEditTextBinding.inflate(layoutInflater)
          editBinding.edit.editText!!.inputType = InputType.TYPE_CLASS_NUMBER

          emit(
            device to showAlertDialogSuspend("pairing") {
              title("Pairing with TV")
              view(editBinding.root)
              cancelable(false)
            }?.let { editBinding.edit.editText!!.text.trim() }
          )
        }
      }
      .onEach { (device, code) ->
        Timber.d("$device $code")

        if (code.isNullOrEmpty()) {
          toast("Empty key")
        } else {
          vm.pair(device, code.toString())
        }
      }
      .launchIn(lifecycleScope)

    connectedChannel
      .consumeAsFlow()
      .flatMapFirst { device ->
        flow {
          showAlertDialogSuspend("connected") {
            title("Connected successfully")
            message(device.lgName)
            cancelable(false)
          }.let { emit(it) }
        }
      }
      .onEach {
        if (it != null) {
          finish()
        }
      }
      .launchIn(lifecycleScope)
  }

  override fun onDestroy() {
    super.onDestroy()
    requirePairingChannel.close()
    connectedChannel.close()
  }

  companion object {
    fun makeIntent(context: Context, type: DeviceType): Intent =
      Intent(context, ScanActivity::class.java).apply {
        putExtra(DEVICE_TYPE_KEY, type)
      }

    internal val DEVICE_TYPE_KEY = ScanActivity::class.java.simpleName + "." + "device_type"

    enum class DeviceType {
      WEB_OS,
      DLNA,
    }
  }
}
