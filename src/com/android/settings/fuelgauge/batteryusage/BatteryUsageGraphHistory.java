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
import android.util.AtomicFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

final class BatteryUsageGraphHistory {
    private static final String FILE_NAME = "battery-usage-week.bin";
    private static final int VERSION = 1;
    private static final int CHARGING = 1;
    private static final int SCREEN_ON = 2;
    private static final int CPU_RUNNING = 4;

    private final List<Interval> mIntervals = new ArrayList<>();
    private final long mNow;
    private final long mStart;
    private long mLastEnd = Long.MIN_VALUE;

    BatteryUsageGraphHistory(long now, TimeZone timeZone) {
        mNow = now;
        final Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        mStart = calendar.getTimeInMillis();
    }

    static synchronized BatteryUsageGraphHistory load(Context context, long now, TimeZone timeZone)
            throws IOException {
        final BatteryUsageGraphHistory history = new BatteryUsageGraphHistory(now, timeZone);
        final AtomicFile file = getFile(context);
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(file.openRead()))) {
            if (input.readInt() != VERSION) {
                throw new IOException("Unsupported weekly battery history version");
            }
            final long capturedThrough = input.readLong();
            final int count = input.readInt();
            if (count < 0 || count > (file.getBaseFile().length() - 16) / 21) {
                throw new IOException("Invalid weekly battery history size");
            }
            for (int i = 0; i < count; i++) {
                final long start = input.readLong();
                final long end = input.readLong();
                final int level = input.readInt();
                final int flags = input.readUnsignedByte();
                if (start >= end || end > capturedThrough || level < -1 || level > 100
                        || flags > (CHARGING | SCREEN_ON | CPU_RUNNING)) {
                    throw new IOException("Invalid weekly battery history interval");
                }
                history.addInterval(start, end, level, (flags & CHARGING) != 0,
                        (flags & SCREEN_ON) != 0, (flags & CPU_RUNNING) != 0);
            }
            history.mLastEnd = Math.max(history.mLastEnd, Math.min(capturedThrough, now));
        } catch (FileNotFoundException e) {
            return history;
        }
        return history;
    }

    void addInterval(long start, long end, int level, boolean charging,
            boolean screenOn, boolean cpuRunning) {
        start = Math.max(Math.max(start, mStart), mLastEnd);
        end = Math.min(end, mNow);
        if (end <= start) {
            return;
        }
        final int flags = (charging ? CHARGING : 0) | (screenOn ? SCREEN_ON : 0)
                | (cpuRunning ? CPU_RUNNING : 0);
        final Interval previous = mIntervals.isEmpty() ? null
                : mIntervals.get(mIntervals.size() - 1);
        if (previous != null && previous.end == start && previous.level == level
                && previous.flags == flags) {
            previous.end = end;
        } else {
            mIntervals.add(new Interval(start, end, level, flags));
        }
        mLastEnd = end;
    }

    void addTo(BatteryUsageGraphData.Builder builder) {
        for (Interval interval : mIntervals) {
            builder.addInterval(interval.start, interval.end, interval.level,
                    (interval.flags & CHARGING) != 0, (interval.flags & SCREEN_ON) != 0,
                    (interval.flags & CPU_RUNNING) != 0);
        }
    }

    void save(Context context) throws IOException {
        synchronized (BatteryUsageGraphHistory.class) {
            final AtomicFile file = getFile(context);
            final FileOutputStream stream = file.startWrite();
            try {
                final DataOutputStream output =
                        new DataOutputStream(new BufferedOutputStream(stream));
                output.writeInt(VERSION);
                output.writeLong(mNow);
                output.writeInt(mIntervals.size());
                for (Interval interval : mIntervals) {
                    output.writeLong(interval.start);
                    output.writeLong(interval.end);
                    output.writeInt(interval.level);
                    output.writeByte(interval.flags);
                }
                output.flush();
                stream.getFD().sync();
                file.finishWrite(stream);
            } catch (IOException | RuntimeException e) {
                file.failWrite(stream);
                throw e;
            }
            verifySavedHistory(file);
        }
    }

    private void verifySavedHistory(AtomicFile file) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(file.openRead()))) {
            if (input.readInt() != VERSION || input.readLong() != mNow
                    || input.readInt() != mIntervals.size()) {
                throw new IOException("Weekly battery history was not saved");
            }
            for (Interval interval : mIntervals) {
                if (input.readLong() != interval.start || input.readLong() != interval.end
                        || input.readInt() != interval.level
                        || input.readUnsignedByte() != interval.flags) {
                    throw new IOException("Saved weekly battery history does not match");
                }
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected data in saved weekly battery history");
            }
        }
    }

    private static AtomicFile getFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    private static final class Interval {
        final long start;
        long end;
        final int level;
        final int flags;

        Interval(long start, long end, int level, int flags) {
            this.start = start;
            this.end = end;
            this.level = level;
            this.flags = flags;
        }
    }
}
