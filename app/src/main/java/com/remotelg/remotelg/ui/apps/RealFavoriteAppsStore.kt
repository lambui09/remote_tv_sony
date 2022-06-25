package com.remotelg.remotelg.ui.apps

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class RealFavoriteAppsStore @Inject constructor(
  private val store: DataStore<Preferences>
) : FavoriteAppsStore {
  private companion object {
    private val key = stringSetPreferencesKey("com.remotelg.remotelg.favorite_app_ids")
  }

  override fun favoriteIds(): Flow<Set<String>> {
    return store.data
      .map { it[key] ?: emptySet() }
      .catch {
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (it is IOException) {
          emit(emptySet())
        } else {
          throw it
        }
      }
      .distinctUntilChanged()
  }

  override suspend fun toggle(id: String) {
    store.edit { preferences ->
      val currentIds = preferences[key]
      preferences[key] = when {
        currentIds === null -> setOf(id)
        id in currentIds -> currentIds - id
        else -> currentIds + id
      }
    }
  }
}
