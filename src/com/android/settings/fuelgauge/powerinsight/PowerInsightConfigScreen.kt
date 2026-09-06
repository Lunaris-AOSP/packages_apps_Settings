/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.settings.R
import kotlin.math.roundToInt

private val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

private val AlarmFrequencyLabels = listOf(
    R.string.power_insight_alarm_freq_once,
    R.string.power_insight_alarm_freq_1,
    R.string.power_insight_alarm_freq_5,
    R.string.power_insight_alarm_freq_10,
    R.string.power_insight_alarm_freq_5min,
)

@Composable
fun PowerInsightConfigScreen(
    state: PowerInsightUiState,
    viewModel: PowerInsightViewModel,
    onPickSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    val context = LocalContext.current

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showResetLevelDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showAlarmFreqDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "general") {
            SettingsGroup(title = stringResource(R.string.power_insight_group_general)) {
                action(
                    title = stringResource(R.string.power_insight_notif_interval),
                    summary = stringResource(
                        R.string.power_insight_notif_interval_summary,
                        config.monitorInterval / 1000,
                    ),
                    trailingIcon = Icons.Rounded.ChevronRight,
                    onClick = { showIntervalDialog = true },
                )
                toggle(
                    title = stringResource(R.string.power_insight_notif_toggle),
                    summary = stringResource(R.string.power_insight_notif_toggle_summary),
                    checked = config.notificationEnabled,
                    enabled = state.monitoringEnabled,
                    onToggle = viewModel::setNotificationEnabled,
                )
            }
        }

        item(key = "autoreset") {
            SettingsGroup(title = stringResource(R.string.power_insight_group_auto_reset)) {
                toggle(
                    title = stringResource(R.string.power_insight_auto_reset_level_title),
                    summary = stringResource(R.string.power_insight_auto_reset_level_summary),
                    checked = config.autoResetLevelEnabled,
                    onToggle = viewModel::setAutoResetLevelEnabled,
                )
                action(
                    title = stringResource(R.string.power_insight_auto_reset_level_percent),
                    summary = stringResource(
                        R.string.power_insight_percent_value,
                        config.autoResetLevel,
                    ),
                    enabled = config.autoResetLevelEnabled,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    onClick = { showResetLevelDialog = true },
                )
                toggle(
                    title = stringResource(R.string.power_insight_reset_plugged),
                    summary = stringResource(R.string.power_insight_reset_plugged_summary),
                    checked = config.resetOnPlugged,
                    onToggle = viewModel::setResetOnPlugged,
                )
                toggle(
                    title = stringResource(R.string.power_insight_reset_reboot),
                    summary = stringResource(R.string.power_insight_reset_reboot_summary),
                    checked = config.resetOnReboot,
                    onToggle = viewModel::setResetOnReboot,
                )
            }
        }

        item(key = "alarms") {
            SettingsGroup(title = stringResource(R.string.power_insight_group_alarms)) {
                toggle(
                    title = stringResource(R.string.power_insight_battery_alarm_title),
                    summary = stringResource(R.string.power_insight_battery_alarm_summary),
                    checked = config.batteryAlarmEnabled,
                    onToggle = viewModel::setBatteryAlarmEnabled,
                )

                if (config.batteryAlarmEnabled) {
                    custom { shape ->
                        ThresholdRow(
                            shape = shape,
                            low = config.batteryLowThreshold,
                            high = config.batteryHighThreshold,
                            onCommit = viewModel::setAlarmThresholds,
                        )
                    }
                    action(
                        title = stringResource(R.string.power_insight_alarm_frequency),
                        summary = stringResource(
                            AlarmFrequencyLabels.getOrElse(config.alarmFrequency) {
                                R.string.power_insight_placeholder_dash
                            },
                        ),
                        trailingIcon = Icons.Rounded.ChevronRight,
                        onClick = { showAlarmFreqDialog = true },
                    )
                    action(
                        title = stringResource(R.string.power_insight_alarm_sound),
                        summary = ringtoneTitle(context, config.alarmSound),
                        trailingIcon = Icons.Rounded.MusicNote,
                        onClick = onPickSound,
                    )
                    toggle(
                        title = stringResource(R.string.power_insight_alarm_vibrate),
                        summary = stringResource(R.string.power_insight_alarm_vibrate_summary),
                        checked = config.alarmVibrate,
                        onToggle = viewModel::setAlarmVibrate,
                    )
                }

                toggle(
                    title = stringResource(R.string.power_insight_full_charge_alarm),
                    summary = stringResource(R.string.power_insight_full_charge_alarm_summary),
                    checked = config.fullChargeAlarmEnabled,
                    onToggle = viewModel::setFullChargeAlarmEnabled,
                )
            }
        }

        item(key = "danger") {
            SettingsGroup {
                action(
                    title = stringResource(R.string.power_insight_manual_reset),
                    summary = stringResource(R.string.power_insight_manual_reset_summary),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showResetConfirmDialog = true },
                )
            }
        }
    }

    if (showIntervalDialog) {
        val options = listOf(
            5_000 to stringResource(R.string.power_insight_interval_5s),
            10_000 to stringResource(R.string.power_insight_interval_10s),
            15_000 to stringResource(R.string.power_insight_interval_15s),
            30_000 to stringResource(R.string.power_insight_interval_30s),
            60_000 to stringResource(R.string.power_insight_interval_60s),
        )
        SingleChoiceDialog(
            title = stringResource(R.string.power_insight_notif_interval),
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == config.monitorInterval },
            onDismiss = { showIntervalDialog = false },
            onSelect = { index ->
                viewModel.setMonitorInterval(options[index].first)
                showIntervalDialog = false
            },
        )
    }

    if (showAlarmFreqDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.power_insight_alarm_frequency),
            options = AlarmFrequencyLabels.map { stringResource(it) },
            selectedIndex = config.alarmFrequency,
            onDismiss = { showAlarmFreqDialog = false },
            onSelect = { index ->
                viewModel.setAlarmFrequency(index)
                showAlarmFreqDialog = false
            },
        )
    }

    if (showResetLevelDialog) {
        ResetLevelDialog(
            current = config.autoResetLevel,
            onDismiss = { showResetLevelDialog = false },
            onConfirm = { level ->
                viewModel.setAutoResetLevel(level)
                showResetLevelDialog = false
            },
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.power_insight_reset_confirm_title)) },
            text = { Text(stringResource(R.string.power_insight_reset_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStats()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.power_insight_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.power_insight_cancel))
                }
            },
        )
    }
}

@Composable
private fun ThresholdRow(
    shape: androidx.compose.ui.graphics.Shape,
    low: Int,
    high: Int,
    onCommit: (Int, Int) -> Unit,
) {
    var range by remember(low, high) {
        mutableStateOf(low.toFloat()..high.toFloat())
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.power_insight_threshold_low,
                        range.start.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accents.drain,
                )
                Text(
                    text = stringResource(
                        R.string.power_insight_threshold_high,
                        range.endInclusive.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accents.charge,
                )
            }
            Spacer(Modifier.height(4.dp))
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                onValueChangeFinished = {
                    onCommit(range.start.roundToInt(), range.endInclusive.roundToInt())
                },
                valueRange = 1f..99f,
                steps = 97,
            )
        }
    }
}

@Composable
private fun ResetLevelDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.power_insight_auto_reset_level_percent)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.power_insight_percent_value,
                        value.roundToInt(),
                    ),
                    style = PowerInsightType.metric,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 1f..100f,
                    steps = 98,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.roundToInt()) }) {
                Text(stringResource(R.string.power_insight_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.power_insight_cancel))
            }
        },
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.power_insight_cancel))
            }
        },
    )
}

fun ringtoneTitle(context: Context, uriString: String?): String {
    val none = context.getString(R.string.power_insight_none)
    if (uriString.isNullOrEmpty()) return none
    return try {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))
            ?.getTitle(context)
            ?: none
    } catch (e: Exception) {
        none
    }
}
