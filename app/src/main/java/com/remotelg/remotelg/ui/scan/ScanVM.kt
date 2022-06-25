package com.remotelg.remotelg.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectsdk.device.ConnectableDevice
import com.remotelg.remotelg.lgcontrol.DLNA
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.MainDeviceManager
import com.remotelg.remotelg.lgcontrol.WebOS
import com.remotelg.remotelg.ui.scan.ScanActivity.Companion.DeviceType
import com.remotelg.remotelg.utils.Lce
import com.remotelg.remotelg.utils.unit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Provider

data class DeviceItem(
  val device: ConnectableDevice,
  val selected: Boolean
) {
  fun isSameExceptSelected(other: DeviceItem): Boolean =
    device == other.device && selected != other.selected
}

typealias DeviceItemsDataResult = Lce<List<DeviceItem>>

interface SearchDevices {
  enum class Type {
    WEB_OS,
    DLNA,
  }

  operator fun invoke(type: Type): Flow<List<ConnectableDevice>>
}

sealed interface ScanSingleEvent {
  object MustSelectTV : ScanSingleEvent
  data class LgEvent(
    val event: com.remotelg.remotelg.lgcontrol.LgEvent,
    val device: ConnectableDevice
  ) : ScanSingleEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScanVM @Inject constructor(
  searchDevices: SearchDevices,
  @WebOS _webOS_MainDeviceManager: Provider<MainDeviceManager>,
  @DLNA _DLNA_MainDeviceManager: Provider<MainDeviceManager>,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val type = savedStateHandle.get<DeviceType>(ScanActivity.DEVICE_TYPE_KEY)!!
  private val mainDeviceManager: MainDeviceManager =
    when (type) {
      DeviceType.WEB_OS -> _webOS_MainDeviceManager.get()
      DeviceType.DLNA -> _DLNA_MainDeviceManager.get()
    }

  private val selectedService = MutableStateFlow(null as ConnectableDevice?)
  private val singleEvents = Channel<ScanSingleEvent>(Channel.UNLIMITED)

  init {
    mainDeviceManager
      .eventFlow
      .onEach { (event, device) -> singleEvents.trySend(ScanSingleEvent.LgEvent(event, device)) }
      .launchIn(viewModelScope)
  }

  val singleEventFlow get() = singleEvents.receiveAsFlow()

  val servicesStateFlow: StateFlow<DeviceItemsDataResult> = combine(
    searchDevices(
      when (type) {
        DeviceType.WEB_OS -> SearchDevices.Type.WEB_OS
        DeviceType.DLNA -> SearchDevices.Type.DLNA
      }
    ),
    selectedService
  ) { services, id ->
    Lce.content(
      services.map {
        DeviceItem(
          device = it,
          selected = it.id == id?.id,
        )
      }
    )
  }
    .onStart { emit(Lce.Loading) }
    .catch { emit(Lce.error(it)) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Lazily,
      initialValue = Lce.Loading
    )

  fun changeSelectedId(s: ConnectableDevice) {
    selectedService.value = s
  }

  fun connect() {
    (
      servicesStateFlow.value as? Lce.Content
        ?: return singleEvents.trySend(ScanSingleEvent.MustSelectTV).unit
      )
      .content
      .find { it.selected }
      ?.device
      ?.let(mainDeviceManager::didSelected)
      ?: singleEvents.trySend(ScanSingleEvent.MustSelectTV)
  }

  fun pair(device: ConnectableDevice, code: String) {
    val (state, control) = mainDeviceManager.controlStateFlow.value ?: return
    if (control.device.id != device.id) return

    when (state) {
      LgState.Connected -> unit
      LgState.Disconnected -> unit
      is LgState.Failed -> unit
      LgState.Unknown -> control.device.sendPairingKey(code)
      LgState.PairingRequired -> control.device.sendPairingKey(code)
    }
  }
}
