@file:Suppress("unused", "ClassName", "SpellCheckingInspection")

import org.gradle.kotlin.dsl.kotlin
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

object pluginVersions {
  const val agp = "7.0.2"
  const val ktlint = "0.42.1"
  const val kotlin = "1.5.30"
  const val hilt = deps.daggerHilt.version
}

object appConfig {
  const val applicationId = "com.remotelg.remotelg"

  const val compileSdkVersion = 30
  const val buildToolsVersion = "30.0.3"

  const val minSdkVersion = 24
  const val targetSdkVersion = 30

  private const val MAJOR = 1
  private const val MINOR = 4
  private const val PATCH = 0
  const val versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
  const val versionName = "$MAJOR.$MINOR.$PATCH"
}

object deps {
  object androidx {
    const val appCompat = "androidx.appcompat:appcompat:1.4.0-alpha03"
    const val coreKtx = "androidx.core:core-ktx:1.7.0-alpha01"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:2.1.0"
    const val recyclerView = "androidx.recyclerview:recyclerview:1.2.0"
    const val swipeRefreshLayout = "androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01"
    const val material = "com.google.android.material:material:1.4.0"
    const val fragmentKtx = "androidx.fragment:fragment-ktx:1.4.0-alpha04"
    const val activityKtx = "androidx.activity:activity-ktx:1.3.1"
    const val datastorePreferences = "androidx.datastore:datastore-preferences:1.0.0"

    object lifecycle {
      private const val version = "2.4.0-alpha03"

      const val viewModelKtx =
        "androidx.lifecycle:lifecycle-viewmodel-ktx:$version" // viewModelScope
      const val runtimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:$version" // lifecycleScope
      const val commonJava8 = "androidx.lifecycle:lifecycle-common-java8:$version"
      const val process = "androidx.lifecycle:lifecycle-process:$version"
    }

    object navigation {
      private const val version = "2.4.0-alpha06"
      const val fragmentKtx = "androidx.navigation:navigation-fragment-ktx:$version"
      const val uiKtx = "androidx.navigation:navigation-ui-ktx:$version"
    }
  }

  object squareup {
    const val retrofit = "com.squareup.retrofit2:retrofit:2.9.0"
    const val converterMoshi = "com.squareup.retrofit2:converter-moshi:2.9.0"
    const val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.2"
    const val moshiKotlin = "com.squareup.moshi:moshi-kotlin:1.11.0"
    const val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.7"
  }

  object coroutines {
    private const val version = "1.5.2"

    const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$version"
    const val android = "org.jetbrains.kotlinx:kotlinx-coroutines-android:$version"
  }

  const val coil = "io.coil-kt:coil:1.3.2"
  const val viewBindingDelegate = "com.github.hoc081098:ViewBindingDelegate:1.2.0"
  const val connectSDK = "com.github.ConnectSDK:Connect-SDK-Android-Lite:master-SNAPSHOT"
  const val timber = "com.jakewharton.timber:timber:5.0.0"
  const val flowBinding = "io.github.reactivecircus.flowbinding:flowbinding-android:1.2.0"
  const val libNeumorphism = "com.github.fornewid:neumorphism:0.3.0"
  const val progessview = "com.airbnb.android:lottie:3.3.1"
  const val nanohttpd = "org.nanohttpd:nanohttpd:2.3.1"

  object test {
    const val junit = "junit:junit:4.13"
    const val androidxJunit = "androidx.test.ext:junit:1.1.2"
    const val androidXSspresso = "androidx.test.espresso:espresso-core:3.3.0"
  }

  object daggerHilt {
    const val version = "2.38.1"
    const val android = "com.google.dagger:hilt-android:$version"
    const val core = "com.google.dagger:hilt-core:$version"
    const val compiler = "com.google.dagger:hilt-compiler:$version"
  }

  object firebase {
    const val firebaseBom = "com.google.firebase:firebase-bom:28.3.1"
    const val fireStore = "com.google.firebase:firebase-firestore-ktx"
    const val crashlytics = "com.google.firebase:firebase-crashlytics-ktx"
    const val analytics = "com.google.firebase:firebase-analytics-ktx"
  }

  object reactiveX {
    const val kotlin = "io.reactivex.rxjava3:rxkotlin:3.0.1"
    const val java = "io.reactivex.rxjava3:rxjava:3.1.0"
    const val android = "io.reactivex.rxjava3:rxandroid:3.0.0"
  }
}

private typealias PDsS = PluginDependenciesSpec
private typealias PDS = PluginDependencySpec

inline val PDsS.androidApplication: PDS get() = id("com.android.application")
inline val PDsS.androidLib: PDS get() = id("com.android.library")
inline val PDsS.kotlinAndroid: PDS get() = id("kotlin-android")
inline val PDsS.kotlin: PDS get() = id("kotlin")
inline val PDsS.kotlinKapt: PDS get() = kotlin("kapt")
inline val PDsS.daggerHiltAndroid: PDS get() = id("dagger.hilt.android.plugin")
