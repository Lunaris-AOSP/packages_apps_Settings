package com.android.settings.wifi.tether;

import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class HotspotDataLimitActionController extends BasePreferenceController {

    private static final String KEY_ACTION = "hotspot_data_limit_action";
    // 0 = Turn off and notify, 1 = Notify only
    private static final int ACTION_TURN_OFF_NOTIFY = 0;
    private static final int ACTION_NOTIFY_ONLY = 1;

    private Preference mPreference;

    public HotspotDataLimitActionController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            updateSummary();
            mPreference.setOnPreferenceClickListener(p -> {
                showActionDialog();
                return true;
            });
        }
    }

    private void updateSummary() {
        int action = Settings.Global.getInt(
                mContext.getContentResolver(), KEY_ACTION, ACTION_TURN_OFF_NOTIFY);
        mPreference.setSummary(action == ACTION_TURN_OFF_NOTIFY
                ? R.string.one_time_data_limit_turn_off_notify
                : R.string.one_time_data_limit_notify_only);
    }

    private void showActionDialog() {
        String[] options = {
                mContext.getString(R.string.one_time_data_limit_turn_off_notify),
                mContext.getString(R.string.one_time_data_limit_notify_only)
        };
        int current = Settings.Global.getInt(
                mContext.getContentResolver(), KEY_ACTION, ACTION_TURN_OFF_NOTIFY);

        new AlertDialog.Builder(mContext)
                .setTitle(R.string.one_time_data_limit_when_exceeded)
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    Settings.Global.putInt(mContext.getContentResolver(), KEY_ACTION, which);
                    updateSummary();
                    dialog.dismiss();
                })
                .show();
    }
}
