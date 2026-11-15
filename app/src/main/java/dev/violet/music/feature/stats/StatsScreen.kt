package dev.violet.music.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.model.ActivityBucket
import dev.violet.music.core.model.RankedListening
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPeriod: (dev.violet.music.core.model.HistoryPeriod) -> Unit,
    onPage: (Int) -> Unit,
    onTrack: (String) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String, String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(VioletLayout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("title") { SectionTitle(stringResource(R.string.listening_stats)) }
        item("period") { PeriodSelector(state.period, onPeriod) }
        when {
            state.loading -> item { LoadingState() }
            state.error ->
                item {
                    MessageCard(
                        stringResource(R.string.listening_stats),
                        stringResource(R.string.load_error),
                    )
                }
            else -> {
                item("totals") {
                    MessageCard(
                        stringResource(R.string.total_plays, state.stats.totals.plays),
                        stringResource(
                            R.string.total_listening,
                            formatDuration(state.stats.totals.listenedMs),
                        ),
                    )
                }
                if (state.stats.totals.listenedMs == 0L && state.stats.totals.plays == 0L)
                    item {
                        MessageCard(
                            stringResource(R.string.history_empty),
                            stringResource(R.string.history_empty_description),
                        )
                    }
                item("daily-heading") { SectionTitle(stringResource(R.string.daily_activity)) }
                item("daily-chart") { ActivityChart(state.stats.days) }
                items(state.stats.days, key = { "day:${it.start}" }) { bucket ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bucket.label)
                        Text(formatDuration(bucket.listenedMs))
                    }
                }
                item("day-pages") {
                    Row(horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing)) {
                        TextButton(
                            onClick = { onPage(state.page + 1) },
                            enabled = state.stats.hasOlderDays,
                        ) {
                            Text(stringResource(R.string.older))
                        }
                        TextButton(onClick = { onPage(state.page - 1) }, enabled = state.page > 0) {
                            Text(stringResource(R.string.newer))
                        }
                    }
                }
                item("overview-heading") {
                    SectionTitle(stringResource(R.string.listening_over_time))
                }
                item("overview") { ActivityChart(state.stats.overview) }
                items(state.stats.overview, key = { "overview:${it.start}" }) { bucket ->
                    Text(
                        "${bucket.label} · ${formatDuration(bucket.listenedMs)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item("tracks-heading") { SectionTitle(stringResource(R.string.most_played_tracks)) }
                items(state.stats.tracks, key = { "track:${it.id}" }) {
                    RankedRow(it) { onTrack(it.id) }
                }
                item("artists-heading") {
                    SectionTitle(stringResource(R.string.top_artists))
                    Text(
                        stringResource(R.string.artist_stats_attribution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.stats.artists, key = { "artist:${it.id}" }) {
                    RankedRow(it) { onArtist(it.id) }
                }
                item("albums-heading") { SectionTitle(stringResource(R.string.top_albums)) }
                items(state.stats.albums, key = { "album:${it.id}" }) {
                    RankedRow(it) { onAlbum(it.id, it.name) }
                }
            }
        }
    }
}

@Composable
private fun RankedRow(item: RankedListening, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.ranked_listening,
                    item.plays,
                    formatDuration(item.listenedMs),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityChart(values: List<ActivityBucket>) {
    val color = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.activity_chart)
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = label }) {
        val maximum = (values.maxOfOrNull { it.listenedMs } ?: 0).coerceAtLeast(1).toFloat()
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val height = size.height * (value.listenedMs / maximum)
            drawRect(
                color,
                Offset(index * slot + slot * 0.15f, size.height - height),
                Size(slot * 0.7f, height),
            )
        }
    }
}
