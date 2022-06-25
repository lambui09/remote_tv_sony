package com.remotelg.remotelg.ui.broadcast

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.database.getStringOrNull
import androidx.lifecycle.ViewModel
import com.connectsdk.core.MediaInfo
import com.connectsdk.service.capability.MediaPlayer
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommandError
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.remotelg.remotelg.R
import com.remotelg.remotelg.ipAddress
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.MainDeviceManager
import com.remotelg.remotelg.lgcontrol.WebOS
import com.remotelg.remotelg.lgcontrol.WebServerManager
import com.remotelg.remotelg.lgcontrol.WebServerManager.Companion.PORT
import com.remotelg.remotelg.utils.unit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed interface BroadcastAction {
  val uri: Uri

  data class Image(override val uri: Uri) : BroadcastAction
  data class Video(override val uri: Uri) : BroadcastAction
  data class Audio(override val uri: Uri) : BroadcastAction
}

sealed interface BroadcastSingleEvent {
  object NotConnected : BroadcastSingleEvent
  data class SendKeyFailure(
    val action: BroadcastAction,
    val commandError: ServiceCommandError
  ) : BroadcastSingleEvent

  data class SendKeySuccess(val action: BroadcastAction) : BroadcastSingleEvent
}

@HiltViewModel
class BroadcastVM @Inject constructor(
  @WebOS private val mainDeviceManager: MainDeviceManager,
  @ApplicationContext private val appContext: Context,
  private val webServerManager: WebServerManager,
) : ViewModel() {
  private var mediaLaunchObject: MediaPlayer.MediaLaunchObject? = null
  private val singleEvent = Channel<BroadcastSingleEvent>(Channel.UNLIMITED)

  val singleEventFlow: Flow<BroadcastSingleEvent> get() = singleEvent.receiveAsFlow()

  val controlStateFlow get() = mainDeviceManager.controlStateFlow

  init {
    Timber.d("$this::init")
    webServerManager.startWebServerIfNeeded()
  }

  fun sendKey(action: BroadcastAction) {
    val (state, control) = mainDeviceManager.controlStateFlow.value
      ?: return singleEvent.trySend(BroadcastSingleEvent.NotConnected).unit
    if (state != LgState.Connected) {
      return singleEvent.trySend(BroadcastSingleEvent.NotConnected).unit
    }

    webServerManager.startWebServerIfNeeded()
    val myMediaSendListener = MyMediaSendListener(
      action,
      onErrorCb = {
        singleEvent.trySend(BroadcastSingleEvent.SendKeyFailure(action, it))
      },
      onSuccessCb = {
        singleEvent.trySend(BroadcastSingleEvent.SendKeySuccess(action))
        it.mediaControl.play(MyResponseListener)
        mediaLaunchObject = it
      },
    )

    mediaLaunchObject?.launchSession?.close(MyResponseListener)
    val mediaPlayer = control.mediaPlayer!!
    when (action) {
      is BroadcastAction.Image -> {
        mediaPlayer.playMedia(
          action.uri.createMediaImageInfo(appContext, PORT),
          false,
          myMediaSendListener,
        )
      }
      is BroadcastAction.Video -> {
        mediaPlayer.playMedia(
          action.uri.createMediaVideoInfo(appContext, PORT),
          false,
          myMediaSendListener,
        )
      }
      is BroadcastAction.Audio -> {
        mediaPlayer.playMedia(
          action.uri.createAudioInfo(appContext, PORT),
          false,
          myMediaSendListener,
        )
      }
    }
  }
}

private object MyResponseListener : ResponseListener<Any?> {
  override fun onError(error: ServiceCommandError?) {}
  override fun onSuccess(`object`: Any?) {}
}

private data class MyMediaSendListener(
  val action: BroadcastAction,
  val onErrorCb: (ServiceCommandError) -> Unit,
  val onSuccessCb: (MediaPlayer.MediaLaunchObject) -> Unit,
) : MediaPlayer.LaunchListener {

  override fun onError(error: ServiceCommandError?) {
    Timber.d("SendDataError $action $error")

    error ?: return
    Firebase.crashlytics.run {
      log("SendDataError $action $error")
      recordException(error)
    }
    onErrorCb(error)
  }

  override fun onSuccess(mediaInfo: MediaPlayer.MediaLaunchObject?) {
    Timber.d("SendDataSuccess $action ${mediaInfo?.launchSession}")

    onSuccessCb(mediaInfo ?: return)
  }
}

internal fun Uri.mimeType(context: Context): String? {
  return kotlin.runCatching {
    when (scheme) {
      ContentResolver.SCHEME_CONTENT -> {
        // get (image/jpeg, video/mp4) from ContentResolver if uri scheme is "content://"
        context.contentResolver.getType(this)
      }
      ContentResolver.SCHEME_FILE -> {
        // get (.jpeg, .mp4) from uri "file://example/example.mp4"
        MimeTypeMap.getFileExtensionFromUrl(toString())
          .lowercase()
          .let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
      }
      else -> null
    }
  }.getOrNull()
    ?.let {
      if (it == "audio/x-wav") "audio/wav"
      else it
    }
}

internal fun Uri.displayName(context: Context): String? {
  return kotlin.runCatching {
    when (scheme) {
      ContentResolver.SCHEME_CONTENT -> context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        /*selection*/ null,
        /*selectArgs*/ null,
        /*sortOrder*/ null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          cursor
            .getColumnIndex(OpenableColumns.DISPLAY_NAME)
            .let { cursor.getStringOrNull(it) }
        } else {
          null
        }
      }
      ContentResolver.SCHEME_FILE -> path?.let(::File)?.name
      else -> null
    }
  }.getOrNull()
}

fun Uri.mediaUrl(port: Int, mimeType: String, context: Context): String {
  return Uri.Builder()
    .scheme("http")
    .encodedAuthority("${context.ipAddress()}:$port")
    .appendQueryParameter("uri", toString())
    .appendQueryParameter("mimeType", mimeType)
    .build()
    .toString()
}

internal fun Uri.createMediaImageInfo(context: Context, port: Int): MediaInfo {
  val mimeType = mimeType(context) ?: "image/jpeg"
  return MediaInfo
    .Builder(mediaUrl(port, mimeType, context), mimeType)
    .setTitle(displayName(context) ?: "Image")
    .setDescription(context.getString(R.string.app_name))
    .setIcon("https://www.lg.com/lg5-common-gp/images/common/header/logo-b2c.jpg")
    .build()
}

internal fun Uri.createMediaVideoInfo(context: Context, port: Int): MediaInfo {
  val mimeType = "video/mp4"
  return MediaInfo
    .Builder(mediaUrl(port, mimeType, context), mimeType)
    .setTitle(displayName(context) ?: "Video")
    .setDescription(context.getString(R.string.app_name))
    .setIcon("https://www.lg.com/lg5-common-gp/images/common/header/logo-b2c.jpg")
    .build()
}

internal fun Uri.createAudioInfo(context: Context, port: Int): MediaInfo {
  val mimeType = mimeType(context) ?: "audio/mp3"
  return MediaInfo
    .Builder(mediaUrl(port, mimeType, context), mimeType)
    .setTitle(displayName(context) ?: "Audio")
    .setDescription(context.getString(R.string.app_name))
    .setIcon("https://www.lg.com/lg5-common-gp/images/common/header/logo-b2c.jpg")
    .build()
}
