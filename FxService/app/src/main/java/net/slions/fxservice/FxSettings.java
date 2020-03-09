package net.slions.fxservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class FxSettings
{

    static void toggleScreenFilter(Context aContext)
    {
        setPrefBoolean(aContext, R.string.pref_key_screen_filter,!getPrefBoolean(aContext,R.string.pref_key_screen_filter,true));
    }

    static boolean isColorFilterEnabled(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_filter,false);
    }

    // Fetch specified boolean preference
    static boolean getPrefBoolean(Context aContext, int aKey, Boolean aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        return preferences.getBoolean(aContext.getResources().getString(aKey),aDefault);
    }

    static void setPrefBoolean(Context aContext, int aKey, Boolean aValue)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(aContext.getString(aKey), aValue);
        editor.commit();
    }


    // Fetch specified integer preference
    static int getPrefInt(Context aContext, int aKey, int aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        return preferences.getInt(aContext.getResources().getString(aKey),aDefault);

    }

    static void setPrefInt(Context aContext, int aKey, int aValue)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(aContext.getString(aKey), aValue);
        editor.commit();
    }

}
