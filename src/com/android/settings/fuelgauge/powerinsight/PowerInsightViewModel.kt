/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.fuelgauge.powerinsight

import android.os.ServiceManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.internal.os.IPowerInsightService
import com.android.internal.os.PowerInsightAppUsage
import com.android.internal.os.PowerInsightFlowSample
import com.android.internal.os.PowerInsightHistoryBucket
import com.android.internal.os.PowerInsightStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PowerInsightViewModel : ViewModel() {
    companion object {
        private const val TAG = "PowerInsightVM"
    }

    private var service: IPowerInsightService? = null

    private val _stats = MutableStateFlow(PowerInsightStats())
    val stats: StateFlow<PowerInsightStats> = _stats.asStateFlow()

    private val _flow = MutableStateFlow<List<PowerInsightFlowSample>>(emptyList())
    val flow: StateFlow<List<PowerInsightFlowSample>> = _flow.asStateFlow()

    private val _history = MutableStateFlow<List<PowerInsightHistoryBucket>>(emptyList())
    val history: StateFlow<List<PowerInsightHistoryBucket>> = _history.asStateFlow()
    private val _apps = MutableStateFlow<List<PowerInsightAppUsage>>(emptyList())
    val apps: StateFlow<List<PowerInsightAppUsage>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isNotifEnabled = MutableStateFlow(false)
    val isNotifEnabled: StateFlow<Boolean> = _isNotifEnabled.asStateFlow()

    private val _monitorInterval = MutableStateFlow(10000)
    val monitorInterval: StateFlow<Int> = _monitorInterval.asStateFlow()

    private val _autoResetLevelEnabled = MutableStateFlow(false)
    val autoResetLevelEnabled: StateFlow<Boolean> = _autoResetLevelEnabled.asStateFlow()

    private val _autoResetLevel = MutableStateFlow(100)
    val autoResetLevel: StateFlow<Int> = _autoResetLevel.asStateFlow()

    private val _resetOnPlugged = MutableStateFlow(false)
    val resetOnPlugged: StateFlow<Boolean> = _resetOnPlugged.asStateFlow()

    private val _resetOnReboot = MutableStateFlow(false)
    val resetOnReboot: StateFlow<Boolean> = _resetOnReboot.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            _isLoading.value = true
            while (true) {
                if (service == null) {
                    val binder = ServiceManager.checkService("power_insight")
                    if (binder != null) {
                        service = IPowerInsightService.Stub.asInterface(binder)
                        Log.i(TAG, "Connected to power_insight binder")
                    } else {
                        Log.w(TAG, "power_insight binder is null")
                    }
                }
                refreshData()
                _isLoading.value = false
                delay(1000)
            }
        }
    }

    private suspend fun refreshData() = withContext(Dispatchers.IO) {
        service?.let { s ->
            try {
                val currentStats = s.batteryState
                _stats.value = currentStats
                _flow.value = s.getCurrentFlow(60).toList()
                _history.value = s.history.toList()
                _apps.value = s.getAppUsageSinceLastCharge(50).toList()
                _isEnabled.value = s.isEnabled
                
                _isNotifEnabled.value = currentStats.isNotificationEnabled
                _monitorInterval.value = currentStats.monitorInterval
                _autoResetLevelEnabled.value = currentStats.isAutoResetLevelEnabled
                _autoResetLevel.value = currentStats.autoResetLevel
                _resetOnPlugged.value = currentStats.isResetOnPlugged
                _resetOnReboot.value = currentStats.isResetOnReboot
            } catch (e: Exception) {
                Log.e(TAG, "refreshData failed", e)
            }
        }
    }

    fun setEnabled(v: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            service?.isEnabled = v
            _isEnabled.value = v
        }
    }

    fun setNotifEnabled(v: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            service?.isNotificationEnabled = v
            _isNotifEnabled.value = v
        }
    }

    fun setMonitorInterval(v: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            service?.monitorInterval = v
            _monitorInterval.value = v
        }
    }

    fun setAutoResetLevel(level: Int) {
        viewModelScope.launch(Dispatchers.IO) { 
            service?.setAutoResetLevel(level)
            _autoResetLevel.value = level
        }
    }

    fun setAutoResetLevelEnabled(v: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { 
            service?.setAutoResetLevelEnabled(v)
            _autoResetLevelEnabled.value = v
        }
    }

    fun setResetOnPlugged(v: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { 
            service?.setResetOnPlugged(v)
            _resetOnPlugged.value = v
        }
    }

    fun setResetOnReboot(v: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { 
            service?.setResetOnReboot(v)
            _resetOnReboot.value = v
        }
    }

    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            service?.resetStats()
            refreshData()
        }
    }
}
