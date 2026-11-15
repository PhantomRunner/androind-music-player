package dev.violet.music.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.violet.music.R
import dev.violet.music.core.model.HistoryPeriod

fun HistoryPeriod.label() =
    when (this) {
        HistoryPeriod.WEEK -> R.string.period_week
        HistoryPeriod.MONTH -> R.string.period_month
        HistoryPeriod.QUARTER -> R.string.period_quarter
        HistoryPeriod.ALL -> R.string.period_all
    }

@Composable
fun PeriodSelector(selected: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryPeriod.entries.forEach { value ->
            FilterChip(
                selected == value,
                { onSelect(value) },
                label = { Text(stringResource(value.label())) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
