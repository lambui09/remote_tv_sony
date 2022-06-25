package com.remotelg.remotelg

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectsdk.service.capability.KeyControl
import com.connectsdk.service.capability.Launcher.AppLaunchListener
import com.connectsdk.service.capability.VolumeControl
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommandError
import com.connectsdk.service.sessions.LaunchSession
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.remotelg.remotelg.lgcontrol.*
import com.remotelg.remotelg.utils.unit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

sealed interface MainSingleEvent {
  object NotConnected : MainSingleEvent
  data class SendKeyFailure(
    val key: RemoteControlKey,
    val commandError: ServiceCommandError
  ) : MainSingleEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainVM @Inject constructor(
  @WebOS private val mainDeviceManager: MainDeviceManager,
  @SuppressLint("StaticFieldLeak")
  @ApplicationContext private val appContext: Context,
  private val webServerManager: WebServerManager,
) : ViewModel() {
  //
  // Channels
  //
  private val singleEvent = Channel<MainSingleEvent>(Channel.UNLIMITED)
  private val keyChannel = Channel<RemoteControlKey>(Channel.UNLIMITED)

  //
  // Listeners cache
  //
  private val responseListeners = HashMap<RemoteControlKey, MyResponseListener>()
  private val appLaunchListeners = HashMap<RemoteControlKey, MyAppLaunchListener>()

  //
  // Event flows
  //
  val controlStateFlow get() = mainDeviceManager.controlStateFlow
  val broadcastEventFlow get() = mainDeviceManager.broadcastEventFlow
  val singleEventFlow get() = singleEvent.receiveAsFlow()

  //
  // Internal state flows
  //
  private val muteStateFlow: StateFlow<Boolean?> = run {
    val initialMute = flowOf(null)

    controlStateFlow
      .flatMapLatest { v ->
        when (v?.first) {
          LgState.Connected -> v.second.volumeControl?.let { vc ->
            callbackFlow<Boolean?> {
              val subscription = vc.subscribeMute(object : VolumeControl.MuteListener {
                override fun onError(error: ServiceCommandError?) = error?.let { close(it) }.unit
                override fun onSuccess(v: Boolean?) = v?.let { trySend(it) }.unit
              })

              awaitClose { subscription.unsubscribe() }
            }
              .onStart { emit(null) }
              .catch { }
          } ?: initialMute
          LgState.Disconnected -> initialMute
          is LgState.Failed -> initialMute
          LgState.PairingRequired -> initialMute
          LgState.Unknown -> initialMute
          null -> initialMute
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
      )
  }

  init {
    appContext.ipAddress()
    mainDeviceManager.didSelected(null) // reset

    keyChannel
      .receiveAsFlow()
      .onEach { key ->
        Timber.d("Send $key")

        val (state, control) = controlStateFlow.value ?: return@onEach singleEvent.trySend(
          MainSingleEvent.NotConnected
        ).unit

        when (state) {
          LgState.Connected -> {
            fun responseListener() = responseListeners.getOrPut(key) {
              MyResponseListener(key) {
                singleEvent.trySend(MainSingleEvent.SendKeyFailure(key, it))
              }
            }

            fun appLaunchListener() = appLaunchListeners.getOrPut(key) {
              MyAppLaunchListener(key) {
                singleEvent.trySend(MainSingleEvent.SendKeyFailure(key, it))
              }
            }

            when (key) {
              RemoteControlKey.power -> control.powerControl?.powerOff(responseListener())
              RemoteControlKey.back -> control.keyControl?.back(responseListener())
              RemoteControlKey.exit -> control.keyControl?.home(responseListener())
              RemoteControlKey.source -> control.externalInputControl?.launchInputPicker(
                appLaunchListener()
              )
              RemoteControlKey.guide -> control.launcher?.launchApp(
                RemoteControlKey.GUIDE_ID,
                appLaunchListener(),
              )
              RemoteControlKey.mute -> {
                val isMute = !(muteStateFlow.value ?: false)
                control.volumeControl?.setMute(
                  isMute,
                  responseListener(),
                )
              }
              RemoteControlKey.voice -> {
                // TODO: voice
                appLaunchListener().onError(ServiceCommandError.notSupported())
              }
              RemoteControlKey.previous -> control.mediaControl?.rewind(responseListener())
              RemoteControlKey.next -> control.mediaControl?.fastForward(responseListener())
              RemoteControlKey.volumeUp -> control.volumeControl?.volumeUp(responseListener())
              RemoteControlKey.volumeDown -> control.volumeControl?.volumeDown(responseListener())
              RemoteControlKey.chUp -> control.tvControl?.channelUp(responseListener())
              RemoteControlKey.chDown -> control.tvControl?.channelDown(responseListener())
              RemoteControlKey.home -> control.keyControl?.home(responseListener())
              RemoteControlKey.pause -> control.mediaControl?.pause(responseListener())
              RemoteControlKey.play -> control.mediaControl?.play(responseListener())
              is RemoteControlKey.number -> control.keyControl?.sendKeyCode(
                KeyControl.KeyCode.createFromInteger(
                  key.number.toInt()
                )!!,
                responseListener(),
              )
              RemoteControlKey.numberDelete -> control.textInputControl?.sendDelete()
              RemoteControlKey.numberNone -> control.keyControl?.sendKeyCode(
                KeyControl.KeyCode.DASH,
                responseListener(),
              )
              RemoteControlKey.ok -> control.keyControl?.sendKeyCode(
                KeyControl.KeyCode.ENTER,
                responseListener(),
              )
              RemoteControlKey.left -> control.keyControl?.left(responseListener())
              RemoteControlKey.right -> control.keyControl?.right(responseListener())
              RemoteControlKey.up -> control.keyControl?.up(responseListener())
              RemoteControlKey.down -> control.keyControl?.down(responseListener())
              RemoteControlKey.clickTrackPad -> control.mouseControl?.click()
              is RemoteControlKey.move -> control.mouseControl?.move(key.dx, key.dy)
              is RemoteControlKey.scroll -> control.mouseControl?.scroll(key.dx, key.dy)
              is RemoteControlKey.launchBrowser -> control.launcher?.launchBrowser(
                key.url,
                appLaunchListener(),
              )
            }.unit
          }
          LgState.Disconnected -> singleEvent.trySend(MainSingleEvent.NotConnected).unit
          is LgState.Failed -> singleEvent.trySend(MainSingleEvent.NotConnected).unit
          LgState.PairingRequired -> singleEvent.trySend(MainSingleEvent.NotConnected).unit
          LgState.Unknown -> singleEvent.trySend(MainSingleEvent.NotConnected).unit
        }
      }
      .launchIn(viewModelScope)
  }

  fun sendKey(key: RemoteControlKey) = keyChannel.trySend(key).unit

  fun disconnect() = mainDeviceManager.didSelected(null)

  override fun onCleared() {
    super.onCleared()
    singleEvent.close()
    keyChannel.close()

    responseListeners.clear()
    appLaunchListeners.clear()

    webServerManager.stopWebServer()
  }
}

private data class MyResponseListener(
  val key: RemoteControlKey,
  val onErrorCb: (ServiceCommandError) -> Unit,
) : ResponseListener<Any> {
  override fun onError(error: ServiceCommandError?) {
    Timber.d("sendKeyError $key $error")

    error ?: return
    Firebase.crashlytics.run {
      log("sendKeyError $key $error")
      recordException(error)
    }
    onErrorCb(error)
  }

  override fun onSuccess(res: Any?) = Timber.d("sendKeySuccess $key $res")
}

private data class MyAppLaunchListener(
  val key: RemoteControlKey,
  val onErrorCb: (ServiceCommandError) -> Unit,
) : AppLaunchListener {
  override fun onError(error: ServiceCommandError?) {
    Timber.d("launchAppError $key $error")

    error ?: return
    Firebase.crashlytics.run {
      log("launchAppError $key $error")
      recordException(error)
    }
    onErrorCb(error)
  }

  override fun onSuccess(res: LaunchSession) = Timber.d("launchAppSuccess $key $res")
}

internal fun Context.ipAddress(): String? = NetworkInterface.getNetworkInterfaces()
  .toList()
  .flatMap { it.inetAddresses.toList() }
  .firstNotNullOfOrNull {
    if (!it.isLoopbackAddress && it is Inet4Address) it.hostAddress
    else null
  }
  ?: (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
    .run { connectionInfo.ipAddress }
    .let {
      @Suppress("DEPRECATION")
      Formatter.formatIpAddress(it)
    }
