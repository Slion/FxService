package net.slions.fxservice


import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat


private const val TITLE_TAG = "settingsActivityTitle"

class ActivitySettings : AppCompatActivity(),
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private var iSettingsRootFragment: FragmentSettingsRoot = FragmentSettingsRoot()

    override fun onCreate(savedInstanceState: Bundle?) {
        if(BuildConfig.DEBUG) {
            StrictMode.enableDefaults();
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, iSettingsRootFragment)
                    .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }

        /*
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.title_activity_settings)
            }
        }
        */

        // Set title in tool bar with version
        var pInfo = packageManager.getPackageInfo(packageName, 0);
        var version = pInfo.versionName
        var title = getText(R.string.title_activity_settings) as String
        title += " - v" + version
        if (BuildConfig.DEBUG) {
            title += " - debug"
        }
        setTitle(title)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }


    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        } else {
            finish()
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                pref.fragment?:""
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit()
        // Just keep Fx Service as title
        //title = pref.title
        return true
    }

    override fun onStart() {
        super.onStart()

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
        (iSettingsRootFragment.findPreference<SwitchPreferenceCompat>(resources.getString(R.string.pref_key_accessibility_service)))?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            false
        }
    }


    override fun onPostResume() {
        super.onPostResume()
        // Set accessibility service preference according to our accessibility service status
        val pref = iSettingsRootFragment.findPreference<SwitchPreferenceCompat>(resources.getString(R.string.pref_key_accessibility_service))
        var serviceEnabled = isAccessServiceEnabled(this, FxService::class.java)
        pref?.isChecked = serviceEnabled

        if (!serviceEnabled)
        {
            // Service is disabled go back to root page then
            while (supportFragmentManager.backStackEntryCount>0)
            {
                supportFragmentManager.popBackStackImmediate()
            }
        }
    }

    // To check if an accessibility service is enabled
    // See: https://stackoverflow.com/questions/18094982/detect-if-my-accessibility-service-is-enabled/18095283
    private fun isAccessServiceEnabled(context: Context, accessibilityServiceClass: Class<*>): Boolean {
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString != null && prefString.contains(context.packageName + "/" + accessibilityServiceClass.name)
    }

    /**
     *
     */
    public fun showDialogNeedPermission(aMessageResId: Int, aIntentAction: String) {
        val builder = AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
        builder.setCancelable(true)
                .setIcon(R.drawable.ic_screen_rotation)
                .setTitle(R.string.app_name)
                .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener { dialog: DialogInterface, which: Int -> dialog.cancel() })
        builder.setMessage(aMessageResId)
        builder.setPositiveButton(R.string.settings
        ) { dialog, which ->  // Launch system UI to manage "write settings" permission for this application
            startActivity(Intent(aIntentAction)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setData(Uri.parse("package:$packageName")))
        }
        builder.create().show()
    }


}