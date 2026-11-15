package dev.violet.music.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
) {
    val tracks = results.collectAsLazyPagingItems()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("title") { SectionTitle(stringResource(R.string.search)) }
        item("query") {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                leadingIcon = { Icon(VioletIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(VioletIcons.Close, stringResource(R.string.clear_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        if (state.query.isBlank()) item("intro") {
            MessageCard(stringResource(R.string.search_intro), stringResource(R.string.search_description))
        } else pagedTracks(tracks, emptySearch = true)
    }
}
