package com.remotelg.remotelg.ui.broadcast

import android.Manifest
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.hoc081098.viewbindingdelegate.viewBinding
import com.remotelg.remotelg.BuildConfig
import com.remotelg.remotelg.MainVM
import com.remotelg.remotelg.R
import com.remotelg.remotelg.common.model.BroadcastModel
import com.remotelg.remotelg.databinding.FragmentBroadcastBinding
import com.remotelg.remotelg.lgcontrol.LgState
import com.remotelg.remotelg.lgcontrol.RemoteControlKey
import com.remotelg.remotelg.lgcontrol.lgName
import com.remotelg.remotelg.ui.broadcast.adapter.BroadcastAdapter
import com.remotelg.remotelg.ui.scan.ScanActivity
import com.remotelg.remotelg.ui.scan.ScanActivity.Companion.DeviceType.WEB_OS
import com.remotelg.remotelg.utils.*
import com.remotelg.remotelg.utils.enum.ChildView
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BroadcastFragment : Fragment(R.layout.fragment_broadcast) {
  private val mainVM by viewModels<MainVM>(ownerProducer = { requireActivity() })
  private val vm by viewModels<BroadcastVM>()

  private var snackbar: Snackbar? = null

  private val requestPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      if (isGranted) { // Do something if permission granted
        showOptionPhoto()
      }
    }
  private val binding by viewBinding<FragmentBroadcastBinding>()
  private val pickVideoAction =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val uri = it.data?.data
      Timber.tag("####")
        .d(
          "Video: %s %s",
          uri,
          if (BuildConfig.DEBUG) uri?.mediaUrl(
            8080,
            uri.mimeType(requireContext()) ?: "video/mp4",
            requireContext(),
          ) else null
        )

      uri ?: return@registerForActivityResult
      vm.sendKey(BroadcastAction.Video(uri))
    }

  private var clickedPosition = EMPTY_INDEX
  private val requestStoragePermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
      if (isGranted == true) {
        when (clickedPosition) {
          ChildView.MUSIC.position -> pickAudioLauncher.launch("audio/*")
          ChildView.VIDEO.position -> showVideos()
        }
      }
      clickedPosition = EMPTY_INDEX
    }

  private val pickAudioLauncher =
    registerForActivityResult(object : ActivityResultContracts.GetContent() {
      override fun createIntent(context: Context, input: String) =
        super.createIntent(context, input)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }) { uri: Uri? ->
      Timber.tag("####")
        .d(
          "audio $uri ${
          uri?.mediaUrl(
            8080,
            uri.mimeType(requireContext()) ?: "audio/mp3",
            requireContext()
          )
          }"
        )

      uri ?: return@registerForActivityResult
      vm.sendKey(BroadcastAction.Audio(uri))
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initView()
    bindVM()
  }

  private fun bindVM() {
    vm.singleEventFlow.collectIn(this) {
      when (it) {
        BroadcastSingleEvent.NotConnected -> {
          showNotConnectedSnackbar()
        }
        is BroadcastSingleEvent.SendKeyFailure -> {
          snackbar?.dismiss()
          snackbar = requireActivity().findViewById<View>(android.R.id.content).snack(
            "Failed to send '${it.action.uri}', error: ${it.commandError.message}",
            SnackbarLength.SHORT
          ) {
            action("OK") { snackbar = null }
          }
        }
        is BroadcastSingleEvent.SendKeySuccess -> {
          requireContext().toast("Successfully: ${it.action.uri}")
        }
      }
    }

    vm.controlStateFlow.collectIn(this) {
      binding.textConnect.text = if (it?.first == LgState.Connected) {
        "Connected to '${it.second.device.lgName}'"
      } else {
        getString(R.string.text_connect_your_tv)
      }
    }
  }

  private fun showNotConnectedSnackbar() {
    snackbar?.dismiss()
    snackbar = requireActivity().findViewById<View>(android.R.id.content).snack(
      "Not connected!",
      SnackbarLength.SHORT
    ) {
      action("CONNECT NOW") {
        snackbar = null
        startActivity(
          ScanActivity.makeIntent(
            requireContext(),
            WEB_OS,
          )
        )
      }
    }
  }

  private fun initView() {
    binding.run {
      val broadcastAdapter = BroadcastAdapter onClick@{
        clickedPosition = EMPTY_INDEX

        when (it) {
          ChildView.PHOTO.position -> {
            if (vm.controlStateFlow.value?.first !== LgState.Connected) {
              return@onClick showNotConnectedSnackbar()
            }

            if (PermissionChecker.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
              )
              != PermissionChecker.PERMISSION_GRANTED
            ) {
              requestPermission.launch(Manifest.permission.CAMERA)
            } else {
              showOptionPhoto()
            }
          }
          ChildView.VIDEO.position -> {
            if (vm.controlStateFlow.value?.first !== LgState.Connected) {
              return@onClick showNotConnectedSnackbar()
            }

            if (ContextCompat.checkSelfPermission(
                requireContext(),
                READ_EXTERNAL_STORAGE,
              ) != PackageManager.PERMISSION_GRANTED
            ) {
              clickedPosition = it
              requestStoragePermission.launch(READ_EXTERNAL_STORAGE)
            } else {
              showVideos()
            }
          }
          ChildView.MUSIC.position -> {
            if (vm.controlStateFlow.value?.first !== LgState.Connected) {
              return@onClick showNotConnectedSnackbar()
            }

            if (ContextCompat.checkSelfPermission(
                requireContext(),
                READ_EXTERNAL_STORAGE,
              ) != PackageManager.PERMISSION_GRANTED
            ) {
              clickedPosition = it
              requestStoragePermission.launch(READ_EXTERNAL_STORAGE)
            } else {
              pickAudioLauncher.launch("audio/*")
            }
          }
          ChildView.WEB.position -> {
            mainVM.sendKey(RemoteControlKey.launchBrowser(URL))
          }
          else -> error("Unhandled position $it")
        }
      }
      recyclerView.adapter = broadcastAdapter
      recyclerView.addItemDecoration(GridSpacingItemDecoration(2, 45, false))
      broadcastAdapter.submitList(createData())
    }

    binding.containerShowConnect.setOnClickListener {
      val state = vm.controlStateFlow.value

      if (state?.first === LgState.Connected) {
        requireActivity().showAlertDialog("already_connected") {
          title("Connected")
          message("Already connected to '${state.second.device.lgName}'")
          positiveAction("OK") { _, _ -> }
        }
      } else {
        startActivity(
          ScanActivity.makeIntent(
            requireContext(),
            WEB_OS,
          )
        )
      }
    }
  }

  private fun showOptionPhoto() {
    DialogCameraFragment().apply {
      onResultImage = {
        Timber.tag("####")
          .d(
            "Image: %s %s",
            it,
            if (BuildConfig.DEBUG) it?.mediaUrl(
              8080,
              it.mimeType(requireContext()) ?: "image/jpeg",
              requireContext(),
            ) else null,
          )

        it?.let {
          vm.sendKey(BroadcastAction.Image(it))
        }
        this.dismissAllowingStateLoss()
      }
    }.show(childFragmentManager, DialogCameraFragment::class.simpleName)
  }

  private fun showVideos() {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
      type = "video/*"
      addCategory(Intent.CATEGORY_OPENABLE)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    pickVideoAction.launch(intent)
  }

  private fun createData(): MutableList<BroadcastModel> {
    val itemYoutube = BroadcastModel(
      name = getString(R.string.text_photo),
      image = ContextCompat.getDrawable(requireContext(), R.drawable.ic_img1),
    )
    val itemBbc = BroadcastModel(
      name = getString(R.string.text_video),
      image = ContextCompat.getDrawable(requireContext(), R.drawable.ic_img2),
    )
    val itemNetflx = BroadcastModel(
      name = getString(R.string.text_Music),
      image = ContextCompat.getDrawable(requireContext(), R.drawable.ic_img3),
    )

    val itemYoutube1 = BroadcastModel(
      name = getString(R.string.text_Browser),
      image = ContextCompat.getDrawable(requireContext(), R.drawable.ic_img4),
    )
    return mutableListOf(itemYoutube, itemBbc, itemNetflx, itemYoutube1)
  }

  private companion object {
    private const val EMPTY_INDEX = -1
    private const val URL = "https://suntechltd.net/portfolio-item/gchat-gay-chat-dating/"
  }
}
