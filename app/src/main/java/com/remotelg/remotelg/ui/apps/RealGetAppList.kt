package com.remotelg.remotelg.ui.apps

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.connectsdk.core.AppInfo
import com.connectsdk.service.capability.Launcher
import com.connectsdk.service.command.ServiceCommandError
import com.google.common.base.Optional
import com.remotelg.remotelg.R
import com.remotelg.remotelg.common.model.AppModel
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.MainDeviceManager
import com.remotelg.remotelg.lgcontrol.WebOS
import com.remotelg.remotelg.utils.Lce
import com.remotelg.remotelg.utils.dpToPx
import com.remotelg.remotelg.utils.widget.ColorGenerator
import com.remotelg.remotelg.utils.widget.TextDrawable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun Launcher.getAppListAsync(): List<AppInfo> {
  return suspendCancellableCoroutine { cnt ->
    launcher.getAppList(object : Launcher.AppListListener {
      override fun onError(error: ServiceCommandError?) {
        cnt.resumeWithException(error ?: ServiceCommandError.getError(-1))
      }

      override fun onSuccess(res: List<AppInfo>) {
        Timber.d(">>> got $res")
        cnt.resume(res)
      }
    })
  }
}

private fun Context.getImage(appInfo: AppInfo, spaceInDp: Int, spanCount: Int): Drawable {
  return when (appInfo.id) {
    "netflix" -> R.drawable.img_netflix
    "com.webos.app.facebooklogin" -> R.drawable.ic_facebook
    "vieplay.vn" -> R.drawable.ic_logo_vion
    "com.webos.app.scheduler" -> R.drawable.ic_logo_schedule
    "com.webos.app.photovideo" -> R.drawable.ic_logo_photo
    "com.webos.app.music" -> R.drawable.ic_logo_mp3
    "com.webos.app.browser" -> R.drawable.ic_logo_web
    "com.fpt.fptplay" -> R.drawable.ic_logo_fpt_play
    "com.webos.app.livetv" -> R.drawable.ic_logo_emit
    "amazon" -> R.drawable.ic_logo_amazon_video
    "com.webos.app.channelsetting" -> R.drawable.ic_logo_search
    "com.webos.app.livedmost" -> R.drawable.ic_logo_live_add
    "com.palm.app.settings" -> R.drawable.ic_logo_setting
    "com.5403008.196062" -> R.drawable.ic_logo_ufc
    "youtube.leanback.v4" -> R.drawable.ic_logo_youtube
    "com.apple.appletv" -> R.drawable.ic_logo_apple
    "com.webos.app.btspeakerapp" -> R.drawable.ic_logo_sound_blutooth
    "com.webos.app.adapp" -> R.drawable.ic_logo_adv
    "com.palm.app.firstuse" -> R.drawable.ic_logo_first_time
    "com.iq.app.iqiyiapp" -> R.drawable.ic_logo_quiyi
    "com.webos.app.connectionwizard" -> R.drawable.ic_logo_connect
    "com.webos.app.customersupport" -> R.drawable.ic_logo_customer_support
    "com.webos.app.remotesetting" -> R.drawable.ic_logo_remote
    "com.webos.app.channeledit" -> R.drawable.ic_logo_channel_manager
    "com.webos.app.brandshop" -> R.drawable.ic_logo_branch_shop
    "com.webos.app.cheeringtv" -> R.drawable.ic_logo_cheer
    "com.webos.app.discovery" -> R.drawable.ic_logo_lg_content
    "com.webos.app.systemmusic" -> R.drawable.ic_logo_tmall_music
    else -> null
  }?.let { ContextCompat.getDrawable(this, it) } ?: run {
    val bgColor = ColorGenerator.MATERIAL.getColor(appInfo.name)
    val textColor =
      if (ColorUtils.calculateLuminance(bgColor) < 0.5) Color.WHITE else Color.parseColor("#FF4C4C4C")

    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val imageSize = (wm.getScreenWidth() - (spanCount + 1) * dpToPx(spaceInDp)) / spanCount

    TextDrawable.builder()
      .beginConfig()
      .width(imageSize)
      .height(imageSize)
      .fontSize(20)
      .bold()
      .textColor(textColor)
      .endConfig()
      .buildRoundRect(
        appInfo.name,
        bgColor,
        20,
      )
  }
}

private fun WindowManager.getScreenWidth(): Int {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    val windowMetrics = getCurrentWindowMetrics()
    val insets =
      windowMetrics.getWindowInsets()
        .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
    return windowMetrics.getBounds().width() - insets.left - insets.right
  } else {
    DisplayMetrics().also {
      @Suppress("DEPRECATION")
      defaultDisplay.getMetrics(it)
    }.widthPixels
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RealGetAppList @Inject constructor(
  @WebOS private val mainDeviceManager: MainDeviceManager,
  @ApplicationContext private val appContext: Context,
  @AppsSpaceInDp private val spaceInDp: Optional<Int>,
  @AppsSpanCount private val spanCount: Optional<Int>,
) : GetAppList {
  override fun invoke() = mainDeviceManager
    .controlStateFlow
    .flatMapLatest { pair ->
      Timber.d(">>> ${pair?.first} ${pair?.second}")

      if (pair?.first == LgState.Connected) {
        val launcher =
          pair.second.launcher
            ?: throw IllegalStateException("LgControl.launcher is null")

        flow { emit(launcher.getAppListAsync()) }
          .map { list ->
            Lce.content(
              list.filter {
                it.id in ids
              }.map {
                AppModel(
                  id = it.id,
                  name = it.name ?: "",
                  image = appContext.getImage(
                    appInfo = it,
                    spaceInDp = spaceInDp.get(),
                    spanCount = spanCount.get(),
                  ),
                  hasFavorite = false,
                )
              }
            )
          }
          .onStart { emit(Lce.Loading) }
          .catch { emit(Lce.error(it)) }
      } else {
        flowOf(Lce.content(emptyList()))
      }
    }
    .distinctUntilChanged()

  companion object {
    val ids = setOf(
      "amazon",
      "com.fpt.fptplay",
      "com.apple.appletv",
      "com.webos.app.btspeakerapp",
      "com.webos.app.brandshop",
      "com.webos.app.cheeringtv",
      "com.iq.app.iqiyiapp",
      "com.mytvb2c.app",
      "youtube.leanback.v4",
      "com.palm.app.settings",
      "com.webos.app.acrcard",
      "com.palm.app.firstuse",
      "com.webos.app.actionhandler",
      "com.webos.app.alibabafull",
      "com.webos.app.browser",
      "com.webos.app.care365",
      "com.webos.app.channeledit",
      "com.webos.app.channelsetting",
      "com.webos.app.connectionwizard",
      "com.webos.app.customersupport",
      "com.webos.app.discovery",
      "com.webos.app.externalinput.av1",
      "com.webos.app.googleassistant",
      "com.webos.app.hdmi1",
      "com.webos.app.home",
      "com.webos.app.homeconnect",
      "com.webos.app.iot-thirdparty-login",
      "com.webos.app.livecudb",
      "com.webos.app.livehbbtv",
      "com.webos.app.livetv",
      "com.webos.app.magicnum",
      "com.webos.app.membership",
      "com.webos.app.miracast",
      "com.webos.app.music",
      "com.webos.app.onetouchsoundtuning",
      "com.webos.app.photovideo",
      "com.webos.app.remoteservice",
      "com.webos.app.remotesetting",
      "com.webos.app.scheduler",
      "om.webos.app.self-diagnosis",
      "com.webos.app.softwareupdate",
      "com.webos.app.systemmusic",
      "com.webos.app.tips",
      "com.webos.app.tvhotkey",
      "com.webos.app.tvuserguide",
      "com.webos.app.voice",
      "com.webos.app.webapphost",
      "tv.twitch.tv.starshot.lg",
      "vieplay.vn",
      "vn.fimplus.movies",
      "netflix"
    )
  }
}
