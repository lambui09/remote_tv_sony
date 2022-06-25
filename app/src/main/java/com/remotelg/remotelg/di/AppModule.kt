package com.remotelg.remotelg.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.remotelg.remotelg.ProcessLifecycleObserver
import com.remotelg.remotelg.lgcontrol.DLNA
import com.remotelg.remotelg.lgcontrol.MainDeviceManager
import com.remotelg.remotelg.lgcontrol.WebOS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppCoroutinesScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
  companion object {
    @AppCoroutinesScope
    @Provides
    @Singleton
    fun appCoroutinesScope(): CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Provides
    @Singleton
    fun dataStore(
      @ApplicationContext applicationContext: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
      produceFile = {
        applicationContext.preferencesDataStoreFile("app_store")
      }
    )

    @Provides
    @Singleton
    @WebOS
    fun webOSMainDeviceManager(
      @AppCoroutinesScope appCoroutinesScope: CoroutineScope,
      processLifecycleObserver: ProcessLifecycleObserver,
    ): MainDeviceManager = MainDeviceManager(appCoroutinesScope, processLifecycleObserver, "WebOS")

    @Provides
    @Singleton
    @DLNA
    fun DLNAMainDeviceManager(
      @AppCoroutinesScope appCoroutinesScope: CoroutineScope,
      processLifecycleObserver: ProcessLifecycleObserver,
    ): MainDeviceManager = MainDeviceManager(appCoroutinesScope, processLifecycleObserver, "DLNA")
  }
}
