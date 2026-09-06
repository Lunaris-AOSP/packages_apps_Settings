/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.internal.os.PowerInsightAppUsage
import com.android.internal.os.PowerInsightHistoryBucket
import com.android.settings.R
import kotlin.math.roundToInt

private val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeScreen(
    state: PowerInsightUiState,
    onOpenHealth: () -> Unit,
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.stats
    val charging = stats.isCharging

    var showCharging by remember { mutableStateOf(charging) }
    LaunchedEffect(charging) { showCharging = charging }

    val visibleSamples = remember(state.flow, showCharging) {
        state.flow.filter { it.isCharging == showCharging }
    }
    val live = state.serviceAvailable && state.monitoringEnabled

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "chart") {
            Surface(
                shape = PowerInsightShapes.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = showCharging,
                            onClick = { showCharging = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text(stringResource(R.string.power_insight_mode_charging)) },
                        )
                        SegmentedButton(
                            selected = !showCharging,
                            onClick = { showCharging = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = {
                                Text(stringResource(R.string.power_insight_mode_discharging))
                            },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    PowerFlowChart(
                        samples = visibleSamples,
                        charging = showCharging,
                        currentNow = stats.currentNow,
                        powerWatts = stats.powerWatts,
                        voltage = stats.voltage,
                        avgCurrent = stats.avgCurrent,
                        minCurrent = stats.minCurrent,
                        maxCurrent = stats.maxCurrent,
                        live = live && showCharging == charging,
                    )
                }
            }
        }

        item(key = "summary") {
            TileRow {
                MetricTile(
                    label = stringResource(R.string.power_insight_battery_health),
                    value = formatPercent(stats.healthPercent),
                    subValue = stringResource(R.string.power_insight_cycles, stats.cycleCount),
                    icon = Icons.Default.Favorite,
                    iconTint = accents.health,
                    valueColor = accents.health,
                    onClick = onOpenHealth,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.power_insight_session),
                    value = stringResource(R.string.power_insight_session_value),
                    subValue = stringResource(R.string.power_insight_session_sub),
                    icon = Icons.Default.Assessment,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenSession,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "drain") {
            TileRow {
                MetricTile(
                    label = stringResource(R.string.power_insight_active_drain),
                    value = formatRate(stats.activeDrainRate),
                    subValue = stringResource(R.string.power_insight_screen_on_lower),
                    icon = Icons.Default.FlashOn,
                    iconTint = accents.drain,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.power_insight_idle_drain),
                    value = formatRate(stats.idleDrainRate),
                    subValue = stringResource(R.string.power_insight_screen_off_lower),
                    icon = Icons.Default.ModeNight,
                    iconTint = accents.sleep,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "time") {
            TileRow {
                MetricTile(
                    label = stringResource(R.string.power_insight_screen_on),
                    value = formatDuration(stats.screenOnTime),
                    icon = Icons.Default.WbSunny,
                    iconTint = accents.awake,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.power_insight_deep_sleep),
                    value = formatDuration(stats.deepSleepTime),
                    icon = Icons.Default.Bedtime,
                    iconTint = accents.sleep,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
fun HistoryScreen(
    buckets: List<PowerInsightHistoryBucket>,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.power_insight_history_empty_title),
                body = stringResource(R.string.power_insight_history_empty_body),
            )
        }
        return
    }

    val peakDrain = remember(buckets) { buckets.maxOf { it.drainPercent }.coerceAtLeast(1) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "overview") {
            Surface(
                shape = PowerInsightShapes.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.power_insight_drain_by_hour),
                        style = PowerInsightType.sectionTitle,
                    )
                    Spacer(Modifier.height(16.dp))
                    HourlyDrainChart(buckets)
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.height(14.dp)) }

        itemsIndexed(
            items = buckets,
            key = { index, bucket -> "bucket-$index-${bucket.hour}" },
        ) { index, bucket ->
            HistoryRow(
                bucket = bucket,
                peakDrain = peakDrain,
                shape = groupedRowShape(index, buckets.size),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    bucket: PowerInsightHistoryBucket,
    peakDrain: Int,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatHourLabel(bucket.hour),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.power_insight_screen_on_duration,
                        formatDuration(bucket.screenOnMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ShareBar(
                    progress = bucket.drainPercent.toFloat() / peakDrain,
                    color = accents.drain,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(
                    R.string.power_insight_drain_percent,
                    bucket.drainPercent,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = accents.drain,
            )
        }
    }
}

@Composable
fun AppsScreen(
    apps: List<PowerInsightAppUsage>,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.Apps,
                title = stringResource(R.string.power_insight_apps_empty_title),
                body = stringResource(R.string.power_insight_apps_empty_body),
            )
        }
        return
    }

    val totalMah = remember(apps) {
        apps.sumOf { it.consumedPowerMah }.coerceAtLeast(0.1)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.power_insight_since_last_charge),
                    style = PowerInsightType.sectionTitle,
                )
                Text(
                    text = stringResource(
                        R.string.power_insight_apps_summary,
                        apps.size,
                        formatMahPrecise(totalMah),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(
            items = apps,
            key = { index, app -> "app-$index-${app.packageName ?: "unknown"}" },
        ) { index, app ->
            AppRow(
                app = app,
                share = (app.consumedPowerMah / totalMah).toFloat(),
                shape = groupedRowShape(index, apps.size),
            )
        }
    }
}

@Composable
private fun AppRow(
    app: PowerInsightAppUsage,
    share: Float,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val label = app.appLabel
        ?: app.packageName
        ?: stringResource(R.string.power_insight_unknown_app)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = app.packageName, label = label)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatMahPrecise(app.consumedPowerMah),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.power_insight_app_row_detail,
                        (share * 100).roundToInt(),
                        formatDuration(app.foregroundTimeMs),
                        formatDuration(app.backgroundTimeMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                ShareBar(
                    progress = share,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }
        }
    }
}
