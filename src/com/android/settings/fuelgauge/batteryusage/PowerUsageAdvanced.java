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

import static com.android.settings.fuelgauge.BatteryBroadcastReceiver.BatteryUpdateType;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.SearchIndexableResource;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.fuelgauge.BatteryBroadcastReceiver;
import com.android.settings.fuelgauge.PowerUsageFeatureProvider;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.AsyncLoaderCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/** Advanced power usage. */
// LINT.IfChange
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class PowerUsageAdvanced extends PowerUsageBase {
    private static final String TAG = "AdvancedBatteryUsage";
    private static final String KEY_REFRESH_TYPE = "refresh_type";
    private static final String KEY_BATTERY_CHART = "battery_chart";
    private static final int BATTERY_USAGE_GRAPH_LOADER = 4;
    private static final int BATTERY_USAGE_RESET_LOADER = 5;

    @VisibleForTesting
    BatteryHistoryPreference mHistPref;

    @VisibleForTesting
    final BatteryLevelDataLoaderCallbacks mBatteryLevelDataLoaderCallbacks =
            new BatteryLevelDataLoaderCallbacks();

    private final LoaderManager.LoaderCallbacks<BatteryUsageGraphData> mGraphLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<BatteryUsageGraphData>() {
                @Override
                public Loader<BatteryUsageGraphData> onCreateLoader(int id, Bundle args) {
                    return new BatteryUsageGraphLoader(getContext().getApplicationContext());
                }

                @Override
                public void onLoadFinished(Loader<BatteryUsageGraphData> loader,
                        BatteryUsageGraphData data) {
                    if (mHistPref != null && !mResetting) {
                        mHistPref.setGraphData(data);
                    }
                }

                @Override
                public void onLoaderReset(Loader<BatteryUsageGraphData> loader) {}
            };

    private final LoaderManager.LoaderCallbacks<Boolean> mResetLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<Boolean>() {
                @Override
                public Loader<Boolean> onCreateLoader(int id, Bundle args) {
                    return new AsyncLoaderCompat<Boolean>(requireContext().getApplicationContext()) {
                        @Override
                        public Boolean loadInBackground() {
                            try {
                                BatteryUsageResetter.reset(getContext());
                                return true;
                            } catch (IOException | RemoteException | RuntimeException e) {
                                Log.e(TAG, "Unable to reset battery usage", e);
                                return false;
                            }
                        }

                        @Override
                        protected void onDiscardResult(Boolean result) {}
                    };
                }

                @Override
                public void onLoadFinished(Loader<Boolean> loader, Boolean success) {
                    mResetting = false;
                    mHistPref.setResetAction(PowerUsageAdvanced.this::resetBatteryUsage, false);
                    mHistPref.setGraphData(null);
                    getLoaderManagerForCurrentFragment().destroyLoader(BATTERY_USAGE_RESET_LOADER);
                    Toast.makeText(requireContext(), Boolean.TRUE.equals(success)
                            ? R.string.battery_graph_reset_success : R.string.battery_graph_reset_failed,
                            Toast.LENGTH_LONG).show();
                    mIsChartDataLoaded = false;
                    restartBatteryStatsLoader(BatteryUpdateType.MANUAL);
                }

                @Override
                public void onLoaderReset(Loader<Boolean> loader) {}
            };

    private boolean mIsChartDataLoaded = false;
    private volatile boolean mResetting;
    private volatile int mDataGeneration;
    private long mStartTimestamp;
    private Map<Integer, Map<Integer, BatteryDiffData>> mBatteryUsageMap;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mBatteryObserver =
            new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    Log.d(TAG, "onBatteryContentChange: " + selfChange);
                    mIsChartDataLoaded = false;
                    restartBatteryStatsLoader(BatteryBroadcastReceiver.BatteryUpdateType.MANUAL);
                }
            };

    @VisibleForTesting BatteryTipsController mBatteryTipsController;
    @VisibleForTesting BatteryChartPreferenceController mBatteryChartPreferenceController;
    @VisibleForTesting ScreenOnTimeController mScreenOnTimeController;
    @VisibleForTesting BatteryUsageBreakdownController mBatteryUsageBreakdownController;
    @VisibleForTesting Optional<BatteryLevelData> mBatteryLevelData;
    @VisibleForTesting Optional<AnomalyEventWrapper> mHighlightEventWrapper;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mHistPref = findPreference(KEY_BATTERY_CHART);
        setBatteryChartPreferenceController();
        final LoaderManager loaderManager = getLoaderManagerForCurrentFragment();
        mResetting = loaderManager.getLoader(BATTERY_USAGE_RESET_LOADER) != null;
        mHistPref.setResetAction(this::resetBatteryUsage, mResetting);
        if (mResetting) {
            loaderManager.initLoader(BATTERY_USAGE_RESET_LOADER, null, mResetLoaderCallbacks);
        }
        AsyncTask.execute(() -> {
            if (getContext() != null) {
                BootBroadcastReceiver.invokeJobRecheck(getContext());
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity().isChangingConfigurations()) {
            BatteryEntry.clearUidCache();
        }
        mExecutor.shutdown();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FUELGAUGE_BATTERY_HISTORY_DETAIL;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.power_usage_advanced;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Resets the flag to reload usage data in onResume() callback.
        mIsChartDataLoaded = false;
        final Uri uri = DatabaseUtils.BATTERY_CONTENT_URI;
        if (uri != null) {
            getContext().getContentResolver().unregisterContentObserver(mBatteryObserver);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mStartTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onResume() {
        super.onResume();
        final Uri uri = DatabaseUtils.BATTERY_CONTENT_URI;
        if (uri != null) {
            getContext()
                    .getContentResolver()
                    .registerContentObserver(uri, /*notifyForDescendants*/ true, mBatteryObserver);
        }
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        final RecyclerView.Adapter adapter = super.onCreateAdapter(preferenceScreen);
        adapter.setHasStableIds(true);
        return adapter;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        mBatteryTipsController = new BatteryTipsController(context);
        mBatteryChartPreferenceController =
                new BatteryChartPreferenceController(
                        context, getSettingsLifecycle(), (SettingsActivity) getActivity());
        mBatteryUsageBreakdownController =
                new BatteryUsageBreakdownController(
                        context, getSettingsLifecycle(), (SettingsActivity) getActivity(), this);

        controllers.add(mBatteryTipsController);
        controllers.add(mBatteryChartPreferenceController);
        controllers.add(mBatteryUsageBreakdownController);
        setBatteryChartPreferenceController();
        mBatteryChartPreferenceController.setOnSelectedIndexUpdatedListener(
                this::onSelectedSlotDataUpdated);

        // Force UI refresh if battery usage data was loaded before UI initialization.
        onSelectedSlotDataUpdated();
        return controllers;
    }

    @Override
    protected void refreshUi(@BatteryUpdateType int refreshType) {
        // Do nothing
    }

    @Override
    protected void restartBatteryStatsLoader(int refreshType) {
        if (mResetting) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putInt(KEY_REFRESH_TYPE, refreshType);
        if (!mIsChartDataLoaded) {
            mIsChartDataLoaded = true;
            mDataGeneration++;
            restartLoader(BATTERY_USAGE_GRAPH_LOADER, bundle, mGraphLoaderCallbacks);
            mBatteryLevelData = null;
            mBatteryUsageMap = null;
            mHighlightEventWrapper = null;
            restartLoader(
                    LoaderIndex.BATTERY_LEVEL_DATA_LOADER,
                    bundle,
                    mBatteryLevelDataLoaderCallbacks);
        }
    }

    private void resetBatteryUsage() {
        if (mResetting || !BatteryUsageResetter.isAvailable(requireContext())) {
            return;
        }
        mResetting = true;
        mDataGeneration++;
        mBatteryUsageMap = null;
        mBatteryLevelData = null;
        mHighlightEventWrapper = null;
        mHistPref.setResetAction(this::resetBatteryUsage, true);
        mHistPref.setGraphData(null);
        mBatteryChartPreferenceController.onBatteryLevelDataUpdate(null);
        findPreference("battery_usage_breakdown").setVisible(false);
        findPreference("battery_tips_card").setVisible(false);
        final LoaderManager loaderManager = getLoaderManagerForCurrentFragment();
        loaderManager.destroyLoader(BATTERY_USAGE_GRAPH_LOADER);
        loaderManager.destroyLoader(LoaderIndex.BATTERY_LEVEL_DATA_LOADER);
        loaderManager.initLoader(BATTERY_USAGE_RESET_LOADER, null, mResetLoaderCallbacks);
    }

    private void onBatteryLevelDataUpdate(BatteryLevelData batteryLevelData) {
        if (!isVisible() && !isResumed()) {
            Log.w(TAG, "onBatteryLevelDataUpdate: fragment not ready");
            return;
        }
        mBatteryLevelData = Optional.ofNullable(batteryLevelData);
        if (mBatteryChartPreferenceController != null) {
            mBatteryChartPreferenceController.onBatteryLevelDataUpdate(batteryLevelData);
            Log.d(
                    TAG,
                    String.format(
                            "Battery chart shows in %d millis",
                            System.currentTimeMillis() - mStartTimestamp));
        }
    }

    private void onBatteryDiffDataMapUpdate(
            Map<Long, BatteryDiffData> batteryDiffDataMap, int generation) {
        if (mResetting || generation != mDataGeneration) {
            return;
        }
        if (!isVisible() && !isResumed()) {
            Log.w(TAG, "onBatteryDiffDataMapUpdate: fragment not ready");
            return;
        }
        if (mBatteryLevelData == null) {
            Log.w(TAG, "onBatteryDiffDataMapUpdate: batteryLevelData is null");
            return;
        }
        mHandler.post(() -> {
            if (mResetting || generation != mDataGeneration || !isAdded()) {
                return;
            }
            mBatteryUsageMap =
                    DataProcessor.generateBatteryUsageMap(
                            getContext(), batteryDiffDataMap, mBatteryLevelData.orElse(null));
            Log.d(TAG, "onBatteryDiffDataMapUpdate: " + mBatteryUsageMap);
            DataProcessor.loadLabelAndIcon(mBatteryUsageMap);
            onSelectedSlotDataUpdated();
            detectAnomaly();
            logScreenUsageTime();
            if (mBatteryChartPreferenceController != null
                    && mBatteryLevelData.isEmpty()
                    && isBatteryUsageMapNullOrEmpty()) {
                // No available battery usage and battery level data.
                mBatteryChartPreferenceController.showEmptyChart();
            }
        });
    }

    private void onSelectedSlotDataUpdated() {
        if (mBatteryChartPreferenceController == null
                || mBatteryUsageBreakdownController == null
                || mBatteryUsageMap == null) {
            return;
        }
        final int dailyIndex = mBatteryChartPreferenceController.getDailyChartIndex();
        final int hourlyIndex = mBatteryChartPreferenceController.getHourlyChartIndex();
        final String slotInformation =
                mBatteryChartPreferenceController.getSlotInformation(
                        /* isAccessibilityText= */ false);
        final String accessibilitySlotInformation =
                mBatteryChartPreferenceController.getSlotInformation(
                        /* isAccessibilityText= */ true);
        final Map<Integer, BatteryDiffData> dayUsageData = mBatteryUsageMap.get(dailyIndex);
        final BatteryDiffData slotUsageData =
                dayUsageData == null ? null : dayUsageData.get(hourlyIndex);
        // Hide card tips if the related highlight slot was clicked.
        if (isAppsAnomalyEventFocused()) {
            mBatteryTipsController.acceptTipsCard();
        }
        mBatteryUsageBreakdownController.handleBatteryUsageUpdated(
                slotUsageData,
                slotInformation,
                accessibilitySlotInformation,
                isBatteryUsageMapNullOrEmpty(),
                isAppsAnomalyEventFocused(),
                mHighlightEventWrapper);
        Log.d(
                TAG,
                String.format(
                        "Battery usage list shows in %d millis",
                        System.currentTimeMillis() - mStartTimestamp));
    }

    private void detectAnomaly() {
        final int generation = mDataGeneration;
        mExecutor.execute(
                () -> {
                    final PowerUsageFeatureProvider powerUsageFeatureProvider =
                            FeatureFactory.getFeatureFactory().getPowerUsageFeatureProvider();
                    final PowerAnomalyEventList anomalyEventList =
                            powerUsageFeatureProvider.detectPowerAnomaly(
                                    getContext(),
                                    /* displayDrain= */ 0,
                                    DetectRequestSourceType.TYPE_USAGE_UI);
                    mHandler.post(() -> {
                        if (!mResetting && generation == mDataGeneration) {
                            onAnomalyDetected(anomalyEventList);
                        }
                    });
                });
    }

    private void onAnomalyDetected(PowerAnomalyEventList anomalyEventList) {
        if (!isVisible() && !isResumed()) {
            Log.w(TAG, "onAnomalyDetected: fragment not ready");
            return;
        }
        if (anomalyEventList == null) {
            Log.d(TAG, "onAnomalyDetected: anomalyEventList is null");
            return;
        }
        logPowerAnomalyEventList(anomalyEventList);
        final Set<String> dismissedPowerAnomalyKeys =
                DatabaseUtils.getDismissedPowerAnomalyKeys(getContext());
        Log.d(TAG, "dismissedPowerAnomalyKeys = " + dismissedPowerAnomalyKeys);

        // Choose an app anomaly event with highest score to show highlight slot
        final PowerAnomalyEvent highlightEvent =
                getAnomalyEvent(anomalyEventList, PowerAnomalyEvent::hasWarningItemInfo);
        // Choose an event never dismissed to show as card.
        // If the slot is already highlighted, the tips card should be the corresponding app
        // or settings anomaly event.
        final PowerAnomalyEvent tipsCardEvent =
                getAnomalyEvent(
                        anomalyEventList,
                        event ->
                                !dismissedPowerAnomalyKeys.contains(event.getDismissRecordKey())
                                        && (event.equals(highlightEvent)
                                                || !event.hasWarningItemInfo()));
        onDisplayAnomalyEventUpdated(tipsCardEvent, highlightEvent);
    }

    @VisibleForTesting
    void onDisplayAnomalyEventUpdated(
            PowerAnomalyEvent tipsCardEvent, PowerAnomalyEvent highlightEvent) {
        if (mBatteryTipsController == null
                || mBatteryChartPreferenceController == null
                || mBatteryUsageBreakdownController == null) {
            return;
        }

        final boolean isSameAnomalyEvent = (tipsCardEvent == highlightEvent);
        // Update battery tips card preference & behaviour
        mBatteryTipsController.setOnAnomalyConfirmListener(null);
        mBatteryTipsController.setOnAnomalyRejectListener(null);
        final AnomalyEventWrapper tipsCardEventWrapper =
                (tipsCardEvent == null)
                        ? null
                        : new AnomalyEventWrapper(getContext(), tipsCardEvent);
        if (tipsCardEventWrapper != null) {
            tipsCardEventWrapper.setRelatedBatteryDiffEntry(
                    findRelatedBatteryDiffEntry(tipsCardEventWrapper));
        }
        mBatteryTipsController.handleBatteryTipsCardUpdated(
                tipsCardEventWrapper, isSameAnomalyEvent);

        // Update highlight slot effect in battery chart view
        Pair<Integer, Integer> highlightSlotIndexPair =
                Pair.create(
                        BatteryChartViewModel.SELECTED_INDEX_INVALID,
                        BatteryChartViewModel.SELECTED_INDEX_INVALID);
        mHighlightEventWrapper =
                Optional.ofNullable(
                        isSameAnomalyEvent
                                ? tipsCardEventWrapper
                                : ((highlightEvent != null)
                                        ? new AnomalyEventWrapper(getContext(), highlightEvent)
                                        : null));
        if (mBatteryLevelData != null
                && mBatteryLevelData.isPresent()
                && mHighlightEventWrapper.isPresent()
                && mHighlightEventWrapper.get().hasHighlightSlotPair(mBatteryLevelData.get())) {
            highlightSlotIndexPair =
                    mHighlightEventWrapper.get().getHighlightSlotPair(mBatteryLevelData.get());
            if (isSameAnomalyEvent) {
                // For main button, focus on highlight slot when clicked
                mBatteryTipsController.setOnAnomalyConfirmListener(
                        () -> {
                            mBatteryChartPreferenceController.selectHighlightSlotIndex();
                            mBatteryTipsController.acceptTipsCard();
                        });
            }
        }
        mBatteryChartPreferenceController.onHighlightSlotIndexUpdate(
                highlightSlotIndexPair.first, highlightSlotIndexPair.second);
    }

    @VisibleForTesting
    BatteryDiffEntry findRelatedBatteryDiffEntry(AnomalyEventWrapper eventWrapper) {
        if (eventWrapper == null
                || mBatteryLevelData == null
                || mBatteryLevelData.isEmpty()
                || !eventWrapper.hasHighlightSlotPair(mBatteryLevelData.get())
                || !eventWrapper.hasAnomalyEntryKey()
                || mBatteryUsageMap == null) {
            return null;
        }
        final Pair<Integer, Integer> highlightSlotIndexPair =
                eventWrapper.getHighlightSlotPair(mBatteryLevelData.get());
        final BatteryDiffData relatedDiffData =
                mBatteryUsageMap
                        .get(highlightSlotIndexPair.first)
                        .get(highlightSlotIndexPair.second);
        final String anomalyEntryKey = eventWrapper.getAnomalyEntryKey();
        if (relatedDiffData == null || anomalyEntryKey == null) {
            return null;
        }
        for (BatteryDiffEntry entry : relatedDiffData.getAppDiffEntryList()) {
            if (anomalyEntryKey.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    private void setBatteryChartPreferenceController() {
        if (mHistPref != null && mBatteryChartPreferenceController != null) {
            mHistPref.setChartPreferenceController(mBatteryChartPreferenceController);
        }
    }

    private boolean isBatteryUsageMapNullOrEmpty() {
        final BatteryDiffData allBatteryDiffData = getAllBatteryDiffData(mBatteryUsageMap);
        // If all data is null or empty, each slot must be null or empty.
        return allBatteryDiffData == null
                || (allBatteryDiffData.getAppDiffEntryList().isEmpty()
                        && allBatteryDiffData.getSystemDiffEntryList().isEmpty());
    }

    private boolean isAppsAnomalyEventFocused() {
        return mBatteryChartPreferenceController != null
                && mBatteryChartPreferenceController.isHighlightSlotFocused();
    }

    private void logScreenUsageTime() {
        final BatteryDiffData allBatteryDiffData = getAllBatteryDiffData(mBatteryUsageMap);
        if (allBatteryDiffData == null) {
            return;
        }
        long totalForegroundUsageTime = 0;
        for (final BatteryDiffEntry entry : allBatteryDiffData.getAppDiffEntryList()) {
            totalForegroundUsageTime += entry.mForegroundUsageTimeInMs;
        }
        mMetricsFeatureProvider.action(
                getContext(),
                SettingsEnums.ACTION_BATTERY_USAGE_SCREEN_ON_TIME,
                (int) allBatteryDiffData.getScreenOnTime());
        mMetricsFeatureProvider.action(
                getContext(),
                SettingsEnums.ACTION_BATTERY_USAGE_FOREGROUND_USAGE_TIME,
                (int) totalForegroundUsageTime);
    }

    @VisibleForTesting
    static PowerAnomalyEvent getAnomalyEvent(
            PowerAnomalyEventList anomalyEventList, Predicate<PowerAnomalyEvent> predicate) {
        if (anomalyEventList == null || anomalyEventList.getPowerAnomalyEventsCount() == 0) {
            return null;
        }

        final PowerAnomalyEvent filterAnomalyEvent =
                anomalyEventList.getPowerAnomalyEventsList().stream()
                        .filter(predicate)
                        .max(Comparator.comparing(PowerAnomalyEvent::getScore))
                        .orElse(null);
        Log.d(TAG, "filterAnomalyEvent = "
                + (filterAnomalyEvent == null ? null : filterAnomalyEvent.getEventId()));
        return filterAnomalyEvent;
    }

    private static void logPowerAnomalyEventList(PowerAnomalyEventList anomalyEventList) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (PowerAnomalyEvent anomalyEvent : anomalyEventList.getPowerAnomalyEventsList()) {
            stringBuilder.append(anomalyEvent.getEventId()).append(", ");
        }
        Log.d(TAG, "anomalyEventList = [" + stringBuilder + "]");
    }

    private static BatteryDiffData getAllBatteryDiffData(
            Map<Integer, Map<Integer, BatteryDiffData>> batteryUsageMap) {
        return batteryUsageMap == null
                ? null
                : batteryUsageMap
                        .get(BatteryChartViewModel.SELECTED_INDEX_ALL)
                        .get(BatteryChartViewModel.SELECTED_INDEX_ALL);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.power_usage_advanced;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    final List<AbstractPreferenceController> controllers = new ArrayList<>();
                    controllers.add(
                            new BatteryChartPreferenceController(
                                    context, null /* lifecycle */, null /* activity */));
                    controllers.add(
                            new BatteryUsageBreakdownController(
                                    context,
                                    null /* lifecycle */,
                                    null /* activity */,
                                    null /* fragment */));
                    controllers.add(new BatteryTipsController(context));
                    return controllers;
                }
            };

    private class BatteryLevelDataLoaderCallbacks
            implements LoaderManager.LoaderCallbacks<BatteryLevelData> {
        @Override
        public Loader<BatteryLevelData> onCreateLoader(int id, Bundle bundle) {
            final int generation = mDataGeneration;
            return new AsyncLoaderCompat<BatteryLevelData>(getContext().getApplicationContext()) {
                @Override
                protected void onDiscardResult(BatteryLevelData result) {}

                @Override
                public BatteryLevelData loadInBackground() {
                    Context context = getContext();
                    return DataProcessManager.getBatteryLevelData(
                            context,
                            getLifecycle(),
                            new UserIdsSeries(context, /* isNonUIRequest= */ false),
                            /* isFromPeriodJob= */ false,
                            data -> onBatteryDiffDataMapUpdate(data, generation));
                }
            };
        }

        @Override
        public void onLoadFinished(
                Loader<BatteryLevelData> loader, BatteryLevelData batteryLevelData) {
            PowerUsageAdvanced.this.onBatteryLevelDataUpdate(batteryLevelData);
        }

        @Override
        public void onLoaderReset(Loader<BatteryLevelData> loader) {}
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return PowerUsageAdvancedScreen.KEY;
    }
}
// LINT.ThenChange(PowerUsageAdvancedScreen.kt)
