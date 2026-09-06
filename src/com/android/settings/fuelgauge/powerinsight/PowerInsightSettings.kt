/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.settings.R
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable
import com.android.settingslib.spa.framework.theme.SettingsTheme

@SearchIndexable
class PowerInsightSettings : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.power_insight_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                PowerInsightAccentTheme {
                    PowerInsightRoot()
                }
            }
        }
    }

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = BaseSearchIndexProvider(R.xml.power_usage_summary)
    }
}

private enum class PowerInsightTab(@StringRes val labelRes: Int) {
    Live(R.string.power_insight_tab_live),
    History(R.string.power_insight_tab_history),
    Apps(R.string.power_insight_tab_apps),
    Settings(R.string.power_insight_tab_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerInsightRoot(viewModel: PowerInsightViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var showSessionDetails by remember { mutableStateOf(false) }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            viewModel.setAlarmSound(uri?.toString())
        }
    }

    val currentSound = state.config.alarmSound
    val pickerTitle = stringResource(R.string.power_insight_alarm_sound)
    val launchRingtonePicker: () -> Unit = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
            if (!currentSound.isNullOrEmpty()) {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(currentSound),
                )
            }
        }
        ringtonePicker.launch(intent)
    }

    if (showSessionDetails) {
        BackHandler { showSessionDetails = false }
        SessionDetailsScreen(
            stats = state.stats,
            onBack = { showSessionDetails = false },
        )
        return
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .fillMaxSize()
                .padding(padding),
        ) {
            MonitorHeroCard(
                enabled = state.monitoringEnabled,
                serviceAvailable = state.serviceAvailable,
                charging = state.stats.isCharging,
                onToggle = viewModel::setEnabled,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {},
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                PowerInsightTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            when {
                state.loading -> LoadingPane(Modifier.weight(1f))
                !state.serviceAvailable -> ServiceUnavailablePane(Modifier.weight(1f))
                else -> AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val shift: (Int) -> Int = { full -> full / 8 }
                        (
                            slideInHorizontally(tween(220)) {
                                if (forward) shift(it) else -shift(it)
                            } + fadeIn(tween(160))
                            ) togetherWith (
                            slideOutHorizontally(tween(220)) {
                                if (forward) -shift(it) else shift(it)
                            } + fadeOut(tween(120))
                            )
                    },
                    label = "tabContent",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { tab ->
                    when (PowerInsightTab.entries[tab]) {
                        PowerInsightTab.Live -> RealtimeScreen(
                            state = state,
                            onOpenHealth = { showHealthDialog = true },
                            onOpenSession = { showSessionDetails = true },
                        )
                        PowerInsightTab.History -> HistoryScreen(state.history)
                        PowerInsightTab.Apps -> AppsScreen(state.apps)
                        PowerInsightTab.Settings -> PowerInsightConfigScreen(
                            state = state,
                            viewModel = viewModel,
                            onPickSound = launchRingtonePicker,
                        )
                    }
                }
            }
        }
    }

    if (showHealthDialog) {
        BatteryHealthDialog(
            stats = state.stats,
            onDismiss = { showHealthDialog = false },
        )
    }
}

@Composable
private fun MonitorHeroCard(
    enabled: Boolean,
    serviceAvailable: Boolean,
    charging: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeContainer = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        charging -> accents.chargeContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContent = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        charging -> accents.charge
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val status = when {
        !serviceAvailable -> stringResource(R.string.power_insight_status_service_unavailable)
        !enabled -> stringResource(R.string.power_insight_status_paused)
        charging -> stringResource(R.string.power_insight_status_charging)
        else -> stringResource(R.string.power_insight_status_active)
    }
    val statusColor = when {
        !serviceAvailable -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        charging -> accents.charge
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = PowerInsightShapes.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveIconBadge(
                icon = when {
                    !serviceAvailable -> Icons.Default.Warning
                    enabled -> Icons.Default.BatteryChargingFull
                    else -> Icons.Default.PowerSettingsNew
                },
                containerColor = badgeContainer,
                contentColor = badgeContent,
                active = enabled && serviceAvailable,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.power_insight_title),
                    style = PowerInsightType.tileMetric,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            PowerInsightSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = serviceAvailable,
            )
        }
    }
}

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ServiceUnavailablePane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = Icons.Default.Warning,
            title = stringResource(R.string.power_insight_service_unavailable_title),
            body = stringResource(R.string.power_insight_service_unavailable_body),
        )
    }
}
