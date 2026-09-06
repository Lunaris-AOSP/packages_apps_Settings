/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.os.PowerInsightStats
import com.android.settings.R
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsScreen(
    stats: PowerInsightStats,
    onBack: () -> Unit,
) {
    val charging = stats.isCharging
    var showCharging by remember { mutableStateOf(charging) }
    LaunchedEffect(charging) { showCharging = charging }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.power_insight_session_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.power_insight_navigate_up,
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "switch") {
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
            }

            if (showCharging) {
                chargingSession(stats)
            } else {
                dischargingSession(stats)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.chargingSession(
    stats: PowerInsightStats,
) {
    if (stats.chargingStartTime > 0L) {
        item(key = "window") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                InfoChip(
                    text = stringResource(
                        R.string.power_insight_session_window,
                        formatDateTime(stats.chargingStartTime),
                        if (stats.isCharging) {
                            stringResource(R.string.power_insight_ongoing)
                        } else {
                            formatDateTime(stats.chargingEndTime)
                        },
                    ),
                )
            }
        }
    }

    sessionSection(
        key = "total",
        titleRes = R.string.power_insight_total_time,
        subtitle = formatDurationPrecise(stats.chargingDurationTime),
        icon = Icons.Default.AccessTime,
        chargedPercent = stats.chargingLevelCharged.toFloat(),
        chargedMah = stats.chargingMahCharged,
        rate = stats.chargingRatePercentPerHour,
        totalCapacity = stats.totalCapacity,
    )

    sessionSection(
        key = "screenOn",
        titleRes = R.string.power_insight_screen_on,
        subtitle = formatDurationPrecise(stats.chargingScreenOnTime),
        icon = Icons.Default.Visibility,
        chargedPercent = stats.chargingScreenOnLevelCharged.toFloat(),
        chargedMah = stats.chargingScreenOnMahCharged,
        rate = stats.chargingScreenOnRatePercentPerHour,
        totalCapacity = stats.totalCapacity,
    )

    sessionSection(
        key = "screenOff",
        titleRes = R.string.power_insight_screen_off,
        subtitle = formatDurationPrecise(stats.chargingScreenOffTime),
        icon = Icons.Default.VisibilityOff,
        chargedPercent = stats.chargingScreenOffLevelCharged.toFloat(),
        chargedMah = stats.chargingScreenOffMahCharged,
        rate = stats.chargingScreenOffRatePercentPerHour,
        totalCapacity = stats.totalCapacity,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.sessionSection(
    key: String,
    @StringRes titleRes: Int,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    chargedPercent: Float,
    chargedMah: Int,
    rate: Float,
    totalCapacity: Int,
) {
    item(key = key) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeaderThemed(
                title = stringResource(titleRes),
                subtitle = subtitle,
                icon = icon,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChargeTile(
                    percent = chargedPercent,
                    mah = chargedMah,
                    modifier = Modifier.weight(1f),
                )
                RateTile(
                    rate = rate,
                    totalCapacity = totalCapacity,
                    charging = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderThemed(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    SectionHeader(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChargeTile(percent: Float, mah: Int, modifier: Modifier = Modifier) {
    MetricTile(
        label = stringResource(R.string.power_insight_charged),
        value = formatPercent(percent),
        subValue = formatMah(mah),
        icon = Icons.Default.BatteryChargingFull,
        iconTint = accents.charge,
        valueColor = accents.charge,
        modifier = modifier,
    )
}

@Composable
private fun RateTile(
    rate: Float,
    totalCapacity: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (charging) accents.charge else accents.drain
    MetricTile(
        label = stringResource(
            if (charging) {
                R.string.power_insight_charging_rate
            } else {
                R.string.power_insight_discharge_rate
            },
        ),
        value = formatRate(rate),
        subValue = stringResource(
            R.string.power_insight_approx,
            formatMilliamps((rate * totalCapacity / 100f).roundToInt()),
        ),
        icon = Icons.Default.Speed,
        iconTint = tint,
        modifier = modifier,
    )
}

@Immutable
private data class DischargeBreakdown(
    val totalTime: Long,
    val totalUsedPercent: Int,
    val totalUsedMah: Int,
    val totalRate: Float,
    val totalMilliamps: Int,
    val activeRate: Float,
    val activeMilliamps: Int,
    val idleRate: Float,
    val idleMilliamps: Int,
    val deepSleepMah: Int,
    val deepSleepPercent: Float,
    val awakeTime: Long,
    val awakeMah: Int,
    val awakePercent: Float,
)

private const val DEEP_SLEEP_WEIGHT = 1.0f
private const val AWAKE_WEIGHT = 5.0f
private const val MIN_HOURS = 0.01f

private fun dischargeBreakdown(stats: PowerInsightStats): DischargeBreakdown {
    val capacity = stats.totalCapacity
    val totalTime = stats.screenOnTime + stats.screenOffTime
    val totalUsedPercent = stats.batteryDrainScreenOn + stats.batteryDrainScreenOff

    val totalHours = totalTime / 3_600_000f
    val totalRate = if (totalHours > MIN_HOURS) totalUsedPercent / totalHours else 0f

    val screenOnHours = stats.screenOnTime / 3_600_000f
    val activeRate =
        if (screenOnHours > MIN_HOURS) stats.batteryDrainScreenOn / screenOnHours else 0f

    val screenOffHours = stats.screenOffTime / 3_600_000f
    val idleRate =
        if (screenOffHours > MIN_HOURS) stats.batteryDrainScreenOff / screenOffHours else 0f

    val screenOffSeconds = stats.screenOffTime / 1000f
    val deepSleepSeconds = stats.deepSleepTime / 1000f
    val awakeSeconds = max(0f, screenOffSeconds - deepSleepSeconds)

    val factor = (deepSleepSeconds * DEEP_SLEEP_WEIGHT) + (awakeSeconds * AWAKE_WEIGHT)
    val screenOffMah = stats.batteryDrainScreenOff * capacity / 100f
    val deepSleepMah = if (factor > 0f) {
        (deepSleepSeconds * DEEP_SLEEP_WEIGHT / factor) * screenOffMah
    } else {
        0f
    }
    val awakeMah = max(0f, screenOffMah - deepSleepMah)

    fun asPercent(mah: Float) = if (capacity > 0) mah * 100f / capacity else 0f

    return DischargeBreakdown(
        totalTime = totalTime,
        totalUsedPercent = totalUsedPercent,
        totalUsedMah = (totalUsedPercent * capacity / 100f).roundToInt(),
        totalRate = totalRate,
        totalMilliamps = (totalRate * capacity / 100f).roundToInt(),
        activeRate = activeRate,
        activeMilliamps = (activeRate * capacity / 100f).roundToInt(),
        idleRate = idleRate,
        idleMilliamps = (idleRate * capacity / 100f).roundToInt(),
        deepSleepMah = deepSleepMah.roundToInt(),
        deepSleepPercent = asPercent(deepSleepMah),
        awakeTime = max(0L, stats.screenOffTime - stats.deepSleepTime),
        awakeMah = awakeMah.roundToInt(),
        awakePercent = asPercent(awakeMah),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.dischargingSession(
    stats: PowerInsightStats,
) {
    item(key = "dischargeTotal") {
        val b = remember(stats) { dischargeBreakdown(stats) }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            DischargeSection(
                title = stringResource(R.string.power_insight_total_time),
                subtitle = formatDurationPrecise(b.totalTime),
                icon = Icons.Default.AccessTime,
                iconTint = MaterialTheme.colorScheme.primary,
                usedPercent = b.totalUsedPercent.toFloat(),
                usedMah = b.totalUsedMah,
                rate = b.totalRate,
                milliamps = b.totalMilliamps,
            )
            DischargeSection(
                title = stringResource(R.string.power_insight_screen_on),
                subtitle = formatDurationPrecise(stats.screenOnTime),
                icon = Icons.Default.Visibility,
                iconTint = accents.awake,
                usedPercent = stats.batteryDrainScreenOn.toFloat(),
                usedMah = stats.batteryDrainScreenOn * stats.totalCapacity / 100,
                rate = b.activeRate,
                milliamps = b.activeMilliamps,
            )
            DischargeSection(
                title = stringResource(R.string.power_insight_screen_off),
                subtitle = formatDurationPrecise(stats.screenOffTime),
                icon = Icons.Default.VisibilityOff,
                iconTint = accents.sleep,
                usedPercent = stats.batteryDrainScreenOff.toFloat(),
                usedMah = stats.batteryDrainScreenOff * stats.totalCapacity / 100,
                rate = b.idleRate,
                milliamps = b.idleMilliamps,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    label = stringResource(R.string.power_insight_deep_sleep),
                    value = formatDurationPrecise(stats.deepSleepTime),
                    subValue = stringResource(
                        R.string.power_insight_mah_percent,
                        formatMah(b.deepSleepMah),
                        formatPercent(b.deepSleepPercent),
                    ),
                    icon = Icons.Default.ModeNight,
                    iconTint = accents.sleep,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.power_insight_held_awake),
                    value = formatDurationPrecise(b.awakeTime),
                    subValue = stringResource(
                        R.string.power_insight_mah_percent,
                        formatMah(b.awakeMah),
                        formatPercent(b.awakePercent),
                    ),
                    icon = Icons.Default.Warning,
                    iconTint = accents.awake,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.power_insight_split_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun DischargeSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    usedPercent: Float,
    usedMah: Int,
    rate: Float,
    milliamps: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconTint = iconTint,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = stringResource(R.string.power_insight_used),
                value = formatPercent(usedPercent),
                subValue = formatMah(usedMah),
                icon = Icons.Default.TrendingDown,
                iconTint = accents.drain,
                valueColor = accents.drain,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.power_insight_discharge_rate),
                value = formatRate(rate),
                subValue = stringResource(
                    R.string.power_insight_approx,
                    formatMilliamps(milliamps),
                ),
                icon = Icons.Default.Speed,
                iconTint = accents.drain,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun BatteryHealthDialog(
    stats: PowerInsightStats,
    onDismiss: () -> Unit,
) {
    val breakdown = remember(stats.capacityHealth, stats.cycleCount) {
        batteryHealthBreakdown(stats.capacityHealth, stats.cycleCount)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.power_insight_battery_health)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthLine(
                    label = stringResource(R.string.power_insight_design_capacity),
                    value = formatMah(stats.totalCapacity),
                )
                HealthLine(
                    label = stringResource(R.string.power_insight_full_charge_capacity),
                    value = formatMah(stats.currentCapacity),
                )
                HealthLine(
                    label = stringResource(R.string.power_insight_reported_status),
                    value = stats.health ?: stringResource(R.string.power_insight_unknown),
                )
                HorizontalDivider()
                HealthLine(
                    label = stringResource(R.string.power_insight_capacity_health),
                    value = formatPercent(breakdown.capacityHealth),
                )
                HealthLine(
                    label = stringResource(R.string.power_insight_cycle_count),
                    value = stats.cycleCount.toString(),
                )
                HealthLine(
                    label = stringResource(R.string.power_insight_cycle_health),
                    value = formatPercent(breakdown.cycleHealth),
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.power_insight_health_weighting_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.power_insight_overall),
                        style = PowerInsightType.sectionTitle,
                    )
                    Text(
                        text = formatPercent(breakdown.weightedHealth),
                        style = PowerInsightType.metric,
                        color = accents.health,
                    )
                }
                Spacer(Modifier.height(4.dp))
                WavyMeter(
                    progress = breakdown.weightedHealth / 100f,
                    color = accents.health,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    animate = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentDescription = stringResource(
                        R.string.power_insight_health_a11y,
                        breakdown.weightedHealth.roundToInt(),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.power_insight_close))
            }
        },
    )
}

@Composable
private fun HealthLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
