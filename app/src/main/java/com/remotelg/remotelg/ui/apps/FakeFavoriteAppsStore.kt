package com.remotelg.remotelg.ui.apps

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class FakeFavoriteAppsStore @Inject constructor() : FavoriteAppsStore {
  private val ids = MutableStateFlow(emptySet<String>())

  override fun favoriteIds() = ids

  override suspend fun toggle(id: String) {
    delay(100)
    ids.update { set -> if (id in set) set - id else set + id }
  }
}
