package com.remotelg.remotelg.utils

import android.os.Build
import java.util.Locale

private val deviceName = lazy {
  val manufacturer = Build.MANUFACTURER
  val model = Build.MODEL
  if (model.startsWith(manufacturer)) {
    model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  } else {
    manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + model
  }
}

fun getDeviceName(): String = deviceName.value

private val isEmulator = lazy {
  (
    Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
      Build.FINGERPRINT.startsWith("generic") ||
      Build.FINGERPRINT.startsWith("unknown") ||
      Build.HARDWARE.contains("goldfish") ||
      Build.HARDWARE.contains("ranchu") ||
      Build.MODEL.contains("google_sdk") ||
      Build.MODEL.contains("Emulator") ||
      Build.MODEL.contains("Android SDK built for x86") ||
      Build.MANUFACTURER.contains("Genymotion") ||
      Build.PRODUCT.contains("sdk_google") ||
      Build.PRODUCT.contains("google_sdk") ||
      Build.PRODUCT.contains("sdk") ||
      Build.PRODUCT.contains("sdk_x86") ||
      Build.PRODUCT.contains("vbox86p") ||
      Build.PRODUCT.contains("emulator") ||
      Build.PRODUCT.contains("simulator")
    )
}

/**
 * A simple emulator-detection based on the flutter tools detection logic and a couple of legacy
 * detection systems
 */
fun isEmulator(): Boolean = isEmulator.value
