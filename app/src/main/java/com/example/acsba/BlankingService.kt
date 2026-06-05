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
    private var isOverlayBlanked = false
    private var hasFastChargingSignal = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkStateRunnable = Runnable { checkBatteryState() }
    private val blankOverlayRunnable = Runnable {
        if (isOverlayShowing) {
            OverlayManager.blankOverlay(this)
            isOverlayBlanked = true
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
                addAction(Intent.ACTION_BATTERY_CHANGED)
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

        handleBatteryState(batteryStatus)
    }

    private fun handleBatteryState(batteryStatus: Intent?) {
        logBatteryState(batteryStatus)

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
        hasFastChargingSignal = hasFastChargingSignal || isFastCharging

        if (isOverlayShowing) {
            if (!isOverlayBlanked) {
                OverlayManager.updateChargingStatus(hasFastChargingSignal)
            }
            return
        }

        OverlayManager.showChargingStatus(this, hasFastChargingSignal)
        isOverlayShowing = true
        isOverlayBlanked = false
        handler.removeCallbacks(blankOverlayRunnable)
        handler.postDelayed(blankOverlayRunnable, CHARGING_STATUS_DISPLAY_MS)
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            handler.removeCallbacks(blankOverlayRunnable)
            OverlayManager.hideOverlay(this)
            isOverlayShowing = false
            isOverlayBlanked = false
            hasFastChargingSignal = false
        }
    }

    private fun isFastCharging(batteryStatus: Intent?): Boolean {
        if (batteryStatus == null) return false

        val batteryManager = getSystemService(BatteryManager::class.java)
        val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargerOnline = batteryStatus.getIntExtra(EXTRA_CHARGER_ONLINE, CHARGER_ONLINE_UNKNOWN)
        val maxCurrentMicroamps = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1)
        val maxVoltageMicrovolts = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1)
        val currentNowMicroamps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val voltageMillivolts = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        if (chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS &&
            chargerOnline == SAMSUNG_FAST_WIRELESS_ONLINE
        ) {
            return true
        }

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

    private fun logBatteryState(batteryStatus: Intent?) {
        if (batteryStatus == null) {
            Log.d(TAG, "battery-event missing")
            return
        }

        val batteryManager = getSystemService(BatteryManager::class.java)
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val online = batteryStatus.getIntExtra(EXTRA_CHARGER_ONLINE, CHARGER_ONLINE_UNKNOWN)
        val chargeType = batteryStatus.getIntExtra(EXTRA_CHARGE_TYPE, -1)
        val chargerType = batteryStatus.getIntExtra(EXTRA_CHARGER_TYPE, -1)
        val chargingStatus = batteryStatus.getIntExtra(EXTRA_ANDROID_CHARGING_STATUS, -1)
        val currentEvent = batteryStatus.getIntExtra(EXTRA_CURRENT_EVENT, 0)
        val maxCurrentMicroamps = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1)
        val maxVoltageMicrovolts = batteryStatus.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1)
        val currentNowMicroamps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val voltageMillivolts = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val maxWatts = wattsFromMicroampsAndMicrovolts(maxCurrentMicroamps, maxVoltageMicrovolts)
        val currentWatts = wattsFromMicroampsAndMillivolts(kotlin.math.abs(currentNowMicroamps), voltageMillivolts)

        Log.d(
            TAG,
            "battery-event status=$status plugged=$plugged online=$online charge_type=$chargeType " +
                "charger_type=$chargerType charging_status=$chargingStatus current_event=0x${currentEvent.toString(16)} " +
                "max_current_ua=$maxCurrentMicroamps max_voltage_uv=$maxVoltageMicrovolts " +
                "current_now_ua=$currentNowMicroamps voltage_mv=$voltageMillivolts " +
                "max_watts=$maxWatts current_watts=$currentWatts level=$level"
        )
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
                Intent.ACTION_BATTERY_CHANGED -> {
                    handler.removeCallbacks(checkStateRunnable)
                    handleBatteryState(intent)
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
        private const val TAG = "WirelessBlanker"
        const val CHANNEL_ID = "BlankingServiceChannel"
        private const val CHARGING_STATUS_DISPLAY_MS = 15_000L
        private const val FAST_CHARGING_WATTS = 10.0
        private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
        private const val EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage"
        private const val EXTRA_CHARGE_TYPE = "charge_type"
        private const val EXTRA_CHARGER_TYPE = "charger_type"
        private const val EXTRA_ANDROID_CHARGING_STATUS = "android.os.extra.CHARGING_STATUS"
        private const val EXTRA_CHARGER_ONLINE = "online"
        private const val CHARGER_ONLINE_UNKNOWN = -1
        private const val SAMSUNG_FAST_WIRELESS_ONLINE = 100
        private const val EXTRA_CURRENT_EVENT = "current_event"
    }
}
