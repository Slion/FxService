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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.widget.Toast;

public class FxService extends AccessibilityService
        implements SensorEventListener
{

    // System sensor manager instance.
    private SensorManager iSensorManager;
    // Proximity and light sensors, as retrieved from the sensor manager.
    private Sensor iSensorProximity;

    private float iLastProximityValue = 0;

    Handler iHandler = new Handler();
    Runnable iLockScreenCallback = new Runnable() {
        @Override
        public void run() {
            //Do something after 100ms
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
    };

    @Override
    protected void onServiceConnected() {
        // Create an overlay and display the action bar
        /*
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLayout = new FrameLayout(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.TOP;
        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.action_bar, mLayout);
        wm.addView(mLayout, lp);
        */

        // Get an instance of the sensor manager.
        iSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Get light and proximity sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        iSensorProximity = iSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (iSensorProximity != null) {
            iSensorManager.registerListener(this, iSensorProximity, SensorManager.SENSOR_DELAY_UI);
        }

    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        Log.d("FxTec", event.toString());


        if (event.getMetaState() == KeyEvent.META_FUNCTION_ON)
        {
            if (keyCode == KeyEvent.KEYCODE_F3)
            {
                // Case closed
                if (action == KeyEvent.ACTION_UP)
                {
                    // Only perform action on key up
                    // Check if user want us to lock screen when closing her case
                    if (getPrefBoolean(R.string.pref_key_case_close_lock_screen,true))
                    {
                        iHandler.postDelayed(iLockScreenCallback, getPrefInt(R.string.pref_key_case_close_delay,0) * 1000);
                    }
                }

                // Consume both up and down events to prevent the system doing anything with those
                // That notably prevents the trigger of search F3 in chrome browser
                return getPrefBoolean(R.string.pref_key_filter_close_case,true);
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

                    // Only show message if lock was requested
                    if (getPrefBoolean(R.string.pref_key_case_close_lock_screen,true))
                    {
                        Toast.makeText(this, R.string.toast_screen_lock_abort, Toast.LENGTH_SHORT).show();
                    }
                }

                // Consume both up and down events to prevent the system doing anything with those
                return getPrefBoolean(R.string.pref_key_filter_open_case,true);
            }
            else if (keyCode == KeyEvent.KEYCODE_F5)
            {
                // Keyboard closed
                // Consume both up and down events to prevent the system doing anything with those
                // Fix issue with browser page reload when closing keyboard
                return getPrefBoolean(R.string.pref_key_filter_close_keyboard,true);
            }
            else if (keyCode == KeyEvent.KEYCODE_F6)
            {
                // Keyboard opened
                // Consume both up and down events to prevent the system doing anything with those
                return getPrefBoolean(R.string.pref_key_filter_open_keyboard,true);
            }



        }



        //return false;
        return super.onKeyEvent(event);

    }

    //
    private boolean getPrefBoolean(int aKey, Boolean aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean(getResources().getString(aKey),aDefault);

    }

    private int getPrefInt(int aKey, int aDefault)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getInt(getResources().getString(aKey),aDefault);

    }



    private void turnOnScreen() {
        PowerManager.WakeLock wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "FxService:wakeLock");
        wakeLock.acquire(5000);
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {
        iSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        // The sensor type (as defined in the Sensor class).
        int sensorType = event.sensor.getType();

        // The new data value of the sensor.  Both the light and proximity
        // sensors report one value at a time, which is always the first
        // element in the values array.
        float currentValue = event.values[0];

        switch (sensorType) {
            // Event came from the light sensor.
            case Sensor.TYPE_LIGHT:
                // Set the light sensor text view to the light sensor string
                // from the resources, with the placeholder filled in.
                //mTextSensorLight.setText(getResources().getString(R.string.label_light, currentValue));
                break;
            case Sensor.TYPE_PROXIMITY:
                // Set the proximity sensor text view to the light sensor
                // string from the resources, with the placeholder filled in.
                //mTextSensorProximity.setText(getResources().getString(R.string.label_proximity, currentValue));

                // If no proximity and previously proximity then wake up the screen for defined time
                if (currentValue>0 && iLastProximityValue == 0)
                {
                    // To be safe just cancel possible callbacks
                    iHandler.removeCallbacks(iLockScreenCallback);
                    //
                    turnOnScreen();
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


}
