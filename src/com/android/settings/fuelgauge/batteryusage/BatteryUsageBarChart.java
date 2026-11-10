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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.android.settings.R;
import com.android.settings.Utils;

import java.util.Collections;
import java.util.List;

/** Narrow, rounded bars sharing a time axis for battery level and active screen usage. */
public class BatteryUsageBarChart extends View {
    interface OnBarSelectedListener {
        void onBarSelected(long timestamp, boolean daily);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mOn;
    private final int mOff;
    private final int mCharging;
    private final int mText;
    private final int mGrid;
    private final float mDensity;
    private final float mTextSize;
    private List<BatteryUsageGraphData.Bar> mBars = Collections.emptyList();
    private boolean mActivity;
    private boolean mWeek;
    private double mMaximum = 100;
    private long mSelectedStart;
    private long mSelectedEnd;
    private float mTouchX;
    private float mTouchY;
    private int mTouchIndex = -1;
    private final int mTouchSlop;
    private final ChartAccessibilityHelper mAccessibilityHelper;
    private OnBarSelectedListener mListener;

    public BatteryUsageBarChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = getResources().getDisplayMetrics().density;
        mTextSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics());
        mOn = context.getColor(R.color.battery_graph_on);
        mOff = context.getColor(R.color.battery_graph_off);
        mCharging = context.getColor(R.color.battery_graph_charging);
        mText = context.getColor(R.color.battery_graph_secondary);
        mGrid = context.getColor(R.color.battery_graph_outline);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mPaint.setTypeface(Typeface.create(context.getString(
                com.android.internal.R.string.config_bodyFontFamily), Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        mAccessibilityHelper = new ChartAccessibilityHelper();
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper);
        setFocusable(true);
    }

    void setData(BatteryUsageGraphData.Period period, boolean activity, boolean week) {
        mBars = period == null ? Collections.emptyList() : period.bars;
        mActivity = activity;
        mWeek = week;
        mMaximum = activity ? BatteryUsageGraphData.HOUR : 100;
        if (activity && week) {
            mMaximum = 24 * BatteryUsageGraphData.HOUR;
            for (BatteryUsageGraphData.Bar bar : mBars) {
                mMaximum = Math.max(mMaximum, bar.screenOn + bar.screenOff);
            }
        }
        updateContentDescription();
        mAccessibilityHelper.invalidateRoot();
        invalidate();
    }

    void setOnBarSelectedListener(OnBarSelectedListener listener) {
        mListener = listener;
        setClickable(listener != null);
    }

    void setSelectedRange(long start, long end) {
        if (mSelectedStart == start && mSelectedEnd == end) {
            return;
        }
        mSelectedStart = start;
        mSelectedEnd = end;
        mAccessibilityHelper.invalidateRoot();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setTextSize(mTextSize);
        final float top = mTextSize / 2 + dp(2);
        final float bottom = getHeight() - mTextSize * 2 - dp(12);
        final float left = plotLeft();
        final float right = getWidth() - dp(8);
        if (right <= left || bottom <= top) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            final float fraction = i / 2f;
            final float y = bottom - fraction * (bottom - top);
            mPaint.setColor(mGrid);
            mPaint.setStrokeWidth(dp(0.5f));
            mPaint.setAlpha(100);
            canvas.drawLine(left, y, right, y, mPaint);
            mPaint.setAlpha(255);
            mPaint.setColor(mText);
            mPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(axisLabel(fraction), 0, y + mTextSize / 3, mPaint);
        }
        if (mBars.isEmpty()) {
            return;
        }

        final float step = (right - left) / mBars.size();
        final float width = Math.min(dp(mWeek ? 12 : 5.5f), step * 0.52f);
        for (int i = 0; i < mBars.size(); i++) {
            final BatteryUsageGraphData.Bar bar = mBars.get(i);
            final float x = left + (i + 0.5f) * step;
            if (isSelected(bar)) {
                mPaint.setColor(mGrid);
                canvas.drawRoundRect(x - step * 0.4f, top, x + step * 0.4f, bottom,
                        dp(4), dp(4), mPaint);
            }
            if (mActivity) {
                final float on = (float) (bar.screenOn / mMaximum) * (bottom - top);
                final float off = (float) (bar.screenOff / mMaximum) * (bottom - top);
                if (on + off > 0) {
                    drawBar(canvas, x, bottom - on - off, bottom, width, mOff);
                    if (on > 0) {
                        drawBar(canvas, x, bottom - on, bottom, width, mOn);
                    }
                }
            } else if (bar.level >= 0) {
                final float height = bar.level / 100f * (bottom - top);
                drawBar(canvas, x, bottom - height, bottom, width,
                        bar.charging ? mCharging : mOn);
            }
        }
        drawTimeLabels(canvas, left, right, bottom + mTextSize + dp(12), step);
    }

    private void drawBar(Canvas canvas, float x, float top, float bottom, float width, int color) {
        if (bottom <= top) {
            return;
        }
        mPaint.setColor(color);
        canvas.drawRoundRect(x - width / 2, top, x + width / 2, bottom,
                width / 2, width / 2, mPaint);
    }

    private void drawTimeLabels(Canvas canvas, float left, float right, float y, float step) {
        mPaint.setColor(mText);
        final String now = getContext().getString(R.string.battery_graph_now);
        final float nowWidth = mPaint.measureText(now);
        float previousRight = -dp(8);
        final int stride = mWeek ? 1 : 4;
        for (int i = 0; i < mBars.size(); i += stride) {
            final String label = shortTimeLabel(mBars.get(i).start);
            final float textWidth = mPaint.measureText(label);
            final float x = left + (i + 0.5f) * step;
            final float textLeft = x - textWidth / 2;
            if (textLeft < previousRight + dp(8)
                    || (!mWeek && x + textWidth / 2 > right - nowWidth - dp(8))) {
                continue;
            }
            mPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, x, y, mPaint);
            previousRight = x + textWidth / 2;
        }
        if (!mWeek) {
            mPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(now, right, y, mPaint);
        }
    }

    private String axisLabel(float fraction) {
        if (!mActivity) {
            return Utils.formatPercentage(Math.round(100 * fraction));
        }
        final long unit = mWeek ? BatteryUsageGraphData.HOUR : DateUtils.MINUTE_IN_MILLIS;
        return getContext().getString(mWeek ? R.string.battery_graph_hours
                : R.string.battery_graph_minutes, Math.round(mMaximum * fraction / unit));
    }

    private String shortTimeLabel(long timestamp) {
        return DateFormat.format(mWeek ? "EE" : DateFormat.is24HourFormat(getContext())
                ? "HH" : "ha", timestamp).toString();
    }

    private void updateContentDescription() {
        setContentDescription(getContext().getString(mActivity
                ? mWeek ? R.string.battery_graph_daily_screen_time
                        : R.string.battery_graph_hourly_screen_time
                : mWeek ? R.string.daily_battery_usage_chart : R.string.hourly_battery_usage_chart));
    }

    private String barDescription(BatteryUsageGraphData.Bar bar) {
        final String time = DateUtils.formatDateRange(getContext(), bar.start, bar.end,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        if (mActivity) {
            return getContext().getString(R.string.battery_graph_activity_description, time,
                    BatteryUsageGraphView.formatTime(getContext(), bar.screenOn),
                    BatteryUsageGraphView.formatTime(getContext(), bar.screenOff));
        }
        final String level = bar.level >= 0 ? Utils.formatPercentage(bar.level)
                : getContext().getString(R.string.battery_graph_unavailable);
        return getContext().getString(R.string.battery_graph_level_description, time, level)
                + (bar.charging ? ", " + getContext().getString(
                        R.string.battery_graph_charging_hours) : "");
    }

    private boolean isSelected(BatteryUsageGraphData.Bar bar) {
        return mSelectedEnd > mSelectedStart && bar.firstTimestamp >= mSelectedStart
                && bar.firstTimestamp < mSelectedEnd;
    }

    private float plotLeft() {
        mPaint.setTextSize(mTextSize);
        return Math.max(dp(30), mPaint.measureText(axisLabel(1)) + dp(8));
    }

    private int barAt(float x, float y) {
        final float left = plotLeft();
        final float right = getWidth() - dp(8);
        if (mBars.isEmpty() || right <= left || x < left || x >= right
                || y < 0 || y >= getHeight()) {
            return -1;
        }
        final int index = (int) ((x - left) / (right - left) * mBars.size());
        return mBars.get(index).covered > 0 ? index : -1;
    }

    private boolean selectBar(int index) {
        if (!isEnabled() || mListener == null || index < 0 || index >= mBars.size()
                || mBars.get(index).covered == 0) {
            return false;
        }
        mListener.onBarSelected(mBars.get(index).firstTimestamp, mWeek);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || mListener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchX = event.getX();
                mTouchY = event.getY();
                mTouchIndex = barAt(mTouchX, mTouchY);
                return mTouchIndex >= 0;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - mTouchX) > mTouchSlop
                        || Math.abs(event.getY() - mTouchY) > mTouchSlop) {
                    mTouchIndex = -1;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mTouchIndex >= 0 && mTouchIndex == barAt(event.getX(), event.getY())) {
                    performClick();
                }
                mTouchIndex = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mTouchIndex = -1;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return selectBar(mTouchIndex);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return mAccessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mAccessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        mAccessibilityHelper.onFocusChanged(gainFocus, direction, previous);
    }

    private final class ChartAccessibilityHelper extends ExploreByTouchHelper {
        ChartAccessibilityHelper() {
            super(BatteryUsageBarChart.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            final int index = barAt(x, y);
            return index < 0 ? INVALID_ID : index;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < mBars.size(); i++) {
                if (mBars.get(i).covered > 0) {
                    ids.add(i);
                }
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
            if (id < 0 || id >= mBars.size()) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect());
                return;
            }
            final BatteryUsageGraphData.Bar bar = mBars.get(id);
            final float left = plotLeft();
            final float step = (getWidth() - dp(8) - left) / mBars.size();
            node.setBoundsInParent(new Rect((int) (left + id * step), 0,
                    (int) (left + (id + 1) * step), getHeight()));
            node.setContentDescription(barDescription(bar));
            node.setSelected(isSelected(bar));
            node.setEnabled(isEnabled());
            node.setClickable(mListener != null && isEnabled());
            if (mListener != null && isEnabled()) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && selectBar(id)) {
                sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
                return true;
            }
            return false;
        }
    }

    private float dp(float value) {
        return value * mDensity;
    }
}
