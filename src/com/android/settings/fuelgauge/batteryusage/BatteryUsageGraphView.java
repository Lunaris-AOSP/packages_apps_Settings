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

package com.android.settings.fuelgauge.batteryusage;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;
import com.android.settings.fuelgauge.BatteryUtils;

import com.google.android.material.button.MaterialButton;

import java.util.function.Consumer;

/** The period selector, screen usage totals, and two battery history charts. */
public class BatteryUsageGraphView extends LinearLayout {
    private final MaterialButton mDay;
    private final MaterialButton mWeek;
    private final MaterialButton mReset;
    private final TextView mScreenOn;
    private final TextView mScreenOff;
    private final TextView mUsedTime;
    private final TextView mScreenTimeTitle;
    private final TextView mStatus;
    private final BatteryUsageBarChart mLevelChart;
    private final BatteryUsageBarChart mActivityChart;
    private BatteryUsageGraphData mData;
    private boolean mShowWeek;
    private Consumer<Boolean> mPeriodListener;
    private Runnable mResetListener;
    private boolean mResetting;
    private AlertDialog mDialog;

    public BatteryUsageGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.battery_usage_graph_content, this);
        mDay = findViewById(R.id.battery_graph_day);
        mWeek = findViewById(R.id.battery_graph_week);
        mReset = findViewById(R.id.battery_graph_reset);
        mScreenOn = findViewById(R.id.battery_graph_screen_on_time);
        mScreenOff = findViewById(R.id.battery_graph_screen_off_time);
        mUsedTime = findViewById(R.id.battery_graph_used_time);
        mScreenTimeTitle = findViewById(R.id.battery_graph_screen_time_title);
        mStatus = findViewById(R.id.battery_graph_status);
        mLevelChart = findViewById(R.id.battery_graph_level_chart);
        mActivityChart = findViewById(R.id.battery_graph_activity_chart);
        mDay.setCheckable(true);
        mWeek.setCheckable(true);
        mDay.setOnClickListener(view -> selectPeriod(false));
        mWeek.setOnClickListener(view -> selectPeriod(true));

        final TextView info = findViewById(R.id.battery_graph_usage_info);
        final android.graphics.drawable.Drawable icon = context.getDrawable(R.drawable.ic_info);
        final int iconSize = Math.round(13 * getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, iconSize, iconSize);
        info.setCompoundDrawablesRelative(null, null, icon, null);
        findViewById(R.id.battery_graph_usage_details).setOnClickListener(
                view -> mDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.battery_graph_since_reset)
                .setMessage(R.string.battery_graph_usage_info_reset)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        mReset.setOnClickListener(view -> mDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.battery_graph_reset_title)
                .setMessage(R.string.battery_graph_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.battery_graph_reset_confirm, (dialog, which) -> {
                    if (mResetListener != null && !mResetting) {
                        mResetListener.run();
                    }
                })
                .show());
        setLegend(R.id.battery_graph_charging_legend, R.color.battery_graph_charging);
        setLegend(R.id.battery_graph_on_legend, R.color.battery_graph_on);
        setLegend(R.id.battery_graph_off_legend, R.color.battery_graph_off);
        bind(null, false, null);
    }

    void bind(BatteryUsageGraphData data, boolean showWeek, Consumer<Boolean> listener) {
        mData = data;
        mShowWeek = showWeek;
        mPeriodListener = listener;
        update();
    }

    void setResetAction(Runnable listener, boolean resetting) {
        mResetListener = listener;
        mResetting = resetting;
        mReset.setVisibility(listener != null && BatteryUsageResetter.isAvailable(getContext())
                ? VISIBLE : GONE);
        mReset.setEnabled(!resetting);
        mReset.setText(resetting ? R.string.battery_graph_resetting : R.string.battery_graph_reset);
        mDay.setEnabled(!resetting);
        mWeek.setEnabled(!resetting);
        mLevelChart.setEnabled(!resetting);
        mActivityChart.setEnabled(!resetting);
    }

    void setOnBarSelectedListener(BatteryUsageBarChart.OnBarSelectedListener listener) {
        mLevelChart.setOnBarSelectedListener(listener);
        mActivityChart.setOnBarSelectedListener(listener);
    }

    void setSelectedRange(long start, long end) {
        mLevelChart.setSelectedRange(start, end);
        mActivityChart.setSelectedRange(start, end);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDetachedFromWindow();
    }

    private void selectPeriod(boolean showWeek) {
        if (mShowWeek == showWeek) {
            update();
            return;
        }
        mShowWeek = showWeek;
        update();
        if (mPeriodListener != null) {
            mPeriodListener.accept(showWeek);
        }
    }

    private void update() {
        mDay.setSelected(!mShowWeek);
        mWeek.setSelected(mShowWeek);
        mDay.setChecked(!mShowWeek);
        mWeek.setChecked(mShowWeek);
        mScreenTimeTitle.setText(mShowWeek ? R.string.battery_graph_daily_screen_time
                : R.string.battery_graph_hourly_screen_time);
        final BatteryUsageGraphData.Period period = mData == null ? null
                : mShowWeek ? mData.lastWeek : mData.lastDay;
        final boolean hasData = period != null && period.hasData;
        final String unavailable = getContext().getString(R.string.battery_graph_unavailable);
        mScreenOn.setText(hasData ? formatTime(getContext(), period.screenOn) : unavailable);
        mScreenOff.setText(hasData ? formatTime(getContext(), period.screenOff) : unavailable);
        mUsedTime.setText(mData != null && mData.hasFullChargeTime
                ? formatTime(getContext(), mData.activeSinceFullCharge) : unavailable);
        final TextView info = findViewById(R.id.battery_graph_usage_info);
        info.setText(R.string.battery_graph_since_reset);
        findViewById(R.id.battery_graph_usage_details).setContentDescription(
                info.getText() + ": " + mUsedTime.getText() + ". "
                        + getContext().getString(R.string.battery_graph_info_description));
        mLevelChart.setData(period, false, mShowWeek);
        mActivityChart.setData(period, true, mShowWeek);
        mStatus.setText(mData == null ? R.string.battery_graph_loading : R.string.battery_graph_empty);
        mStatus.setVisibility(hasData ? GONE : VISIBLE);
    }

    static CharSequence formatTime(Context context, long millis) {
        return BatteryUtils.formatElapsedTimeWithoutComma(context, millis,
                /* withSeconds= */ false, /* collapseTimeUnit= */ false);
    }

    private void setLegend(int viewId, int colorId) {
        final float density = getResources().getDisplayMetrics().density;
        final GradientDrawable marker = new GradientDrawable();
        marker.setColor(getContext().getColor(colorId));
        marker.setCornerRadius(2 * density);
        marker.setBounds(0, 0, Math.round(8 * density), Math.round(8 * density));
        ((TextView) findViewById(viewId)).setCompoundDrawablesRelative(marker, null, null, null);
    }
}
