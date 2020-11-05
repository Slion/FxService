package net.slions.fxservice

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class FragmentSettingsScreenRotation : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_screen_rotation, rootKey)

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.pref_key_screen_rotation_auto))?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (Settings.System.canWrite(context)) {
                true
            } else {
                // We don't have the proper permissions, ask the user to give it to us
                (activity as ActivitySettings).showDialogNeedPermission(
                        R.string.screen_rotation_permission_alert_dialog_message,
                        Settings.ACTION_MANAGE_WRITE_SETTINGS)
                false
            }
        }


        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.pref_key_screen_rotation_force_landscape))?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (Settings.canDrawOverlays(context)) {
                true
            } else {
                // We don't have the proper permissions, ask the user to give it to us
                (activity as ActivitySettings).showDialogNeedPermission(
                        R.string.screen_rotation_permission_message_force_landscape_show_overlay,
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                false
            }
        }


    }
}