@file:Suppress("SpellCheckingInspection", "unused", "NOTHING_TO_INLINE")

package com.remotelg.remotelg.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnyRes
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

inline infix fun ViewGroup.inflate(layoutRes: Int) =
  LayoutInflater.from(context).inflate(layoutRes, this, false)!!

val Context.isOrientationPortrait get() = this.resources.configuration.orientation == ORIENTATION_PORTRAIT

@ColorInt
inline fun Context.getColorBy(@ColorRes id: Int) = ContextCompat.getColor(this, id)

inline fun Context.getDrawableBy(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)

/**
 * Retrieve a color from the current [android.content.res.Resources.Theme].
 */
@ColorInt
@SuppressLint("Recycle")
inline fun Context.themeColor(@AttrRes themeAttrId: Int): Int =
  obtainStyledAttributes(intArrayOf(themeAttrId)).use { it.getColor(0, Color.TRANSPARENT) }

/**
 * Get uri from any resource type
 * @receiver Context
 * @param resId - Resource id
 * @return - Uri to resource by given id or null
 */
inline fun Context.uriFromResourceId(@AnyRes resId: Int): Uri? {
  return runCatching {
    val res = this@uriFromResourceId.resources
    Uri.parse(
      ContentResolver.SCHEME_ANDROID_RESOURCE +
        "://" + res.getResourcePackageName(resId) +
        '/' + res.getResourceTypeName(resId) +
        '/' + res.getResourceEntryName(resId)
    )
  }.getOrNull()
}

inline fun Context.dpToPx(dp: Int): Int {
  val displayMetrics = resources.displayMetrics
  return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
}

inline fun Context.toast(
  @StringRes messageRes: Int,
  short: Boolean = true,
) = this.toast(getString(messageRes), short)

inline fun Context.toast(
  message: String,
  short: Boolean = true,
) =
  Toast.makeText(
    this,
    message,
    if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
  ).apply { show() }!!

@SuppressLint("Recycle")
inline fun Context.themeInterpolator(@AttrRes attr: Int): Interpolator {
  return AnimationUtils.loadInterpolator(
    this,
    obtainStyledAttributes(intArrayOf(attr)).use {
      it.getResourceId(0, android.R.interpolator.fast_out_slow_in)
    }
  )
}

@Suppress("unused")
inline val Any?.unit
  get() = Unit

@Deprecated(
  level = DeprecationLevel.ERROR,
  message = "This value is Unit",
  replaceWith = ReplaceWith("this")
)
inline val Unit.unit
  get() = this

inline val ViewGroup.inflater: LayoutInflater get() = LayoutInflater.from(context)

/**
 * Start enter transitions that were postponed for this fragment when its content has been redrawn.
 * This is meant to be used when the data backing a RecyclerView
 * has been updated for the first time.
 *
 * See [https://developer.android.com/training/basics/fragments/animate#recyclerview]
 */
inline fun Fragment.startPostponedEnterTransitionWhenDrawn() {
  (requireView().parent as? ViewGroup)?.doOnPreDraw {
    startPostponedEnterTransition()
  }
}
inline fun View.debounceClick(timePrevent: Int = 1000, crossinline block: (View) -> Unit) {
  var lastTimeClicked: Long = 0

  setOnClickListener {
    if (SystemClock.elapsedRealtime() - lastTimeClicked < timePrevent) {
      return@setOnClickListener
    }
    lastTimeClicked = SystemClock.elapsedRealtime()
    block(it)
  }
}

inline fun Fragment.requestPermission(
  permission: String,
  crossinline granted: (permission: String) -> Unit = {},
  crossinline denied: (permission: String) -> Unit = {},
  crossinline explained: (permission: String) -> Unit = {}

) {
  registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
    when {
      result -> granted.invoke(permission)
      shouldShowRequestPermissionRationale(permission) -> denied.invoke(permission)
      else -> explained.invoke(permission)
    }
  }.launch(permission)
}
