package com.example.acsba

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.acsba.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("BlankerPrefs", Context.MODE_PRIVATE)
        val prefKey = getString(R.string.preference_service_enabled)

        updateUIControlState()

        binding.buttonGrantPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            // Save state so BootReceiver knows whether to start it or not
            prefs.edit().putBoolean(prefKey, isChecked).apply()

            val serviceIntent = Intent(this, BlankingService::class.java)
            if (isChecked) {
                if (hasOverlayPermission()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    binding.switchService.text = getString(R.string.service_enabled)
                } else {
                    // Turn switch back off, we don't have permission yet
                    binding.switchService.isChecked = false
                    prefs.edit().putBoolean(prefKey, false).apply()
                }
            } else {
                stopService(serviceIntent)
                binding.switchService.text = getString(R.string.service_disabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIControlState()
    }

    private fun updateUIControlState() {
        val prefs = getSharedPreferences("BlankerPrefs", Context.MODE_PRIVATE)
        val prefKey = getString(R.string.preference_service_enabled)
        val isEnabled = prefs.getBoolean(prefKey, false)
        
        if (hasOverlayPermission()) {
            binding.buttonGrantPermission.visibility = View.GONE
            binding.switchService.isChecked = isEnabled
            binding.switchService.text = if (isEnabled) getString(R.string.service_enabled) else getString(R.string.service_disabled)
        } else {
            binding.buttonGrantPermission.visibility = View.VISIBLE
            binding.switchService.isChecked = false
            prefs.edit().putBoolean(prefKey, false).apply() // Reset if permission was revoked
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
}
