/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.fuelgauge.BatteryUtils;
import com.android.settingslib.widget.GroupSectionDividerMixin;
import com.android.settingslib.widget.NormalPaddingMixin;

/** Custom preference for displaying the battery level as chart graph. */
public class BatteryHistoryPreference extends Preference
        implements GroupSectionDividerMixin, NormalPaddingMixin {
    private static final String TAG = "BatteryHistoryPreference";

    private BatteryUsageGraphView mGraphView;
    private BatteryChartView mDailyChartView;
    private BatteryChartView mHourlyChartView;
    private BatteryUsageGraphData mGraphData;
    private boolean mShowWeek;
    private boolean mResetting;
    private Runnable mResetListener;
    private BatteryChartPreferenceController mChartPreferenceController;

    public BatteryHistoryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.battery_usage_graph);
        setSelectable(false);
    }

    void setChartPreferenceController(BatteryChartPreferenceController controller) {
        mChartPreferenceController = controller;
        if (mGraphView != null) {
            mChartPreferenceController.setBatteryUsageGraphView(mGraphView);
        } else if (mDailyChartView != null && mHourlyChartView != null) {
            mChartPreferenceController.setBatteryChartView(mDailyChartView, mHourlyChartView);
        }
    }

    void setGraphData(BatteryUsageGraphData data) {
        mGraphData = data;
        bindGraph();
    }

    void setResetAction(Runnable listener, boolean resetting) {
        mResetListener = listener;
        mResetting = resetting;
        if (mGraphView != null) {
            mGraphView.setResetAction(listener, resetting);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        final long startTime = System.currentTimeMillis();
        view.setDividerAllowedAbove(false);
        view.setDividerAllowedBelow(false);
        mDailyChartView = null;
        mHourlyChartView = null;
        final View graph = view.itemView instanceof BatteryUsageGraphView
                ? view.itemView : view.findViewById(R.id.battery_usage_graph);
        mGraphView = graph instanceof BatteryUsageGraphView ? (BatteryUsageGraphView) graph : null;
        if (mChartPreferenceController != null) {
            mChartPreferenceController.setBatteryUsageGraphView(mGraphView);
        }
        if (mGraphView == null) {
            final View dailyChart = view.findViewById(R.id.daily_battery_chart);
            final View hourlyChart = view.findViewById(R.id.hourly_battery_chart);
            if (!(dailyChart instanceof BatteryChartView)
                    || !(hourlyChart instanceof BatteryChartView)) {
                Log.w(TAG, "Battery history layout does not contain chart views");
                return;
            }
            final View companionView = view.findViewById(R.id.companion_text);
            final TextView companion = companionView instanceof TextView
                    ? (TextView) companionView : null;
            mDailyChartView = (BatteryChartView) dailyChart;
            mHourlyChartView = (BatteryChartView) hourlyChart;
            mDailyChartView.setCompanionTextView(companion);
            mHourlyChartView.setCompanionTextView(companion);
            if (mChartPreferenceController != null) {
                mChartPreferenceController.setBatteryChartView(mDailyChartView, mHourlyChartView);
            }
            return;
        }
        bindGraph();
        BatteryUtils.logRuntime(TAG, "onBindViewHolder", startTime);
    }

    private void bindGraph() {
        if (mGraphView != null) {
            mGraphView.setResetAction(mResetListener, mResetting);
            mGraphView.bind(mGraphData, mShowWeek, showWeek -> {
                mShowWeek = showWeek;
                if (mChartPreferenceController != null) {
                    mChartPreferenceController.clearGraphSelection();
                }
            });
        }
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (mChartPreferenceController != null) {
            mChartPreferenceController.setBatteryUsageGraphView(null);
        }
        mGraphView = null;
        mDailyChartView = null;
        mHourlyChartView = null;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final SavedState state = new SavedState(super.onSaveInstanceState());
        state.showWeek = mShowWeek;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        final SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mShowWeek = savedState.showWeek;
        bindGraph();
    }

    private static class SavedState extends BaseSavedState {
        boolean showWeek;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel source) {
            super(source);
            showWeek = source.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeInt(showWeek ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel source) {
                        return new SavedState(source);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
