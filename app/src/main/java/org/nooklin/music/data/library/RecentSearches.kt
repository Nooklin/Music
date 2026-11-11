package org.nooklin.music.data.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// What was searched for stays on this device only
private val Context.searchDataStore by preferencesDataStore(name = "nooklin_music_search")

private val RECENT_KEY = stringPreferencesKey("recent")

private const val MAX_RECENT = 10

class RecentSearches(private val context: Context) {

    val terms: Flow<List<String>> = context.searchDataStore.data.map { prefs -> split(prefs[RECENT_KEY]) }

    suspend fun add(term: String) {
        val clean = term.trim().replace("\n", " ")
        if (clean.isEmpty()) return
        context.searchDataStore.edit { prefs ->
            val others = split(prefs[RECENT_KEY]).filter { !it.equals(clean, ignoreCase = true) }
            prefs[RECENT_KEY] = (listOf(clean) + others).take(MAX_RECENT).joinToString("\n")
        }
    }

    suspend fun clear() {
        context.searchDataStore.edit { it.remove(RECENT_KEY) }
    }

    private fun split(text: String?): List<String> = (text ?: "").split("\n").filter { it.isNotBlank() }
}
