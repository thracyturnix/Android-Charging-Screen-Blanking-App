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

class BlankingService : Service() {

    private val receiver = PowerConnectionReceiver()
    private var isReceiverRegistered = false
    private var isOverlayShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkStateRunnable = Runnable { checkBatteryState() }
    private val blankOverlayRunnable = Runnable {
        if (isOverlayShowing) {
            OverlayManager.blankOverlay(this)
        }
    }

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
            showOverlay(isFastCharging(batteryStatus))
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay(isFastCharging: Boolean) {
        if (isOverlayShowing) return

        OverlayManager.showChargingStatus(this, isFastCharging)
        isOverlayShowing = true
        handler.removeCallbacks(blankOverlayRunnable)
        handler.postDelayed(blankOverlayRunnable, CHARGING_STATUS_DISPLAY_MS)
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            handler.removeCallbacks(blankOverlayRunnable)
            OverlayManager.hideOverlay(this)
            isOverlayShowing = false
        }
    }

    private fun isFastCharging(batteryStatus: Intent?): Boolean {
        if (batteryStatus == null) return false

        val batteryManager = getSystemService(BatteryManager::class.java)
        val maxCurrentMicroamps = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1)
        val maxVoltageMicrovolts = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1)
        val currentNowMicroamps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val voltageMillivolts = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        val maxWatts = wattsFromMicroampsAndMicrovolts(maxCurrentMicroamps, maxVoltageMicrovolts)
        if (maxWatts >= FAST_CHARGING_WATTS) return true

        val currentWatts = wattsFromMicroampsAndMillivolts(kotlin.math.abs(currentNowMicroamps), voltageMillivolts)
        return currentWatts >= FAST_CHARGING_WATTS
    }

    private fun wattsFromMicroampsAndMicrovolts(currentMicroamps: Int, voltageMicrovolts: Int): Double {
        if (currentMicroamps <= 0 || voltageMicrovolts <= 0) return 0.0
        return currentMicroamps.toDouble() * voltageMicrovolts.toDouble() / 1_000_000_000_000.0
    }

    private fun wattsFromMicroampsAndMillivolts(currentMicroamps: Int, voltageMillivolts: Int): Double {
        if (currentMicroamps <= 0 || voltageMillivolts <= 0) return 0.0
        return currentMicroamps.toDouble() * voltageMillivolts.toDouble() / 1_000_000_000.0
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkStateRunnable)
        handler.removeCallbacks(blankOverlayRunnable)
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
        private const val CHARGING_STATUS_DISPLAY_MS = 15_000L
        private const val FAST_CHARGING_WATTS = 10.0
        private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
        private const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"
    }
}
