/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.settings.R
import com.android.settingslib.search.SearchIndexable
import com.android.settingslib.search.SearchIndexableData
import com.android.settingslib.search.SearchIndexableResources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.surfaceColorAtElevation
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settings.search.BaseSearchIndexProvider

@SearchIndexable
class PowerInsightSettings : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.power_insight_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                PowerInsightRoot()
            }
        }
    }

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = BaseSearchIndexProvider(R.xml.power_usage_summary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerInsightRoot(viewModel: PowerInsightViewModel = viewModel()) {
    val stats by viewModel.stats.collectAsState()
    val flow by viewModel.flow.collectAsState()
    val history by viewModel.history.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val isNotifEnabled by viewModel.isNotifEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showHealthDetails by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MasterToggleCard(
                enabled = isEnabled,
                onToggle = { viewModel.setEnabled(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Realtime") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Apps") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Settings") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> RealtimeTab(stats, flow) { showHealthDetails = true }
                    1 -> HistoryTab(history)
                    2 -> AppsTab(apps)
                    3 -> SettingsTab(viewModel, isEnabled, isNotifEnabled)
                }
            }
        }
    }

    if (showHealthDetails) {
        BatteryHealthDialog(stats = stats, onDismiss = { showHealthDetails = false })
    }
}

@Composable
fun MasterToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.BatteryChargingFull, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        stringResource(R.string.power_insight_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (enabled) "Monitoring active" else "Monitoring disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun RealtimeTab(
    stats: com.android.internal.os.PowerInsightStats,
    flow: List<com.android.internal.os.PowerInsightFlowSample>,
    onOpenHealth: () -> Unit
) {
    var selectedChartTab by remember { mutableIntStateOf(if (stats.isCharging) 0 else 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TabRow(
                    selectedTabIndex = selectedChartTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedChartTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedChartTab == 0,
                        onClick = { selectedChartTab = 0 },
                        text = { Text("Charging", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedChartTab == 1,
                        onClick = { selectedChartTab = 1 },
                        text = { Text("Discharging", fontWeight = FontWeight.SemiBold) }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                val filteredFlow = flow.filter { it.isCharging == (selectedChartTab == 0) }
                PowerFlowChart(filteredFlow, stats)
            }
        }
        
        RealtimeStatsGrid(stats)
        
        BatteryHealthCard(stats, onOpenHealth)
    }
}

@Composable
fun HistoryTab(history: List<com.android.internal.os.PowerInsightHistoryBucket>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            history.forEach { bucket ->
                HistoryBucketItem(bucket)
            }
        }
    }
}

@Composable
fun AppsTab(apps: List<com.android.internal.os.PowerInsightAppUsage>) {
    val context = LocalContext.current
    val pm = context.packageManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("App usage since last charge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No app usage data available yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val totalMah = apps.sumOf { it.consumedPowerMah }.coerceAtLeast(0.1)
        apps.forEach { app ->
            val pct = (app.consumedPowerMah * 100.0 / totalMah).coerceIn(0.0, 100.0)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val icon = remember(app.packageName) {
                            try {
                                pm.getApplicationIcon(app.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (icon != null) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        setImageDrawable(icon)
                                    }
                                },
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                app.appLabel ?: app.packageName ?: "Unknown",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                String.format("%.1f mAh", app.consumedPowerMah),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "FG ${formatTime(app.foregroundTimeMs)} • BG ${formatTime(app.backgroundTimeMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(viewModel: PowerInsightViewModel, isEnabled: Boolean, isNotifEnabled: Boolean) {
    val monitorInterval by viewModel.monitorInterval.collectAsState()
    val resetPlugged by viewModel.resetOnPlugged.collectAsState()
    val resetReboot by viewModel.resetOnReboot.collectAsState()
    val autoResetEnabled by viewModel.autoResetLevelEnabled.collectAsState()
    val autoResetLevel by viewModel.autoResetLevel.collectAsState()
    
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showResetLevelDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GroupCard(title = "General") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.power_insight_notif_interval)) },
                supportingContent = { Text(stringResource(R.string.power_insight_notif_interval_summary, monitorInterval / 1000)) },
                modifier = Modifier.clickable { showIntervalDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        GroupCard(title = "Notification") {
            ToggleRow(
                title = stringResource(R.string.power_insight_notif_toggle),
                summary = stringResource(R.string.power_insight_notif_toggle_summary),
                checked = isNotifEnabled,
                enabled = isEnabled,
                onToggle = { viewModel.setNotifEnabled(it) }
            )
        }

        GroupCard(title = "Auto Reset") {
            ToggleRow(
                title = stringResource(R.string.power_insight_auto_reset_level_title),
                summary = stringResource(R.string.power_insight_auto_reset_level_summary),
                checked = autoResetEnabled,
                onToggle = { viewModel.setAutoResetLevelEnabled(it) }
            )
            ListItem(
                headlineContent = { 
                    Text(
                        stringResource(R.string.power_insight_auto_reset_level_percent),
                        color = if (autoResetEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ) 
                },
                supportingContent = { 
                    Text(
                        "$autoResetLevel%",
                        color = if (autoResetEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ) 
                },
                modifier = Modifier.clickable(enabled = autoResetEnabled) { showResetLevelDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ToggleRow(
                title = stringResource(R.string.power_insight_reset_plugged),
                summary = stringResource(R.string.power_insight_reset_plugged_summary),
                checked = resetPlugged,
                onToggle = { viewModel.setResetOnPlugged(it) }
            )
            ToggleRow(
                title = stringResource(R.string.power_insight_reset_reboot),
                summary = stringResource(R.string.power_insight_reset_reboot_summary),
                checked = resetReboot,
                onToggle = { viewModel.setResetOnReboot(it) }
            )
        }

        GroupCard {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.power_insight_manual_reset),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = { Text(stringResource(R.string.power_insight_manual_reset_summary)) },
                modifier = Modifier.clickable { showResetConfirmDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    if (showIntervalDialog) {
        IntervalDialog(
            currentInterval = monitorInterval,
            onDismiss = { showIntervalDialog = false },
            onSelect = {
                viewModel.setMonitorInterval(it)
                showIntervalDialog = false
            }
        )
    }

    if (showResetLevelDialog) {
        ResetThresholdDialog(
            currentLevel = autoResetLevel,
            onDismiss = { showResetLevelDialog = false },
            onConfirm = {
                viewModel.setAutoResetLevel(it)
                showResetLevelDialog = false
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset statistics?") },
            text = { Text("This will clear all currently tracked battery usage and flow data.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStats()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ResetThresholdDialog(currentLevel: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(currentLevel.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.power_insight_auto_reset_level_percent)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 3) text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Percentage (1-100)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                val level = text.toIntOrNull()?.coerceIn(1, 100) ?: 100
                onConfirm(level)
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IntervalDialog(currentInterval: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(
        5000 to stringResource(R.string.power_insight_interval_5s),
        10000 to stringResource(R.string.power_insight_interval_10s),
        15000 to stringResource(R.string.power_insight_interval_15s),
        30000 to stringResource(R.string.power_insight_interval_30s),
        60000 to stringResource(R.string.power_insight_interval_60s)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.power_insight_notif_interval)) },
        text = {
            Column {
                options.forEach { (ms, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(ms) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentInterval == ms, onClick = { onSelect(ms) })
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GroupCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ToggleRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
        supportingContent = { Text(summary, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onToggle(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun PowerFlowChart(
    flow: List<com.android.internal.os.PowerInsightFlowSample>,
    stats: com.android.internal.os.PowerInsightStats
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "${Math.abs(stats.currentNow)} mA",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = String.format("%.2f W • %d mV", stats.powerWatts, stats.voltage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${flow.size} samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw Grid
                val gridLines = 5
                for (i in 0 until gridLines) {
                    val y = height * i / (gridLines - 1)
                    drawLine(gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
                }
                
                if (flow.isNotEmpty()) {
                    val displaySamples = flow.takeLast(60)
                    val maxVal = displaySamples.maxOf { Math.abs(it.current) }.coerceAtLeast(100).toFloat() * 1.1f
                    
                    val points = displaySamples.mapIndexed { index, sample ->
                        val x = if (displaySamples.size > 1) {
                            width * index / (displaySamples.size - 1)
                        } else width / 2
                        val y = height - (Math.abs(sample.current) / maxVal * height)
                        Offset(x, y)
                    }

                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val p1 = points[i - 1]
                                val p2 = points[i]
                                val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                            }
                        }
                        
                        // Draw Gradient Area
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(points.last().x, height)
                            lineTo(points.first().x, height)
                            close()
                        }
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        
                        // Draw Main Line
                        drawPath(
                            path,
                            color = primaryColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw Last Point Pulse
                        val lastPoint = points.last()
                        drawCircle(primaryColor, radius = 6.dp.toPx(), center = lastPoint)
                        drawCircle(primaryColor.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = lastPoint)
                    }
                }
            }
            
            // X-Axis Marking (Simplified)
            Text(
                "Now",
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "2m ago",
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))

        // Min / Avg / Max Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MiniStat("Average", "${stats.avgCurrent} mA")
            VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically), color = gridColor)
            MiniStat("Minimum", "${stats.minCurrent} mA")
            VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically), color = gridColor)
            MiniStat("Maximum", "${stats.maxCurrent} mA")
        }
    }
}

@Composable
fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RealtimeStatsGrid(stats: com.android.internal.os.PowerInsightStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), "Active Drain", String.format("%.1f%%/h", stats.activeDrainRate), Icons.Default.FlashOn)
        StatCard(Modifier.weight(1f), "Idle Drain", String.format("%.1f%%/h", stats.idleDrainRate), Icons.Default.ModeNight)
    }
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), "Screen On", formatTime(stats.screenOnTime), Icons.Default.WbSunny)
        StatCard(Modifier.weight(1f), "Deep Sleep", formatTime(stats.deepSleepTime), Icons.Default.Bedtime)
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BatteryHealthCard(stats: com.android.internal.os.PowerInsightStats, onOpenHealth: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenHealth() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFE57373))
                Spacer(Modifier.width(12.dp))
                Text("Battery Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(String.format("%.1f%%", stats.healthPercent), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HealthMetric("Capacity", String.format("%d/%d mAh", stats.currentCapacity, stats.totalCapacity))
                HealthMetric("Cycles", stats.cycleCount.toString())
                HealthMetric("Status", stats.health ?: "Unknown")
            }
        }
    }
}

@Composable
fun BatteryHealthDialog(stats: com.android.internal.os.PowerInsightStats, onDismiss: () -> Unit) {
    val cyclePenalty = ((stats.cycleCount / 800f) * 35f).coerceIn(0f, 35f)
    val cycleHealth = (100f - cyclePenalty).coerceIn(0f, 100f)
    val weighted = ((stats.capacityHealth * 0.7f) + (cycleHealth * 0.3f)).coerceIn(0f, 100f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery health details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Design capacity: ${stats.totalCapacity} mAh")
                Text("Full charge capacity: ${stats.currentCapacity} mAh")
                Text("Capacity health: ${String.format("%.1f%%", stats.capacityHealth)}")
                Text("Cycle count: ${stats.cycleCount}")
                Text("Cycle health: ${String.format("%.1f%%", cycleHealth)}")
                Text("Final health = (capacityHealth × 0.7) + (cycleHealth × 0.3)")
                Text("Final health: ${String.format("%.1f%%", weighted)}", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun HealthMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HistoryBucketItem(bucket: com.android.internal.os.PowerInsightHistoryBucket) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%02d:00", bucket.hour), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("SOT: ${formatTime(bucket.screenOnMs)}", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { (bucket.drainPercent / 20f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text("-${bucket.drainPercent}%", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "${h}h ${m % 60}m" else "${m}m ${s % 60}s"
}
