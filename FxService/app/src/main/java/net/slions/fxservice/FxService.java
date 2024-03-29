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
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.Toast;

// For aut-sync scheduler
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;
import static net.slions.fxservice.UtilsKt.createForceLandscapeOverlay;
import static net.slions.fxservice.UtilsKt.destroyForceLandscapeOverlay;
import static net.slions.fxservice.UtilsKt.updateForceLandscapeOverlay;
import static net.slions.fxservice.UtilsKt.vibrateOnScreenLock;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

public class FxService extends AccessibilityService
        implements SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener
{

    private AlertDialog iLockAlertDialog;
    private int iSecondsBeforeLock=0;

    // System sensor manager instance.
    private SensorManager iSensorManager;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    // Accelerometer sensor
    private Sensor iSensorAccelerometer;
    // Accelerometer sensor
    private Sensor iSensorMagnetometer;
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
    // Used to unable screen wake from proximity sensor only if locked by case close
    private boolean iProximitySensorArmed = false;
    // Well... you know what that it :)
    Vibrator iVibrator;
    // Rotation management
    int iRotationDelay = 250;
    double iRotationPortraitAngle = Math.toRadians(50);
    double iRotationLandscapeAngle = Math.toRadians(50);
    // Hardware keyboard status
    private int iHardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_UNDEFINED;
    // Force landscape overlay
    View iForceLandscapeOverlay;

    class BroadcastListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //Log.d("FxService",intent.getAction());
            if (Objects.equals(intent.getAction(), Intent.ACTION_USER_PRESENT)) {
                // Device was unlocked, disarm proximity sensor
                iProximitySensorArmed = false;
                // Reset that guy here too as it keeps failing us
                closeProximitySensor();
                setupProximitySensor(true);
            }
        }
    }

    private BroadcastListener iBroadcastListener = new BroadcastListener();

    //ArrayList<SensorEvent> iLightSensorSamples = new ArrayList<SensorEvent>();
    //
    FrameLayout mLayout;
    //View mColorLayout;

    // Used to schedule various callbacks
    Handler iHandler = new Handler();

    // Used to turn on auto-sync
    Runnable iAutoSyncTurnOnCallback = new Runnable()
    {
        @Override
        public void run()
        {
            //
            ContentResolver.setMasterSyncAutomatically(true);
            Toast.makeText(FxService.this, R.string.toast_auto_sync_enabled, Toast.LENGTH_SHORT).show();
            //
            long delay = FxSettings.getPrefInt(FxService.this,R.string.pref_key_auto_sync_on_duration,5) * 60 * 1000;
            iHandler.postDelayed(iAutoSyncTurnOffCallback, delay);
        }
    };


    // Used to turn off auto-sync
    Runnable iAutoSyncTurnOffCallback = new Runnable()
    {
        @Override
        public void run()
        {
            // Turn off call back
            ContentResolver.setMasterSyncAutomatically(false);
            Toast.makeText(FxService.this, R.string.toast_auto_sync_disabled, Toast.LENGTH_SHORT).show();
            //
            scheduleNextAutoSync();
        }
    };

    // Used to delay lock screen action
    Runnable iLockScreenCallback = new Runnable()
    {
        @Override
        public void run()
        {
            // Lock our screen
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            // No need for proximity lock anymore
            releaseProximityWakeLock();
            // Vibrate if user wants it
            String pattern = FxSettings.getPrefString(FxService.this, R.string.pref_key_case_close_vibration_pattern, R.string.pref_default_case_close_vibration_pattern);
            vibrateOnScreenLock(FxService.this, pattern);
        }
    };

    // Used to implement lock logic after closing keyboard
    Runnable iLockAlertDialogCallback = new Runnable()
    {
        @Override
        public void run()
        {
            if (iLockAlertDialog==null)
            {
                return;
            }

            // If our timer is not elapsed and the device is not already locked
            if (iSecondsBeforeLock>0 && !isDeviceLocked())
            {
                // Then keep counting
                // Update our message
                iLockAlertDialog.setMessage(getString(R.string.dialog_lock_message,iSecondsBeforeLock));
                iSecondsBeforeLock--;
                // Wait another second
                iHandler.postDelayed(iLockAlertDialogCallback, 1000);
            }
            else
            {
                // Either our countdown is at an end or the device is already locked
                // Time to lock our device
                iLockAlertDialog.dismiss();
                if (!isDeviceLocked()) // Defensive
                {
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                }
            }
        }
    };

    // Used to delay lock screen action
    Runnable iAutoRotateCallback = new Runnable()
    {
        @Override
        public void run()
        {
            // Time to apply our rotation
            Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, iTargetRotation);
            // Tell that we don't have any pending auto-rotate callback
            iTargetRotation = KNoPendingRotation;
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

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        //filter.addAction(Intent.ACTION_USER_UNLOCKED);

        registerReceiver(iBroadcastListener, filter);

        // Create an overlay
        setupColorFilter();
        //
        setupProximitySensor();
        //
        setupLightSensor();
        //
        setupAutoSync();
        //
        setupAccelerometer();
        setupMagnetometer();

        // Get notification when preferences are changed
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        Toast.makeText(this, R.string.toast_service_connected, Toast.LENGTH_SHORT).show();
        if(BuildConfig.DEBUG) {
            // Useful to tell which variant of the service was started
            Toast.makeText(this, R.string.toast_debug, Toast.LENGTH_SHORT).show();
        }

        iVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Save that so that we can compare it upon resource change
        iHardKeyboardHidden = getResources().getConfiguration().hardKeyboardHidden;
        setupForceLandscapeOverlay(isKeyboardOpened());
    }

    // From Service
    // Called whenever our service is stopped
    @Override
    public void onDestroy()
    {
        super.onDestroy();

        unregisterReceiver(iBroadcastListener);
        cancelAutoSync();
        closeLightSensor();
        closeProximitySensor();
        closeAccelerometer();
        closeMagnetometer();
        releaseProximityWakeLock();
        iWakeLockProximityScreenOff = null;
        iSensorManager = null;
        iVibrator = null;
        iHardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_UNDEFINED;

        if (iForceLandscapeOverlay!=null)
        {
            destroyForceLandscapeOverlay(this,iForceLandscapeOverlay);
            iForceLandscapeOverlay = null;
        }
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

    /**
     * From here we can notably check if our keyboard was just closed or opened.
     *
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        //Toast.makeText(FxService.this, "Config changed", Toast.LENGTH_SHORT).show();

        // Check if hardware keyboard status has changed
        if (newConfig.hardKeyboardHidden != iHardKeyboardHidden)
        {
            iHardKeyboardHidden = newConfig.hardKeyboardHidden;
            setupForceLandscapeOverlay(isKeyboardOpened());

            // Hardware keyboard status changed
            if (isKeyboardClosed())
            {
                // Keyboard was closed
                onKeyboardClosed();
                // Show debug message if needed
                if (FxSettings.showKeyboardStatusChange(this)) {
                    Toast.makeText(FxService.this, R.string.toast_hardware_keyboard_closed, Toast.LENGTH_SHORT).show();
                }

                // Change screen rotation if needed
                if (Settings.System.canWrite(this) && FxSettings.isScreenRotationAuto(this) && FxSettings.isScreenRotationKeyboardPortrait(this)) {
                    scheduleRotationIfNeeded(Surface.ROTATION_0);
                }
            }
            else if (isKeyboardOpened())
            {
                // Keyboard was opened
                onKeyboardOpened();
                // Show debug message if needed
                if (FxSettings.showKeyboardStatusChange(this)) {
                    Toast.makeText(FxService.this, R.string.toast_hardware_keyboard_opened, Toast.LENGTH_SHORT).show();
                }

                // Change screen rotation if needed
                if (Settings.System.canWrite(this) && FxSettings.isScreenRotationAuto(this) && FxSettings.isScreenRotationKeyboardLandscape(this)) {
                    scheduleRotationIfNeeded(Surface.ROTATION_90);
                }
            }
        }
    }

    /**
     * For some reason that works properly only when the keyboard is open.
     * Using values other than ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED is breaking our auto custom rotation somehow.
     * Even removing/destroying the view overlay did not help somehow.
     * Ho well that was a big mess so don't assume anything if you are changing that code.
     */
    void setupForceLandscapeOverlay(boolean aForce)
    {
        // Defensive
        if (!Settings.canDrawOverlays(this))
        {
            return;
        }

        if (aForce && isForceLandscapeEnabled())
        {
            // Force landscape if desired
            //iForceLandscapeOverlay.setVisibility(View.VISIBLE);
            if (iForceLandscapeOverlay==null)
            {
                iForceLandscapeOverlay = createForceLandscapeOverlay(FxService.this);
            }
            else
            {
                updateForceLandscapeOverlay(FxService.this,iForceLandscapeOverlay,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
        else
        {
            // Make sure we allow auto rotate
            //iForceLandscapeOverlay.setVisibility(View.GONE);
            if (iForceLandscapeOverlay!=null)
            {
                updateForceLandscapeOverlay(FxService.this,iForceLandscapeOverlay,ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                //destroyForceLandscapeOverlay(FxService.this, iForceLandscapeOverlay);
                //iForceLandscapeOverlay = null;
            }
        }
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
        else if (key.startsWith("pref_key_auto_sync"))
        {
            // Something changed in our auto sync setup
            setupAutoSync();
        }
        else if (key == getResources().getString(R.string.pref_key_screen_rotation_auto))
        {
            // Something changed in our screen rotation setup
            setupAccelerometer();
            setupMagnetometer();
            setupRotation();
        }
        else if (key == getResources().getString(R.string.pref_key_screen_rotation_force_landscape))
        {
            // Force landscape option changed
            setupForceLandscapeOverlay(isKeyboardOpened());
        }
        else if (key.startsWith("pref_key_screen_rotation"))
        {
            // Update screen rotation settings
            setupRotation();
        }
    }

    void setupRotation()
    {
        iRotationDelay = FxSettings.getPrefInt(this, R.string.pref_key_screen_rotation_delay,25) * 10;
        iRotationPortraitAngle = Math.toRadians(FxSettings.getPrefInt(this, R.string.pref_key_screen_rotation_portrait_angle,50));
        iRotationLandscapeAngle = Math.toRadians(FxSettings.getPrefInt(this, R.string.pref_key_screen_rotation_landscape_angle,50));
    }

    void scheduleNextAutoSync()
    {
        if (!isAutoSyncSchedulerEnabled())
        {
            // Scheduler is disabled
            return;
        }

        if (isWithinAutoSyncSchedule())
        {
            long delay = FxSettings.getPrefInt(FxService.this,R.string.pref_key_auto_sync_off_duration,15) * 60 * 1000;
            iHandler.postDelayed(iAutoSyncTurnOnCallback, delay);
        }
        else
        {
            // Only turn auto sync back on at the start of the next start
            int startHour = FxSettings.getPrefInt(FxService.this,R.string.pref_key_auto_sync_start,8);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = now.truncatedTo(ChronoUnit.DAYS).withHour(startHour);
            if (now.isAfter(start))
            {
                start = start.plusDays(1);
            }

            long delay = ChronoUnit.MILLIS.between(now, start);
            iHandler.postDelayed(iAutoSyncTurnOnCallback, delay);
        }
    }

    boolean isKeyboardOpened()
    {
        return iHardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
    }

    boolean isKeyboardClosed()
    {
        return iHardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    boolean isWithinAutoSyncSchedule()
    {
        int startHour = FxSettings.getPrefInt(FxService.this,R.string.pref_key_auto_sync_start,8);
        int endHour = FxSettings.getPrefInt(FxService.this,R.string.pref_key_auto_sync_end,22);

        if ( startHour == endHour )
        {
            // Just sync all day long
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.truncatedTo(ChronoUnit.DAYS).withHour(startHour);
        LocalDateTime end = now.truncatedTo(ChronoUnit.DAYS).withHour(endHour);

        // Make sure start is before end
        if (start.isAfter(end))
        {
            start = start.minusHours(24);
        }

        if ( now.isAfter(start) && now.isBefore(end) )
        {
            return true;
        }

        return false;
    }

    private void setupAutoSync()
    {
        cancelAutoSync();

        if (isAutoSyncSchedulerEnabled() && isWithinAutoSyncSchedule())
        {
            // If within schedule hours start by turning on auto-sync now
            iHandler.postDelayed(iAutoSyncTurnOnCallback, 0);
        }
        else
        {
            // If outside schedule hours start by turning off auto-sync now
            iHandler.postDelayed(iAutoSyncTurnOffCallback, 0);
        }
        //scheduleNextAutoSync();
    }

    private void cancelAutoSync()
    {
        iHandler.removeCallbacks(iAutoSyncTurnOffCallback);
        iHandler.removeCallbacks(iAutoSyncTurnOnCallback);
    }


    private boolean isAutoSyncSchedulerEnabled()
    {
        return FxSettings.getPrefBoolean(this, R.string.pref_key_auto_sync_scheduler,false);
    }

    private boolean isScreenFilterEnabled()
    {
        return FxSettings.getPrefBoolean(this, R.string.pref_key_screen_filter,false);
    }

    private boolean isForceLandscapeEnabled()
    {
        return FxSettings.getPrefBoolean(this, R.string.pref_key_screen_rotation_force_landscape,false);
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
            //mLayout = new FxOverlay(this);
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
            if (!FxSettings.getPrefBoolean(this, R.string.pref_key_screen_filter_disable_touch_screen,false)) {
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }

            //lp.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            lp.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            lp.gravity = Gravity.TOP;

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
        setupProximitySensor(false);
    }

    private void setupProximitySensor(boolean aSilent)
    {
        // Open proximity sensor if needed
        if (FxSettings.getPrefBoolean(this,R.string.pref_key_proximity_wake_up,true))
        {
            if (!aSilent)
            {
                Toast.makeText(this, R.string.toast_proximity_sensor_enabled, Toast.LENGTH_SHORT).show();
            }
            openProximitySensor();
        }
        else
        {
            if (!aSilent)
            {
                Toast.makeText(this, R.string.toast_proximity_sensor_disabled, Toast.LENGTH_SHORT).show();
            }
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

    private void setupAccelerometer()
    {
        // Open proximity sensor if needed
        if (FxSettings.isScreenRotationAuto(this))
        {
            //Toast.makeText(this, R.string.toast_proximity_sensor_enabled, Toast.LENGTH_SHORT).show();
            openAccelerometer();
        }
        else
        {
            //Toast.makeText(this, R.string.toast_proximity_sensor_disabled, Toast.LENGTH_SHORT).show();
            closeAccelerometer();
        }
    }

    //

    private void openAccelerometer()
    {
        iSensorAccelerometer = iSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (iSensorAccelerometer != null) {
            iSensorManager.registerListener(this, iSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void closeAccelerometer()
    {
        if (iSensorAccelerometer != null)
        {
            iSensorManager.unregisterListener(this, iSensorAccelerometer);
            iSensorAccelerometer = null;
        }
    }

    /**
     *
     */
    private void setupMagnetometer()
    {
        // Open proximity sensor if needed
        if (FxSettings.isScreenRotationAuto(this))
        {
            //Toast.makeText(this, R.string.toast_proximity_sensor_enabled, Toast.LENGTH_SHORT).show();
            openMagnetometer();
        }
        else
        {
            //Toast.makeText(this, R.string.toast_proximity_sensor_disabled, Toast.LENGTH_SHORT).show();
            closeMagnetometer();
        }
    }

    /**
     *
     */
    private void openMagnetometer()
    {
        iSensorMagnetometer = iSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (iSensorMagnetometer != null) {
            iSensorManager.registerListener(this, iSensorMagnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void closeMagnetometer()
    {
        if (iSensorMagnetometer != null)
        {
            iSensorManager.unregisterListener(this, iSensorMagnetometer);
            iSensorMagnetometer = null;
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

    /**
     * Lock the screen upon case closure with specified delay if specified in user settings.
     */
    void onCaseClosed()
    {
        Log.d("FxService:", "Case close detected");
        // Check if user wants us to lock screen when closing her case
        if (FxSettings.getPrefBoolean(this, R.string.pref_key_case_close_lock_screen,true))
        {
            Log.d("FxService:", "Lock on case close");
            int delayInMs = getLockDelayInMilliseconds();
            // Make sure the screen goes off while we are delaying screen lock
            iWakeLockProximityScreenOff.acquire(delayInMs+1000);
            // Delayed screen lock
            iHandler.postDelayed(iLockScreenCallback, delayInMs);
            //
            iProximitySensorArmed = true;
        }
    }

    /**
     *
     */
    void onCaseOpened()
    {
        // To be safe just cancel possible callbacks
        iHandler.removeCallbacks(iLockScreenCallback);
        releaseProximityWakeLock();

        // Only show message if lock was requested
        if (FxSettings.getPrefBoolean(this, R.string.pref_key_case_close_lock_screen,true))
        {
            Toast.makeText(this, R.string.toast_screen_lock_abort, Toast.LENGTH_SHORT).show();
        }

        iProximitySensorArmed = false;
    }

    /**
     *
     */
    void onKeyboardClosed()
    {
        if (FxSettings.getPrefBoolean(this,R.string.pref_key_keyboard_close_lock_screen,false))
        {
            lockDeviceUponKeyboardClose();
        }
    }

    /**
     *
     */
    void onKeyboardOpened()
    {
        iHandler.removeCallbacks(iLockAlertDialogCallback);
        if (iLockAlertDialog!=null)
        {
            iLockAlertDialog.dismiss();
        }
    }


    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        //Log.d("FxService:", event.toString());

        // Hardcoded shortcut to turn overlay on and off
        // That's notably intended to help people who shot themselves in the foot by using a solid color for instance
        // Ctrl + Fn + O
        if ((event.getMetaState() == (KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_FUNCTION_ON))
        // Ctrl + Fn + O with Fn and Alt swapped with Fx Qwerty keyboard layout
        ||(event.getMetaState() == (KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_ALT_ON|KeyEvent.META_ALT_LEFT_ON))
        ||(event.getMetaState() == (KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON|KeyEvent.META_ALT_RIGHT_ON)))
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

        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P)
        {
            // For OS SDK level > 28 we assume we are on Lineage OS most certainly 18.1 which is Android 12

            // On Lineage OS we don't get a keycode but still get the scan code
            if (scanCode == 468)
            {
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)
                {
                    // That's our case close cue
                    onCaseClosed();
                }
                else if (action == KeyEvent.ACTION_UP)
                {
                    onCaseOpened();
                }
            }
        }
        else
        {
            // That was working well for F(x)tec Pro¹ on stock Android 9
            if (event.getMetaState() == KeyEvent.META_FUNCTION_ON)
            {
                if (keyCode == KeyEvent.KEYCODE_F3)
                {
                    // Case closed
                    if (action == KeyEvent.ACTION_UP)
                    {
                        // Only perform action on key up
                        onCaseClosed();
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
                        onCaseOpened();
                    }

                    // Consume both up and down events to prevent the system doing anything with those
                    return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_open_case,true);
                }
                else if (keyCode == KeyEvent.KEYCODE_F5)
                {
                    // Keyboard closed
                    if (action == KeyEvent.ACTION_UP)
                    {
                        // Moved to proper handler so that it works on Lineage OS too
                        //onKeyboardClosed();
                    }

                    // Consume both up and down events to prevent the system doing anything with those
                    // Fix issue with browser page reload when closing keyboard
                    return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_close_keyboard,true);
                }
                else if (keyCode == KeyEvent.KEYCODE_F6)
                {
                    // Keyboard opened
                    if (action == KeyEvent.ACTION_UP)
                    {
                        // Cancel potential pending lock
                        //onKeyboardOpened()
                    }
                    // Consume both up and down events to prevent the system doing anything with those
                    return FxSettings.getPrefBoolean(this, R.string.pref_key_filter_open_keyboard,true);
                }

            }
        }


        // For proper system vibration and audio feedback consider using:
        // view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // This will require a view however and currently we only have one when our overlay is one.

        if (action == KeyEvent.ACTION_DOWN) {
            //v.vibrate(25);
            //v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            //v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));

            int duration = FxSettings.getPrefInt(this, R.string.pref_key_keyboard_key_down_vibration_duration,1);
            int amplitude = FxSettings.getPrefInt(this, R.string.pref_key_keyboard_key_down_vibration_amplitude,1);

            if (duration!=0) {
                iVibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            }

        }
        else if (action == KeyEvent.ACTION_UP) {

            int duration = FxSettings.getPrefInt(this, R.string.pref_key_keyboard_key_up_vibration_duration,0);
            int amplitude = FxSettings.getPrefInt(this, R.string.pref_key_keyboard_key_up_vibration_amplitude,1);

            if (duration!=0) {
                iVibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
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

                //Log.d("FxService:", "Proximity sensor data");
                //Log.d("FxService:", "Proximity sensor armed: " + iProximitySensorArmed);
                //Log.d("FxService:", "Proximity sensor current: " + currentValue);
                //Log.d("FxService:", "Proximity sensor last: " + iLastProximityValue);

                if (currentValue>0 && iLastProximityValue == 0 && iProximitySensorArmed)
                {
                    // To be safe just cancel possible callbacks
                    iHandler.removeCallbacks(iLockScreenCallback);
                    releaseProximityWakeLock();
                    //
                    turnOnScreen(FxSettings.getPrefInt(this,R.string.pref_key_proximity_wake_up_timeout,5) * 1000);
                    // On Lineage OS for some reason we needed to reset our proximity sensor here
                    // Otherwise we stop getting notification after the first one once the screen is locked
                    closeProximitySensor();
                    setupProximitySensor(true);
                }

                iLastProximityValue = currentValue;
                break;

            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerReading,0, accelerometerReading.length);
                updateOrientationAngles();
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerReading,0, magnetometerReading.length);
                break;

            default:
                // do nothing
        }
    }

    final static int KNoPendingRotation = -1;
    int iTargetRotation = KNoPendingRotation;


    private void cancelRotation() {
        iHandler.removeCallbacks(iAutoRotateCallback);
        iTargetRotation = KNoPendingRotation;
    }

    /**
     *
     * @param aRequestedRotation
     */
    private void scheduleRotationIfNeeded(int aRequestedRotation) {

        int rotation = Surface.ROTATION_0;
        try {
            rotation = Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        // System is already in request rotation
        if (rotation == aRequestedRotation) {
            // Just cancel any pending rotation then
            cancelRotation();
        }
        // If we have not already scheduled this rotation, do it now
        else if (iTargetRotation != aRequestedRotation) {
            cancelRotation();
            iTargetRotation = aRequestedRotation;
            iHandler.postDelayed(iAutoRotateCallback, iRotationDelay);
        }
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    // See: https://developer.android.com/guide/topics/sensors/sensors_position#sensors-pos-orient
    public void updateOrientationAngles() {
        //
        if (isDeviceLocked())
        {
            // Don't bother doing rotation if the screen is locked one way or another
            //Log.d("FxService", "Device Locked, no rotation");
            return;
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        /*
        double azimuth = Math.toDegrees(orientationAngles[0]);
        // Pitch is used for portrait detection
        // Pitch -90 is head down, 90 is head up
        double pitch = Math.toDegrees(orientationAngles[1]);
        // Roll is used for landscape detection
        // Roll -90 is left side down, +90 is right side down
        double roll = Math.toDegrees(orientationAngles[2]);

        Log.d("FxService:",  azimuth + "," + pitch +"," + roll);
        */

        // Pitch is used for portrait detection
        // Pitch -90 is head down, +90 is head up
        double pitchInRadians = orientationAngles[1];
        // Roll is used for landscape detection
        // Roll -90 is left side down, +90 is right side down
        double rollInRadians = orientationAngles[2];


        if (Settings.System.canWrite(this)) { // defensive
            if (isKeyboardClosed() && FxSettings.isScreenRotationKeyboardPortraitLocked(this)) {
                scheduleRotationIfNeeded(Surface.ROTATION_0);
            }
            else if (isKeyboardOpened() && FxSettings.isScreenRotationKeyboardLandscapeLocked(this)) {
                scheduleRotationIfNeeded(Surface.ROTATION_90);
            }
            // Portrait detection
            else if (pitchInRadians < -iRotationPortraitAngle) {
                scheduleRotationIfNeeded(Surface.ROTATION_0);
            } else if (pitchInRadians > iRotationPortraitAngle) {
                scheduleRotationIfNeeded(Surface.ROTATION_180);
            }
            // Landscape detection
            else if (rollInRadians < -iRotationLandscapeAngle) {
                scheduleRotationIfNeeded(Surface.ROTATION_90);
            } else if (rollInRadians > iRotationLandscapeAngle) {
                scheduleRotationIfNeeded(Surface.ROTATION_270);
            }
        }

        // "mOrientationAngles" now has up-to-date information.
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

        if (!FxSettings.isScreenFilterEnabled(this) && aEvent.values[0] < FxSettings.getPrefInt(this, R.string.pref_key_light_sensor_screen_filter_threshold_on,2))
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

    private boolean isDeviceLocked()
    {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km.isKeyguardLocked();
    }


    private void lockDeviceUponKeyboardClose()
    {
        if (isDeviceLocked())
        {
            // Device is already locked
            return;
        }

        // Start our countdown if needed
        iSecondsBeforeLock = FxSettings.getPrefInt(this,R.string.pref_key_keyboard_close_delay_in_seconds,0);
        if (iSecondsBeforeLock>0)
        {
            // If we don't have that permission to show alert dialog then ask for it
            if (!Settings.canDrawOverlays(this))
            {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

            // TODO: custom dark theme that works
            //AlertDialog.Builder builder = new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            //AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.Theme_AppCompat_Dialog_Alert);
            AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.AppTheme_LockAlertDialog);

            //
            builder.setMessage(getString(R.string.dialog_lock_message,iSecondsBeforeLock))
                    .setTitle(R.string.dialog_lock_title)
                    .setIcon(R.drawable.ic_app_icon)
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {iHandler.removeCallbacks(iLockAlertDialogCallback); dialog.cancel();});
            //
            iLockAlertDialog = builder.create();
            iLockAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            iLockAlertDialog.show();

            // Start our delayed lock
            iSecondsBeforeLock--;
            iHandler.postDelayed(iLockAlertDialogCallback, 1000);
        }
        else
        {
            // Perform lock on the spot
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
    }
}
