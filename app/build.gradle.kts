import java.util.Properties

plugins {
  androidApplication
  kotlinAndroid
  kotlinKapt
  daggerHiltAndroid
}

android {
  compileSdk = appConfig.compileSdkVersion
  buildToolsVersion = appConfig.buildToolsVersion

  defaultConfig {
    applicationId = appConfig.applicationId
    minSdk = appConfig.minSdkVersion
    targetSdk = appConfig.targetSdkVersion
    versionCode = appConfig.versionCode
    versionName = appConfig.versionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystoreProperties = Properties().apply {
        load(
          rootProject.file("keystore/key.properties")
            .apply { check(exists()) }
            .reader()
        )
      }

      keyAlias = keystoreProperties["keyAlias"] as String
      keyPassword = keystoreProperties["keyPassword"] as String
      storeFile =
        rootProject.file(keystoreProperties["storeFile"] as String).apply { check(exists()) }
      storePassword = keystoreProperties["storePassword"] as String

      // Optional, specify signing versions used
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
      isDebuggable = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = JavaVersion.VERSION_1_8.toString() }
  buildFeatures { viewBinding = true }

  useLibrary("org.apache.http.legacy")
}

dependencies {
  implementation(deps.androidx.coreKtx)
  implementation(deps.androidx.appCompat)
  implementation(deps.androidx.material)
  implementation(deps.androidx.constraintLayout)
  implementation(deps.androidx.fragmentKtx)
  implementation(deps.androidx.activityKtx)

  implementation(deps.androidx.lifecycle.commonJava8)
  implementation(deps.androidx.lifecycle.runtimeKtx)
  implementation(deps.androidx.lifecycle.viewModelKtx)
  implementation(deps.androidx.lifecycle.process)

  implementation(deps.androidx.navigation.fragmentKtx)
  implementation(deps.androidx.navigation.uiKtx)

  implementation(deps.androidx.datastorePreferences)

  testImplementation(deps.test.junit)
  androidTestImplementation(deps.test.androidxJunit)
  androidTestImplementation(deps.test.androidXSspresso)

  implementation(deps.daggerHilt.core)
  implementation(deps.daggerHilt.android)
  kapt(deps.daggerHilt.compiler)

  implementation(deps.viewBindingDelegate)
  implementation(deps.coil)
  implementation(deps.timber)

  implementation(deps.coroutines.core)
  implementation(deps.coroutines.android)

  implementation(deps.connectSDK)
  implementation(deps.libNeumorphism)
  implementation(deps.flowBinding)
  implementation(deps.progessview)

  // add firebase
  implementation(platform(deps.firebase.firebaseBom))
  implementation(deps.firebase.fireStore)
  implementation(deps.firebase.crashlytics)
  implementation(deps.firebase.analytics)

  // rx
  implementation(deps.reactiveX.java)
  implementation(deps.reactiveX.kotlin)
  implementation(deps.reactiveX.android)

  implementation(deps.nanohttpd)
}

kapt {
  correctErrorTypes = true
  javacOptions {
    // These options are normally set automatically via the Hilt Gradle plugin, but we
    // set them manually to workaround a bug in the Kotlin 1.5.20
    option("-Adagger.fastInit=ENABLED")
    option("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
  }
}

apply {
  plugin("com.google.gms.google-services")
  plugin("com.google.firebase.crashlytics")
}
