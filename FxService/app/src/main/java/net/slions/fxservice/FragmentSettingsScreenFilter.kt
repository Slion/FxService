package net.slions.fxservice

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class FragmentSettingsScreenFilter : PreferenceFragmentCompat() {

    // Used to show warnings before disabling touch screen
    var disableTouchCountDown = 10

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_screen_filter, rootKey)

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.pref_key_screen_filter_disable_touch_screen))?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue==true) {
                if (disableTouchCountDown==0)
                {

                    disableTouchCountDown=10;
                    val builder = context?.let { AlertDialog.Builder(it, R.style.AppTheme_AlertDialog) }
                    if (builder != null) {
                        builder.setCancelable(true)
                                .setIcon(R.drawable.ic_no_touch_input)
                                .setTitle(R.string.app_name)
                        builder.setMessage(R.string.warning_touch_screen_disabled)
                        builder.setPositiveButton(R.string.ok, null)
                        builder.create().show()
                    }
                    true
                }
                else
                {
                    Toast.makeText(context, "Touch screen disabled in $disableTouchCountDown tap(s)", Toast.LENGTH_SHORT).show()
                    disableTouchCountDown--
                    false
                }
            } else {
                true
            }
        }



    }
}