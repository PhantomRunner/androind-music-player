package dev.violet.music.feature.player

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.*
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    state: PlaybackUiState,
    controller: PlaybackController,
    onMenu: (QueueItem) -> Unit = {},
) {
    val scroll = rememberLazyListState()
    val latest by rememberUpdatedState(state)
    var dragging by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val edgeSize = with(density) { 64.dp.toPx() }
    val scrollStep = with(density) { 8.dp.toPx() }
    val ghostHeight = with(density) { 40.dp.toPx() }
    val up = stringResource(R.string.move_up)
    val down = stringResource(R.string.move_down)
    val currentLabel = stringResource(R.string.currently_playing)
    val dragStateLabel = stringResource(R.string.reordering_queue)
    fun updateTarget() {
        scroll.layoutInfo.visibleItemsInfo
            .firstOrNull {
                pointerY >= it.offset &&
                    pointerY < it.offset + it.size &&
                    latest.queue.any { item -> item.occurrenceId == it.key }
            }
            ?.let { target = it.key as? String }
    }
    fun resetDrag() {
        dragging = null
        target = null
        edge = 0
    }
    LaunchedEffect(dragging, edge) {
        while (isActive && dragging != null && edge != 0) {
            scroll.scrollBy(edge * scrollStep)
            updateTarget()
            delay(16)
        }
    }
    LaunchedEffect(state.queue, dragging) {
        if (dragging != null && state.queue.none { it.occurrenceId == dragging }) resetDrag()
    }
    Box(
        Modifier.fillMaxSize().semantics { if (dragging != null) stateDescription = dragStateLabel }
    ) {
        // The gesture lives on the container so scrolling cannot dispose its source row.
        LazyColumn(
            state = scroll,
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            val source =
                                scroll.layoutInfo.visibleItemsInfo.firstOrNull {
                                    position.y >= it.offset &&
                                        position.y < it.offset + it.size &&
                                        latest.queue.any { item -> item.occurrenceId == it.key }
                                }
                            dragging = source?.key as? String
                            target = dragging
                            pointerY = position.y
                        },
                        onDragCancel = { resetDrag() },
                        onDragEnd = {
                            val source = dragging
                            val destination = target
                            if (
                                source != null &&
                                    destination != null &&
                                    source != destination &&
                                    latest.queue.any { it.occurrenceId == source } &&
                                    latest.queue.any { it.occurrenceId == destination }
                            )
                                controller.moveOccurrence(source, destination)
                            resetDrag()
                        },
                        onDrag = { change, amount ->
                            if (dragging != null) {
                                change.consume()
                                pointerY += amount.y
                                updateTarget()
                                edge =
                                    when {
                                        pointerY > scroll.layoutInfo.viewportEndOffset - edgeSize ->
                                            1
                                        pointerY <
                                            scroll.layoutInfo.viewportStartOffset + edgeSize -> -1
                                        else -> 0
                                    }
                            }
                        },
                    )
                },
            contentPadding = PaddingValues(VioletLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
        ) {
            item("title") { SectionTitle(stringResource(R.string.queue)) }
            if (state.shuffle)
                item("shuffle") {
                    Text(
                        stringResource(R.string.queue_shuffle_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (state.queue.isEmpty())
                item("empty") {
                    MessageCard(
                        stringResource(R.string.nothing_playing),
                        stringResource(R.string.choose_track),
                    )
                }
            itemsIndexed(state.queue, key = { _, item -> item.occurrenceId }) { index, item ->
                val current = index == state.currentIndex
                Surface(
                    onClick = { controller.playOccurrence(item.occurrenceId) },
                    color =
                        if (current || (target == item.occurrenceId && dragging != null))
                            MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier =
                        Modifier.graphicsLayer {
                                alpha = if (dragging == item.occurrenceId) 0.25f else 1f
                            }
                            .semantics {
                                if (current) stateDescription = currentLabel
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(up) {
                                            latest.queue
                                                .getOrNull(
                                                    latest.queue.indexOfFirst {
                                                        it.occurrenceId == item.occurrenceId
                                                    } - 1
                                                )
                                                ?.let {
                                                    controller.moveOccurrence(
                                                        item.occurrenceId,
                                                        it.occurrenceId,
                                                    )
                                                }
                                            true
                                        },
                                        CustomAccessibilityAction(down) {
                                            latest.queue
                                                .getOrNull(
                                                    latest.queue.indexOfFirst {
                                                        it.occurrenceId == item.occurrenceId
                                                    } + 1
                                                )
                                                ?.let {
                                                    controller.moveOccurrence(
                                                        item.occurrenceId,
                                                        it.occurrenceId,
                                                    )
                                                }
                                            true
                                        },
                                    )
                            },
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Artwork(item.artworkUri, 48.dp, revision = item.artworkVersion)
                            QueueLabel(item, Modifier.weight(1f))
                            IconButton(onClick = { onMenu(item) }) {
                                Icon(
                                    PlaybackIcons.More,
                                    stringResource(R.string.track_options, item.title),
                                )
                            }
                            val dragLabel = stringResource(R.string.drag_queue, item.title)
                            Box(
                                Modifier.size(VioletLayout.touchTarget).semantics {
                                    contentDescription = dragLabel
                                },
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Icon(PlaybackIcons.Queue, null)
                            }
                        }
                        if (current) Text(currentLabel, color = MaterialTheme.colorScheme.primary)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    controller.moveOccurrence(
                                        item.occurrenceId,
                                        state.queue[index - 1].occurrenceId,
                                    )
                                },
                                enabled = index > 0 && state.connected,
                            ) {
                                Text(up)
                            }
                            TextButton(
                                onClick = {
                                    controller.moveOccurrence(
                                        item.occurrenceId,
                                        state.queue[index + 1].occurrenceId,
                                    )
                                },
                                enabled = index < state.queue.lastIndex && state.connected,
                            ) {
                                Text(down)
                            }
                            IconButton(
                                onClick = { controller.removeOccurrence(item.occurrenceId) },
                                enabled = state.connected,
                            ) {
                                Icon(VioletIcons.Close, stringResource(R.string.remove_from_queue))
                            }
                        }
                    }
                }
            }
        }
        dragging?.let { id ->
            latest.queue
                .firstOrNull { it.occurrenceId == id }
                ?.let { item ->
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = VioletLayout.contentPadding)
                                .graphicsLayer { translationY = pointerY - ghostHeight },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(VioletLayout.itemSpacing),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Artwork(item.artworkUri, 48.dp, revision = item.artworkVersion)
                            QueueLabel(item, Modifier.weight(1f))
                        }
                    }
                }
        }
    }
}

@Composable
private fun QueueLabel(item: QueueItem, modifier: Modifier) {
    Column(modifier) {
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            item.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
