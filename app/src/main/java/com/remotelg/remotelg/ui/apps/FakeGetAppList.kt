package com.remotelg.remotelg.ui.apps

import android.content.Context
import androidx.core.content.ContextCompat
import com.remotelg.remotelg.R
import com.remotelg.remotelg.common.model.AppModel
import com.remotelg.remotelg.utils.Lce
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeGetAppList @Inject constructor(
  @ApplicationContext private val appContext: Context,
) : GetAppList {
  override fun invoke(): Flow<LceAppModels> {
    return flow {
      emit(Lce.Loading)

      delay(5000)

      emit(
        Lce.content(
          List(32) { i ->
            AppModel(
              id = "id.$i",
              name = "App $i",
              image = ContextCompat.getDrawable(appContext, R.drawable.img_youtube)!!,
              hasFavorite = false,
            )
          }
        ),
      )
    }
  }
}
