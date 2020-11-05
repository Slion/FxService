package net.slions.fxservice

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

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
