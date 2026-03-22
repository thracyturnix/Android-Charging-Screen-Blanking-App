package com.example.acsba

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class BlankingService : Service() {

    private val receiver = PowerConnectionReceiver()
    private var isReceiverRegistered = false
    private var isOverlayShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkStateRunnable = Runnable { checkBatteryState() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                // We also need ACTION_BATTERY_CHANGED to check the initial state thoroughly
                // but registering for CONNECTED/DISCONNECTED is better for battery than constant polling
            }
            registerReceiver(receiver, filter)
            isReceiverRegistered = true
        }

        // Check initial state in case it's ALREADY on a wireless charger
        checkBatteryState()

        return START_STICKY
    }

    private fun checkBatteryState() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            applicationContext.registerReceiver(null, ifilter)
        }
        
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                  status == BatteryManager.BATTERY_STATUS_FULL
        val isWireless: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        if (isCharging && isWireless) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay() {
        if (!isOverlayShowing) {
            OverlayManager.showOverlay(this)
            isOverlayShowing = true
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            OverlayManager.hideOverlay(this)
            isOverlayShowing = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkStateRunnable)
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }

    inner class PowerConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // When power connect/disconnect changes, re-evaluate battery state to see if it's wireless
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handler.removeCallbacks(checkStateRunnable)
                    handler.postDelayed(checkStateRunnable, 2000)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    handler.removeCallbacks(checkStateRunnable)
                    checkBatteryState()
                }
                else -> {
                    checkBatteryState()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "BlankingServiceChannel"
    }
}
