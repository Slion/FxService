package net.slions.fxservice;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

public class SettingsActivity extends AppCompatActivity {

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
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


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

    }


    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}