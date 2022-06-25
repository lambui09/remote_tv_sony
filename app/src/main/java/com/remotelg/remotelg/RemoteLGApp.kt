package com.remotelg.remotelg

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.connectsdk.discovery.DiscoveryManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class RemoteLGApp : Application() {
  @Inject
  lateinit var processLifecycleObserver: ProcessLifecycleObserver

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    DiscoveryManager.init(this)
    ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
  }
}

@Singleton
class ProcessLifecycleObserver @Inject constructor() : DefaultLifecycleObserver {
  private val _state = MutableStateFlow(true)

  val isForegroundStateFlow get() = _state.asStateFlow()

  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)
    _state.value = true
  }

  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)
    _state.value = false
  }
}
