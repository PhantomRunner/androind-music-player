package dev.violet.music.feature.player

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.playback.PlaybackController
import dev.violet.music.core.playback.PlaybackUiState
import dev.violet.music.core.playback.QueueItem
import dev.violet.music.core.ui.*
import dev.violet.music.core.ui.theme.VioletLayout
import kotlinx.coroutines.isActive

@Composable
fun QueueScreen(
    state: PlaybackUiState,
    controller: PlaybackController,
    onBack: () -> Unit,
    onMenu: (QueueItem) -> Unit,
) {
    val scroll = rememberLazyListState()
    val latest by rememberUpdatedState(state)
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val edgeSize = with(density) { 64.dp.toPx() }
    val scrollStep = with(density) { 8.dp.toPx() }
    val ghostOffset = with(density) { 28.dp.toPx() }
    val dragDescription = stringResource(R.string.reordering_queue)
    val currentIndex = state.currentIndex.takeIf { it in state.queue.indices } ?: -1

    fun itemAt(y: Float): String? {
        val layout = scroll.layoutInfo
        val offset = y + layout.viewportStartOffset
        val key = layout.visibleItemsInfo.firstOrNull { offset >= it.offset && offset < it.offset + it.size }
            ?.key as? String
        return key?.takeIf { id -> latest.queue.any { it.occurrenceId == id } }
    }

    fun resetDrag() {
        dragging = null
        target = null
        edge = 0
    }

    LaunchedEffect(dragging, edge) {
        while (isActive && dragging != null && edge != 0) {
            withFrameNanos { }
            if (scroll.scrollBy(edge * scrollStep) == 0f) break
            itemAt(pointerY)?.let { target = it }
        }
    }
    LaunchedEffect(state.queue, state.connected) {
        val dragId = dragging
        if (dragId != null && (!state.connected || state.queue.none { it.occurrenceId == dragId })) {
            resetDrag()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = VioletLayout.contentPadding)) {
            PlayerHeader(stringResource(R.string.queue), onBack, close = true)
        }
        Box(
            Modifier.weight(1f).semantics {
                if (dragging != null) stateDescription = dragDescription
            }
        ) {
            LazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            val source = if (latest.connected) itemAt(position.y) else null
                            dragging = source
                            target = source
                            pointerY = position.y
                        },
                        onDragCancel = ::resetDrag,
                        onDragEnd = {
                            val source = dragging
                            val destination = target
                            if (
                                source != null && destination != null && source != destination &&
                                latest.queue.any { it.occurrenceId == source } &&
                                latest.queue.any { it.occurrenceId == destination }
                            ) {
                                controller.moveOccurrence(source, destination)
                            }
                            resetDrag()
                        },
                        onDrag = { change, amount ->
                            if (dragging != null) {
                                change.consume()
                                pointerY += amount.y
                                itemAt(pointerY)?.let { target = it }
                                edge = when {
                                    pointerY < edgeSize -> -1
                                    pointerY > size.height - edgeSize -> 1
                                    else -> 0
                                }
                            }
                        },
                    )
                },
                contentPadding = PaddingValues(horizontal = VioletLayout.contentPadding, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (state.shuffle) {
                    item("shuffle") {
                        Text(
                            stringResource(R.string.queue_shuffle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.queue.isEmpty()) {
                    item("empty") {
                        MessageCard(stringResource(R.string.nothing_playing), stringResource(R.string.choose_track))
                    }
                }
                if (currentIndex > 0) {
                    item("earlier-toggle") {
                        val label = if (showEarlier) R.string.queue_hide_earlier else R.string.queue_show_earlier
                        TextButton(onClick = { showEarlier = !showEarlier }) {
                            Text(stringResource(label, currentIndex))
                        }
                    }
                    if (showEarlier) {
                        queueRows(0 until currentIndex, state, dragging, target, controller, onMenu)
                    }
                }
                if (currentIndex >= 0) {
                    item("current-title") { SectionTitle(stringResource(R.string.currently_playing)) }
                    queueRows(currentIndex..currentIndex, state, dragging, target, controller, onMenu)
                }
                val nextStart = currentIndex + 1
                if (nextStart < state.queue.size) {
                    item("next-title") { SectionTitle(stringResource(R.string.queue_up_next)) }
                    queueRows(nextStart until state.queue.size, state, dragging, target, controller, onMenu)
                } else if (currentIndex >= 0) {
                    item("next-empty") {
                        Text(
                            stringResource(R.string.queue_no_upcoming),
                            Modifier.padding(vertical = 14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            dragging?.let { id ->
                latest.queue.firstOrNull { it.occurrenceId == id }?.let { item ->
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = VioletLayout.contentPadding)
                            .graphicsLayer { translationY = pointerY - ghostOffset }
                            .clearAndSetSemantics { },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Artwork(item.artworkUri, 42.dp, revision = item.artworkVersion)
                            QueueTrackLabel(item, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (state.current != null) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    TransportControls(state, controller, compact = true)
                }
            }
        }
    }
}

private fun LazyListScope.queueRows(
    indices: IntRange,
    state: PlaybackUiState,
    dragging: String?,
    target: String?,
    controller: PlaybackController,
    onMenu: (QueueItem) -> Unit,
) {
    items(indices.last - indices.first + 1, key = { state.queue[indices.first + it].occurrenceId }) { offset ->
        val index = indices.first + offset
        val item = state.queue[index]
        val id = item.occurrenceId
        val actions = buildList<Pair<Int, () -> Unit>> {
            if (state.connected) {
                state.queue.getOrNull(index - 1)?.let { before ->
                    add(R.string.move_up to { controller.moveOccurrence(id, before.occurrenceId) })
                }
                state.queue.getOrNull(index + 1)?.let { after ->
                    add(R.string.move_down to { controller.moveOccurrence(id, after.occurrenceId) })
                }
                add(R.string.remove_from_queue to { controller.removeOccurrence(id) })
            }
        }
        QueueTrackRow(
            item,
            current = index == state.currentIndex,
            highlighted = dragging != null && id == target,
            dragging = id == dragging,
            onPlay = { controller.playOccurrence(id) },
            enabled = state.connected,
            onMenu = { onMenu(item) },
            actions = actions,
        )
    }
}

@Composable
private fun QueueTrackRow(
    item: QueueItem,
    current: Boolean,
    highlighted: Boolean,
    dragging: Boolean,
    onPlay: () -> Unit,
    enabled: Boolean,
    onMenu: () -> Unit,
    actions: List<Pair<Int, () -> Unit>>,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val currentLabel = stringResource(R.string.currently_playing)
    val dragLabel = stringResource(R.string.drag_queue, item.title)
    val accessibilityActions = actions.map { (label, run) ->
        CustomAccessibilityAction(stringResource(label)) {
            run()
            true
        }
    }
    Surface(
        onClick = onPlay,
        enabled = enabled,
        modifier = Modifier.graphicsLayer { alpha = if (dragging) 0.36f else 1f }.semantics {
            if (current) stateDescription = currentLabel
            customActions = accessibilityActions
        },
        shape = MaterialTheme.shapes.medium,
        color = if (current || highlighted) colors.surfaceVariant else colors.background,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(item.artworkUri, 42.dp, revision = item.artworkVersion)
            QueueTrackLabel(item, Modifier.weight(1f), current)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(PlaybackIcons.More, stringResource(R.string.track_options, item.title))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.queue_track_actions)) },
                        onClick = {
                            menu = false
                            onMenu()
                        },
                    )
                    actions.forEach { (label, run) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menu = false
                                run()
                            },
                        )
                    }
                }
            }
            Box(
                Modifier.size(VioletLayout.touchTarget).semantics { contentDescription = dragLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VioletIcons.List, null, tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueTrackLabel(item: QueueItem, modifier: Modifier, current: Boolean = false) {
    Column(modifier) {
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = if (current) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
        Text(
            item.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
