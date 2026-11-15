package dev.violet.music.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.violet.music.R
import dev.violet.music.core.model.Track
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.VioletIcons
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.pagedTracks
import dev.violet.music.feature.library.LibraryUiState
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchScreen(
    state: LibraryUiState,
    results: Flow<PagingData<Track>>,
    onQuery: (String) -> Unit,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
    onMenu: (Track) -> Unit,
    selection: dev.violet.music.feature.selection.SelectionViewModel? = null,
    collections: dev.violet.music.feature.collection.CollectionViewModel? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val tracks = results.collectAsLazyPagingItems()
    val selectionState =
        selection?.uiState?.collectAsStateWithLifecycle()?.value
            ?: dev.violet.music.feature.selection.SelectionUiState()
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("title") { SectionTitle(stringResource(R.string.search)) }
        item("query") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(VioletIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty())
                        IconButton(onClick = { onQuery("") }) {
                            Icon(VioletIcons.Close, stringResource(R.string.clear_search))
                        }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            focus.clearFocus()
                        }
                    ),
            )
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        if (selection != null && collections != null && state.query.isNotBlank())
            item("selection") {
                dev.violet.music.feature.selection.SelectionToolbar(
                    selection,
                    dev.violet.music.core.model.SelectionSource(
                        "search",
                        dev.violet.music.core.model.TrackFilter(query = state.query),
                    ),
                    collections,
                )
            }
        if (state.query.isBlank())
            item("intro") {
                MessageCard(
                    stringResource(R.string.search_intro),
                    stringResource(R.string.search_description),
                )
            }
        else
            pagedTracks(
                tracks,
                emptySearch = true,
                onMenu = onMenu,
                selected = selectionState.selected.keys,
                selecting = selectionState.active,
                onToggle =
                    selection?.let { vm ->
                        { track ->
                            vm.toggle(
                                dev.violet.music.core.model.SelectedTrack(
                                    track.id,
                                    track.id,
                                    track.availability == "LOCAL",
                                )
                            )
                        }
                    },
                onPlay = onPlay,
            )
    }
}
