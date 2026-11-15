package dev.violet.music.core.data

import androidx.room.withTransaction
import dev.violet.music.core.database.ArtistCreditEntity
import dev.violet.music.core.database.ArtistEntity
import dev.violet.music.core.database.ArtistOverrideEntity
import dev.violet.music.core.database.MusicDatabase
import dev.violet.music.core.model.ArtistRole
import dev.violet.music.core.model.CreditSource
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun artistKey(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

data class ResolvedCredit(val names: List<String>, val source: CreditSource)

fun resolveArtistCredit(
    raw: String?,
    structured: List<String>,
    known: Set<String>,
): ResolvedCredit {
    val explicit = structured.map(String::trim).filter(String::isNotEmpty).distinctBy(::artistKey)
    if (explicit.size > 1) return ResolvedCredit(explicit, CreditSource.METADATA)
    val name =
        explicit.singleOrNull()
            ?: raw?.trim()?.takeIf(String::isNotEmpty)
            ?: return ResolvedCredit(emptyList(), CreditSource.METADATA)
    val parts =
        name
            .split(
                Regex(
                    "\\s+(?:feat\\.?|ft\\.?|featuring)\\s+|\\s*[,;&]\\s*",
                    RegexOption.IGNORE_CASE,
                )
            )
            .map(String::trim)
    return if (parts.size > 1 && parts.all { it.isNotEmpty() && artistKey(it) in known }) {
        ResolvedCredit(parts.distinctBy(::artistKey), CreditSource.INFERRED)
    } else ResolvedCredit(listOf(name), CreditSource.METADATA)
}

@Singleton
class ArtistRepository @Inject constructor(private val database: MusicDatabase) {
    private val dao
        get() = database.experienceDao()

    fun credits(trackId: String) = dao.credits(trackId)

    fun name(id: String) = dao.artistName(id)

    suspend fun automatic(trackId: String, role: ArtistRole, credit: ResolvedCredit) =
        database.withTransaction {
            dao.clearAutomatic(trackId, role.name)
            write(trackId, role, credit)
        }

    suspend fun correct(trackId: String, role: ArtistRole, names: List<String>) {
        val cleaned = names.map(String::trim).filter(String::isNotEmpty).distinctBy(::artistKey)
        require(cleaned.size in 1..30 && cleaned.all { it.length <= 200 })
        database.withTransaction {
            requireNotNull(database.libraryDao().findTrack(trackId))
            dao.clearManual(trackId, role.name)
            dao.override(ArtistOverrideEntity(trackId, role.name))
            write(trackId, role, ResolvedCredit(cleaned, CreditSource.MANUAL))
        }
    }

    suspend fun reset(trackId: String, role: ArtistRole) =
        database.withTransaction {
            dao.clearManual(trackId, role.name)
            dao.reset(trackId, role.name)
        }

    private suspend fun write(trackId: String, role: ArtistRole, credit: ResolvedCredit) {
        for ((position, name) in credit.names.withIndex()) {
            val key = artistKey(name)
            dao.insertArtist(ArtistEntity(UUID.randomUUID().toString(), name, key))
            val artist = requireNotNull(dao.artist(key))
            dao.credit(
                ArtistCreditEntity(trackId, artist.id, role.name, credit.source.name, position)
            )
        }
    }
}
