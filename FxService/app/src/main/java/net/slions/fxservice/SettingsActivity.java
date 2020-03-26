package net.slions.fxservice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            try
            {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                actionBar.setSubtitle(version);
            }
            catch (PackageManager.NameNotFoundException e)
            {
                e.printStackTrace();
            }

            // Show back button on action bar
            actionBar.setDisplayHomeAsUpEnabled(true);
            // Show icon
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setIcon(R.drawable.action_bar_icon);
            //actionBar.setDisplayUseLogoEnabled(true);
            //actionBar.setLogo(R.drawable.ic_launcher_foreground);
        }


            iSettingsFragment = new SettingsFragment();
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, iSettingsFragment)
                .commit();



    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Make sure the back button closes the application
        // See: https://stackoverflow.com/questions/14545139/android-back-button-in-the-title-bar
        switch (item.getItemId())
        {
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
    protected void onPostResume()
    {
        super.onPostResume();
        // Set accessibility service preference according to our accessibility service status
        SwitchPreferenceCompat pref = (SwitchPreferenceCompat) iSettingsFragment.findPreference(getResources().getString(R.string.pref_key_accessibility_service));
        pref.setChecked(isAccessServiceEnabled(this,FxService.class));
    }

    // To check if an accessibility service is enabled
    // See: https://stackoverflow.com/questions/18094982/detect-if-my-accessibility-service-is-enabled/18095283
    public boolean isAccessServiceEnabled(Context context, Class accessibilityServiceClass)
    {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString!= null && prefString.contains(context.getPackageName() + "/" + accessibilityServiceClass.getName());
    }

    // Boilerplate code to load our preferences from XML
    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}



