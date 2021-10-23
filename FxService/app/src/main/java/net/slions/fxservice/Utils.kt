package net.slions.fxservice

import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import java.lang.Exception

fun createForceLandscapeOverlay(aContext: Context) : View?
{
    var orientationChanger = FrameLayout(aContext);
    orientationChanger.isClickable = false;
    orientationChanger.isFocusable = false;
    orientationChanger.isFocusableInTouchMode = false;
    orientationChanger.isLongClickable = false;
    orientationChanger.visibility = View.VISIBLE;

    var orientationLayout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
    orientationLayout.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

    val wm = aContext.getSystemService(WINDOW_SERVICE) as WindowManager
    wm.addView(orientationChanger, orientationLayout)

    return orientationChanger

}

fun updateForceLandscapeOverlay(aContext: Context, aView : View, aScreenOrientation: Int)
{
    var orientationLayout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
    orientationLayout.screenOrientation = aScreenOrientation;

    val wm = aContext.getSystemService(WINDOW_SERVICE) as WindowManager
    wm.updateViewLayout(aView,orientationLayout)
}



fun destroyForceLandscapeOverlay(aContext: Context, aView : View)
{
    val wm = aContext.getSystemService(WINDOW_SERVICE) as WindowManager
    wm.removeView(aView)
}

//
fun vibrateOnScreenLock(aContext: Context, aPattern: String): Boolean {
    val vibrateOnLock = FxSettings.getPrefBoolean(aContext, R.string.pref_key_case_close_vibration_on_lock, true)
    val useDoubleClick = FxSettings.getPrefBoolean(aContext, R.string.pref_key_case_close_vibration_double_click, false)
    var parseSuccess = true
    if (vibrateOnLock) {

        val vibrator = aContext.getSystemService(VIBRATOR_SERVICE) as Vibrator
        //iVibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && useDoubleClick) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            var longs = longArrayOf(0, 30, 50, 15, 50, 30)
            // Try parse our our pattern
            try {
                val parts = aPattern.split(",").toTypedArray()
                var tmp = LongArray(parts.size)
                for (i in parts.indices) {
                    tmp[i] = parts[i].toInt().toLong()
                }
                longs = tmp
            } catch (ex: Exception) {
                // Fail to parse our pattern, notify our user
                Toast.makeText(aContext, R.string.toast_vibration_pattern_error, Toast.LENGTH_SHORT).show()
                parseSuccess = false
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longs, -1))
            // For the record F(x)Tec Pro1 does not have amplitude control so defining amplitude does not bring us anything
            //boolean hasAmplitudeControl = iVibrator.hasAmplitudeControl();
            //if (hasAmplitudeControl) {
            //    VibrationEffect effect = VibrationEffect.createWaveform(mVibratePattern, mAmplitudes, -1);
            //    iVibrator.vibrate(effect);
            //}
        }
    }

    return parseSuccess
}
