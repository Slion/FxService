package net.slions.fxservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.StringRes;

import java.util.Map;

public class FxSettings
{

    static void toggleScreenFilter(Context aContext)
    {
        setPrefBoolean(aContext, R.string.pref_key_screen_filter,!getPrefBoolean(aContext,R.string.pref_key_screen_filter,true));
    }

    static void screenFilterOn(Context aContext)
    {
        setPrefBoolean(aContext, R.string.pref_key_screen_filter,true);
    }

    static void screenFilterOff(Context aContext)
    {
        setPrefBoolean(aContext, R.string.pref_key_screen_filter,false);
    }

    static boolean isScreenFilterEnabled(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_filter,false);
    }

    static void toggleScreenRotationAuto(Context aContext)
    {
        setPrefBoolean(aContext, R.string.pref_key_screen_rotation_auto,!getPrefBoolean(aContext,R.string.pref_key_screen_rotation_auto,false));
    }

    static boolean isScreenRotationAuto(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_rotation_auto,false);
    }

    static boolean isScreenRotationKeyboardLandscape(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_rotation_keyboard_landscape,true);
    }

    static boolean isScreenRotationKeyboardLandscapeLocked(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_rotation_keyboard_landscape_locked,true);
    }

    static boolean isScreenRotationKeyboardPortrait(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_rotation_keyboard_portrait,true);
    }

    static boolean isScreenRotationKeyboardPortraitLocked(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_screen_rotation_keyboard_portrait_locked,false);
    }

    static boolean showKeyboardStatusChange(Context aContext)
    {
        return getPrefBoolean(aContext,R.string.pref_key_debug_keyboard_show_status_change,false);
    }


    // Fetch specified boolean preference
    static boolean getPrefBoolean(Context aContext, @StringRes int aKey, Boolean aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        return preferences.getBoolean(aContext.getResources().getString(aKey),aDefault);
    }

    static void setPrefBoolean(Context aContext, @StringRes int aKey, Boolean aValue)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(aContext.getString(aKey), aValue);
        editor.commit();
    }


    // Fetch specified integer preference
    static int getPrefInt(Context aContext, @StringRes int aKey, int aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        return preferences.getInt(aContext.getResources().getString(aKey),aDefault);

    }

    static void setPrefInt(Context aContext, @StringRes int aKey, int aValue)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(aContext.getString(aKey), aValue);
        editor.commit();
    }

    // Fetch specified integer preference
    static String getPrefString(Context aContext, @StringRes int aKey, @StringRes int aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        return preferences.getString(aContext.getResources().getString(aKey),aContext.getResources().getString(aDefault));

    }

    static void setPrefString(Context aContext, @StringRes int aKey, String aValue)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(aContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(aContext.getString(aKey), aValue);
        editor.commit();
    }

    // Following are a bunch of stuff we intended to persist some adaptive brightness settings but I guess we won't need it
    // In fact we are probably going to keep adaptive brightness very simple.

    static SharedPreferences getCustomPreferences(Context aContext, String aName)
    {
        return aContext.getSharedPreferences(aContext.getPackageName() + "_" + aName,Context.MODE_PRIVATE);
    }


    static SharedPreferences getLuxToSystemBrightnessPreferences(Context aContext)
    {
        return getCustomPreferences(aContext, "LuxToSystemBrightness");
    }

    static SharedPreferences getLuxToBrightnessPreferences(Context aContext)
    {
        return getCustomPreferences(aContext, "LuxToBrightness");
    }

    static int getSystemBrightnessForLux(Context aContext, float aLux)
    {
        //TODO: should we do interpolation here?
        SharedPreferences prefs = getLuxToSystemBrightnessPreferences(aContext);
        return prefs.getInt(String.valueOf(aLux),-1);
    }

    static void setSystemBrightnessForLux(Context aContext, int aBrightness, float aLux)
    {
        SharedPreferences prefs = getLuxToSystemBrightnessPreferences(aContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(String.valueOf(aLux), aBrightness);
        editor.commit();
    }

    static int getBrightnessForLux(Context aContext, float aLux)
    {
        //TODO: should we do interpolation here?
        SharedPreferences prefs = getLuxToSystemBrightnessPreferences(aContext);
        return prefs.getInt(String.valueOf(aLux),-1);
    }

    static void setBrightnessForLux(Context aContext, int aBrightness, float aLux)
    {
        SharedPreferences prefs = getLuxToSystemBrightnessPreferences(aContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(String.valueOf(aLux), aBrightness);
        editor.commit();
    }


}
