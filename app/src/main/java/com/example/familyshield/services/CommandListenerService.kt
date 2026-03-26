package com.example.familyshield.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.familyshield.utils.DevicePolicyHelper
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class CommandListenerService : Service() {

    private lateinit var database: DatabaseReference
    private lateinit var devicePolicyHelper: DevicePolicyHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var vibrator: Vibrator
    private var commandListener: ValueEventListener? = null
    private var factoryResetListener: ValueEventListener? = null
    private var userMobile: String = ""
    private var adminMobile: String = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null  // For siren

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

        if (!checkUserSession()) {
            stopSelf()
            return
        }

        createNotificationChannel()
        startForegroundService()
        listenForCommands()
        listenForFactoryResetStatus()
    }

    private fun initializeServices() {
        sessionManager = SessionManager(this)
        devicePolicyHelper = DevicePolicyHelper(this)
        database = FirebaseDatabase.getInstance().reference
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Acquire wake lock for critical operations
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
            Log.d(TAG, "✅ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wake lock error: ${e.message}")
        }
    }

    private fun checkUserSession(): Boolean {
        userMobile = sessionManager.getUserMobile() ?: ""
        adminMobile = sessionManager.getParentAdminMobile() ?: ""

        if (userMobile.isEmpty() || adminMobile.isEmpty()) {
            Log.e(TAG, "❌ User not logged in properly")
            return false
        }

        Log.d(TAG, "✅ Service started for user: $userMobile, admin: $adminMobile")
        return true
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
            Log.d(TAG, "✅ Foreground service started")
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

        factoryResetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = snapshot.getValue(Boolean::class.java) ?: true

                if (!enabled) {
                    // DISABLE FACTORY RESET - Use DevicePolicyHelper
                    val success = devicePolicyHelper.disableFactoryReset()
                    if (success) {
                        Log.d(TAG, "🚫 Factory reset DISABLED by admin")
                    } else {
                        Log.e(TAG, "❌ Failed to disable factory reset")
                    }
                } else {
                    // ENABLE FACTORY RESET - Use DevicePolicyHelper
                    val success = devicePolicyHelper.enableFactoryReset()
                    if (success) {
                        Log.d(TAG, "✅ Factory reset ENABLED by admin")
                    } else {
                        Log.e(TAG, "❌ Failed to enable factory reset")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Factory reset status error: ${error.message}")
            }
        }

        resetRef.addValueEventListener(factoryResetListener!!)
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
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check factory reset status: ${e.message}")
                updateCommandStatus(commandRef, commandId, false, "Error checking reset status")
            }
    }

    private fun handleDisableResetCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🚫 Executing DISABLE RESET")

        // Disable factory reset using DevicePolicyHelper
        val success = devicePolicyHelper.disableFactoryReset()

        if (success) {
            // Update Firebase only if device policy operation succeeded
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("factoryResetEnabled")
                .setValue(false)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Firebase updated: factoryResetEnabled = false")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to update Firebase: ${e.message}")
                }
        }

        val message = if (success) "🚫 Factory reset disabled" else "❌ Failed to disable factory reset"
        updateCommandStatus(commandRef, commandId, success, message)
    }

    private fun handleEnableResetCommand(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "✅ Executing ENABLE RESET")

        // Enable factory reset using DevicePolicyHelper
        val success = devicePolicyHelper.enableFactoryReset()

        if (success) {
            // Update Firebase only if device policy operation succeeded
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("factoryResetEnabled")
                .setValue(true)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Firebase updated: factoryResetEnabled = true")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to update Firebase: ${e.message}")
                }
        }

        val message = if (success) "✅ Factory reset enabled" else "❌ Failed to enable factory reset"
        updateCommandStatus(commandRef, commandId, success, message)
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

    // ========== VIBRATION METHODS ==========
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

    // ========== SIREN METHODS ==========
    private fun playSiren() {
        try {
            // Stop any existing siren
            stopSiren()

            // Try to use alarm sound first
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@CommandListenerService, alarmUri)
                setLooping(true)
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }

            Log.d(TAG, "🔊 Siren playing")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Siren error: ${e.message}")

            // Fallback: Use notification sound
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@CommandListenerService, notificationUri)
                    setLooping(true)
                    prepare()
                    start()
                }
                Log.d(TAG, "🔊 Fallback siren playing")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Fallback siren also failed: ${e2.message}")
            }
        }
    }

    private fun stopSiren() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "🔇 Siren stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping siren: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "✅ Service onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroyed - will restart")

        // Clean up Firebase listeners
        commandListener?.let {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("commands")
                .removeEventListener(it)
        }

        factoryResetListener?.let {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("factoryResetEnabled")
                .removeEventListener(it)
        }

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "✅ Wake lock released")
            }
        }

        // Stop siren if playing
        stopSiren()

        // Restart service to maintain persistence
        val restartIntent = Intent(this, CommandListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}