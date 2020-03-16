// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.slions.fxservice;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;

import androidx.core.graphics.ColorUtils;

import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

import static android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;

public class FxService extends AccessibilityService
        implements SensorEventListener,
         SharedPreferences.OnSharedPreferenceChangeListener
{

    // System sensor manager instance.
    private SensorManager iSensorManager;
    // Proximity sensor
    private Sensor iSensorProximity;
    // Light sensor
    private Sensor iSensorLight;
    // Last value read from the proximity sensor
    private float iLastProximityValue = 0;
    // Used to turn the screen off as soon as you close the case without locking the screen
    private PowerManager.WakeLock iWakeLockProximityScreenOff = null;
    // The minimum brightness on F(x)tec Pro1 is 10
    private final int KFxTecProOneBrightnessMin = 10;
    private final int KFxTecProOneBrightnessMax = 255;
    private final int KScreenFilterBrightnessMin = 25;
    private final int KScreenFilterBrightnessMax = 255;


    //ArrayList<SensorEvent> iLightSensorSamples = new ArrayList<SensorEvent>();


    //
    FrameLayout mLayout;
    //View mColorLayout;

    Handler iHandler = new Handler();
    Runnable iLockScreenCallback = new Runnable()
    {
        @Override
        public void run()
        {
            //Do something after 100ms
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            //
            releaseProximityWakeLock();
        }
    };

    // From AccessibilityService
    // Called whenever our service is connected
    @Override
    protected void onServiceConnected()
    {
        super.onServiceConnected();
        //
        iWakeLockProximityScreenOff = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "FxService:PROXIMITY_SCREEN_OFF_WAKE_LOCK");
        // Get an instance of the sensor manager.
        iSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);


        // Create an overlay
        setupColorFilter();

        setupProximitySensor();
        setupLightSensor();

        // Get notification when preferences are changed
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        Toast.makeText(this, R.string.toast_service_connected, Toast.LENGTH_SHORT).show();

    }

    // From Service
    // Called whenever our service is stopped
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        closeLightSensor();
        closeProximitySensor();
        releaseProximityWakeLock();
        iWakeLockProximityScreenOff = null;
        iSensorManager = null;
        Toast.makeText(this, R.string.toast_service_destroyed, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    // I'm not really sure when this is called
    @Override
    public void onInterrupt() {
        Toast.makeText(this, R.string.toast_service_interrupted, Toast.LENGTH_SHORT).show();
    }


    // Receive preference change notifications
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (key == getResources().getString(R.string.pref_key_proximity_wake_up))
        {
            // Setup proximity sensor anew
            setupProximitySensor();
        }
        else if (key == getResources().getString(R.string.pref_key_light_sensor_adaptive_brightness))
        {
            // Setup proximity sensor anew
            setupLightSensor();
        }
        else if (key == getResources().getString(R.string.pref_key_screen_filter_color) || key == getResources().getString(R.string.pref_key_screen_filter_brightness))
        {
            // Overlay color was changed
            setupColorFilter();
        }
        else if (key == getResources().getString(R.string.pref_key_screen_filter))
        {
            // Overlay was turned on or off
            setupColorFilter();
        }
    }


    private boolean isScreenFilterEnabled()
    {
        return FxSettings.getPrefBoolean(this, R.string.pref_key_screen_filter,false);
    }

    // Setting up the overlay to draw on top of status bar and navigation bar can be tricky
    // See: https://stackoverflow.com/questions/21380167/draw-bitmaps-on-top-of-the-navigation-bar-in-android
    // See: https://stackoverflow.com/questions/31516089/draw-over-navigation-bar-and-other-apps-on-android-version-5
    // See: https://stackoverflow.com/questions/50677833/full-screen-type-accessibility-overlay
    //
    private void setupColorFilter()
    {

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (isScreenFilterEnabled() && mLayout == null)
        {
            mLayout = new FrameLayout(this);

            // Fetch screen size to work out our overlay size
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            // We need it to be large enough to cover navigation bar both in portrait and landscape
            // Doing Math.max here didn't work for whatever reason
            int width = size.x+500;
            int height = size.y+500;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            // We need to explicitly specify our extent so as to make sure we cover the navigation bar
            lp.width=Math.max(width,height);
            lp.height=Math.max(width,height);

            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            lp.format = PixelFormat.TRANSLUCENT;

            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            lp.gravity = Gravity.TOP;

            //LayoutInflater inflater = LayoutInflater.from(this);
            //mColorLayout = inflater.inflate(R.layout.action_bar, mLayout);

            wm.addView(mLayout, lp);
        }
        else if (!isScreenFilterEnabled() && mLayout != null)
        {
            // Disable our overlay
            wm.removeView(mLayout);
            mLayout = null;
            //mColorLayout = null;
        }

        // Set overlay color
        if (mLayout!=null)
        {
            int brightness = FxSettings.getPrefInt(this, R.string.pref_key_screen_filter_brightness,0);
            // Just keep alpha
            int color = FxSettings.getPrefInt(this, R.string.pref_key_screen_filter_color,0);
            int blackAlpha = ColorUtils.setAlphaComponent(0,0xFF - brightness);
            mLayout.setBackgroundColor(ColorUtils.compositeColors(blackAlpha,color));
        }

    }


    private void setupLightSensor()
    {
        // Open proximity sensor if needed
        if (FxSettings.getPrefBoolean(this,R.string.pref_key_light_sensor_adaptive_brightness,false))
        {
            Toast.makeText(this, R.string.toast_light_sensor_enabled, Toast.LENGTH_SHORT).show();
            openLightSensor();
        }
        else
        {
            Toast.makeText(this, R.string.toast_light_sensor_disabled, Toast.LENGTH_SHORT).show();
            closeLightSensor();
        }

    }

    private void openLightSensor()
    {
        // Get proximity sensor from the sensor manager.
        iSensorLight = iSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (iSensorLight != null)
        {
            // SensorManager.SENSOR_DELAY_UI
            // Sampling period does not seem to be bringing anything
            iSensorManager.registerListener(this, iSensorLight, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }

    private void closeLightSensor()
    {
        if (iSensorLight != null)
        {
            iSensorManager.unregisterListener(this, iSensorLight);
            iSensorLight = null;
        }
    }

    private void setupProximitySensor()
    {
        // Open proximity sensor if needed
        if (FxSettings.getPrefBoolean(this,R.string.pref_key_proximity_wake_up,true))
        {
            Toast.makeText(this, R.string.toast_proximity_sensor_enabled, Toast.LENGTH_SHORT).show();
            openProximitySensor();
        }
        else
        {
            Toast.makeText(this, R.string.toast_proximity_sensor_disabled, Toast.LENGTH_SHORT).show();
            closeProximitySensor();
        }

    }

    private void openProximitySensor()
    {
        // Get proximity sensor from the sensor manager.
        iSensorProximity = iSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (iSensorProximity != null)
        {
            // SensorManager.SENSOR_DELAY_UI
            // Sampling period does not seem to be bringing anything
            iSensorManager.registerListener(this, iSensorProximity, SensorManager.SENSOR_DELAY_FASTEST);
        }

    }

    private void closeProximitySensor()
    {
        if (iSensorProximity!=null)
        {
            iSensorManager.unregisterListener(this, iSensorProximity);
            iSensorProximity = null;
        }
    }


    private boolean isScreenFilterBrightnessMaxed()
    {
        return KScreenFilterBrightnessMax == getScreenFilterBrightness();
    }

    private int getScreenFilterBrightness()
    {
        return FxSettings.getPrefInt(this, R.string.pref_key_screen_filter_brightness, 150);
    }

    private int getSystemBrightness()
    {
        int brightness = 0;
        try
        {
            brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        }
        catch (Settings.SettingNotFoundException e)
        {
            e.printStackTrace();
        }

        return brightness;
    }

    private int getLockDelayInMilliseconds()
    {
        int delay = FxSettings.getPrefInt(this, R.string.pref_key_case_close_delay_in_seconds,0) * 1000;
        delay += FxSettings.getPrefInt(this, R.string.pref_key_case_close_delay_in_minutes,0) * 60 * 1000;
        return delay;
    }

    private void increaseFxScreenBrightness()
    {
        if (isScreenFilterEnabled())
        {
            // Increase brightness
            int brightness = getScreenFilterBrightness();
            brightness = Math.min(brightness+0x10,255);
            FxSettings.setPrefInt(this,R.string.pref_key_screen_filter_brightness,brightness);
        }
    }


    private void decreaseFxScreenBrightness()
    {
        if (isScreenFilterEnabled())
        {
            // Decrease brightness
            int brightness = getScreenFilterBrightness();
            brightness = Math.max(25,brightness-0x10);
            FxSettings.setPrefInt(this, R.string.pref_key_screen_filter_brightness,brightness);
        }
    }


    private void logBrightness()
    {
        System.out.println("Brightness sys/fx:  " + getSystemBrightness() + "/" + getScreenFilterBrightness());
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        //Log.d("FxService:", event.toString());

        // Hardcoded shortcut to turn overlay on and off
        // That's notably intended to help people who shot themselves in the foot by using a solid color for instance
        // Ctrl + Fn + O
        if ((event.getMetaState() == (KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_FUNCTION_ON))
        // Ctrl + Fn + O with Fn and Alt swapped with Fx Qwerty keyboard layout
        ||(event.getMetaState() == (KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_ALT_ON|KeyEvent.META_ALT_LEFT_ON)))
        {
            if (action == KeyEvent.ACTION_UP)
            {
                if (keyCode == KeyEvent.KEYCODE_O)
                {
                    // Toggle color filter overlay
                    FxSettings.toggleScreenFilter(this);
                }
                else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
                {
                    increaseFxScreenBrightness();
                }
                else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                {
                    decreaseFxScreenBrightness();
                }
            }
        }

        if (event.getKeyCode()==KeyEvent.KEYCODE_BRIGHTNESS_UP)
        {
            // Do not transmit event to system if currently using screen filter
            boolean swallowEvent = isScreenFilterEnabled() && !isScreenFilterBrightnessMaxed();

            if (action == KeyEvent.ACTION_UP)
            {
                increaseFxScreenBrightness();
            }

            //logBrightness();
            return swallowEvent;
        }
        else if (event.getKeyCode()==KeyEvent.KEYCODE_BRIGHTNESS_DOWN)
        {
            boolean swallowEvent = isScreenFilterEnabled() && getSystemBrightness() == KFxTecProOneBrightnessMin;
            // Had to be done on down action to check system brightness before the system modifies it
            if (action == KeyEvent.ACTION_DOWN)
            {
                int brightness = getSystemBrightness();
                // Decreased screen filter brightness if system brightness is already at minimum
                if (brightness == KFxTecProOneBrightnessMin)
                {
                    decreaseFxScreenBrightness();
                }
            }

            //logBrightness();
            return swallowEvent;
        }

        // Here we handle case and keyboard, open and close events
        // Ideally we should do that using sensors rather than intercepting key events.
        if (event.getMetaState() == KeyEvent.META_FUNCTION_ON)
        {
            if (keyCode == KeyEvent.KEYCODE_F3)
            {
                // Case closed
                if (action == KeyEvent.ACTION_UP)
                {
                    // Only perform action on key up
                    // Check if user wants us to lock screen when closing her case
                    if (FxSettings.getPrefBoolean(this, R.string.pref_key_case_close_lock_screen,true))
                    {
                        int delayInMs = getLockDelayInMilliseconds();
                        // Make sure the screen goes off while we are delaying screen lock
                        iWakeLockProximityScreenOff.acquire(delayInMs+1000);
                        // Delayed screen lock
                        iHandler.postDelayed(iLockScreenCallback, delayInMs);
                    }
                }

                // Consume both up and down events to prevent the system doing anything with those
                // That notably prevents the trigger of search F3 in chrome browser
                return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_close_case,true);
            }
            else if (keyCode == KeyEvent.KEYCODE_F4)
            {
                // Case opened
                // Abort screen lock
                if (action == KeyEvent.ACTION_UP)
                {
                    // Only perform action on key up
                    // To be safe just cancel possible callbacks
                    iHandler.removeCallbacks(iLockScreenCallback);
                    releaseProximityWakeLock();

                    // Only show message if lock was requested
                    if (FxSettings.getPrefBoolean(this, R.string.pref_key_case_close_lock_screen,true))
                    {
                        Toast.makeText(this, R.string.toast_screen_lock_abort, Toast.LENGTH_SHORT).show();
                    }
                }

                // Consume both up and down events to prevent the system doing anything with those
                return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_open_case,true);
            }
            else if (keyCode == KeyEvent.KEYCODE_F5)
            {
                // Keyboard closed

                if (action == KeyEvent.ACTION_UP)
                {
                    if (FxSettings.getPrefBoolean(this,R.string.pref_key_keyboard_close_lock_screen,false))
                    {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                    }

                    // TODO: make screen lock cancellable via proximity sensor?
                    // TODO: make screen lock cancellable using on screen button?
                }


                // Consume both up and down events to prevent the system doing anything with those
                // Fix issue with browser page reload when closing keyboard
                return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_close_keyboard,true);
            }
            else if (keyCode == KeyEvent.KEYCODE_F6)
            {
                // Keyboard opened
                // Consume both up and down events to prevent the system doing anything with those
                return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_open_keyboard,true);
            }



        }



        //return false;
        return super.onKeyEvent(event);

    }

    // Safely release our wake lock
    private void releaseProximityWakeLock()
    {
        if (iWakeLockProximityScreenOff.isHeld())
        {
            iWakeLockProximityScreenOff.release();
        }
    }



    private void turnOnScreen(int aTimeout) {
        PowerManager.WakeLock wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "FxService:wakeLock");
        wakeLock.acquire(aTimeout);
    }


    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.values.length==0)
        {
            // No data
            return;
        }

        // Not helpful
        //Log.d("FxService:", event.toString());

        // The sensor type (as defined in the Sensor class).
        int sensorType = event.sensor.getType();
        float currentValue = event.values[0];

        switch (sensorType) {
            // Event came from the light sensor.
            case Sensor.TYPE_LIGHT:
                // Set the light sensor text view to the light sensor string
                // from the resources, with the placeholder filled in.
                //mTextSensorLight.setText(getResources().getString(R.string.label_light, currentValue));
                //Log.d("FxService: Light sensor ", String.valueOf(currentValue));
                onLightSensorChanged(event);
                break;
            case Sensor.TYPE_PROXIMITY:
                // If no proximity and previously proximity then wake up the screen for defined time
                if (currentValue>0 && iLastProximityValue == 0)
                {
                    // To be safe just cancel possible callbacks
                    iHandler.removeCallbacks(iLockScreenCallback);
                    releaseProximityWakeLock();
                    //
                    turnOnScreen(FxSettings.getPrefInt(this,R.string.pref_key_proximity_wake_up_timeout,5) * 1000);
                }

                iLastProximityValue = currentValue;

                break;
            default:
                // do nothing
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    private void onLightSensorChanged(SensorEvent aEvent)
    {
        /*
        iLightSensorSamples.add(aEvent);

        if (iLightSensorSamples.size()>100)
        {
            iLightSensorSamples.remove(0);
        }
        */

        if (!FxSettings.isScreenFilterEnabled(this) && aEvent.values[0] <= FxSettings.getPrefInt(this, R.string.pref_key_light_sensor_screen_filter_threshold_on,2))
        {
            // Turn on screen filter
            FxSettings.screenFilterOn(this);
        }
        else if (FxSettings.isScreenFilterEnabled(this) && aEvent.values[0] >= FxSettings.getPrefInt(this, R.string.pref_key_light_sensor_screen_filter_threshold_off,10))
        {
            // Turn off screen filter
            FxSettings.screenFilterOff(this);
        }


    }


}
