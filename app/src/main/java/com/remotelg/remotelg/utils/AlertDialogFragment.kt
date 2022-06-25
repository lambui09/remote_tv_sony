package com.remotelg.remotelg.utils

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.annotation.DrawableRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.remotelg.remotelg.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

/**
 * Show alert dialog fragment
 * @return a [Maybe] that emits [Unit] when pressing OK button,
 * otherwise return an empty [Maybe]
 */
suspend fun FragmentActivity.showAlertDialogSuspend(
  tag: String,
  negativeText: String? = "Cancel",
  positiveText: String? = "OK",
  init: AlertDialogFragment.Builder.() -> Unit,
): Unit? {
  return suspendCancellableCoroutine { emitter ->
    check(Looper.getMainLooper() == Looper.myLooper())

    showAlertDialog(tag) {
      init()

      onCancel { emitter.resume(null) }

      negativeText?.let {
        negativeAction(it) { _, _ ->
          emitter.resume(null)
        }
      }

      positiveText?.let {
        positiveAction(it) { _, _ ->
          emitter.resume(Unit)
        }
      }
    }

    emitter.invokeOnCancellation {
      Dispatchers.Main.immediate.dispatch(EmptyCoroutineContext) {
        dismissAlertDialog(tag)
      }
    }
  }
}

private val prefixTag = AlertDialogFragment::class.java.simpleName

/**
 * Show alert dialog
 */
fun FragmentActivity.showAlertDialog(
  tag: String,
  init: AlertDialogFragment.Builder.() -> Unit
): AlertDialogFragment {
  val ft = supportFragmentManager.beginTransaction().apply {
    supportFragmentManager
      .findFragmentByTag(prefixTag + tag)
      ?.let(::remove)
    addToBackStack(null)
  }

  return AlertDialogFragment.Builder()
    .apply(init)
    .build()
    .apply { show(ft, prefixTag + tag) }
}

/**
 * Dismiss alert dialog
 */
fun FragmentActivity.dismissAlertDialog(tag: String) {
  try {
    val dialogFragment =
      supportFragmentManager.findFragmentByTag(prefixTag + tag) as? AlertDialogFragment
    dialogFragment?.cleanUp()
    dialogFragment?.dismissAllowingStateLoss()
    Timber.d("dismissAlertDialog")
  } catch (e: Exception) {
    Timber.d("dismissAlertDialog $e")
  }
}

class AlertDialogFragment : DialogFragment() {
  var builder: Builder? = null
    private set

  override fun onCancel(dialog: DialogInterface) {
    super.onCancel(dialog)
    builder?.onCancelListener?.onCancel(dialog)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return MaterialAlertDialogBuilder(requireContext(), R.style.AppTheme_AlertDialog)
      .apply {
        val builder = this@AlertDialogFragment.builder ?: return@apply

        setTitle(builder.titleText)
        setMessage(builder.messageText)
        setCancelable(builder.cancelable)

        setIcon(builder.iconId)
        setIcon(builder.icon)

        if (builder.negativeButtonText !== null && builder.negativeButtonClickListener !== null) {
          setNegativeButton(
            builder.negativeButtonText,
            builder.negativeButtonClickListener
          )
        }
        if (builder.positiveButtonText !== null && builder.positiveButtonClickListener !== null) {
          setPositiveButton(
            builder.positiveButtonText,
            builder.positiveButtonClickListener
          )
        }
        if (builder.neutralButtonText !== null && builder.neutralButtonClickListener !== null) {
          setNeutralButton(
            builder.neutralButtonText,
            builder.neutralButtonClickListener
          )
        }
        if (builder.view !== null) {
          setView(builder.view)
        }
      }
      .create()
  }

  fun cleanUp() {
    builder = null
  }

  companion object {
    fun getInstance(builder: Builder): AlertDialogFragment {
      return AlertDialogFragment().apply { this.builder = builder }
    }
  }

  @Suppress("unused")
  class Builder {
    var titleText: String? = null
      private set
    var messageText: String? = null
      private set
    var cancelable: Boolean = true
      private set

    @DrawableRes
    var iconId: Int = 0
      private set
    var icon: Drawable? = null
      private set

    var onCancelListener: DialogInterface.OnCancelListener? = null
      private set

    var negativeButtonText: String? = null
      private set
    var negativeButtonClickListener: DialogInterface.OnClickListener? = null
      private set

    var positiveButtonText: String? = null
      private set
    var positiveButtonClickListener: DialogInterface.OnClickListener? = null
      private set

    var neutralButtonText: String? = null
      private set
    var neutralButtonClickListener: DialogInterface.OnClickListener? = null
      private set

    var view: View? = null
      private set

    fun title(title: String) = apply { this.titleText = title }

    fun message(message: String) = apply { this.messageText = message }

    fun cancelable(cancelable: Boolean) = apply { this.cancelable = cancelable }

    fun iconId(@DrawableRes iconId: Int) = apply { this.iconId = iconId }

    fun icon(icon: Drawable) = apply { this.icon = icon }

    fun onCancel(listener: (DialogInterface) -> Unit) {
      this.onCancelListener = DialogInterface.OnCancelListener(listener)
    }

    fun negativeAction(
      text: String,
      listener: (dialog: DialogInterface, which: Int) -> Unit,
    ) = apply {
      this.negativeButtonText = text
      this.negativeButtonClickListener = DialogInterface.OnClickListener(listener)
    }

    fun positiveAction(
      text: String,
      listener: (dialog: DialogInterface, which: Int) -> Unit,
    ) = apply {
      this.positiveButtonText = text
      this.positiveButtonClickListener = DialogInterface.OnClickListener(listener)
    }

    fun neutralAction(
      text: String,
      listener: (dialog: DialogInterface, which: Int) -> Unit,
    ) = apply {
      this.neutralButtonText = text
      this.neutralButtonClickListener = DialogInterface.OnClickListener(listener)
    }

    fun view(view: View) = apply { this.view = view }

    fun build() = getInstance(this)
  }
}
