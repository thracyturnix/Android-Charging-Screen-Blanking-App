package com.example.acsba

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

object OverlayManager {

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun showChargingStatus(context: Context, isFastCharging: Boolean) {
        if (overlayView != null) {
            hideOverlay(context)
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val message = if (isFastCharging) {
            "⚡\nFAST CHARGING"
        } else {
            "CHARGING"
        }

        overlayView = LinearLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL

            addView(TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = if (isFastCharging) 54f else 48f
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
        }

        layoutParams = createLayoutParams().apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
            layoutParams = null
        }
    }

    fun showOverlay(context: Context) {
        if (overlayView != null) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        overlayView = View(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        layoutParams = createLayoutParams().apply {
            screenBrightness = 0.0f
        }

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
            layoutParams = null
        }
    }

    fun blankOverlay(context: Context) {
        if (overlayView == null) {
            showOverlay(context)
            return
        }

        overlayView?.let {
            it.setBackgroundColor(Color.BLACK)
            if (it is LinearLayout) {
                it.removeAllViews()
            }
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        layoutParams?.let {
            it.screenBrightness = 0.0f
            try {
                windowManager.updateViewLayout(overlayView, it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    fun hideOverlay(context: Context) {
        overlayView?.let {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            layoutParams = null
        }
    }
}
