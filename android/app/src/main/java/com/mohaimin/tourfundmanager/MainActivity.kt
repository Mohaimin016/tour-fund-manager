package com.mohaimin.tourfundmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var nameInput: EditText
    private lateinit var status: TextView
    private val requestCode = 101
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad); gravity = Gravity.CENTER_VERTICAL }
        root.addView(TextView(this).apply { text = "Tour Fund Manager"; textSize = 25f; setTextColor(0xFF123C35.toInt()) })
        root.addView(TextView(this).apply { text = "Background location sharing"; textSize = 17f; setPadding(0,pad/3,0,pad) })
        root.addView(TextView(this).apply { text = "Enter your name, then start sharing. Your location will be sent to the Tour Fund Manager live map while this foreground service is running. Anyone with the app link can see shared locations."; textSize = 14f })
        nameInput = EditText(this).apply { hint = "Your name shown on map"; setSingleLine(true); setText(getPreferences(0).getString("display_name", "")) }
        root.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })
        val start = Button(this).apply { text = "Start location sharing"; setOnClickListener { val name = nameInput.text.toString().trim(); if (name.isBlank()) { nameInput.error = "Enter your name"; return@setOnClickListener }; getPreferences(0).edit().putString("display_name", name).apply(); requestLocationAndStart(name) } }
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply { text = "Stop sharing"; setOnClickListener { stopService(Intent(this@MainActivity, LocationService::class.java)); status.text = "Sharing stopped. The service will remove your last shared location." } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        status = TextView(this).apply { text = "Sharing is off until you start it."; textSize = 14f; setPadding(0,pad,0,0) }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Keep the persistent notification visible while sharing. Android battery-saving settings may affect updates. Stop sharing any time in this app or from Android's app controls."; textSize = 12f; setPadding(0,pad,0,0) })
        setContentView(root)
    }
    private fun requestLocationAndStart(name: String) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), requestCode); return }
        startSharing(name)
    }
    private fun startSharing(name: String) {
        val i = Intent(this, LocationService::class.java).putExtra("display_name", name)
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i); status.text = "Sharing requested. Check the persistent notification for service status." }
        catch (e: Exception) { status.text = "Could not start location service: " + (e.message ?: "unknown error") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startSharing(nameInput.text.toString().trim())
            else Toast.makeText(this, "Location permission is required to share your location.", Toast.LENGTH_LONG).show()
        }
    }
}
