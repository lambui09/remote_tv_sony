package com.remotelg.remotelg.ui.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class ScanModule {
  @Binds
  @ViewModelScoped
  abstract fun searchDevices(impl: RealSearchDevices): SearchDevices
}
