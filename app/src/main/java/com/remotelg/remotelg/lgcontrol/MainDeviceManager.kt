package com.remotelg.remotelg.lgcontrol

import com.connectsdk.device.ConnectableDevice
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.remotelg.remotelg.ProcessLifecycleObserver
import com.remotelg.remotelg.utils.unit
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(
  FlowPreview::class,
  ExperimentalCoroutinesApi::class,
  ExperimentalTime::class,
)
class MainDeviceManager(
  private val appCoroutinesScope: CoroutineScope,
  private val processLifecycleObserver: ProcessLifecycleObserver,
  private val tag: String,
) {
  private val deviceChannel = Channel<Pair<ConnectableDevice, Boolean>?>(Channel.UNLIMITED)
  private val broadcastEventMutableFlow =
    MutableSharedFlow<Pair<LgEvent, ConnectableDevice>>(extraBufferCapacity = 128)

  val controlStateFlow: StateFlow<Pair<LgState, LGControl>?> = deviceChannel
    .consumeAsFlow()
    .onEach { addLog("device" to it?.first?.toString(), "retry" to it?.second) }
    .flatMapLatest { pair ->
      if (pair == null) {
        flowOf(null)
      } else {
        val (device, retry) = pair

        if (retry) {
          delay(Duration.seconds(1))
        }

        callbackFlow {
          val control = LGControl(device, retry)

          // subscribe first to avoid lost events
          launch(start = CoroutineStart.UNDISPATCHED) {
            control.stateFlow
              .map { it to control }
              .collect { return@collect send(it) }
          }
          val disposable = control
            .broadcastEventObservable
            .subscribeBy { broadcastEventMutableFlow.tryEmit(it to device) }

          control.connect()

          awaitClose {
            // close control first to avoid lost events
            control.close()
            disposable.dispose()
          }
        }
      }
    }
    .stateIn(
      appCoroutinesScope,
      SharingStarted.Eagerly,
      null,
    )

  /**
   * Broadcast event flow
   */
  val broadcastEventFlow: Flow<Pair<LgEvent, ConnectableDevice>> =
    broadcastEventMutableFlow.asSharedFlow()

  /**
   * Single-listener event flow
   */
  val eventFlow: Flow<Pair<LgEvent, ConnectableDevice>>
    get() = controlStateFlow
      .flatMapLatest { pair ->
        val (_, lgControl) = pair ?: return@flatMapLatest emptyFlow()

        lgControl
          .eventFlow
          .map { it to lgControl.device }
      }

  init {
    logEvents()

    val reconnect = { connectableDevice: ConnectableDevice ->
      addLog("reconnect" to connectableDevice.toString())
      deviceChannel.trySend(connectableDevice to true)
    }
    val foregroundStateFlow = processLifecycleObserver.isForegroundStateFlow

    broadcastEventFlow
      .filter { (event) -> event === LgEvent.Disconnected }
      .onEach { (_, device) ->
        val isForeground = foregroundStateFlow.value

        addLog(
          "disconnect" to if (isForeground) {
            "foreground"
          } else {
            "background"
          }
        )
        if (isForeground) {
          reconnect(device)
        }
      }
      .launchIn(appCoroutinesScope)

    foregroundStateFlow
      .filter { it }
      .onEach {
        val state = controlStateFlow.value

        addLog("check" to state?.first?.description)
        if (state?.first === LgState.Disconnected) {
          reconnect(state.second.device)
        }
      }
      .launchIn(appCoroutinesScope)

    //        foreground           foreground
    //       |----------|         |-----------|
    // ---x------x-------x---x--------x---x-----
    // ------y---y--------------- y---y---y-----
    //
    // x: disconnect event
    // y: reconnect event
    //

    old1()
    old2()
  }

  fun didSelected(s: ConnectableDevice?) = deviceChannel.trySend(s?.to(false)).unit

  // /
  // /
  // /

  private fun addLog(vararg map: Pair<String, Any?>) {
    Timber.tag("@@@@@").d("%s - %s", tag, map.contentToString())

    Firebase.firestore
      .collection("${LG_CONTROL_COLLECTION}__$tag")
      .add(
        map.toMap() + mapOf(
          "local_time" to Date(),
          "server_time" to FieldValue.serverTimestamp(),
          "thread" to Thread.currentThread().toString(),
        )
      )
      .addOnFailureListener { }
  }

  private fun logEvents() {
    processLifecycleObserver.isForegroundStateFlow
      .onEach { addLog("on_start_stop" to if (it) "start" else "stop") }
      .launchIn(appCoroutinesScope)

    controlStateFlow
      .onEach { addLog("state" to it?.first?.description) }
      .launchIn(appCoroutinesScope)

    broadcastEventFlow
      .onEach { (event) -> addLog("event" to event.description) }
      .launchIn(appCoroutinesScope)
  }

  private fun old1() {
    //
    // val sharedEvents = disconnectedFlow.shareIn(
    //   appCoroutinesScope,
    //   SharingStarted.WhileSubscribed(),
    // )
    // val onForeground = processLifecycleObserver.isForegroundStateFlow
    //   .filter { it }
    //   .shareIn(
    //     appCoroutinesScope,
    //     SharingStarted.WhileSubscribed(),
    //   )
    // val onBackground = processLifecycleObserver.isForegroundStateFlow
    //   .filter { !it }
    //   .shareIn(
    //     appCoroutinesScope,
    //     SharingStarted.WhileSubscribed(),
    //   )
    //
    // fun <T, O> Flow<T>.windowToggle(
    //   openings: Flow<O>,
    //   closingSelector: (O) -> Flow<Any?>
    // ): Flow<Flow<T>> = TODO()
    //
    // fun <T, O> Flow<T>.bufferToggle(
    //   openings: Flow<O>,
    //   closingSelector: (O) -> Flow<Any?>
    // ): Flow<List<T>> = TODO()
    //
    // val retryEvent = merge(
    //   sharedEvents.windowToggle(onForeground) { onBackground }.flatMapMerge { it },
    //   sharedEvents.bufferToggle(onBackground) { onForeground }.mapNotNull { it.lastOrNull() }
    // )
  }

  private fun old2() {
    //    val pendingRetry = AtomicReference<ConnectableDevice>()
    //
    //    disconnectedFlow
    //      .withLatestFrom(processLifecycleObserver.isForegroundStateFlow)
    //      .onEach { (pair, isForeground) ->
    //        addLog("disconnect" to if (isForeground) "foreground" else "background")
    //
    //        val connectableDevice = pair.second
    //        if (isForeground) {
    //          reconnect(connectableDevice)
    //        } else {
    //          pendingRetry.set(connectableDevice)
    //        }
    //      }
    //      .launchIn(appCoroutinesScope)

    //    processLifecycleObserver
    //      .isForegroundStateFlow
    //      .filter { it }
    //      .onEach {
    //        pendingRetry.getAndSet(null)
    //          .also { addLog("pending" to it?.toString()) }
    //          ?.let(reconnect)
    //      }
    //      .launchIn(appCoroutinesScope)
  }
}
