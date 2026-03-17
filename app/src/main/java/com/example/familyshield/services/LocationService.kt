package com.example.familyshield.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.familyshield.utils.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private var userMobile: String = ""
    private var adminMobile: String = ""

    companion object {
        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1002
        private const val TAG = "LocationService"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 seconds
    }

    override fun onCreate() {
        super.onCreate()

        sessionManager = SessionManager(this)
        userMobile = sessionManager.getUserMobile() ?: ""
        adminMobile = sessionManager.getParentAdminMobile() ?: ""

        if (userMobile.isEmpty() || adminMobile.isEmpty()) {
            Log.e(TAG, "❌ User not logged in")
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sharing location with admin"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FamilyShield Location")
                .setContentText("Sharing your location")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Location permission not granted")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).setMinUpdateIntervalMillis(15000) // 15 seconds minimum
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationToFirebase(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "✅ Location updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception: ${e.message}")
            stopSelf()
        }
    }

    private fun sendLocationToFirebase(location: Location) {
        val batteryLevel = getBatteryLevel()

        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "lastLocationUpdate" to ServerValue.TIMESTAMP,
            "batteryLevel" to batteryLevel,
            "isOnline" to true
        )

        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .updateChildren(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "📍 Location sent: ${location.latitude}, ${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to send location: ${e.message}")
            }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        // Update offline status
        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("isOnline")
            .setValue(false)

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing updates: ${e.message}")
        }
    }
}