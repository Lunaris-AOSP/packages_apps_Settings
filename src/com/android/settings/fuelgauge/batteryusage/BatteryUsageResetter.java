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
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;

import com.android.internal.os.TransferPipe;
import com.android.settings.fuelgauge.batteryusage.db.BatteryStateDatabase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Clears battery statistics and the local history that feeds the usage screen. */
final class BatteryUsageResetter {
    static final Object LOCK = new Object();
    private static final String KEY_RESET_TIMESTAMP = "battery_usage_reset_timestamp";

    private BatteryUsageResetter() {}

    static boolean isAvailable(Context context) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        return userManager != null && userManager.isAdminUser();
    }

    static long getResetTimestamp(Context context) {
        return DatabaseUtils.getSharedPreferences(context).getLong(KEY_RESET_TIMESTAMP, 0);
    }

    static void onTimeChanged(Context context, long timestamp) {
        synchronized (LOCK) {
            if (getResetTimestamp(context) > timestamp) {
                DatabaseUtils.getSharedPreferences(context).edit()
                        .putLong(KEY_RESET_TIMESTAMP, timestamp).apply();
            }
        }
    }

    static void reset(Context context) throws IOException, RemoteException {
        if (!isAvailable(context)) {
            throw new SecurityException("Only an administrator can reset battery usage");
        }
        final IBinder service = ServiceManager.getService("batterystats");
        if (service == null) {
            throw new IOException("Battery statistics service unavailable");
        }
        synchronized (LOCK) {
            BatteryUsageGraphLoader.loadHistory(context, System.currentTimeMillis()).save(context);
            final String result = new String(TransferPipe.dumpAsync(service, "--reset-all"),
                    StandardCharsets.UTF_8);
            if (!result.contains("Battery stats and history reset.")) {
                throw new IOException("Battery statistics reset was not acknowledged");
            }
            final long timestamp = System.currentTimeMillis();
            if (!DatabaseUtils.getSharedPreferences(context).edit()
                    .putLong(KEY_RESET_TIMESTAMP, timestamp)
                    .remove(DatabaseUtils.KEY_LAST_LOAD_FULL_CHARGE_TIME)
                    .remove(DatabaseUtils.KEY_LAST_UPLOAD_FULL_CHARGE_TIME)
                    .remove(DatabaseUtils.KEY_DISMISSED_POWER_ANOMALY_KEYS)
                    .commit()) {
                throw new IOException("Could not save battery usage reset time");
            }
            final BatteryStateDatabase database = BatteryStateDatabase.getInstance(context);
            database.runInTransaction(() -> {
                database.appUsageEventDao().clearAll();
                database.batteryEventDao().clearAll();
                database.batteryStateDao().clearAll();
                database.batteryUsageSlotDao().clearAll();
                database.batteryReattributeDao().clearAll();
            });
            BatteryDiffEntry.clearCache();
            BatteryEntry.clearUidCache();
        }
        context.getContentResolver().notifyChange(DatabaseUtils.BATTERY_CONTENT_URI, null);
    }
}
