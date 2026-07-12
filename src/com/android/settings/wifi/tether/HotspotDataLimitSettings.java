package com.android.settings.wifi.tether;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated screen for "One-time data limit" (HyperOS-style),
 * reached from the Hotspot settings screen.
 */
public class HotspotDataLimitSettings extends DashboardFragment {

    private static final String TAG = "HotspotDataLimitSettings";

    @Override
    public int getMetricsCategory() {
        return 0; // replace with a proper metrics constant if your ROM tracks these
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.hotspot_data_limit_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new HotspotDataLimitSizeController(context, "one_time_data_limit_size"));
        controllers.add(new HotspotDataLimitActionController(context, "one_time_data_limit_action"));
        return controllers;
    }
}
