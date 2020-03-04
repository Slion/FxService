package net.slions.fxservice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    final static String TAG = "FxService: ";
    // Data members
    SettingsFragment iSettingsFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        iSettingsFragment = new SettingsFragment();

        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, iSettingsFragment)
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show back button on action bar
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Make sure the back button closes the application
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();

        /*
        // Make sure we show currently selected value
        ((SeekBarPreference) iSettingsFragment.findPreference(getResources().getString(R.string.pref_key_case_close_delay))).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final int progress = Integer.valueOf(String.valueOf(newValue));
                preference.setSummary(String.format("My progress value: %d", progress));
                return true;
            }
        });
         */

        // Present user with Accessibility Service settings when tapping that preference
        ((SwitchPreferenceCompat) iSettingsFragment.findPreference(getResources().getString(R.string.pref_key_accessibility_service))).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return false;
            }
        });

    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        SwitchPreferenceCompat pref = (SwitchPreferenceCompat) iSettingsFragment.findPreference(getResources().getString(R.string.pref_key_accessibility_service));

        //boolean isdocumentModeEnabled = !mDocumentModeManager.isOptedOutOfDocumentMode();
        pref.setChecked(isAccessibilitySettingsOn(this));

    }

    // To check if service is enabled
    private boolean isAccessibilitySettingsOn(Context mContext)
    {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + FxService.class.getCanonicalName();
        try
        {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
            Log.v(TAG, "accessibilityEnabled = " + accessibilityEnabled);
        }
        catch (Settings.SettingNotFoundException e)
        {
            Log.e(TAG, "Error finding setting, default accessibility to not found: "
                    + e.getMessage());
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1)
        {
            Log.v(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------");
            String settingValue = Settings.Secure.getString(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null)
            {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext())
                {
                    String accessibilityService = mStringColonSplitter.next();

                    Log.v(TAG, "-------------- > accessibilityService :: " + accessibilityService + " " + service);
                    if (accessibilityService.equalsIgnoreCase(service))
                    {
                        Log.v(TAG, "We've found the correct setting - accessibility is switched on!");
                        return true;
                    }
                }
            }
        }
        else
        {
            Log.v(TAG, "***ACCESSIBILITY IS DISABLED***");
        }

        return false;
    }


    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}



