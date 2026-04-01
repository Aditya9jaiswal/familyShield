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
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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

    // Lock Overlay (for lost message only)
    private var lockOverlay: FrameLayout? = null
    private var isLockOverlayVisible = false
    private var isFullBlankLocked = false
    private var isLostMessageLocked = false
    private var isProcessingLock = false
    private var lockStatusListener: ValueEventListener? = null
    private var broadcastReceiver: android.content.BroadcastReceiver? = null

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

        userMobile = sessionManager.getUserMobile() ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        adminMobile = sessionManager.getParentAdminMobile() ?: ""

        userRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        initViews()
        setupHiddenReset()
        setupClickListeners()
        setupBroadcastReceiver()
        startServices()
        loadUserData()
        loadAdminInfo()
        listenForCommands()
        listenForSirenCommand()
        listenForLockStatus()
        sendDeviceInfoToFirebase()
        checkPermissionsAndStartLocation()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

        tvUserName.text = sessionManager.getUserName() ?: "User"
        tvUserMobile.text = "📱 +91 $userMobile"
        tvDeviceModel.text = Build.MODEL
        tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE}"
    }

    // ========== BROADCAST RECEIVER ==========
    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "SHOW_FULL_BLANK_LOCK" -> {
                        if (!isFullBlankLocked && !isProcessingLock) {
                            showFullBlankLock()
                        }
                    }
                    "SHOW_LOST_MESSAGE_LOCK" -> {
                        if (!isLostMessageLocked && !isProcessingLock) {
                            showLostMessageLock()
                        }
                    }
                    "REMOVE_LOCK_OVERLAY" -> removeLockOverlay()
                    "SHOW_CONTACT_ADMIN_DIALOG" -> showContactAdminDialog()
                    "DEVICE_LOCKED" -> Toast.makeText(this@FamilyDashboardActivity, "🔒 Device locked by admin", Toast.LENGTH_SHORT).show()
                }
            }
        }
        registerReceiver(broadcastReceiver, android.content.IntentFilter().apply {
            addAction("SHOW_FULL_BLANK_LOCK")
            addAction("SHOW_LOST_MESSAGE_LOCK")
            addAction("REMOVE_LOCK_OVERLAY")
            addAction("SHOW_CONTACT_ADMIN_DIALOG")
            addAction("DEVICE_LOCKED")
        })
    }

    // ========== LOCK STATUS LISTENER ==========
    private fun listenForLockStatus() {
        lockStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isProcessingLock) return

                val newFullBlank = snapshot.child("isFullBlankLocked").getValue(Boolean::class.java) ?: false
                val newLostMessage = snapshot.child("isLostMessageLocked").getValue(Boolean::class.java) ?: false

                Log.d("LOCK", "Status - FullBlank: $newFullBlank, LostMessage: $newLostMessage")

                if (newFullBlank && !isFullBlankLocked) {
                    isProcessingLock = true
                    isFullBlankLocked = true
                    isLostMessageLocked = false
                    showFullBlankLock()
                    isProcessingLock = false
                }
                else if (newLostMessage && !isLostMessageLocked && !isFullBlankLocked) {
                    isProcessingLock = true
                    isLostMessageLocked = true
                    showLostMessageLock()
                    isProcessingLock = false
                }
                else if (!newFullBlank && !newLostMessage && (isFullBlankLocked || isLostMessageLocked)) {
                    isProcessingLock = true
                    isFullBlankLocked = false
                    isLostMessageLocked = false
                    removeLockOverlay()
                    isProcessingLock = false
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(lockStatusListener!!)
    }

    // ========== ✅ FULL BLANK LOCK (No Black Screen - Only Lock Task Mode) ==========
    private fun showFullBlankLock() {
        Log.d("LOCK", "🔒 Showing FULL BLANK LOCK - Lock Task Mode Only")

        // Lock device
        devicePolicyHelper.lockDevice()

        // ✅ START LOCK TASK MODE (Kiosk Mode)
        startLockTaskMode()

        // No black overlay - just lock task mode

        Toast.makeText(this, "🔒 Device locked by admin", Toast.LENGTH_LONG).show()
    }

    // ========== ✅ LOST MESSAGE LOCK (Message + Lock Task Mode) ==========
    private fun showLostMessageLock() {
        Log.d("LOCK", "📱 Showing LOST MESSAGE LOCK")

        // Lock device
        devicePolicyHelper.lockDevice()

        // Create message overlay
        createLostMessageOverlay()

        // ✅ START LOCK TASK MODE
        startLockTaskMode()

        Toast.makeText(this, "📱 Device reported lost. Contact admin.", Toast.LENGTH_LONG).show()
    }

    // ========== ✅ START LOCK TASK MODE ==========
    private fun startLockTaskMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startLockTask()
                Log.d("LOCK_TASK", "✅ Lock Task Mode Started")
            }
        } catch (e: Exception) {
            Log.e("LOCK_TASK", "Error starting: ${e.message}")
        }
    }

    // ========== ✅ STOP LOCK TASK MODE ==========
    private fun stopLockTaskMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopLockTask()
                Log.d("LOCK_TASK", "🔓 Lock Task Mode Stopped")
            }
        } catch (e: Exception) {
            Log.e("LOCK_TASK", "Error stopping: ${e.message}")
        }
    }

    private fun createLostMessageOverlay() {
        removeLockOverlay()

        val lostView = LayoutInflater.from(this).inflate(R.layout.activity_lost_phone_lock, null)

        val tvLostTitle = lostView.findViewById<TextView>(R.id.tvLostTitle)
        val tvLostMessage = lostView.findViewById<TextView>(R.id.tvLostMessage)
        val tvAdminInfo = lostView.findViewById<TextView>(R.id.tvAdminInfo)
        val btnCallAdmin = lostView.findViewById<Button>(R.id.btnCallAdmin)
        val btnOk = lostView.findViewById<Button>(R.id.btnOk)

        tvLostTitle?.text = "📱 DEVICE REPORTED LOST"
        tvLostMessage?.text = "This device has been reported as lost by your admin.\n\nPlease contact your admin immediately to unlock this device."
        tvAdminInfo?.text = "Admin: $adminMobile"

        btnCallAdmin?.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$adminMobile"))
            startActivity(intent)
        }

        btnOk?.setOnClickListener {
            Toast.makeText(this, "Contact admin to unlock device", Toast.LENGTH_SHORT).show()
        }

        val rootLayout = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        lockOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(lostView)
        }

        rootLayout.addView(lockOverlay)
        isLockOverlayVisible = true

        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    // ========== ✅ REMOVE LOCK OVERLAY (WITH STOP LOCK TASK) ==========
    private fun removeLockOverlay() {
        try {
            lockOverlay?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                it.removeAllViews()
            }
            lockOverlay = null
            isLockOverlayVisible = false

            // Re-enable touch
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

            // ✅ STOP LOCK TASK MODE
            stopLockTaskMode()

            Log.d("LOCK", "🔓 Lock removed, Lock Task stopped")
        } catch (e: Exception) {
            Log.e("LOCK", "Error removing overlay: ${e.message}")
        }
    }

    // ========== ✅ ON RESUME - Maintain Lock Task Mode ==========
    override fun onResume() {
        super.onResume()
        // If device is locked, ensure lock task mode is active
        if (isFullBlankLocked || isLostMessageLocked) {
            startLockTaskMode()
            Log.d("LOCK_TASK", "✅ Lock Task resumed")
        }
    }

    override fun onBackPressed() {
        if (isFullBlankLocked || isLostMessageLocked) {
            Toast.makeText(this, "🔒 Device locked. Contact admin.", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isFullBlankLocked || isLostMessageLocked) {
            Handler(Looper.getMainLooper()).postDelayed({ bringAppToForeground() }, 300)
        }
    }

    private fun bringAppToForeground() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("LOCK", "Error: ${e.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if ((isFullBlankLocked || isLostMessageLocked) && !hasFocus) {
            bringAppToForeground()
        }
    }

    // ========== HIDDEN RESET ==========
    private fun setupHiddenReset() {
        ivProfileImage.setOnClickListener {
            tapCount++
            resetHandler.removeCallbacks(resetRunnable)
            resetHandler.postDelayed(resetRunnable, 3000)
            if (tapCount == 3) {
                tapCount = 0
                showAdminPasswordDialog()
            }
        }
    }

    private val resetRunnable = Runnable { tapCount = 0 }

    private fun showAdminPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etAdminPassword)

        AlertDialog.Builder(this)
            .setTitle("🔐 Admin Verification")
            .setMessage("Enter admin password to reset this device")
            .setView(dialogView)
            .setPositiveButton("VERIFY") { _, _ ->
                verifyAdminPassword(etPassword.text.toString())
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun verifyAdminPassword(enteredPassword: String) {
        val expectedPassword = if (adminMobile.length >= 6) adminMobile.takeLast(6) else adminMobile
        if (enteredPassword == expectedPassword) {
            performAdminReset()
        } else {
            Toast.makeText(this, "❌ Incorrect password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performAdminReset() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ DEVICE RESET")
            .setMessage("This will log you out and stop all services.")
            .setPositiveButton("PROCEED") { _, _ ->
                stopServices()
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun stopServices() {
        try {
            stopService(Intent(this, CommandListenerService::class.java))
            stopService(Intent(this, LocationService::class.java))
            stopSiren()
            fusedLocationClient.removeLocationUpdates(locationCallback)
            removeLockOverlay()
            Log.d("RESET", "✅ All services stopped")
        } catch (e: Exception) {
            Log.e("RESET", "Error: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        btnEnableAdmin.setOnClickListener { requestDeviceAdmin() }
        btnRefresh.setOnClickListener {
            loadUserData()
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
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
        val locationIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(commandIntent)
            startForegroundService(locationIntent)
        } else {
            startService(commandIntent)
            startService(locationIntent)
        }
    }

    // ========== SIREN ==========
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
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer.create(this, alarmUri).apply {
                isLooping = true
                start()
            }
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
            Log.e("SIREN", "Error: ${e.message}")
        }
    }

    // ========== LOCATION ==========
    private fun checkPermissionsAndStartLocation() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationToFirebase(location)
                    runOnUiThread {
                        tvLastLocation.text = "📍 ${location.latitude}, ${location.longitude}"
                        val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        tvLastUpdated.text = "Last update: $date"
                    }
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun sendLocationToFirebase(location: Location) {
        userRef.updateChildren(mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "lastLocationUpdate" to ServerValue.TIMESTAMP,
            "batteryLevel" to getBatteryLevel(),
            "isOnline" to true
        ))
    }

    private fun sendDeviceInfoToFirebase() {
        val simInfo = getSimInfo()
        userRef.updateChildren(mapOf(
            "simOperator" to simInfo["operator"],
            "simCountry" to simInfo["country"],
            "networkType" to simInfo["networkType"],
            "batteryLevel" to getBatteryLevel(),
            "deviceModel" to Build.MODEL,
            "androidVersion" to Build.VERSION.RELEASE,
            "lastInfoUpdate" to ServerValue.TIMESTAMP
        ))
        tvSimOperator.text = "SIM: ${simInfo["operator"]}"
        tvSimCountry.text = "Country: ${simInfo["country"]}"
    }

    private fun getSimInfo(): Map<String, String> {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val info = mutableMapOf("operator" to "Unknown", "country" to "Unknown", "networkType" to "Unknown")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            info["operator"] = tm.simOperatorName ?: "No SIM"
            info["country"] = tm.simCountryIso?.uppercase() ?: "Unknown"
            info["networkType"] = when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                else -> "Unknown"
            }
        }
        return info
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ========== COMMANDS ==========
    private fun listenForCommands() {
        val commandsRef = userRef.child("commands").orderByChild("status").equalTo("pending")
        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (commandSnapshot in snapshot.children) {
                    val commandId = commandSnapshot.key ?: continue
                    val action = commandSnapshot.child("action").getValue(String::class.java)
                        ?: commandSnapshot.child("type").getValue(String::class.java) ?: ""
                    processCommand(action, commandSnapshot.ref, commandId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        commandsRef.addValueEventListener(commandListener)
    }

    private fun processCommand(commandType: String, commandRef: DatabaseReference, commandId: String) {
        when (commandType) {
            "lock", "LOCK" -> {
                val success = devicePolicyHelper.lockDevice()
                updateCommandStatus(commandRef, commandId, success, if (success) "Device locked" else "Lock failed")
            }
            "disable_reset", "DISABLE_RESET" -> {
                factoryResetEnabled = false
                userRef.child("factoryResetEnabled").setValue(false)
                tvResetStatus.text = "Factory Reset: ❌ Disabled"
                updateCommandStatus(commandRef, commandId, true, "Factory reset disabled")
            }
            "enable_reset", "ENABLE_RESET" -> {
                factoryResetEnabled = true
                userRef.child("factoryResetEnabled").setValue(true)
                tvResetStatus.text = "Factory Reset: ✅ Enabled"
                updateCommandStatus(commandRef, commandId, true, "Factory reset enabled")
            }
            "wipe", "FACTORY_RESET" -> {
                if (factoryResetEnabled) {
                    showFactoryResetConfirmation(commandRef, commandId)
                } else {
                    showContactAdminDialog()
                    updateCommandStatus(commandRef, commandId, false, "Factory reset disabled by admin")
                }
            }
            "locate", "LOCATE" -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
                updateCommandStatus(commandRef, commandId, success, if (success) "Factory reset executed" else "Reset failed")
            }
            .setNegativeButton("NO") { _, _ ->
                updateCommandStatus(commandRef, commandId, false, "User cancelled")
            }
            .show()
    }

    private fun showContactAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚫 FACTORY RESET DISABLED")
            .setMessage("Factory reset is disabled by admin.\n\nContact: $adminMobile")
            .setPositiveButton("OK", null)
            .setNeutralButton("CALL ADMIN") { _, _ ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$adminMobile")))
            }
            .show()
    }

    private fun updateCommandStatus(commandRef: DatabaseReference, commandId: String, success: Boolean, message: String) {
        commandRef.updateChildren(mapOf(
            "status" to if (success) "executed" else "failed",
            "result" to message,
            "executedAt" to ServerValue.TIMESTAMP
        ))
    }

    private fun loadUserData() {
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                factoryResetEnabled = snapshot.child("factoryResetEnabled").getValue(Boolean::class.java) ?: true
                tvDeviceStatus.text = when {
                    snapshot.child("isLocked").getValue(Boolean::class.java) == true -> "Device Status: 🔒 Locked"
                    snapshot.child("isOnline").getValue(Boolean::class.java) == true -> "Device Status: 🟢 Active"
                    else -> "Device Status: ⚫ Offline"
                }
                tvResetStatus.text = if (factoryResetEnabled) "Factory Reset: ✅ Enabled" else "Factory Reset: ❌ Disabled"
                tvBatteryLevel.text = "${snapshot.child("batteryLevel").getValue(Int::class.java) ?: 0}% 🔋"
                val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                if (lat != 0.0) tvLastLocation.text = "📍 $lat, $lng"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadAdminInfo() {
        database.child("familyshield").child("admins").child(adminMobile)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tvAdminName.text = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                    tvAdminMobile.text = "+91 ${snapshot.child("mobile").getValue(String::class.java) ?: adminMobile}"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            userRef.removeEventListener(commandListener)
            lockStatusListener?.let { userRef.removeEventListener(it) }
            broadcastReceiver?.let { unregisterReceiver(it) }
            fusedLocationClient.removeLocationUpdates(locationCallback)
            mediaPlayer?.release()
            resetHandler.removeCallbacks(resetRunnable)
            stopLockTaskMode()
            removeLockOverlay()
        } catch (e: Exception) {
            Log.e("DESTROY", "Error: ${e.message}")
        }

        Log.d("DESTROY", "✅ Activity destroyed")
    }
}