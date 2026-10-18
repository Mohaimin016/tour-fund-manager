package com.mohaimin.tourfundmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var nameInput: EditText
    private lateinit var status: TextView
    private lateinit var keepScreenSwitch: Switch
    private val requestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val prefs = getPreferences(0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(TextView(this).apply {
            text = "Tour Fund Manager"
            textSize = 25f
            setTextColor(0xFF123C35.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Live location sharing"
            textSize = 17f
            setPadding(0, pad / 3, 0, pad)
        })
        root.addView(TextView(this).apply {
            text = "Start sharing to send your location to the tour map. You can stop sharing at any time."
            textSize = 14f
        })
        nameInput = EditText(this).apply {
            hint = "Your name shown on map"
            setSingleLine(true)
            setText(prefs.getString("display_name", ""))
        }
        root.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })

        keepScreenSwitch = Switch(this).apply {
            text = "Keep screen on in this app"
            isChecked = prefs.getBoolean("keep_screen_on", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("keep_screen_on", checked).apply()
                applyKeepScreenSetting(checked)
            }
        }
        root.addView(keepScreenSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = pad / 2
            bottomMargin = pad / 2
        })
        root.addView(TextView(this).apply {
            text = "Note: this keeps the screen awake only while this app is open. Location sharing can continue with the screen off, subject to Android permissions and battery settings."
            textSize = 12f
        })

        root.addView(Button(this).apply {
            text = "Start location sharing"
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = "Enter your name"
                    return@setOnClickListener
                }
                prefs.edit().putString("display_name", name).apply()
                requestLocationAndStart(name)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })

        root.addView(Button(this).apply {
            text = "Stop sharing"
            setOnClickListener {
                stopService(Intent(this@MainActivity, LocationService::class.java))
                status.text = "Sharing stopped. The service will remove your last shared location."
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(this).apply {
            text = "Sharing is off until you start it."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Keep the persistent notification visible while sharing. Android battery-saving settings may affect updates."
            textSize = 12f
            setPadding(0, pad, 0, 0)
        })
        setContentView(root)
        applyKeepScreenSetting(keepScreenSwitch.isChecked)
    }

    private fun applyKeepScreenSetting(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestLocationAndStart(name: String) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), requestCode)
            return
        }
        startSharing(name)
    }

    private fun startSharing(name: String) {
        val i = Intent(this, LocationService::class.java).putExtra("display_name", name)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            status.text = "Sharing requested. Check the persistent notification for service status."
        } catch (e: Exception) {
            status.text = "Could not start location service: " + (e.message ?: "unknown error")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startSharing(nameInput.text.toString().trim())
            else Toast.makeText(this, "Location permission is required to share your location.", Toast.LENGTH_LONG).show()
        }
    }
}
