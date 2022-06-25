package com.remotelg.remotelg.ui.apps

import com.google.common.base.Optional
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppsSpaceInDp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppsSpanCount

@Module
@InstallIn(ViewModelComponent::class)
abstract class AppsVMModule {
  @Binds
  @ViewModelScoped
  abstract fun getAppList(impl: RealGetAppList): GetAppList

  @Binds
  @ViewModelScoped
  abstract fun favoriteAppsStore(impl: RealFavoriteAppsStore): FavoriteAppsStore
}

@Module
@InstallIn(ActivityRetainedComponent::class)
class AppsRetainedModule {
  @Provides
  @AppsSpaceInDp
  @ActivityRetainedScoped
  fun provideSpaceInDp(): Optional<Int> = Optional.of(24)

  @Provides
  @AppsSpanCount
  @ActivityRetainedScoped
  fun provideSpanCount(): Optional<Int> = Optional.of(3)
}
