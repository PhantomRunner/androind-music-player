package dev.violet.music.feature.collection

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.core.model.Track
import dev.violet.music.core.model.TrackFilter
import dev.violet.music.feature.player.PlayerViewModel

/** Presentation state only; catalogue, favourites and credits are observed live. */
data class PlaylistMenuContext(val id: String, val entryId: String)

class CollectionDialogsState {
    var menuTrack by mutableStateOf<Track?>(null)
    var addingTrack by mutableStateOf<Track?>(null)
    var creating by mutableStateOf(false)
    var infoTrackId by mutableStateOf<String?>(null)
    var artistTrackId by mutableStateOf<String?>(null)
    var menuFilter: TrackFilter? = null
    var menuOccurrence: String? = null
    var menuPlaylist: PlaylistMenuContext? = null
}

@Composable
fun CollectionDialogs(
    state: CollectionDialogsState,
    viewModel: CollectionViewModel,
    player: PlayerViewModel,
    onArtist: (String) -> Unit,
    onAlbum: (String, String) -> Unit,
    onCreated: (String) -> Unit,
) {
    state.menuTrack?.let { snapshot ->
        val live by
            remember(snapshot.id) { viewModel.track(snapshot.id) }
                .collectAsStateWithLifecycle(snapshot)
        live?.let { track ->
            fun close(action: () -> Unit) {
                state.menuTrack = null
                action()
            }
            TrackActionSheet(
                track,
                { state.menuTrack = null },
                onFavourite = { close { viewModel.favourite(track.id, !track.isFavourite) } },
                onAdd = { close { state.addingTrack = track } },
                onPlay = {
                    close {
                        val occurrence = state.menuOccurrence
                        val playlist = state.menuPlaylist
                        if (playlist != null) player.playPlaylist(playlist.id, playlist.entryId)
                        else if (occurrence != null) player.controller.playOccurrence(occurrence)
                        else player.play(track.id, state.menuFilter ?: TrackFilter())
                    }
                },
                onNext = { close { player.enqueue(listOf(track.id), true) } },
                onQueue = { close { player.enqueue(listOf(track.id)) } },
                onArtist = { close { state.artistTrackId = track.id } },
                onAlbum = { close { onAlbum(track.albumKey, track.albumTitle) } },
                onInfo = { close { state.infoTrackId = track.id } },
            )
        }
    }
    state.artistTrackId?.let { id ->
        val credits by
            remember(id) { viewModel.credits(id) }.collectAsStateWithLifecycle(emptyList())
        ArtistChoiceDialog(credits.filter { it.role == "TRACK" }, { state.artistTrackId = null }) {
            artist ->
            state.artistTrackId = null
            onArtist(artist)
        }
    }
    state.infoTrackId?.let { id ->
        TrackInformationDialog(id, viewModel) { state.infoTrackId = null }
    }
    if (state.addingTrack != null && !state.creating) {
        val playlists = viewModel.playlists.collectAsLazyPagingItems()
        PlaylistPicker(
            playlists,
            { state.addingTrack = null },
            onSelect = { id ->
                state.addingTrack?.let { viewModel.add(id, it.id) }
                state.addingTrack = null
            },
            onCreate = { state.creating = true },
        )
    }
    if (state.creating)
        PlaylistNameDialog(
            onDismiss = {
                state.creating = false
                state.addingTrack = null
            },
            onSave = { name ->
                val track = state.addingTrack
                state.creating = false
                state.addingTrack = null
                viewModel.create(name) { id ->
                    if (track != null) viewModel.add(id, track.id)
                    onCreated(id)
                }
            },
        )
}
