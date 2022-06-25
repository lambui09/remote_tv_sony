package com.remotelg.remotelg.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectsdk.service.capability.Launcher
import com.connectsdk.service.command.ServiceCommandError
import com.connectsdk.service.sessions.LaunchSession
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.remotelg.remotelg.common.model.AppModel
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.MainDeviceManager
import com.remotelg.remotelg.lgcontrol.WebOS
import com.remotelg.remotelg.utils.Lce
import com.remotelg.remotelg.utils.isEmulator
import com.remotelg.remotelg.utils.unit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface AppsSingleEvent {
  data class Error(val throwable: Throwable) : AppsSingleEvent

  object NotConnected : AppsSingleEvent

  data class LaunchAppSuccess(val appModel: AppModel) : AppsSingleEvent
}

typealias LceAppModels = Lce<List<AppModel>>

interface GetAppList {
  operator fun invoke(): Flow<LceAppModels>
}

interface FavoriteAppsStore {
  fun favoriteIds(): Flow<Set<String>>

  suspend fun toggle(id: String)
}

//
//
//

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AppsVM @Inject constructor(
  @WebOS private val mainDeviceManager: MainDeviceManager,
  private val getAppList: GetAppList,
  private val favoriteAppsStore: FavoriteAppsStore,
) : ViewModel() {
  private val termChannel = Channel<String>(Channel.CONFLATED)
  private val singleEvents = Channel<AppsSingleEvent>(Channel.UNLIMITED)

  val singleEventFlow get() = singleEvents.receiveAsFlow()

  val appsListStateFlow: StateFlow<LceAppModels> = combine(
    flow = getAppList(),
    flow2 = termChannel
      .consumeAsFlow()
      .onStart { emit("") }
      .distinctUntilChanged(),
    flow3 = favoriteAppsStore.favoriteIds(),
    transform = ::sort,
  )
    .onEach {
      Timber.d(">>> lce $it")
      when (it) {
        is Lce.Error -> singleEvents.trySend(AppsSingleEvent.Error(it.exception))
        is Lce.Content -> submitFirestore(it.content)
        Lce.Loading -> Unit
      }
    }
    .catch { Timber.e(it, ">>> $it") }
    .stateIn(
      viewModelScope,
      SharingStarted.Lazily,
      Lce.Loading,
    )

  fun launch(app: AppModel) {
    val (lgState, lgControl) = mainDeviceManager.controlStateFlow.value
      ?: return singleEvents.trySend(AppsSingleEvent.NotConnected).unit

    when (lgState) {
      LgState.Connected -> lgControl.launcher?.launchApp(
        app.id,
        object : Launcher.AppLaunchListener {
          override fun onError(error: ServiceCommandError?) {
            singleEvents.trySend(AppsSingleEvent.Error(error ?: return))
          }

          override fun onSuccess(res: LaunchSession) =
            singleEvents.trySend(AppsSingleEvent.LaunchAppSuccess(app)).unit
        }
      )
      else -> singleEvents.trySend(AppsSingleEvent.NotConnected)
    }
  }

  override fun onCleared() {
    super.onCleared()
    singleEvents.close()
    termChannel.close()
  }

  fun search(term: String) = termChannel.trySend(term).unit

  fun toggle(appModel: AppModel) {
    viewModelScope.launch {
      try {
        favoriteAppsStore.toggle(appModel.id)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "Toggle $appModel failed")
      }
    }
  }

  private fun sort(lce: LceAppModels, term: String, ids: Set<String>): LceAppModels =
    lce.map { apps ->
      val appsWithFav = apps.map { it.copy(hasFavorite = it.id in ids) }
      val (f, s) = if (term.isBlank()) {
        appsWithFav.partition { it.hasFavorite }
      } else {
        val termLowercase = term.lowercase()
        appsWithFav.partition { termLowercase in it.name.lowercase() }
      }
      f.sortedBy { it.name } + s.sortedBy { it.name }
    }

  private fun submitFirestore(data: List<AppModel>) {
    if (data.isEmpty() || isEmulator()) {
      return
    }

    Firebase
      .firestore
      .collection("list_app")
      .add(mapOf("apps" to data.map { it.toJson() }))
      .addOnSuccessListener {}
      .addOnFailureListener {
        Firebase.crashlytics.run {
          log("submitFirestore failed")
          recordException(it)
        }
      }
  }
}

private fun AppModel.toJson(): Map<String, Any?> = hashMapOf(
  "id" to id,
  "name" to name,
)
