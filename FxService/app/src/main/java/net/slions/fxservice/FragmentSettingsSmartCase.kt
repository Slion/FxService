package net.slions.fxservice

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class FragmentSettingsSmartCase : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_smart_case, rootKey)

        //
        (findPreference<EditTextPreference>(resources.getString(R.string.pref_key_case_close_vibration_pattern)))?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            // Validate our new vibration pattern and automatically return true to accept it or false to reject it
            vibrateOnScreenLock(requireActivity(),newValue as String)
        }

    }
}