/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.os.ServiceManager
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.internal.os.IPowerInsightService
import com.android.internal.os.PowerInsightAppUsage
import com.android.internal.os.PowerInsightFlowSample
import com.android.internal.os.PowerInsightHistoryBucket
import com.android.internal.os.PowerInsightStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class PowerInsightConfig(
    val notificationEnabled: Boolean = false,
    val monitorInterval: Int = 10_000,
    val autoResetLevelEnabled: Boolean = false,
    val autoResetLevel: Int = 100,
    val resetOnPlugged: Boolean = false,
    val resetOnReboot: Boolean = false,
    val batteryAlarmEnabled: Boolean = false,
    val batteryLowThreshold: Int = 20,
    val batteryHighThreshold: Int = 80,
    val alarmFrequency: Int = 0,
    val alarmSound: String? = null,
    val alarmVibrate: Boolean = false,
    val fullChargeAlarmEnabled: Boolean = false,
)

@Immutable
data class PowerInsightUiState(
    val loading: Boolean = true,
    val serviceAvailable: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val stats: PowerInsightStats = PowerInsightStats(),
    val flow: List<PowerInsightFlowSample> = emptyList(),
    val history: List<PowerInsightHistoryBucket> = emptyList(),
    val apps: List<PowerInsightAppUsage> = emptyList(),
    val config: PowerInsightConfig = PowerInsightConfig(),
)

class PowerInsightViewModel : ViewModel() {

    private companion object {
        const val TAG = "PowerInsightVM"
        const val SERVICE_NAME = "power_insight"

        const val FAST_POLL_MS = 2_000L

        const val SLOW_POLL_MS = 15_000L

        const val FLOW_SAMPLE_COUNT = 60
        const val APP_LIMIT = 50
    }

    @Volatile
    private var service: IPowerInsightService? = null

    private val _state = MutableStateFlow(PowerInsightUiState())
    val state: StateFlow<PowerInsightUiState> = _state.asStateFlow()

    private val writeGeneration = AtomicInteger(0)

    init {
        viewModelScope.launch {
            _state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) runPolling() }
        }
    }

    private suspend fun runPolling() {
        var lastSlowPoll = 0L
        while (true) {
            connectIfNeeded()
            val now = System.currentTimeMillis()
            val includeSlow = now - lastSlowPoll >= SLOW_POLL_MS
            if (includeSlow) lastSlowPoll = now
            refresh(includeSlow = includeSlow)
            delay(FAST_POLL_MS)
        }
    }

    private suspend fun connectIfNeeded() {
        if (service != null) return
        withContext(Dispatchers.IO) {
            val binder = runCatching { ServiceManager.checkService(SERVICE_NAME) }.getOrNull()
            if (binder == null) {
                Log.w(TAG, "$SERVICE_NAME binder unavailable")
                _state.update { it.copy(loading = false, serviceAvailable = false) }
            } else {
                service = IPowerInsightService.Stub.asInterface(binder)
                Log.i(TAG, "Connected to $SERVICE_NAME")
            }
        }
    }

    private suspend fun refresh(includeSlow: Boolean) = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext
        try {
            val generation = writeGeneration.get()
            val stats = svc.batteryState
            val flow = svc.getCurrentFlow(FLOW_SAMPLE_COUNT).toList()
            val enabled = svc.isEnabled
            val history = if (includeSlow) svc.history.toList() else null
            val apps = if (includeSlow) svc.getAppUsageSinceLastCharge(APP_LIMIT).toList() else null

            val stale = writeGeneration.get() != generation

            _state.update { current ->
                current.copy(
                    loading = false,
                    serviceAvailable = true,
                    monitoringEnabled = if (stale) current.monitoringEnabled else enabled,
                    stats = stats,
                    flow = flow,
                    history = history ?: current.history,
                    apps = apps ?: current.apps,
                    config = if (stale) current.config else stats.toConfig(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
            service = null
            _state.update { it.copy(loading = false, serviceAvailable = false) }
        }
    }

    private fun PowerInsightStats.toConfig() = PowerInsightConfig(
        notificationEnabled = isNotificationEnabled,
        monitorInterval = monitorInterval,
        autoResetLevelEnabled = isAutoResetLevelEnabled,
        autoResetLevel = autoResetLevel,
        resetOnPlugged = isResetOnPlugged,
        resetOnReboot = isResetOnReboot,
        batteryAlarmEnabled = isBatteryAlarmEnabled,
        batteryLowThreshold = batteryLowThreshold,
        batteryHighThreshold = batteryHighThreshold,
        alarmFrequency = alarmFrequency,
        alarmSound = batteryAlarmSound,
        alarmVibrate = isBatteryAlarmVibrate,
        fullChargeAlarmEnabled = isFullChargeAlarmEnabled,
    )

    private fun commit(
        optimistic: (PowerInsightConfig) -> PowerInsightConfig,
        push: (IPowerInsightService) -> Unit,
    ) {
        writeGeneration.incrementAndGet()
        _state.update { it.copy(config = optimistic(it.config)) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                service?.let(push)
            } catch (e: Exception) {
                Log.e(TAG, "service write failed", e)
            } finally {
                writeGeneration.incrementAndGet()
            }
        }
    }

    fun setEnabled(value: Boolean) {
        writeGeneration.incrementAndGet()
        _state.update { it.copy(monitoringEnabled = value) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                service?.isEnabled = value
            } catch (e: Exception) {
                Log.e(TAG, "setEnabled failed", e)
            } finally {
                writeGeneration.incrementAndGet()
            }
        }
    }

    fun setNotificationEnabled(value: Boolean) = commit(
        { it.copy(notificationEnabled = value) },
        { it.isNotificationEnabled = value },
    )

    fun setMonitorInterval(value: Int) = commit(
        { it.copy(monitorInterval = value) },
        { it.monitorInterval = value },
    )

    fun setAutoResetLevelEnabled(value: Boolean) = commit(
        { it.copy(autoResetLevelEnabled = value) },
        { it.setAutoResetLevelEnabled(value) },
    )

    fun setAutoResetLevel(value: Int) {
        val clamped = value.coerceIn(1, 100)
        commit({ it.copy(autoResetLevel = clamped) }, { it.setAutoResetLevel(clamped) })
    }

    fun setResetOnPlugged(value: Boolean) = commit(
        { it.copy(resetOnPlugged = value) },
        { it.setResetOnPlugged(value) },
    )

    fun setResetOnReboot(value: Boolean) = commit(
        { it.copy(resetOnReboot = value) },
        { it.setResetOnReboot(value) },
    )

    fun setBatteryAlarmEnabled(value: Boolean) = commit(
        { it.copy(batteryAlarmEnabled = value) },
        { it.setBatteryAlarmEnabled(value) },
    )

    fun setAlarmThresholds(low: Int, high: Int) {
        val safeLow = low.coerceIn(1, 99)
        val safeHigh = high.coerceIn(safeLow, 99)
        commit(
            { it.copy(batteryLowThreshold = safeLow, batteryHighThreshold = safeHigh) },
            {
                it.setBatteryLowThreshold(safeLow)
                it.setBatteryHighThreshold(safeHigh)
            },
        )
    }

    fun setAlarmFrequency(value: Int) = commit(
        { it.copy(alarmFrequency = value) },
        { it.setAlarmFrequency(value) },
    )

    fun setFullChargeAlarmEnabled(value: Boolean) = commit(
        { it.copy(fullChargeAlarmEnabled = value) },
        { it.setFullChargeAlarmEnabled(value) },
    )

    fun setAlarmVibrate(value: Boolean) = commit(
        { it.copy(alarmVibrate = value) },
        { it.setBatteryAlarmVibrate(value) },
    )

    fun setAlarmSound(uri: String?) = commit(
        { it.copy(alarmSound = uri) },
        { it.setBatteryAlarmSound(uri ?: "") },
    )

    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                service?.resetStats()
            } catch (e: Exception) {
                Log.e(TAG, "resetStats failed", e)
                return@launch
            }
            refresh(includeSlow = true)
        }
    }
}
