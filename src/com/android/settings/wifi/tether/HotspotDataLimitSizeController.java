package com.android.settings.wifi.tether;

import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;

public class HotspotDataLimitSizeController extends BasePreferenceController {

    private static final String KEY_LIMIT_BYTES = "hotspot_data_limit_bytes";
    private static final long DEFAULT_LIMIT_MB = 50; // matches screenshot default

    private Preference mPreference;

    public HotspotDataLimitSizeController(Context context, String key) {
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
                showSizeDialog();
                return true;
            });
        }
    }

    private void updateSummary() {
        long currentMb = Settings.Global.getLong(
                mContext.getContentResolver(), KEY_LIMIT_BYTES,
                DEFAULT_LIMIT_MB * 1024 * 1024) / (1024 * 1024);
        mPreference.setSummary(String.format("%.1fMB", (float) currentMb));
    }

    private void showSizeDialog() {
        EditText input = new EditText(mContext);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        long currentMb = Settings.Global.getLong(
                mContext.getContentResolver(), KEY_LIMIT_BYTES,
                DEFAULT_LIMIT_MB * 1024 * 1024) / (1024 * 1024);
        input.setText(String.valueOf(currentMb));

        new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(com.android.settings.R.string.one_time_data_limit_size))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        float mb = Float.parseFloat(input.getText().toString());
                        long bytes = (long) (mb * 1024 * 1024);
                        Settings.Global.putLong(mContext.getContentResolver(), KEY_LIMIT_BYTES, bytes);
                        updateSummary();
                    } catch (NumberFormatException e) {
                        // ignore invalid input
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
