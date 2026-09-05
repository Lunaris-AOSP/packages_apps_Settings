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
package com.android.settings.display;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.internal.util.ReadingModeApps.Mode;
import org.lineageos.internal.util.ReadingModeApps;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReadingModeAppsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SwitchPreferenceCompat mEnabledPref;
    private SwitchPreferenceCompat mOverridePref;
    private PreferenceCategory mAppCategory;
    private Set<String> mSelected;

    private static class AppEntry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;

        AppEntry(String packageName, CharSequence label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(screen);

        mSelected = new HashSet<>(ReadingModeApps.getSelectedApps(getContext()));

        mEnabledPref = new SwitchPreferenceCompat(getContext());
        mEnabledPref.setKey("reading_mode_enabled");
        mEnabledPref.setTitle(R.string.reading_mode_enable_title);
        mEnabledPref.setSummary(R.string.reading_mode_enable_summary);
        mEnabledPref.setChecked(ReadingModeApps.getMode(getContext()) != Mode.OFF);
        mEnabledPref.setOnPreferenceChangeListener(this);
        screen.addPreference(mEnabledPref);

        mOverridePref = new SwitchPreferenceCompat(getContext());
        mOverridePref.setKey("reading_mode_override");
        mOverridePref.setTitle(R.string.reading_mode_override_title);
        mOverridePref.setSummary(R.string.reading_mode_override_summary);
        mOverridePref.setChecked(ReadingModeApps.isOverrideEnabled(getContext()));
        mOverridePref.setOnPreferenceChangeListener(this);
        screen.addPreference(mOverridePref);

        mAppCategory = new PreferenceCategory(getContext());
        mAppCategory.setKey("reading_mode_apps_category");
        mAppCategory.setTitle(R.string.reading_mode_apps_title);
        screen.addPreference(mAppCategory);

        final Preference loading = new Preference(getContext());
        loading.setKey("loading");
        loading.setTitle(R.string.reading_mode_apps_loading);
        loading.setSelectable(false);
        mAppCategory.addPreference(loading);

        updateEnabledStates();
        loadApps();
    }

    private void loadApps() {
        final Context context = getContext().getApplicationContext();
        new Thread(() -> {
            final List<AppEntry> entries = queryLaunchableApps(context);
            mHandler.post(() -> {
                if (!isAdded() || getContext() == null) {
                    return;
                }
                populate(entries);
            });
        }, "ReadingModeAppsLoader").start();
    }

    private List<AppEntry> queryLaunchableApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        final List<ResolveInfo> resolved = pm.queryIntentActivities(launcherIntent, 0);
        final List<AppEntry> entries = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            final String packageName = info.activityInfo.packageName;
            if (packageName == null || !seen.add(packageName)) {
                continue;
            }
            entries.add(new AppEntry(packageName, info.loadLabel(pm), info.loadIcon(pm)));
        }

        final Collator collator = Collator.getInstance();
        Collections.sort(entries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry lhs, AppEntry rhs) {
                return collator.compare(lhs.label.toString(), rhs.label.toString());
            }
        });

        return entries;
    }

    private void populate(List<AppEntry> entries) {
        mAppCategory.removeAll();

        for (AppEntry entry : entries) {
            final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(getContext());
            pref.setPersistent(false);
            pref.setKey(entry.packageName);
            pref.setTitle(entry.label);
            pref.setSummary(entry.packageName);
            pref.setIcon(entry.icon);
            pref.setOnPreferenceChangeListener(this);
            mAppCategory.addPreference(pref);
            pref.setChecked(mSelected.contains(entry.packageName));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnabledPref) {
            final boolean masterEnabled = (Boolean) newValue;
            ReadingModeApps.setMode(getContext(), masterEnabled ? Mode.PER_APP : Mode.OFF);
            mOverridePref.setChecked(false);
            updateEnabledStates();
            return true;
        }

        if (preference == mOverridePref) {
            final boolean overrideEnabled = (Boolean) newValue;
            ReadingModeApps.setOverrideEnabled(getContext(), overrideEnabled);
            updateEnabledStates();
            return true;
        }

        final String packageName = preference.getKey();
        if ((Boolean) newValue) {
            mSelected.add(packageName);
        } else {
            mSelected.remove(packageName);
        }
        ReadingModeApps.setSelectedApps(getContext(), mSelected);
        return true;
    }

    private void updateEnabledStates() {
        final boolean masterEnabled = mEnabledPref.isChecked();
        mOverridePref.setEnabled(masterEnabled);
        mAppCategory.setEnabled(masterEnabled && !mOverridePref.isChecked());
    }
}
