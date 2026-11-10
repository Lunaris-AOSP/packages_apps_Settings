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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Battery history reduced to the two ranges displayed by the usage graph. */
final class BatteryUsageGraphData {
    static final long HOUR = 60 * 60 * 1000L;

    final Period lastDay;
    final Period lastWeek;
    final long activeSinceFullCharge;
    final boolean hasFullChargeTime;
    final boolean sinceReset;

    private BatteryUsageGraphData(Builder builder) {
        lastDay = new Period(builder.day);
        lastWeek = new Period(builder.week);
        activeSinceFullCharge = builder.activeSinceFullCharge;
        hasFullChargeTime = builder.lastFullCharge > 0
                && (builder.hasFullChargeHistory || (builder.sinceReset
                        && builder.lastFullCharge >= builder.week.get(0).start));
        sinceReset = builder.sinceReset;
    }

    static final class Bar {
        final long start;
        final long end;
        final long screenOn;
        final long screenOff;
        final long covered;
        final long firstTimestamp;
        final int level;
        final boolean charging;

        private Bar(MutableBar bar) {
            start = bar.start;
            end = bar.end;
            screenOn = bar.screenOn;
            screenOff = bar.screenOff;
            covered = bar.covered;
            firstTimestamp = bar.firstTimestamp;
            level = bar.level;
            charging = bar.charging;
        }
    }

    static final class Period {
        final List<Bar> bars;
        final long screenOn;
        final long screenOff;
        final boolean hasData;

        private Period(List<MutableBar> source) {
            final List<Bar> result = new ArrayList<>(source.size());
            long on = 0;
            long off = 0;
            long coverage = 0;
            for (MutableBar bar : source) {
                result.add(new Bar(bar));
                on += bar.screenOn;
                off += bar.screenOff;
                coverage += bar.covered;
            }
            bars = Collections.unmodifiableList(result);
            screenOn = on;
            screenOff = off;
            hasData = coverage > 0;
        }
    }

    private static final class MutableBar {
        final long start;
        final long end;
        long screenOn;
        long screenOff;
        long covered;
        long firstTimestamp = Long.MAX_VALUE;
        int level = -1;
        boolean charging;

        MutableBar(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    /** Consumes ordered, non-overlapping intervals; gaps are deliberately left empty. */
    static final class Builder {
        private final List<MutableBar> day = new ArrayList<>(24);
        private final List<MutableBar> week = new ArrayList<>(7);
        private final long lastFullCharge;
        private final long now;
        private final boolean sinceReset;
        private long activeSinceFullCharge;
        private boolean hasFullChargeHistory;
        private long lastEnd = Long.MIN_VALUE;

        Builder(long now, long lastFullCharge, TimeZone timeZone) {
            this(now, lastFullCharge, timeZone, false);
        }

        Builder(long now, long lastFullCharge, TimeZone timeZone, boolean sinceReset) {
            this.now = now;
            this.lastFullCharge = lastFullCharge;
            this.sinceReset = sinceReset;
            final long dayStart = now - 24 * HOUR;
            for (int i = 0; i < 24; i++) {
                day.add(new MutableBar(dayStart + i * HOUR, dayStart + (i + 1) * HOUR));
            }
            final Calendar calendar = Calendar.getInstance(timeZone);
            calendar.setTimeInMillis(now);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_YEAR, -6);
            for (int i = 0; i < 7; i++) {
                final long start = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                week.add(new MutableBar(start, Math.min(now, calendar.getTimeInMillis())));
            }
        }

        void addInterval(long start, long end, int level, boolean charging,
                boolean screenOn, boolean cpuRunning) {
            start = Math.max(start, lastEnd);
            end = Math.min(end, now);
            if (end <= start) {
                return;
            }
            lastEnd = end;
            final long dayStart = sinceReset ? Math.max(start, lastFullCharge) : start;
            addToBars(day, dayStart, end, level, charging, screenOn, cpuRunning);
            addToBars(week, start, end, level, charging, screenOn, cpuRunning);
            if (lastFullCharge > 0 && end > lastFullCharge) {
                hasFullChargeHistory |= start <= lastFullCharge;
                if (!charging && (screenOn || cpuRunning)) {
                    activeSinceFullCharge += end - Math.max(start, lastFullCharge);
                }
            }
        }

        BatteryUsageGraphData build() {
            return new BatteryUsageGraphData(this);
        }

        private static void addToBars(List<MutableBar> bars, long start, long end, int level,
                boolean charging, boolean screenOn, boolean cpuRunning) {
            for (MutableBar bar : bars) {
                final long overlap = Math.min(end, bar.end) - Math.max(start, bar.start);
                if (overlap <= 0) {
                    continue;
                }
                bar.covered += overlap;
                bar.firstTimestamp = Math.min(bar.firstTimestamp, Math.max(start, bar.start));
                if (level >= 0 && level <= 100) {
                    bar.level = level;
                }
                bar.charging |= charging;
                if (!charging) {
                    if (screenOn) {
                        bar.screenOn += overlap;
                    } else if (cpuRunning) {
                        bar.screenOff += overlap;
                    }
                }
            }
        }
    }
}
