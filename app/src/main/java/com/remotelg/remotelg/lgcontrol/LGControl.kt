@file:Suppress("ClassName")

package com.remotelg.remotelg.lgcontrol

import com.connectsdk.device.ConnectableDevice
import com.connectsdk.device.ConnectableDeviceListener
import com.connectsdk.service.DeviceService
import com.connectsdk.service.DeviceService.PairingType.*
import com.connectsdk.service.capability.*
import com.connectsdk.service.command.ServiceCommandError
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.io.Closeable
import javax.inject.Qualifier
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

sealed interface LgState {
  object Unknown : LgState
  object PairingRequired : LgState
  object Connected : LgState
  object Disconnected : LgState
  data class Failed(val error: ServiceCommandError) : LgState
}

val LgState.description: String
  get() = when (this) {
    LgState.Connected -> "LgState.Connected"
    LgState.Disconnected -> "LgState.Disconnected"
    is LgState.Failed -> "LgState.Failed($error)"
    LgState.PairingRequired -> "LgState.PairingRequired"
    LgState.Unknown -> "LgState.Unknown"
  }

sealed interface LgEvent {
  object PairingRequired : LgEvent
  object Connecting : LgEvent
  object Connected : LgEvent
  data class ConnectFailed(val error: ServiceCommandError) : LgEvent
  object Disconnected : LgEvent
  object DisconnectedManual : LgEvent
}

val LgEvent.description: String
  get() = when (this) {
    is LgEvent.ConnectFailed -> "LgEvent.ConnectFailed"
    LgEvent.Connected -> "LgEvent.Connected"
    LgEvent.Disconnected -> "LgEvent.Disconnected"
    LgEvent.DisconnectedManual -> "LgEvent.DisconnectedManual"
    LgEvent.PairingRequired -> "LgEvent.PairingRequired"
    LgEvent.Connecting -> "LgEvent.Connecting"
  }

@OptIn(ExperimentalTime::class)
data class LGControl(
  val device: ConnectableDevice,
  val retry: Boolean,
) : Closeable, ConnectableDeviceListener {
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  @Volatile
  private var closed = false
  private val stateMutableFlow = MutableStateFlow<LgState>(LgState.Unknown)
  private val eventS = PublishSubject.create<LgEvent>().toSerialized()
  private val eventChannel = Channel<LgEvent>(Channel.UNLIMITED)

  val stateFlow get() = stateMutableFlow.asStateFlow()

  val broadcastEventObservable: Observable<LgEvent> = eventS.hide()

  val eventFlow get() = eventChannel.receiveAsFlow()

  fun connect() {
    eventS.onNext(LgEvent.Connecting)

    device.run {
      addListener(this@LGControl)
      setPairingType(PIN_CODE)
      connect()
    }

    if (retry) {
      scope.launch {
        delay(Duration.seconds(10))

        if (stateFlow.value === LgState.Unknown) {
          ServiceCommandError(
            -1,
            "Timeout to retry! Go to scan and connect again!"
          ).let {
            closeInternal(
              LgState.Failed(it),
              LgEvent.ConnectFailed(it)
            )
          }
        }
      }
    }
  }

  val launcher: Launcher? by getCapability()

  val mediaPlayer: MediaPlayer? by getCapability()

  val mediaControl: MediaControl? by getCapability()

  val tvControl: TVControl? by getCapability()

  val volumeControl: VolumeControl? by getCapability()

  val toastControl: ToastControl? by getCapability()

  val textInputControl: TextInputControl? by getCapability()

  val mouseControl: MouseControl? by getCapability()

  val externalInputControl: ExternalInputControl? by getCapability()

  val powerControl: PowerControl? by getCapability()

  val keyControl: KeyControl? by getCapability()

  val webAppLauncher: KeyControl? by getCapability()

  override fun close() = closeInternal(
    LgState.Disconnected,
    LgEvent.DisconnectedManual,
  )

  private fun closeInternal(state: LgState, event: LgEvent) {
    Timber.tag("####").d("${System.identityHashCode(this)} closeInternal $state $event")

    if (closed) {
      return
    }
    closed = true

    scope.cancel()

    stateMutableFlow.value = state
    event.let {
      eventChannel.trySend(it)
      eventChannel.close()

      eventS.onNext(it)
      eventS.onComplete()
    }

    runCatching { device.removeListener(this) }
    runCatching { mouseControl?.disconnectMouse() }
    runCatching { device.disconnect() }
  }

  override fun onDeviceReady(device: ConnectableDevice?) {
    Timber.tag("####").d("${System.identityHashCode(this)} onDeviceReady $device")

    stateMutableFlow.value = LgState.Connected
    eventChannel.trySend(LgEvent.Connected)
    eventS.onNext(LgEvent.Connected)

    mouseControl?.connectMouse()
  }

  override fun onDeviceDisconnected(device: ConnectableDevice?) {
    Timber.tag("####").d("${System.identityHashCode(this)} onDeviceDisconnected $device")

    closeInternal(
      LgState.Disconnected,
      LgEvent.Disconnected,
    )
    Firebase.firestore
      .collection(LG_CONTROL_COLLECTION)
      .add(
        mapOf(
          "onDeviceDisconnected" to device?.toString(),
          "time" to FieldValue.serverTimestamp(),
        )
      )
      .addOnFailureListener { }
  }

  override fun onPairingRequired(
    device: ConnectableDevice?,
    service: DeviceService?,
    pairingType: DeviceService.PairingType?
  ) {
    Timber.tag("####")
      .d("${System.identityHashCode(this)} onPairingRequired $pairingType $device $service")

    return when (pairingType) {
      NONE -> Unit
      FIRST_SCREEN -> Unit
      PIN_CODE, MIXED -> {
        stateMutableFlow.value = LgState.PairingRequired
        eventChannel.trySend(LgEvent.PairingRequired)
        eventS.onNext(LgEvent.PairingRequired)
      }
      null -> Unit
    }
  }

  override fun onCapabilityUpdated(
    device: ConnectableDevice?,
    added: MutableList<String>?,
    removed: MutableList<String>?
  ) = Unit

  override fun onConnectionFailed(device: ConnectableDevice?, error: ServiceCommandError) {
    Timber.tag("####").d("${System.identityHashCode(this)} onConnectionFailed $device $error")

    closeInternal(
      LgState.Failed(error),
      LgEvent.ConnectFailed(error)
    )
  }

  private inline fun <reified T : CapabilityMethods> getCapability(): Lazy<T?> =
    lazy {
      check(stateFlow.value === LgState.Connected) { "Not connected yet!" }
      device.getCapability(T::class.java)
    }
}

sealed class RemoteControlKey {
  object power : RemoteControlKey()
  object left : RemoteControlKey()
  object right : RemoteControlKey()
  object down : RemoteControlKey()
  object up : RemoteControlKey()
  object ok : RemoteControlKey()
  object back : RemoteControlKey()
  object exit : RemoteControlKey()
  object source : RemoteControlKey()
  object guide : RemoteControlKey()
  object mute : RemoteControlKey()
  object voice : RemoteControlKey()
  object previous : RemoteControlKey()
  object next : RemoteControlKey()
  object volumeUp : RemoteControlKey()
  object volumeDown : RemoteControlKey()
  object chUp : RemoteControlKey()
  object chDown : RemoteControlKey()
  object home : RemoteControlKey()
  object pause : RemoteControlKey()
  object play : RemoteControlKey()
  data class number(val number: UByte) : RemoteControlKey()
  object numberDelete : RemoteControlKey()
  object numberNone : RemoteControlKey()

  object clickTrackPad : RemoteControlKey()
  data class move(val dx: Double, val dy: Double) : RemoteControlKey()
  data class scroll(val dx: Double, val dy: Double) : RemoteControlKey()

  data class launchBrowser(val url: String) : RemoteControlKey()

  val description: String by lazy {
    when (this) {
      back -> "Back"
      chDown -> "Channel down"
      chUp -> "Channel up"
      clickTrackPad -> "Click"
      down -> "Down"
      exit -> "Exit"
      guide -> "Guide"
      home -> "Home"
      left -> "Left"
      is move -> "Move"
      mute -> "Mute"
      next -> "Next"
      is number -> "Number $number"
      numberDelete -> "Number delete"
      numberNone -> "Number none"
      ok -> "OK"
      pause -> "Pause"
      play -> "Play"
      power -> "Power"
      previous -> "Previous"
      right -> "Right"
      is scroll -> "Scroll"
      source -> "Source"
      up -> "Up"
      voice -> "Voice"
      volumeDown -> "Volume down"
      volumeUp -> "Volume up"
      is launchBrowser -> "Launch browser"
    }
  }

  companion object {
    const val GUIDE_ID = "com.webos.app.tvuserguide"
  }
}

const val LG_CONTROL_COLLECTION = "lg_control_logs"

val ConnectableDevice.lgName: String get() = "[LG] webOS TV $modelNumber"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebOS

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DLNA
