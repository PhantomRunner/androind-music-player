package dev.violet.music.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.media.ScanStatus
import dev.violet.music.core.ui.MessageCard
import dev.violet.music.core.ui.SectionTitle
import dev.violet.music.core.ui.libraryNotices
import dev.violet.music.core.ui.trackCountLabel
import dev.violet.music.feature.library.LibraryUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    state: LibraryUiState,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPeriod: (dev.violet.music.core.model.HistoryPeriod) -> Unit = {},
    onStats: () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("title") { SectionTitle(stringResource(R.string.settings)) }
        item("history-title") {
            dev.violet.music.core.ui.SectionTitle(stringResource(R.string.history_period))
            Text(stringResource(R.string.history_period_description))
        }
        item("history-period") {
            dev.violet.music.core.ui.PeriodSelector(state.preferences.period, onPeriod)
        }
        item("stats") {
            Button(onClick = onStats) { Text(stringResource(R.string.listening_stats)) }
        }
        libraryNotices(state, onPermission, onSettings, onRefresh)
        item("device") {
            MessageCard(
                stringResource(R.string.indexing_title),
                stringResource(R.string.indexing_description),
            )
        }
        item("access") {
            Text(
                stringResource(
                    if (state.hasPermission) R.string.permission_granted
                    else R.string.permission_denied
                )
            )
        }
        item("scan") {
            Button(
                onClick = onRefresh,
                enabled = state.hasPermission && state.scanStatus !is ScanStatus.Running,
            ) {
                Text(stringResource(R.string.scan_library))
            }
        }
        item("last-scan") {
            Text(
                state.lastScan?.let {
                    stringResource(
                        R.string.last_scan,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(it)),
                    )
                } ?: stringResource(R.string.never_scanned)
            )
        }
        if (state.scanStatus is ScanStatus.Complete)
            item("complete") {
                Text(
                    stringResource(
                        R.string.scan_complete,
                        trackCountLabel(state.scanStatus.processed),
                    )
                )
            }
        if (state.stats.unavailableCount > 0)
            item("unavailable") {
                Text(
                    pluralStringResource(
                        R.plurals.saved_unavailable,
                        state.stats.unavailableCount,
                        state.stats.unavailableCount,
                    )
                )
            }
        item("theme") {
            MessageCard(
                stringResource(R.string.theme_title),
                stringResource(R.string.theme_description),
            )
        }
        item("privacy") {
            MessageCard(
                stringResource(R.string.privacy_title),
                stringResource(R.string.privacy_description),
            )
        }
        item("about") {
            MessageCard(
                stringResource(R.string.about_title),
                stringResource(R.string.about_description),
            )
        }
    }
}
