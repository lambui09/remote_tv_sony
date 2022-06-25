package com.remotelg.remotelg.ui.scan

import com.connectsdk.device.ConnectableDevice
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.service.command.ServiceCommandError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

class RealSearchDevices @Inject constructor() : SearchDevices {
  private sealed interface DiscoveryEvent {
    val device: ConnectableDevice

    data class Added(override val device: ConnectableDevice) : DiscoveryEvent
    data class Updated(override val device: ConnectableDevice) : DiscoveryEvent
    data class Removed(override val device: ConnectableDevice) : DiscoveryEvent
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun invoke(type: SearchDevices.Type): Flow<List<ConnectableDevice>> {
    return callbackFlow {
      val listener = object : DiscoveryManagerListener {
        override fun onDeviceAdded(manager: DiscoveryManager?, device: ConnectableDevice?) {
          trySend(DiscoveryEvent.Added(device ?: return))
        }

        override fun onDeviceUpdated(manager: DiscoveryManager?, device: ConnectableDevice?) {
          trySend(DiscoveryEvent.Updated(device ?: return))
        }

        override fun onDeviceRemoved(manager: DiscoveryManager?, device: ConnectableDevice?) {
          trySend(DiscoveryEvent.Removed(device ?: return))
        }

        override fun onDiscoveryFailed(manager: DiscoveryManager?, error: ServiceCommandError?) {
          close(error ?: return)
        }
      }

      val manager = DiscoveryManager.getInstance().apply {
        registerDefaultDeviceTypes()
        pairingLevel = DiscoveryManager.PairingLevel.ON
        addListener(listener)
        start()
      }

      awaitClose {
        manager.run {
          removeListener(listener)
          stop()
        }
      }
    }
      .onEach { Timber.d(">>>> $it") }
      .scan(emptyList<ConnectableDevice>()) { acc, event ->
        val device = event.device

        when (event) {
          is DiscoveryEvent.Added -> if (when (type) {
            SearchDevices.Type.WEB_OS -> "webos"
            SearchDevices.Type.DLNA -> "dlna"
          } in device.serviceId.lowercase()
          ) {
            acc + device
          } else {
            acc
          }
          is DiscoveryEvent.Removed -> acc.filter { it.id != device.id }
          is DiscoveryEvent.Updated -> acc.map { if (it.id == device.id) device else it }
        }
      }
      .drop(1)
  }
}
