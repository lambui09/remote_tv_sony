package com.remotelg.remotelg.ui.broadcast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.remotelg.remotelg.databinding.DialogCameraBottomSheetBinding
import com.remotelg.remotelg.utils.debounceClick
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DialogCameraFragment : BottomSheetDialogFragment() {
  private lateinit var viewBinding: DialogCameraBottomSheetBinding
  private var tempUri: Uri? = null
  var onResultImage: ((Uri?) -> Unit)? = {}
  private val pickImageCamera =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val uri = it.data?.data
      onResultImage?.invoke(uri)
    }

  private val takeImagePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) {
    if (!it) return@registerForActivityResult
    onResultImage?.invoke(tempUri)
  }

  private val requestPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) permission@{ isGrant ->
      if (!isGrant) {
        // Finish the camera screen if the permission not granted
        Toast.makeText(requireContext(), "not permission", Toast.LENGTH_LONG).show()
        return@permission
      }
      tempUri = createTempUri()
      Timber.tag("<<<").d("tempUri=$tempUri")
      takeImagePhoto.launch(tempUri)
    }

  private fun createTempUri(): Uri {
    val file = File.createTempFile(
      getFileName(),
      FILE_NAME_EXTENSION,
      getOutputDirectory(requireContext())
    )
    Timber.tag("<<<").d("file=$file")
    return FileProvider.getUriForFile(
      requireContext(),
      "${context?.packageName}.provider",
      file
    )
  }

  private fun getOutputDirectory(context: Context): File {
    return context
      .externalCacheDir!!
      .let { File(it, "MyCachedImages") }
      .apply { mkdirs() }
  }

  private fun getFileName(): String {
    return SimpleDateFormat(
      FILE_NAME_FORMAT,
      Locale.US
    ).format(System.currentTimeMillis())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    viewBinding = DialogCameraBottomSheetBinding.inflate(inflater, container, false)
    return viewBinding.root
  }

  override fun onStart() {
    super.onStart()

    dialog?.run {
      window?.setBackgroundDrawableResource(android.R.color.transparent)
      setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
          dismiss()
          true
        } else {
          false
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    with(viewBinding) {
      btnCamera.debounceClick {
        requestPermission.launch(Manifest.permission.CAMERA)
      }
      btnGallery.debounceClick {
        val intent = Intent(ACTION_OPEN_DOCUMENT).apply {
          val mimeTypes = arrayOf("image/png", "image/jpg", "image/jpeg")
          type = "*/*"
          putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
          addCategory(Intent.CATEGORY_OPENABLE)
          addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pickImageCamera.launch(intent)
      }
      btnCancel.debounceClick {
        dismiss()
      }
    }
  }

  companion object {
    const val FILE_NAME_FORMAT = "yyyyMMdd_HHmmss"
    const val FILE_NAME_EXTENSION = ".jpg"
  }
}
