package dev.violet.music.feature.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.core.model.Track

/** Transient dialog state only. Persistent collection state always comes from Room. */
class CollectionDialogsState {
    var menuTrack by mutableStateOf<Track?>(null)
    var addingTrack by mutableStateOf<Track?>(null)
    var creating by mutableStateOf(false)
}

@Composable
fun CollectionDialogs(state: CollectionDialogsState, viewModel: CollectionViewModel, onCreated: (String) -> Unit) {
    state.menuTrack?.let { track ->
        TrackActionSheet(
            track, onDismiss = { state.menuTrack = null },
            onFavourite = { viewModel.favourite(track.id, !track.isFavourite); state.menuTrack = null },
            onAdd = { state.addingTrack = track; state.menuTrack = null },
        )
    }
    if (state.addingTrack != null && !state.creating) {
        val playlists = viewModel.playlists.collectAsLazyPagingItems()
        PlaylistPicker(
            playlists, onDismiss = { state.addingTrack = null },
            onSelect = { id -> state.addingTrack?.let { viewModel.add(id, it.id) }; state.addingTrack = null },
            onCreate = { state.creating = true },
        )
    }
    if (state.creating) PlaylistNameDialog(
        onDismiss = { state.creating = false; state.addingTrack = null },
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
