/*
 * Copyright (C) 2025 AxionOS
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
import android.os.SystemProperties;

import com.android.settings.core.TogglePreferenceController;
import com.android.settings.R;

import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

public class HighTouchSensitivityPreferenceController extends TogglePreferenceController {

    private static final String PROP_TOUCHBOOST = "persist.sys.touchboost_enable";
    private final LineageHardwareManager mHardware;

    public HighTouchSensitivityPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mHardware = LineageHardwareManager.getInstance(context);
    }

    @Override
    public boolean isChecked() {
        return LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        LineageSettings.System.putInt(mContext.getContentResolver(),
                LineageSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, isChecked ? 1 : 0);
        SystemProperties.set(PROP_TOUCHBOOST, isChecked ? "1" : "0");
        return true;
    }

    @Override
    public int getAvailabilityStatus() {
        return mHardware.isSupported(LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }
    
    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
