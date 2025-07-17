/*
 * Copyright (C) 2024-2025 Lunaris-OS
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

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.util.Log;

import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Service that manages flashlight strength integration with the existing flashlight system
 * This service bridges the gap between the settings seekbar and the flashlight controller
 * without requiring changes to the existing FlashlightTile
 */
@SysUISingleton
public class FlashlightStrengthService {
    private static final String TAG = "FlashlightStrengthService";

    private final Context mContext;
    private final FlashlightController mFlashlightController;
    private final FlashlightStrengthHelper mStrengthHelper;

    private final FlashlightController.FlashlightListener mFlashlightListener = 
            new FlashlightController.FlashlightListener() {
        @Override
        public void onFlashlightChanged(boolean enabled) {
            handleFlashlightStateChange(enabled);
        }

        @Override
        public void onFlashlightError() {
            Log.e(TAG, "Flashlight error occurred");
        }

        @Override
        public void onFlashlightAvailabilityChanged(boolean available) {
            Log.d(TAG, "Flashlight availability changed: " + available);
        }
    };

    @Inject
    public FlashlightStrengthService(Context context, FlashlightController flashlightController) {
        mContext = context;
        mFlashlightController = flashlightController;
        mStrengthHelper = new FlashlightStrengthHelper(context, flashlightController);
        
        mFlashlightController.addCallback(mFlashlightListener);
        
        mStrengthHelper.startListening();
        
        Log.d(TAG, "FlashlightStrengthService initialized");
    }

    private void handleFlashlightStateChange(boolean enabled) {
        Log.d(TAG, "Flashlight state changed: " + enabled);
        
        if (enabled && mStrengthHelper.supportsFlashStrength()) {
            float currentPercent = mStrengthHelper.getCurrentStrengthPercent();
            
            mStrengthHelper.mHandler.postDelayed(() -> {
                mStrengthHelper.applyStrengthToFlashlight(currentPercent);
            }, 100);
        }
    }

    public boolean supportsFlashStrength() {
        return mStrengthHelper.supportsFlashStrength();
    }

    public float getCurrentStrengthPercent() {
        return mStrengthHelper.getCurrentStrengthPercent();
    }

    public void destroy() {
        mFlashlightController.removeCallback(mFlashlightListener);
        mStrengthHelper.stopListening();
        Log.d(TAG, "FlashlightStrengthService destroyed");
    }
}