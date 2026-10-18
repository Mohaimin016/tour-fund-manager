package com.mohaimin.tourfundmanager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

class LocationService : Service(), LocationListener {
    companion object {
        private const val TAG = "TourFundLocation"
        private const val CHANNEL_ID = "tour_fund_location"
        private const val NOTIFICATION_ID = 5137
        private const val SUPABASE_URL = "https://ixksktyqzjwudzaciwcr.supabase.co"
        private const val SUPABASE_KEY = "sb_publishable_JntqprSibYEaJIRWNjVrEw_5EwC7qvC"
    }
    private lateinit var locationManager: LocationManager
    private val io = Executors.newSingleThreadExecutor()
    private var visitorId = ""
    private var displayName = "Team member"
    private var started = false

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        visitorId = getPreferences().getString("visitor_id", null) ?: UUID.randomUUID().toString().also { getPreferences().edit().putString("visitor_id", it).apply() }
        createChannel()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        displayName = intent?.getStringExtra("display_name")?.trim()?.take(60)?.ifBlank { null } ?: getPreferences().getString("display_name", "Team member") ?: "Team member"
        getPreferences().edit().putString("display_name", displayName).apply()
        startForeground(NOTIFICATION_ID, notification("Sharing location for " + displayName))
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, this, Looper.getMainLooper())
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15000L, 20f, this, Looper.getMainLooper())
            started = true
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        } catch (e: SecurityException) { Log.e(TAG, "Location permission unavailable", e); stopSelf() }
        return START_STICKY
    }
    override fun onLocationChanged(location: Location) {
        val payload = JSONObject().apply {
            put("user_id", visitorId); put("display_name", displayName); put("latitude", location.latitude); put("longitude", location.longitude)
            put("accuracy_m", location.accuracy.toDouble()); put("sharing_enabled", true); put("updated_at", java.time.Instant.now().toString())
        }
        io.execute { try { request("POST", "/rest/v1/tour_fund_locations?on_conflict=user_id", payload.toString(), "resolution=merge-duplicates,return=minimal") } catch (e: Exception) { Log.e(TAG, "Location sync failed", e) } }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification("Location sharing is active · updated just now"))
    }
    override fun onProviderDisabled(provider: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Turn on location services to continue sharing")) }
    override fun onProviderEnabled(provider: String) { }
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        if (started && visitorId.isNotBlank()) io.execute { try { request("DELETE", "/rest/v1/tour_fund_locations?user_id=eq." + enc(visitorId), null, "return=minimal") } catch (e: Exception) { Log.e(TAG, "Could not remove last location", e) } }
        io.shutdown()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun getPreferences() = getSharedPreferences("tour_fund_location", Context.MODE_PRIVATE)
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Live location sharing", NotificationManager.IMPORTANCE_LOW).apply { description = "Visible while Tour Fund Manager shares your location" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Tour Fund Manager") .setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build()
    }
    private fun request(method: String, path: String, body: String?, prefer: String) {
        val conn = (URL(SUPABASE_URL + path).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method; conn.connectTimeout = 15000; conn.readTimeout = 15000
            conn.setRequestProperty("apikey", SUPABASE_KEY); conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY); conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("Prefer", prefer)
            if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
            val code = conn.responseCode
            if (code !in 200..299) { val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""; Log.e(TAG, "Supabase HTTP " + code + ": " + err) }
        } finally { conn.disconnect() }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
