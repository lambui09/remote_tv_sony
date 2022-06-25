package com.remotelg.remotelg

// import androidx.navigation.ui.setupActionBarWithNavController
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import com.connectsdk.service.command.NotSupportedServiceCommandError
import com.google.android.material.snackbar.Snackbar
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.databinding.ActivityMainBinding
import com.remotelg.remotelg.lgcontrol.LgEvent
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.RemoteControlKey
import com.remotelg.remotelg.lgcontrol.lgName
import com.remotelg.remotelg.ui.scan.ScanActivity
import com.remotelg.remotelg.ui.scan.ScanActivity.Companion.DeviceType.WEB_OS
import com.remotelg.remotelg.utils.*
import com.remotelg.remotelg.utils.dialog.ProgressDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
  private val binding by viewBinding<ActivityMainBinding>()
  private val vm by viewModels<MainVM>()

  private var snackbar: Snackbar? = null
  private var progressDialog: ProgressDialog? = null
  private var currentNavController: LiveData<NavController>? = null
  private var observeNavControllerJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState === null) {
      setupBottomNavBar()
    }

    vm.controlStateFlow.collectIn(this) {}

    vm.broadcastEventFlow.collectIn(this) { (event, device) ->
      val name = device.lgName
      when (event) {
        LgEvent.Connecting -> {
          progressDialog = ProgressDialog(this).apply { show() }
        }
        LgEvent.PairingRequired -> Unit
        LgEvent.Connected -> {
          hideDialog()
          dismissAlertDialog("require_connect")
          toast("Connected to '$name'")
        }
        is LgEvent.ConnectFailed -> {
          hideDialog()

          toast(
            message = event.error.code.takeIf { it == -1 }
              ?.let { event.error.message }
              ?: "Failed to connect to '$name'. Try connect again!",
            short = false,
          )
        }
        LgEvent.Disconnected -> {
          hideDialog()
        }
        LgEvent.DisconnectedManual -> {
          hideDialog()
        }
      }
    }

    vm.singleEventFlow.collectIn(this) {
      when (it) {
        MainSingleEvent.NotConnected -> {
          snackbar?.dismiss()
          snackbar = findViewById<View>(android.R.id.content).snack(
            "Not connected!",
            SnackbarLength.SHORT
          ) {
            action("CONNECT NOW") {
              snackbar = null
              startActivity(ScanActivity.makeIntent(this@MainActivity, WEB_OS))
            }
          }
        }
        is MainSingleEvent.SendKeyFailure -> {
          when {
            it.key === RemoteControlKey.source -> {
              showAlertDialog("error_source_button") {
                title("Error")
                message("This TV does not support SOURCE button")
                positiveAction("OK") { _, _ -> }
              }
            }
            it.key === RemoteControlKey.voice && it.commandError is NotSupportedServiceCommandError -> {
              showAlertDialog("error_not_support") {
                title("Error")
                message("This feature is not supported")
                positiveAction("OK") { _, _ -> }
              }
            }
            else -> {
              snackbar?.dismiss()
              snackbar = findViewById<View>(android.R.id.content).snack(
                "Failed to send '${it.key.description}', error: ${it.commandError.message}",
                SnackbarLength.SHORT
              ) {
                action("OK") { snackbar = null }
              }
            }
          }
        }
      }
    }

    if (vm.controlStateFlow.value?.first !== LgState.Connected) {
      showAlertDialog("require_connect") {
        title("LG Remote")
        message("To use this app, your smartphone must be connected to the LG TV!")
        positiveAction("CONNECT NOW") { _, _ ->
          startActivity(ScanActivity.makeIntent(this@MainActivity, WEB_OS))
        }
      }
    }
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    super.onRestoreInstanceState(savedInstanceState)
    // Now that BottomNavigationBar has restored its instance state
    // and its selectedItemId, we can proceed with setting up the
    // BottomNavigationBar with Navigation
    setupBottomNavBar()
  }

  override fun onDestroy() {
    super.onDestroy()
    snackbar?.dismiss()
    snackbar = null
    hideDialog()
  }

  override fun onSupportNavigateUp(): Boolean = currentNavController?.value?.navigateUp() ?: false

  private fun hideDialog() {
    progressDialog?.dismiss()
    progressDialog = null
  }

  /**
   * Called on first creation and when restoring state.
   */
  private fun setupBottomNavBar() {
    val navGraphIds = listOf(
      R.navigation.apps,
      R.navigation.remote,
      R.navigation.broadcast,
      R.navigation.settings,
    )

    // Setup the bottom navigation view with a list of navigation graphs
    val controller = binding.navView.setupWithNavController(
      navGraphIds = navGraphIds,
      fragmentManager = supportFragmentManager,
      containerId = R.id.nav_host_fragment_activity_main,
      intent = intent
    )

    // Whenever the selected controller changes, setup the action bar.
    controller.observe(this) {
      // setupActionBarWithNavController(navController)
    }
    currentNavController = controller

//    val destinationFlow = controller
//      .toFlow()
//      .filterNotNull()
//      .distinctUntilChanged()
//      .flatMapLatest { navController ->
//        callbackFlow {
//          val listener = OnDestinationChangedListener { _, destination, _ ->
//            trySend(destination)
//          }.also(navController::addOnDestinationChangedListener)
//
//          awaitClose {
//            navController.removeOnDestinationChangedListener(listener)
//          }
//        }.buffer(Channel.CONFLATED)
//      }
//      .distinctUntilChangedBy { it.id }
//      .shareIn(lifecycleScope, SharingStarted.WhileSubscribed(), 0)

//    observeNavControllerJob?.cancel()
//    observeNavControllerJob = lifecycleScope.launch {
//      destinationFlow
//        .filter { it.id == R.id.broadcastFragment }
//        .onEach { vm.onSelectBroadcast() }
//        .launchIn(this)
//
//      destinationFlow
//        .filter { it.id != R.id.broadcastFragment }
//        .onEach { vm.onNotSelectBroadcast() }
//        .launchIn(this)
//    }
  }
}
