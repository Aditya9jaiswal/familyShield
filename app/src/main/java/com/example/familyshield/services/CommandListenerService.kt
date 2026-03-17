package com.example.familyshield.services

import android.app.admin.DevicePolicyManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.familyshield.R
import com.example.familyshield.admin.MyDeviceAdminReceiver
import com.example.familyshield.utils.DevicePolicyHelper
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.*

class CommandListenerService : Service() {

    private lateinit var database: DatabaseReference
    private lateinit var devicePolicyHelper: DevicePolicyHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var powerManager: PowerManager
    private lateinit var vibrator: Vibrator
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var commandListener: ValueEventListener? = null
    private var userMobile: String = ""
    private var adminMobile: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "command_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "CommandListener"
        private const val WAKE_LOCK_TAG = "FamilyShield:CommandLock"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Service onCreate called")

        initializeServices()
        checkUserSession()
        createNotificationChannel()
        startForegroundService()
        listenForCommands()
        listenForFactoryResetStatus()
    }

    private fun initializeServices() {
        sessionManager = SessionManager(this)
        devicePolicyHelper = DevicePolicyHelper(this)
        database = FirebaseDatabase.getInstance().reference
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Acquire wake lock
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L)
            }
            Log.d(TAG, "✅ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wake lock error: ${e.message}")
        }
    }

    private fun checkUserSession() {
        userMobile = sessionManager.getUserMobile() ?: ""
        adminMobile = sessionManager.getParentAdminMobile() ?: ""

        if (userMobile.isEmpty() || adminMobile.isEmpty()) {
            Log.e(TAG, "❌ User not logged in properly")
            stopSelf()
            return
        }

        Log.d(TAG, "✅ Service started for user: $userMobile, admin: $adminMobile")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "FamilyShield Protection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Remote protection service running in background"
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                Log.d(TAG, "✅ Notification channel created")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Channel creation error: ${e.message}")
            }
        }
    }

    private fun startForegroundService() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ FamilyShield Active")
                .setContentText("Protection service is running")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ Foreground service started with notification")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Foreground start error: ${e.message}")
        }
    }

    // ========== LISTEN FOR FACTORY RESET STATUS ==========
    private fun listenForFactoryResetStatus() {
        val resetRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("factoryResetEnabled")

        resetRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = snapshot.getValue(Boolean::class.java) ?: true

                if (!enabled) {
                    // ✅ DISABLE FACTORY RESET - HIDE OPTION COMPLETELY
                    disableFactoryResetCompletely()
                    Log.d(TAG, "🚫 Factory reset DISABLED by admin - option hidden")
                } else {
                    // ✅ ENABLE FACTORY RESET - SHOW OPTION
                    enableFactoryResetCompletely()
                    Log.d(TAG, "✅ Factory reset ENABLED by admin - option visible")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Factory reset status error: ${error.message}")
            }
        })
    }

    // ========== COMPLETE FACTORY RESET CONTROL ==========
    private fun disableFactoryResetCompletely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Method 1: User Restriction - Settings se factory reset option hide karo
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                Log.d(TAG, "✅ Factory reset option hidden from Settings")

                // Method 2: Block remote reset (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val policy = android.app.admin.FactoryResetProtectionPolicy.Builder()
                            .setFactoryResetProtectionEnabled(false)
                            .build()
                        dpm.setFactoryResetProtectionPolicy(adminComponent, policy)
                        Log.d(TAG, "✅ Remote reset blocked")
                    } catch (e: Exception) {
                        Log.w(TAG, "FRP not supported")
                    }
                }

                // Method 3: Block Google FRP (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val restrictions = android.os.Bundle()
                        restrictions.putBoolean("disable_factory_reset_protection_admin", true)
                        dpm.setApplicationRestrictions(
                            adminComponent,
                            "com.google.android.gms",
                            restrictions
                        )
                        sendBroadcast(Intent("com.google.android.gms.auth.FRP_CONFIG_CHANGED"))
                        Log.d(TAG, "✅ Google FRP blocked")
                    } catch (e: Exception) {
                        Log.w(TAG, "Google FRP not supported")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Disable error: ${e.message}")
        }
    }

    private fun enableFactoryResetCompletely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Method 1: Remove User Restriction - Settings mein factory reset option dikhao
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                Log.d(TAG, "✅ Factory reset option visible in Settings")

                // Method 2: Enable remote reset (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val policy = android.app.admin.FactoryResetProtectionPolicy.Builder()
                            .setFactoryResetProtectionEnabled(true)
                            .build()
                        dpm.setFactoryResetProtectionPolicy(adminComponent, policy)
                        Log.d(TAG, "✅ Remote reset enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "FRP not supported")
                    }
                }

                // Method 3: Enable Google FRP (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val restrictions = android.os.Bundle()
                        restrictions.putBoolean("disable_factory_reset_protection_admin", false)
                        dpm.setApplicationRestrictions(
                            adminComponent,
                            "com.google.android.gms",
                            restrictions
                        )
                        sendBroadcast(Intent("com.google.android.gms.auth.FRP_CONFIG_CHANGED"))
                        Log.d(TAG, "✅ Google FRP enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "Google FRP not supported")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Enable error: ${e.message}")
        }
    }

    // ========== MAIN COMMAND LISTENER ==========
    private fun listenForCommands() {
        Log.d(TAG, "📡 Listening for commands - User: $userMobile")

        val commandsRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .orderByChild("status")
            .equalTo("pending")

        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                for (commandSnapshot in snapshot.children) {
                    val commandId = commandSnapshot.key ?: continue
                    val type = commandSnapshot.child("type").getValue(String::class.java) ?: ""
                    Log.d(TAG, "📩 Command received: $type (ID: $commandId)")

                    when (type) {
                        "LOCK" -> handleLockCommand(commandSnapshot.ref, commandId)
                        "FACTORY_RESET" -> handleFactoryResetCommand(commandSnapshot.ref, commandId)
                        "DISABLE_RESET" -> handleDisableResetCommand(commandSnapshot.ref, commandId)
                        "ENABLE_RESET" -> handleEnableResetCommand(commandSnapshot.ref, commandId)
                        "LOCATE" -> handleLocateCommand(commandSnapshot.ref, commandId)
                        "SIREN_ON" -> handleSirenOnCommand(commandSnapshot.ref, commandId)
                        "SIREN_OFF" -> handleSirenOffCommand(commandSnapshot.ref, commandId)
                        else -> handleUnknownCommand(commandSnapshot.ref, commandId, type)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Database error: ${error.message}")
            }
        }

        commandsRef.addValueEventListener(commandListener!!)
    }

    // ========== COMMAND HANDLERS ==========
    private fun handleLockCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🔒 Executing LOCK command")
        vibrate(500)
        val success = devicePolicyHelper.lockDevice()
        val message = if (success) "✅ Device locked" else "❌ Lock failed"
        updateCommandStatus(commandRef, commandId, success, message)
    }

    private fun handleFactoryResetCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "⚠️ Executing FACTORY RESET")
        vibratePattern()

        // Check if reset is currently enabled
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("factoryResetEnabled")
            .get()
            .addOnSuccessListener { snapshot ->
                val enabled = snapshot.getValue(Boolean::class.java) ?: true

                if (enabled) {
                    val success = devicePolicyHelper.factoryReset()
                    val message = if (success) "⚠️ Factory reset executed" else "❌ Reset failed"
                    updateCommandStatus(commandRef, commandId, success, message)
                } else {
                    Log.w(TAG, "🚫 Factory reset command ignored - disabled by admin")
                    updateCommandStatus(commandRef, commandId, false, "Factory reset disabled by admin")
                }
            }
    }

    private fun handleDisableResetCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🚫 Executing DISABLE RESET")

        // Disable factory reset completely
        disableFactoryResetCompletely()

        // Update Firebase
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("factoryResetEnabled")
            .setValue(false)

        updateCommandStatus(commandRef, commandId, true, "🚫 Factory reset disabled")
    }

    private fun handleEnableResetCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "✅ Executing ENABLE RESET")

        // Enable factory reset completely
        enableFactoryResetCompletely()

        // Update Firebase
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("factoryResetEnabled")
            .setValue(true)

        updateCommandStatus(commandRef, commandId, true, "✅ Factory reset enabled")
    }

    private fun handleLocateCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "📍 Executing LOCATE")
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        vibrate(200)
        updateCommandStatus(commandRef, commandId, true, "📍 Location request sent")
    }

    private fun handleSirenOnCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🔊 SIREN ON")
        playSiren()
        vibratePattern()
        updateCommandStatus(commandRef, commandId, true, "🔊 Siren activated")
    }

    private fun handleSirenOffCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🔇 SIREN OFF")
        stopSiren()
        vibrator.cancel()
        updateCommandStatus(commandRef, commandId, true, "🔇 Siren deactivated")
    }

    private fun handleUnknownCommand(commandRef: DatabaseReference, commandId: String, type: String) {
        Log.e(TAG, "❓ Unknown command: $type")
        updateCommandStatus(commandRef, commandId, false, "Unknown command: $type")
    }

    private fun updateCommandStatus(commandRef: DatabaseReference, commandId: String, success: Boolean, message: String) {
        val updates = mapOf(
            "status" to if (success) "executed" else "failed",
            "result" to message,
            "executedAt" to ServerValue.TIMESTAMP
        )
        commandRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Command $commandId status updated: $message")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update status: ${e.message}")
            }
    }

    private fun vibrate(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Vibrate error: ${e.message}")
        }
    }

    private fun vibratePattern() {
        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Pattern error: ${e.message}")
        }
    }

    private fun playSiren() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, notification)
            ringtone.play()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Siren error: ${e.message}")
        }
    }

    private fun stopSiren() {
        // Implement if needed
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "✅ Service onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroyed - will restart")

        // Clean up
        commandListener?.let {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("commands")
                .removeEventListener(it)
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        // Restart
        val restartIntent = Intent(this, CommandListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}