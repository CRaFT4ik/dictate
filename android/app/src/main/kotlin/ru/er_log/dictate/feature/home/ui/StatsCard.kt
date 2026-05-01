package ru.er_log.dictate.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.er_log.dictate.R
import ru.er_log.dictate.feature.stats.domain.Period

/**
 * Displays a stats card with a period selector and the word count for the selected period.
 *
 * @param selectedPeriod The currently active aggregation period.
 * @param wordsForPeriod Number of words recognised in [selectedPeriod].
 * @param onPeriodChange Called when the user selects a different period tab.
 * @param modifier Optional [Modifier] for the outer [Card].
 */
@Composable
public fun StatsCard(
    selectedPeriod: Period,
    wordsForPeriod: Long,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = listOf(Period.Week, Period.Month, Period.Year)
    val labels = listOf(
        stringResource(R.string.home_stats_week),
        stringResource(R.string.home_stats_month),
        stringResource(R.string.home_stats_year),
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = period == selectedPeriod,
                        onClick = { onPeriodChange(period) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = periods.size,
                        ),
                    ) {
                        Text(text = labels[index])
                    }
                }
            }

            Text(
                text = wordsForPeriod.toString(),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(top = 16.dp),
            )

            val periodLabel = when (selectedPeriod) {
                Period.Week -> stringResource(R.string.home_stats_week)
                Period.Month -> stringResource(R.string.home_stats_month)
                Period.Year -> stringResource(R.string.home_stats_year)
            }
            Text(
                text = "${stringResource(R.string.home_words_label)} · $periodLabel",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
