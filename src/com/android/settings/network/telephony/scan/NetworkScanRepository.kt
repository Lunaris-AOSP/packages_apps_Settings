/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network.telephony.scan

import android.content.Context
import android.telephony.AccessNetworkConstants.AccessNetworkType
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.NetworkScanRequest
import android.telephony.NetworkScan
import android.telephony.PhoneCapability
import android.telephony.RadioAccessSpecifier
import android.telephony.TelephonyManager
import android.telephony.TelephonyScanManager
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.android.internal.telephony.CellNetworkScanResult
import com.android.internal.telephony.OperatorInfo
import com.android.settings.R
import com.android.settings.network.telephony.CellInfoUtil.getNetworkTitle
import com.android.settings.network.telephony.telephonyManager
import com.android.settingslib.spa.framework.util.collectLatestWithLifecycle
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class NetworkScanRepository(private val context: Context, subId: Int) {
    enum class NetworkScanState {
        ACTIVE, COMPLETE, ERROR
    }

    data class NetworkScanResult(
        val state: NetworkScanState,
        val cellInfos: List<CellInfo>,
        val errorCode: Int? = null,
    )

    private val telephonyManager = context.telephonyManager(subId)

    /** TODO: Move this to UI layer, when UI layer migrated to Kotlin. */
    fun launchNetworkScan(lifecycleOwner: LifecycleOwner, onResult: (NetworkScanResult) -> Unit) =
        networkScanFlow().collectLatestWithLifecycle(lifecycleOwner, action = onResult)

    data class CellInfoScanKey(
        val title: String?,
        val className: String,
        val isRegistered: Boolean,
    ) {
        constructor(cellInfo: CellInfo) : this(
            title = cellInfo.cellIdentity.getNetworkTitle(),
            className = cellInfo.javaClass.name,
            isRegistered = cellInfo.isRegistered,
        )
    }

    /**
     * Performs a manual network scan.
     *
     * Some Qualcomm qcril versions do not implement RIL_REQUEST_START_NETWORK_SCAN correctly:
     * the request is rejected by the modem with INVALID_ARGUMENTS when
     * `persist.vendor.radio.enableadvancedscan` is set, and qcril answers REQUEST_NOT_SUPPORTED
     * otherwise, so manual network selection always fails. On those devices fall back to the
     * legacy RIL_REQUEST_QUERY_AVAILABLE_NETWORKS request, which is what AOSP used before
     * Android 12 and which those qcril versions do implement.
     */
    fun networkScanFlow(): Flow<NetworkScanResult> = flow {
        var fallBackToLegacyApi = false
        networkScanRequestFlow().collect { result ->
            val errorCode = result.errorCode
            if (result.state == NetworkScanState.ERROR &&
                errorCode != null && errorCode in UNSUPPORTED_SCAN_ERRORS
            ) {
                Log.w(TAG, "RIL_REQUEST_START_NETWORK_SCAN unsupported, " +
                        "error=$errorCode, falling back to " +
                        "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS")
                fallBackToLegacyApi = true
            } else {
                emit(result)
            }
        }
        if (fallBackToLegacyApi) {
            emitAll(legacyNetworkScanFlow())
        }
    }.conflate().flowOn(Dispatchers.Default)

    /** Performs a RIL_REQUEST_START_NETWORK_SCAN and reports its results. */
    private fun networkScanRequestFlow(): Flow<NetworkScanResult> = callbackFlow {
        var state = NetworkScanState.ACTIVE
        var cellInfos: List<CellInfo> = emptyList()

        val callback = object : TelephonyScanManager.NetworkScanCallback() {
            override fun onResults(results: List<CellInfo>) {
                cellInfos = results.distinctBy { CellInfoScanKey(it) }
                sendResult()
            }

            override fun onComplete() {
                state = NetworkScanState.COMPLETE
                sendResult()
                // Don't call close() here since onComplete() could happens before onResults()
            }

            override fun onError(error: Int) {
                state = NetworkScanState.ERROR
                trySend(NetworkScanResult(state, cellInfos, error))
                close()
            }

            private fun sendResult() {
                trySend(NetworkScanResult(state, cellInfos))
            }
        }

        val networkScan = telephonyManager.requestNetworkScan(
            createNetworkScan(),
            // requestNetworkScan() could call callbacks concurrently, so we use a single thread
            // to avoid racing conditions.
            Executors.newSingleThreadExecutor(),
            callback,
        )

        awaitClose {
            networkScan.stopScan()
            Log.d(TAG, "network scan stopped")
        }
    }.conflate().onEach { Log.d(TAG, "networkScanFlow: $it") }.flowOn(Dispatchers.Default)

    /**
     * Performs a RIL_REQUEST_QUERY_AVAILABLE_NETWORKS and reports its result once.
     *
     * Unlike [TelephonyManager.requestNetworkScan] this is a blocking call, so it must not run
     * on the main thread.
     */
    private fun legacyNetworkScanFlow(): Flow<NetworkScanResult> = flow {
        val scanResult: CellNetworkScanResult? = telephonyManager.availableNetworks
        if (scanResult == null) {
            Log.w(TAG, "legacyNetworkScanFlow: null scan result")
            emit(NetworkScanResult(NetworkScanState.ERROR, emptyList()))
            return@flow
        }
        val cellInfos = scanResult.operators.orEmpty()
            .map { it.toCellInfo() }
            .distinctBy { CellInfoScanKey(it) }
        Log.d(TAG, "legacyNetworkScanFlow: status=${scanResult.status}, " +
                "operators=${scanResult.operators.orEmpty().size}, cellInfos=${cellInfos.size}")
        val state = if (scanResult.status == CellNetworkScanResult.STATUS_SUCCESS) {
            NetworkScanState.COMPLETE
        } else {
            NetworkScanState.ERROR
        }
        emit(NetworkScanResult(state, cellInfos))
    }.conflate().onEach { Log.d(TAG, "legacyNetworkScanFlow: $it") }.flowOn(Dispatchers.Default)

    /**
     * Converts a legacy [OperatorInfo] (the only thing RIL_REQUEST_QUERY_AVAILABLE_NETWORKS
     * returns) into a [CellInfo] so that the existing UI can consume it.
     *
     * The operator numeric may be suffixed with `+<RAN>` when the result comes from the
     * vendor's legacy incremental scan, so the radio access network is parsed from there.
     */
    private fun OperatorInfo.toCellInfo(): CellInfo {
        var mcc: String? = null
        var mnc: String? = null
        var ran = AccessNetworkType.UNKNOWN

        val numeric = operatorNumeric
        if (numeric != null) {
            val plmn = numeric.substringBefore('+')
            if (plmn.matches(Regex("^[0-9]{5,6}$"))) {
                mcc = plmn.substring(0, 3)
                mnc = plmn.substring(3)
                if ('+' in numeric) {
                    ran = ranToAccessNetworkType(numeric.substringAfter('+').toIntOrNull() ?: 0)
                }
            }
        }

        val cellInfo: CellInfo = when (ran) {
            AccessNetworkType.NGRAN -> CellInfoNr().apply {
                setCellIdentity(CellIdentityNr(
                    /* pci */ Int.MAX_VALUE,
                    /* tac */ Int.MAX_VALUE,
                    /* nrArfcn */ Int.MAX_VALUE,
                    /* bands */ intArrayOf(),
                    mcc,
                    mnc,
                    /* nci */ Int.MAX_VALUE.toLong(),
                    "$operatorAlphaLong 5G",
                    "$operatorAlphaShort 5G",
                    emptyList(),
                ))
            }
            AccessNetworkType.EUTRAN -> CellInfoLte().apply {
                setCellIdentity(CellIdentityLte(
                    /* ci */ Int.MAX_VALUE,
                    /* pci */ Int.MAX_VALUE,
                    /* tac */ Int.MAX_VALUE,
                    /* earfcn */ Int.MAX_VALUE,
                    /* bands */ intArrayOf(),
                    /* bandwidth */ Int.MAX_VALUE,
                    mcc,
                    mnc,
                    "$operatorAlphaLong 4G",
                    "$operatorAlphaShort 4G",
                    emptyList(),
                    /* csgInfo */ null,
                ))
            }
            AccessNetworkType.UTRAN -> CellInfoWcdma().apply {
                setCellIdentity(CellIdentityWcdma(
                    /* lac */ Int.MAX_VALUE,
                    /* cid */ Int.MAX_VALUE,
                    /* psc */ Int.MAX_VALUE,
                    /* uarfcn */ Int.MAX_VALUE,
                    mcc,
                    mnc,
                    "$operatorAlphaLong 3G",
                    "$operatorAlphaShort 3G",
                    emptyList(),
                    /* csgInfo */ null,
                ))
            }
            else -> CellInfoGsm().apply {
                setCellIdentity(CellIdentityGsm(
                    /* lac */ Int.MAX_VALUE,
                    /* cid */ Int.MAX_VALUE,
                    /* arfcn */ Int.MAX_VALUE,
                    /* bsic */ Int.MAX_VALUE,
                    mcc,
                    mnc,
                    operatorAlphaLong,
                    operatorAlphaShort,
                    emptyList(),
                ))
            }
        }
        // The legacy query returns the currently registered operator with State.CURRENT.
        cellInfo.setRegistered(state == OperatorInfo.State.CURRENT)
        return cellInfo
    }

    private fun ranToAccessNetworkType(ran: Int): Int = when (ran) {
        1 -> AccessNetworkType.GERAN
        2 -> AccessNetworkType.UTRAN
        3 -> AccessNetworkType.EUTRAN
        4 -> AccessNetworkType.CDMA2000
        5 -> AccessNetworkType.NGRAN
        else -> AccessNetworkType.UNKNOWN
    }

    /** Create network scan for allowed network types. */
    private fun createNetworkScan(): NetworkScanRequest {
        val allowedNetworkTypes = getAllowedNetworkTypes()
        Log.d(TAG, "createNetworkScan: allowedNetworkTypes = $allowedNetworkTypes")
        val radioAccessSpecifiers = allowedNetworkTypes
            .map { RadioAccessSpecifier(it, null, null) }
            .toTypedArray()
        return NetworkScanRequest(
            NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
            radioAccessSpecifiers,
            NetworkScanRequest.MIN_SEARCH_PERIODICITY_SEC, // one shot, not used
            context.resources.getInteger(R.integer.config_network_scan_helper_max_search_time_sec),
            true,
            INCREMENTAL_RESULTS_PERIODICITY_SEC,
            null,
        )
    }

    private fun getAllowedNetworkTypes(): List<Int> {
        val networkTypeBitmap3gpp: Long =
            telephonyManager.getAllowedNetworkTypesBitmask() and
                TelephonyManager.NETWORK_STANDARDS_FAMILY_BITMASK_3GPP
        return buildList {
            // If the allowed network types are unknown or if they are of the right class, scan for
            // them; otherwise, skip them to save scan time and prevent users from being shown
            // networks that they can't connect to.
            if (networkTypeBitmap3gpp == 0L
                || networkTypeBitmap3gpp and TelephonyManager.NETWORK_CLASS_BITMASK_2G != 0L
            ) {
                add(AccessNetworkType.GERAN)
            }
            if (networkTypeBitmap3gpp == 0L
                || networkTypeBitmap3gpp and TelephonyManager.NETWORK_CLASS_BITMASK_3G != 0L
            ) {
                add(AccessNetworkType.UTRAN)
            }
            if (networkTypeBitmap3gpp == 0L
                || networkTypeBitmap3gpp and TelephonyManager.NETWORK_CLASS_BITMASK_4G != 0L
            ) {
                add(AccessNetworkType.EUTRAN)
            }
            // If a device supports 5G stand-alone then the code below should be re-enabled; however
            // a device supporting only non-standalone mode cannot perform PLMN selection and camp
            // on a 5G network, which means that it shouldn't scan for 5G at the expense of battery
            // as part of the manual network selection process.
            //
            if (networkTypeBitmap3gpp == 0L
                || (networkTypeBitmap3gpp and TelephonyManager.NETWORK_CLASS_BITMASK_5G != 0L &&
                    hasNrSaCapability())
            ) {
                add(AccessNetworkType.NGRAN)
                Log.d(TAG, "radioAccessSpecifiers add NGRAN.")
            }
        }
    }

    private fun hasNrSaCapability(): Boolean {
        val phoneCapability = telephonyManager.getPhoneCapability()
        return PhoneCapability.DEVICE_NR_CAPABILITY_SA in phoneCapability.deviceNrCapabilities
    }

    companion object {
        private const val TAG = "NetworkScanRepository"

        private const val INCREMENTAL_RESULTS_PERIODICITY_SEC = 3

        /** Errors telling that this device does not support the network scan request. */
        private val UNSUPPORTED_SCAN_ERRORS = setOf(
            NetworkScan.ERROR_UNSUPPORTED,
            NetworkScan.ERROR_INVALID_SCAN,
        )
    }
}
