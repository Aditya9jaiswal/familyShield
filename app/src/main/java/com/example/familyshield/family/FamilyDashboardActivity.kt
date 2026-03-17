package com.example.familyshield.family

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.familyshield.R
import com.example.familyshield.admin.MyDeviceAdminReceiver
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.services.CommandListenerService
import com.example.familyshield.services.LocationService
import com.example.familyshield.utils.DevicePolicyHelper
import com.example.familyshield.utils.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class FamilyDashboardActivity : AppCompatActivity() {

    // Views
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserMobile: TextView
    private lateinit var tvDeviceModel: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvBatteryLevel: TextView
    private lateinit var tvSimOperator: TextView
    private lateinit var tvSimCountry: TextView
    private lateinit var tvLastLocation: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvAdminName: TextView
    private lateinit var tvAdminMobile: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvResetStatus: TextView
    private lateinit var btnEnableAdmin: Button
    private lateinit var btnRefresh: Button

    // Data
    private lateinit var sessionManager: SessionManager
    private lateinit var devicePolicyHelper: DevicePolicyHelper
    private lateinit var database: DatabaseReference
    private lateinit var userRef: DatabaseReference
    private lateinit var commandListener: ValueEventListener
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Siren
    private var mediaPlayer: MediaPlayer? = null
    private var isSirenPlaying = false

    // Hidden reset
    private var tapCount = 0
    private val resetHandler = Handler(Looper.getMainLooper())

    private var userMobile: String = ""
    private var adminMobile: String = ""
    private var factoryResetEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_dashboard)

        sessionManager = SessionManager(this)
        devicePolicyHelper = DevicePolicyHelper(this)
        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get user info from session
        userMobile = sessionManager.getUserMobile() ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        adminMobile = sessionManager.getParentAdminMobile() ?: ""

        // Firebase reference
        userRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        initViews()
        setupHiddenReset() // 👈 HIDDEN RESET SETUP
        setupClickListeners()
        startServices()
        loadUserData()
        loadAdminInfo()
        listenForCommands()
        listenForSirenCommand()
        sendDeviceInfoToFirebase()
        checkPermissionsAndStartLocation()
    }

    private fun initViews() {
        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserMobile = findViewById(R.id.tvUserMobile)
        tvDeviceModel = findViewById(R.id.tvDeviceModel)
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel)
        tvSimOperator = findViewById(R.id.tvSimOperator)
        tvSimCountry = findViewById(R.id.tvSimCountry)
        tvLastLocation = findViewById(R.id.tvLastLocation)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        tvAdminName = findViewById(R.id.tvAdminName)
        tvAdminMobile = findViewById(R.id.tvAdminMobile)
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus)
        tvResetStatus = findViewById(R.id.tvResetStatus)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        btnRefresh = findViewById(R.id.btnRefresh)

        // Set basic info
        tvUserName.text = sessionManager.getUserName() ?: "User"
        tvUserMobile.text = "📱 +91 $userMobile"
        tvDeviceModel.text = Build.MODEL
        tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE}"
    }

    // ========== HIDDEN RESET FEATURE ==========
    private fun setupHiddenReset() {
        ivProfileImage.setOnClickListener {
            tapCount++

            // Reset counter after 3 seconds if no more taps
            resetHandler.removeCallbacks(resetRunnable)
            resetHandler.postDelayed(resetRunnable, 3000)

            if (tapCount == 3) {
                // 3 taps - show password dialog
                tapCount = 0
                showAdminPasswordDialog()
            }
        }
    }

    private val resetRunnable = Runnable {
        tapCount = 0
    }

    private fun showAdminPasswordDialog() {
        // ✅ FIXED: Using LayoutInflater properly
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etAdminPassword)

        AlertDialog.Builder(this)
            .setTitle("🔐 Admin Verification")
            .setMessage("Enter admin password to reset this device")
            .setView(dialogView)
            .setPositiveButton("VERIFY") { _, _ ->
                val enteredPassword = etPassword.text.toString()
                verifyAdminPassword(enteredPassword)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun verifyAdminPassword(enteredPassword: String) {
        // Get admin mobile from session
        val adminMobile = sessionManager.getParentAdminMobile() ?: return

        // Password is last 6 digits of admin mobile
        val expectedPassword = if (adminMobile.length >= 6) {
            adminMobile.takeLast(6)
        } else {
            adminMobile
        }

        if (enteredPassword == expectedPassword) {
            // Correct password - perform reset
            performAdminReset()
        } else {
            Toast.makeText(this, "❌ Incorrect password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performAdminReset() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ DEVICE RESET")
            .setMessage("This will log you out and stop all services.\n\nYou will need to login again.")
            .setPositiveButton("PROCEED") { _, _ ->
                // Stop all services
                stopServices()

                // Clear session
                sessionManager.clearSession()

                // Go to login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun stopServices() {
        try {
            // Stop CommandListenerService
            stopService(Intent(this, CommandListenerService::class.java))

            // Stop LocationService
            stopService(Intent(this, LocationService::class.java))

            // Stop siren if playing
            stopSiren()

            // Remove location updates
            fusedLocationClient.removeLocationUpdates(locationCallback)

            Log.d("RESET", "✅ All services stopped")
        } catch (e: Exception) {
            Log.e("RESET", "Error stopping services: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        btnEnableAdmin.setOnClickListener {
            requestDeviceAdmin()
        }

        btnRefresh.setOnClickListener {
            loadUserData()
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== REMOTE SIREN CONTROL ==========
    private fun listenForSirenCommand() {
        userRef.child("sirenEnabled").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sirenEnabled = snapshot.getValue(Boolean::class.java) ?: false

                if (sirenEnabled && !isSirenPlaying) {
                    startSiren()
                    Toast.makeText(this@FamilyDashboardActivity, "🔊 Siren activated by admin", Toast.LENGTH_SHORT).show()
                } else if (!sirenEnabled && isSirenPlaying) {
                    stopSiren()
                    Toast.makeText(this@FamilyDashboardActivity, "🔇 Siren stopped by admin", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startSiren() {
        try {
            mediaPlayer?.release()

            val resId = try {
                R.raw.siren_sound
            } catch (e: Exception) {
                null
            }

            if (resId != null && resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId)
            } else {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer.create(this, alarmUri)
            }

            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
            isSirenPlaying = true

            Log.d("SIREN", "🔊 Siren started")
        } catch (e: Exception) {
            Log.e("SIREN", "Error: ${e.message}")
        }
    }

    private fun stopSiren() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isSirenPlaying = false
            Log.d("SIREN", "🔇 Siren stopped")
        } catch (e: Exception) {
            Log.e("SIREN", "Error stopping: ${e.message}")
        }
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            ComponentName(this, MyDeviceAdminReceiver::class.java))
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "FamilyShield needs device admin for remote protection")
        startActivityForResult(intent, 101)
    }

    private fun startServices() {
        val commandIntent = Intent(this, CommandListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(commandIntent)
        } else {
            startService(commandIntent)
        }

        val locationIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(locationIntent)
        } else {
            startService(locationIntent)
        }
    }

    // ========== LOCATION TRACKING ==========
    private fun checkPermissionsAndStartLocation() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 200)
        } else {
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationToFirebase(location)

                    runOnUiThread {
                        tvLastLocation.text = "📍 ${location.latitude}, ${location.longitude}"
                        val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        tvLastUpdated.text = "Last update: $date"
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LOCATION", "✅ Real-time location updates started")
        } catch (e: SecurityException) {
            Log.e("LOCATION", "❌ Security exception: ${e.message}")
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

        userRef.updateChildren(locationData)
            .addOnSuccessListener {
                Log.d("LOCATION", "📍 Location sent: ${location.latitude}, ${location.longitude}")
            }
    }

    private fun sendDeviceInfoToFirebase() {
        val simInfo = getSimInfo()
        val batteryLevel = getBatteryLevel()

        val deviceInfo = mapOf(
            "simOperator" to simInfo["operator"],
            "simCountry" to simInfo["country"],
            "networkType" to simInfo["networkType"],
            "batteryLevel" to batteryLevel,
            "deviceModel" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "lastInfoUpdate" to ServerValue.TIMESTAMP
        )

        userRef.updateChildren(deviceInfo)
            .addOnSuccessListener {
                tvSimOperator.text = "SIM: ${simInfo["operator"]}"
                tvSimCountry.text = "Country: ${simInfo["country"]}"
            }
    }

    private fun getSimInfo(): Map<String, String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val info = mutableMapOf<String, String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED) {
            info["operator"] = tm.simOperatorName ?: "No SIM"
            info["country"] = tm.simCountryIso?.uppercase() ?: "Unknown"
            info["networkType"] = when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                else -> "Unknown"
            }
        } else {
            info["operator"] = "Permission denied"
            info["country"] = "Permission denied"
            info["networkType"] = "Permission denied"
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_PHONE_STATE), 102)
        }
        return info
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ========== COMMAND LISTENING ==========
    private fun listenForCommands() {
        val commandsRef = userRef.child("commands")
            .orderByChild("status")
            .equalTo("pending")

        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (commandSnapshot in snapshot.children) {
                    val commandId = commandSnapshot.key ?: continue
                    val type = commandSnapshot.child("type").getValue(String::class.java) ?: ""
                    processCommand(type, commandSnapshot.ref, commandId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        commandsRef.addValueEventListener(commandListener)
    }

    private fun processCommand(commandType: String, commandRef: DatabaseReference, commandId: String) {
        when (commandType) {
            "LOCK" -> {
                val success = devicePolicyHelper.lockDevice()
                updateCommandStatus(commandRef, commandId, success,
                    if (success) "Device locked" else "Lock failed")
            }
            "DISABLE_RESET" -> {
                factoryResetEnabled = false
                userRef.child("factoryResetEnabled").setValue(false)
                tvResetStatus.text = "Factory Reset: ❌ Disabled"
                updateCommandStatus(commandRef, commandId, true, "Factory reset disabled")
            }
            "ENABLE_RESET" -> {
                factoryResetEnabled = true
                userRef.child("factoryResetEnabled").setValue(true)
                tvResetStatus.text = "Factory Reset: ✅ Enabled"
                updateCommandStatus(commandRef, commandId, true, "Factory reset enabled")
            }
            "FACTORY_RESET" -> {
                if (factoryResetEnabled) {
                    showFactoryResetConfirmation(commandRef, commandId)
                } else {
                    showContactAdminDialog()
                    updateCommandStatus(commandRef, commandId, false, "Factory reset disabled by admin")
                }
            }
            "LOCATE" -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            sendLocationToFirebase(it)
                            updateCommandStatus(commandRef, commandId, true, "Location sent")
                        }
                    }
                } else {
                    updateCommandStatus(commandRef, commandId, false, "Location permission denied")
                }
            }
        }
    }

    private fun showFactoryResetConfirmation(commandRef: DatabaseReference, commandId: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ FACTORY RESET")
            .setMessage("Admin has requested to reset this device.\n\nThis will ERASE ALL DATA!\n\nDo you want to proceed?")
            .setPositiveButton("YES, RESET") { _, _ ->
                val success = devicePolicyHelper.factoryReset()
                updateCommandStatus(commandRef, commandId, success,
                    if (success) "Factory reset executed" else "Reset failed")
            }
            .setNegativeButton("NO") { _, _ ->
                updateCommandStatus(commandRef, commandId, false, "User cancelled")
            }
            .show()
    }

    private fun showContactAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚫 FACTORY RESET DISABLED")
            .setMessage("Factory reset is currently disabled by admin.\n\nPlease contact your admin at:\n$adminMobile")
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("CALL ADMIN") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$adminMobile"))
                startActivity(intent)
            }
            .show()
    }

    private fun updateCommandStatus(commandRef: DatabaseReference, commandId: String, success: Boolean, message: String) {
        val updates = mapOf(
            "status" to if (success) "executed" else "failed",
            "result" to message,
            "executedAt" to ServerValue.TIMESTAMP
        )
        commandRef.updateChildren(updates)
    }

    private fun loadUserData() {
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val isLocked = snapshot.child("isLocked").getValue(Boolean::class.java) ?: false
                factoryResetEnabled = snapshot.child("factoryResetEnabled").getValue(Boolean::class.java) ?: true
                val batteryLevel = snapshot.child("batteryLevel").getValue(Int::class.java) ?: 0
                val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                val lastUpdate = snapshot.child("lastLocationUpdate").getValue(Long::class.java) ?: 0
                val simOperator = snapshot.child("simOperator").getValue(String::class.java) ?: "Unknown"

                tvDeviceStatus.text = when {
                    isLocked -> "Device Status: 🔒 Locked"
                    isOnline -> "Device Status: 🟢 Active"
                    else -> "Device Status: ⚫ Offline"
                }

                tvResetStatus.text = if (factoryResetEnabled)
                    "Factory Reset: ✅ Enabled"
                else
                    "Factory Reset: ❌ Disabled (Contact Admin)"

                if (batteryLevel > 0) tvBatteryLevel.text = "$batteryLevel% 🔋"
                if (latitude != 0.0) tvLastLocation.text = "📍 $latitude, $longitude"
                if (lastUpdate > 0) {
                    val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(lastUpdate))
                    tvLastUpdated.text = "Last update: $date"
                }
                tvSimOperator.text = "SIM: $simOperator"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadAdminInfo() {
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val adminName = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                    val adminMobileNum = snapshot.child("mobile").getValue(String::class.java) ?: adminMobile

                    tvAdminName.text = adminName
                    tvAdminMobile.text = "+91 $adminMobileNum"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            102 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendDeviceInfoToFirebase()
            }
            200 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userRef.removeEventListener(commandListener)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
        resetHandler.removeCallbacks(resetRunnable)
    }
}