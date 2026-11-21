/*
 * Copyright (C) 2024 Yet Another AOSP Project
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

package com.android.settings.sound;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.CustomSeekBarPreference;

/**
 * Volume step settings located in the sound menu.
 */
@SearchIndexable
public class VolumeSteps extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.volume_steps_settings);

        initVolumePreference("max_music_volume", 15);
        initVolumePreference("max_call_volume", 5);
        initVolumePreference("max_alarm_volume", 7);
    }

    private void initVolumePreference(String key, int fallbackDefault) {
        ContentResolver resolver = getContentResolver();
        final int defaultValue = Settings.System.getInt(resolver, "default_" + key,
                fallbackDefault);
        final int value = Settings.System.getInt(resolver, key, defaultValue);
        CustomSeekBarPreference preference = findPreference(key);
        preference.setDefaultValue(defaultValue);
        preference.setValue(value);
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(preference instanceof CustomSeekBarPreference)) {
            return false;
        }
        return Settings.System.putInt(getContentResolver(),
                preference.getKey(), (Integer) newValue);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.volume_steps_settings);
}
