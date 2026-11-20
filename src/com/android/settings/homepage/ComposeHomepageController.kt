/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
package com.android.settings.homepage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import android.net.wifi.WifiManager
import com.android.internal.util.UserIcons
import com.android.settings.R
import com.android.settings.SettingsActivity
import com.android.settings.communal.CommunalPreferenceController
import com.android.settings.overlay.FeatureFactory
import com.android.settings.safetycenter.SafetyCenterManagerWrapper
import android.app.settings.SettingsEnums
import android.content.ComponentName
import android.content.pm.PackageManager

class ComposeHomepageController(private val activity: SettingsHomepageActivity) {

    companion object {
        private const val TAG = "ComposeHomepageCtrl"
        private const val WELLBEING_PKG = "com.google.android.apps.wellbeing"
        private const val WELLBEING_ACTIVITY = "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
        private const val GOOGLE_PKG = "com.google.android.gms"
        private const val GOOGLE_ACTIVITY = "com.google.android.gms.app.settings.GoogleSettingsIALink"

        @JvmStatic
        fun isComposeHomepageEnabled(context: Context): Boolean =
            Settings.System.getInt(context.contentResolver, 
                "compose_homepage_enabled", 1
            ) == 1
    }

    private var userInfoReceiver: BroadcastReceiver? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var airplaneModeReceiver: BroadcastReceiver? = null
    private val wifiManager by lazy {
        activity.applicationContext.getSystemService(WifiManager::class.java)
    }

    private val editModeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!SettingsHomepageComposeInterop.exitEditMode()) {
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    fun onCreate(highlightMenuKey: String): TopLevelSettings {
        setupEdgeToEdge()
        activity.onBackPressedDispatcher.addCallback(activity, editModeBackCallback)

        val connectivityManager = activity.applicationContext
            .getSystemService(ConnectivityManager::class.java)

        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(composeView)

        val fragmentContainer = FrameLayout(activity).apply {
            id = R.id.main_content
            layoutParams = ViewGroup.LayoutParams(0, 0)
            visibility = android.view.View.GONE
        }
        root.addView(fragmentContainer)

        activity.setContentView(root)

        val fragment = TopLevelSettings().apply {
            arguments?.putString(
                SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
                highlightMenuKey
            )
        }
        activity.supportFragmentManager
            .beginTransaction()
            .add(R.id.main_content, fragment)
            .commit()

        SettingsHomepageComposeInterop.setContent(
            composeView,
            onSearchClick = Runnable {
                val intent = FeatureFactory.featureFactory.searchFeatureProvider
                    .buildSearchIntent(activity, SettingsEnums.SETTINGS_HOMEPAGE)
                activity.startActivity(intent)
            },
            onAvatarClick = Runnable {
                val intent = Intent(android.provider.Settings.ACTION_USER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            },
            onPreferenceClick = java.util.function.Consumer { key ->
                handlePreferenceClick(key, fragment)
            },
            onWifiToggle = java.util.function.Consumer { enabled ->
                wifiManager?.isWifiEnabled = enabled
            },
            onAirplaneToggle = java.util.function.Consumer { enabled ->
                setAirplaneMode(connectivityManager, enabled)
            },
            onEditModeChanged = java.util.function.Consumer { editing ->
                editModeBackCallback.isEnabled = editing
            },
            userName = getUserName(),
            avatarBitmap = getAvatarBitmap(),
            isWifiEnabled = wifiManager?.isWifiEnabled ?: false,
            wifiSummary = null,
            isAirplaneModeOn = isAirplaneModeOn(),
            airplaneSummary = null,
            isCommunalAvailable = CommunalPreferenceController.isAvailable(activity),
            isSafetyCenterAvailable = SafetyCenterManagerWrapper.get().isEnabled(activity),
            isEmergencyAvailable = activity.resources.getBoolean(R.bool.config_show_emergency_settings),
            isSupportAvailable = FeatureFactory.featureFactory.supportFeatureProvider != null,
            isWellbeingAvailable = isActivityResolvable(WELLBEING_PKG, WELLBEING_ACTIVITY),
            isGoogleAvailable = isActivityResolvable(GOOGLE_PKG, GOOGLE_ACTIVITY)
        )

        return fragment
    }

    fun onStart() {
        registerUserInfoReceiver()
        registerWifiStateReceiver()
        registerAirplaneModeReceiver()
    }

    fun onResume() {
        SettingsHomepageComposeInterop.updateUserInfo(getUserName(), getAvatarBitmap())
        updateWifiState()
        SettingsHomepageComposeInterop.updateAirplaneMode(isAirplaneModeOn(), null)
    }

    fun onStop() {
        unregisterUserInfoReceiver()
        unregisterWifiStateReceiver()
        unregisterAirplaneModeReceiver()
    }

    private fun launchActivity(pkg: String, cls: String) {
        activity.startActivity(Intent()
            .setClassName(pkg, cls)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun handlePreferenceClick(key: String, fragment: TopLevelSettings) {
        when (key) {
            "top_level_wellbeing" -> { launchExternalSettings(WELLBEING_PKG, WELLBEING_ACTIVITY); return }
            "top_level_google" -> { launchExternalSettings(GOOGLE_PKG, GOOGLE_ACTIVITY); return }
        }
        val screen = fragment.preferenceScreen ?: return
        val pref = screen.findPreference<Preference>(key)
        if (pref != null) {
            fragment.onPreferenceTreeClick(pref)
        } else {
            Log.w(TAG, "Preference not found for key: $key")
        }
    }

    private fun launchExternalSettings(pkg: String, cls: String) {
        try {
            activity.startActivity(Intent().setClassName(pkg, cls))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch $pkg/$cls", e)
        }
    }

    private fun isActivityResolvable(pkg: String, cls: String): Boolean {
        return try {
            activity.packageManager.getActivityInfo(
                ComponentName(pkg, cls),
                PackageManager.MATCH_DEFAULT_ONLY
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(
            activity.findViewById(android.R.id.content)
        ) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(insets.left, 0, insets.right, 0)
            windowInsets
        }
    }

    private fun getUserName(): String {
        return try {
            activity.getSystemService(UserManager::class.java)?.userName ?: "User"
        } catch (e: Exception) {
            "User"
        }
    }

    private fun getAvatarBitmap(): Bitmap {
        val userManager = activity.getSystemService(UserManager::class.java)
        var bitmap = userManager?.getUserIcon(UserHandle.myUserId())
        if (bitmap == null) {
            val defaultIcon = UserIcons.getDefaultUserIcon(
                activity.resources, UserHandle.myUserId(), false
            )
            bitmap = UserIcons.convertToBitmap(defaultIcon)
        }
        return bitmap
    }

    private fun registerUserInfoReceiver() {
        if (userInfoReceiver != null) return
        userInfoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                SettingsHomepageComposeInterop.updateUserInfo(getUserName(), getAvatarBitmap())
            }
        }
        activity.registerReceiver(
            userInfoReceiver,
            IntentFilter(Intent.ACTION_USER_INFO_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterUserInfoReceiver() {
        userInfoReceiver?.let {
            activity.unregisterReceiver(it)
            userInfoReceiver = null
        }
    }

    private fun updateWifiState() {
        SettingsHomepageComposeInterop.updateWifiState(wifiManager?.isWifiEnabled ?: false, null)
    }

    private fun registerWifiStateReceiver() {
        if (wifiStateReceiver != null) return
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateWifiState()
            }
        }
        activity.registerReceiver(
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            Context.RECEIVER_EXPORTED
        )
        updateWifiState()
    }

    private fun unregisterWifiStateReceiver() {
        wifiStateReceiver?.let {
            activity.unregisterReceiver(it)
            wifiStateReceiver = null
        }
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(
            activity.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

    private fun setAirplaneMode(connectivityManager: ConnectivityManager?, enabled: Boolean) {
        try {
            connectivityManager?.setAirplaneMode(enabled)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set airplane mode to $enabled", e)
        }
    }

    private fun registerAirplaneModeReceiver() {
        if (airplaneModeReceiver != null) return
        airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                SettingsHomepageComposeInterop.updateAirplaneMode(isAirplaneModeOn(), null)
            }
        }
        activity.registerReceiver(
            airplaneModeReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterAirplaneModeReceiver() {
        airplaneModeReceiver?.let {
            activity.unregisterReceiver(it)
            airplaneModeReceiver = null
        }
    }
}
