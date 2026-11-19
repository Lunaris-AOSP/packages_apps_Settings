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
import android.os.BatteryStats.HistoryItem;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.util.Log;

import com.android.internal.os.BatteryStatsHistoryIterator;
import com.android.settingslib.utils.AsyncLoaderCompat;

import java.io.IOException;
import java.util.TimeZone;

/** Loads screen activity and charging intervals without doing binder or history work on the UI. */
final class BatteryUsageGraphLoader extends AsyncLoaderCompat<BatteryUsageGraphData> {
    private static final String TAG = "BatteryUsageGraphLoader";

    BatteryUsageGraphLoader(Context context) {
        super(context);
    }

    @Override
    public BatteryUsageGraphData loadInBackground() {
        synchronized (BatteryUsageResetter.LOCK) {
            final long now = System.currentTimeMillis();
            try {
                final long lastFullCharge = DatabaseUtils.getLastFullChargeTime(getContext());
                final long resetTimestamp = BatteryUsageResetter.getResetTimestamp(getContext());
                final BatteryUsageGraphData.Builder builder = new BatteryUsageGraphData.Builder(
                        now, resetTimestamp > 0 ? resetTimestamp : lastFullCharge,
                        TimeZone.getDefault(), resetTimestamp > 0);
                BatteryUsageGraphHistory history;
                try {
                    history = loadHistory(getContext(), now);
                } catch (RuntimeException | IOException e) {
                    Log.w(TAG, "Unable to refresh battery graph history", e);
                    history = loadSavedHistory(getContext(), now);
                }
                try {
                    history.save(getContext());
                } catch (RuntimeException | IOException e) {
                    Log.w(TAG, "Unable to save battery graph history", e);
                }
                history.addTo(builder);
                return builder.build();
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to load battery graph history", e);
                return new BatteryUsageGraphData.Builder(now, 0, TimeZone.getDefault()).build();
            }
        }
    }

    static BatteryUsageGraphHistory loadHistory(Context context, long now) throws IOException {
        synchronized (BatteryUsageResetter.LOCK) {
            final BatteryUsageGraphHistory history = loadSavedHistory(context, now);
            try (BatteryUsageStats stats = context.getSystemService(BatteryStatsManager.class)
                    .getBatteryUsageStats(new BatteryUsageStatsQuery.Builder()
                            .includeBatteryHistory()
                            .setPreferredHistoryDurationMs(now - history.getStartTimestamp())
                            .setMaxStatsAgeMs(0).build())) {
                readHistory(stats, history, now);
            }
            return history;
        }
    }

    static void recordHistory(Context context, BatteryUsageStats stats) throws IOException {
        synchronized (BatteryUsageResetter.LOCK) {
            final long now = System.currentTimeMillis();
            final BatteryUsageGraphHistory history = loadSavedHistory(context, now);
            readHistory(stats, history, now);
            history.save(context);
        }
    }

    private static BatteryUsageGraphHistory loadSavedHistory(Context context, long now) {
        try {
            return BatteryUsageGraphHistory.load(context, now, TimeZone.getDefault());
        } catch (IOException e) {
            Log.w(TAG, "Unable to read saved battery graph history", e);
            return new BatteryUsageGraphHistory(now, TimeZone.getDefault());
        }
    }

    private static void readHistory(BatteryUsageStats stats, BatteryUsageGraphHistory history,
            long now) {
        final long endTimestamp = Math.min(now, stats.getStatsEndTimestamp());
        if (endTimestamp < history.getEndTimestamp()) {
            return;
        }
        final BatteryUsageGraphHistory recent =
                new BatteryUsageGraphHistory(now, TimeZone.getDefault());
        try (BatteryStatsHistoryIterator iterator = stats.iterateBatteryStatsHistory()) {
            readHistory(iterator, recent, endTimestamp);
        }
        history.merge(recent);
    }

    private static void readHistory(BatteryStatsHistoryIterator iterator,
            BatteryUsageGraphHistory history, long endTimestamp) {
        long previousTime = 0;
        long previousMonotonicTime = 0;
        int level = -1;
        int states = 0;
        boolean charging = false;
        HistoryItem record;
        while ((record = iterator.next()) != null) {
            final long time = record.currentTime;
            final boolean discontinuity = record.cmd == HistoryItem.CMD_START
                    || record.cmd == HistoryItem.CMD_RESET
                    || record.cmd == HistoryItem.CMD_OVERFLOW
                    || Math.abs((time - previousTime)
                            - (record.time - previousMonotonicTime)) > 1000;
            if (previousTime > 0 && !discontinuity) {
                addInterval(history, previousTime, time, level, charging, states);
            }
            if (record.cmd == HistoryItem.CMD_UPDATE
                    || record.cmd == HistoryItem.CMD_CURRENT_TIME
                    || record.cmd == HistoryItem.CMD_RESET) {
                previousTime = time;
                previousMonotonicTime = record.time;
                level = record.batteryLevel;
                states = record.states;
                charging = record.batteryPlugType != 0;
            } else {
                previousTime = 0;
            }
        }
        if (previousTime > 0) {
            addInterval(history, previousTime, endTimestamp, level, charging, states);
        }
    }

    private static void addInterval(BatteryUsageGraphHistory history, long start, long end,
            int level, boolean charging, int states) {
        history.addInterval(start, end, level, charging,
                (states & HistoryItem.STATE_SCREEN_ON_FLAG) != 0,
                (states & HistoryItem.STATE_CPU_RUNNING_FLAG) != 0);
    }

    @Override
    protected void onDiscardResult(BatteryUsageGraphData result) {}
}
