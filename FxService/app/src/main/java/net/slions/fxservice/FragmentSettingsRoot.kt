package net.slions.fxservice

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class FragmentSettingsRoot : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)
    }
}
