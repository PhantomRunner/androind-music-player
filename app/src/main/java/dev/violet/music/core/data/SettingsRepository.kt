package dev.violet.music.core.data

import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.database.PreferenceEntity
import dev.violet.music.core.model.DisplayMode
import dev.violet.music.core.model.HistoryPeriod
import dev.violet.music.core.model.LibraryCategory
import dev.violet.music.core.model.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(private val database: MusicDatabase) {
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

    private suspend fun save(key: String, value: String) =
        database.experienceDao().preference(PreferenceEntity(key, value))
}
