/*
 * Copyright (C) 2026 Project ASCP
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

package com.android.settings.wifi.tether;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;

/**
 * Controls the "One-time data limit" entry row shown on the main Hotspot
 * settings screen. Tapping it navigates (via android:fragment in the XML)
 * to {@link HotspotDataLimitSettings}. This controller only keeps the
 * summary ("On"/"Off") up to date.
 */
public class HotspotDataLimitEntryPreferenceController extends BasePreferenceController {

    private static final String KEY_ENABLED = "hotspot_data_limit_enabled";

    private Preference mPreference;

    public HotspotDataLimitEntryPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        updateSummary();
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        mPreference = preference;
        updateSummary();
    }

    private void updateSummary() {
        if (mPreference == null) {
            return;
        }
        boolean enabled = Settings.Global.getInt(
                mContext.getContentResolver(), KEY_ENABLED, 0) == 1;
        mPreference.setSummary(enabled ? "On" : "Off");
    }
}
