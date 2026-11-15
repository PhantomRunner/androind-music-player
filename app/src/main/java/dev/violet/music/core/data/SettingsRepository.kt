package dev.violet.music.core.data

import androidx.room.withTransaction
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.PreferenceEntity
import dev.violet.music.core.model.DisplayMode
import dev.violet.music.core.model.HistoryPeriod
import dev.violet.music.core.model.LibraryCategory
import dev.violet.music.core.model.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private const val RECENT_SEARCHES = "search.recentQueries"
private const val SEARCH_HISTORY_LIMIT = 10

@Singleton
class SettingsRepository @Inject constructor(private val database: MusicDatabase) {
    val recentSearches = database.experienceDao().observePreference(RECENT_SEARCHES).map(::decodeSearches)

    val preferences =
        database.experienceDao().preferences().map { rows ->
            val values = rows.associate { it.key to it.value }
            LibraryPreferences(
                period =
                    values["history.period"]?.let {
                        runCatching { HistoryPeriod.valueOf(it) }.getOrNull()
                    } ?: HistoryPeriod.MONTH,
                modes =
                    LibraryCategory.entries
                        .mapNotNull { category ->
                            values["mode.${category.name}"]?.let { value ->
                                runCatching { category to DisplayMode.valueOf(value) }.getOrNull()
                            }
                        }
                        .toMap(),
                sorts =
                    LibraryCategory.entries
                        .mapNotNull { category ->
                            values["sort.${category.name}"]?.let { category to it }
                        }
                        .toMap(),
            )
        }

    suspend fun period(value: HistoryPeriod) = save("history.period", value.name)

    suspend fun mode(category: LibraryCategory, value: DisplayMode) =
        save("mode.${category.name}", value.name)

    suspend fun sort(category: LibraryCategory, value: String) =
        save("sort.${category.name}", value)

    suspend fun recordSearch(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        updateSearches { existing ->
            (listOf(text) + existing.filterNot { it.equals(text, ignoreCase = true) })
                .take(SEARCH_HISTORY_LIMIT)
        }
    }

    suspend fun removeSearch(query: String) = updateSearches { existing ->
        existing.filterNot { it.equals(query, ignoreCase = true) }
    }

    suspend fun clearSearches() = updateSearches { emptyList() }

    private suspend fun updateSearches(edit: (List<String>) -> List<String>) {
        database.withTransaction {
            val current = decodeSearches(database.experienceDao().readPreference(RECENT_SEARCHES))
            val updated = edit(current)
            if (updated != current) save(RECENT_SEARCHES, JSONArray(updated).toString())
        }
    }

    private suspend fun save(key: String, value: String) =
        database.experienceDao().preference(PreferenceEntity(key, value))
}

private fun decodeSearches(value: String?): List<String> {
    if (value == null) return emptyList()
    val values = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
    return List(minOf(values.length(), SEARCH_HISTORY_LIMIT)) { values.optString(it) }
        .filter(String::isNotBlank)
}
