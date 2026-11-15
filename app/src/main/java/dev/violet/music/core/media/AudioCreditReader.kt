package dev.violet.music.core.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class EmbeddedCredits(val trackArtists: List<String>, val albumArtists: List<String>)

fun interface AudioCreditReader {
    suspend fun read(uri: String): EmbeddedCredits?
}

@androidx.annotation.OptIn(UnstableApi::class)
class Media3AudioCreditReader
@Inject
constructor(@param:ApplicationContext private val context: Context) : AudioCreditReader {
    override suspend fun read(uri: String): EmbeddedCredits? =
        withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(3_000) {
                    MetadataRetriever.Builder(context, MediaItem.fromUri(uri.toUri()))
                        .build()
                        .use { reader ->
                            val groups = reader.retrieveTrackGroups().await()
                            val artists = linkedSetOf<String>()
                            val albumArtists = linkedSetOf<String>()
                            for (group in 0 until groups.length) for (format in
                                0 until groups[group].length) {
                                val metadata = groups[group].getFormat(format).metadata ?: continue
                                for (index in 0 until metadata.length()) {
                                    when (val entry = metadata[index]) {
                                        is TextInformationFrame ->
                                            when (entry.id) {
                                                "TPE1",
                                                "TP1" -> artists.addAll(entry.values)
                                                "TPE2",
                                                "TP2" -> albumArtists.addAll(entry.values)
                                            }
                                        is VorbisComment ->
                                            when (entry.key.uppercase(java.util.Locale.ROOT)) {
                                                "ARTIST" -> artists.add(entry.value)
                                                "ALBUMARTIST",
                                                "ALBUM ARTIST" -> albumArtists.add(entry.value)
                                            }
                                    }
                                }
                            }
                            EmbeddedCredits(artists.toList(), albumArtists.toList())
                        }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
}
