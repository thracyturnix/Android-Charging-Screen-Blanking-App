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
    private var statusTextView: TextView? = null

    fun showChargingStatus(context: Context, isFastCharging: Boolean) {
        if (overlayView != null && statusTextView != null) {
            updateChargingStatus(isFastCharging)
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayView = LinearLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL

            statusTextView = TextView(context).apply {
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            addView(statusTextView)
        }

        updateChargingStatus(isFastCharging)

        layoutParams = createLayoutParams().apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
            layoutParams = null
            statusTextView = null
        }
    }

    fun updateChargingStatus(isFastCharging: Boolean) {
        statusTextView?.apply {
            text = if (isFastCharging) {
                "⚡\nFAST CHARGING"
            } else {
                "CHARGING"
            }
            textSize = if (isFastCharging) 54f else 48f
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
            statusTextView = null
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
        statusTextView = null

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
        val layoutParams = WindowManager.LayoutParams(
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutParams.fitInsetsTypes = 0
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        }

        return layoutParams
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
            statusTextView = null
        }
    }
}
