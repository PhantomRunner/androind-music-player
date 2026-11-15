package dev.violet.music.feature.selection

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.violet.music.core.model.SelectionSource

@Composable
internal fun SelectionSourceEffect(
    viewModel: SelectionViewModel,
    source: SelectionSource,
    startSelecting: Boolean = false,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    LaunchedEffect(lifecycle, viewModel, source, startSelecting) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.source(source)
            if (startSelecting) viewModel.begin()
        }
    }
    DisposableEffect(viewModel, source.context) {
        onDispose {
            if (activity?.isChangingConfigurations != true) viewModel.leave(source.context)
        }
    }
}
