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
import com.google.firebase.database.*

class CommandListenerService : Service() {

    private lateinit var database: DatabaseReference
    private lateinit var devicePolicyHelper: DevicePolicyHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var vibrator: Vibrator
    private var commandsListener: ValueEventListener? = null
    private var userMobile: String = ""
    private var adminMobile: String = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null

    // Track executing commands to prevent duplicates
    private val executingCommands = mutableSetOf<String>()

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
    }

    private fun initializeServices() {
        sessionManager = SessionManager(this)
        devicePolicyHelper = DevicePolicyHelper(this)
        database = FirebaseDatabase.getInstance().reference
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
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
                    description = "Remote protection service running"
                    setSound(null, null)
                    enableVibration(false)
                }
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
                Log.d(TAG, "✅ Notification channel created")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Channel error: ${e.message}")
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
                .build()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Foreground error: ${e.message}")
        }
    }

    // ========== MAIN COMMAND LISTENER ==========
    private fun listenForCommands() {
        val commandsRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .orderByChild("status")
            .equalTo("pending")

        commandsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                for (commandSnapshot in snapshot.children) {
                    val commandId = commandSnapshot.key ?: continue

                    // Skip if already processing this command
                    if (executingCommands.contains(commandId)) {
                        Log.d(TAG, "⚠️ Command $commandId already processing, skipping")
                        continue
                    }

                    // ✅ Read from "action" field (NOT type)
                    val action = commandSnapshot.child("action").getValue(String::class.java)
                        ?: commandSnapshot.child("type").getValue(String::class.java) // Fallback for old commands
                        ?: continue

                    Log.d(TAG, "📩 Command received: $action (ID: $commandId)")

                    // Mark as processing
                    executingCommands.add(commandId)

                    // Execute command
                    executeCommand(action, commandSnapshot.ref, commandId)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Database error: ${error.message}")
            }
        }
        commandsRef.addValueEventListener(commandsListener!!)
    }

    // ========== COMMAND EXECUTION ==========
    private fun executeCommand(action: String, commandRef: DatabaseReference, commandId: String) {
        try {
            when (action) {
                "lock" -> handleLock(commandRef, commandId)
                "wipe" -> handleWipe(commandRef, commandId)
                "disable_reset" -> handleDisableReset(commandRef, commandId)
                "enable_reset" -> handleEnableReset(commandRef, commandId)
                "locate" -> handleLocate(commandRef, commandId)
                "siren_on" -> handleSiren(true, commandRef, commandId)
                "siren_off" -> handleSiren(false, commandRef, commandId)
                "full_blank_lock" -> handleFullBlankLock(commandRef, commandId)
                "lost_message_lock" -> handleLostMessageLock(commandRef, commandId)
                "unlock_app" -> handleUnlockApp(commandRef, commandId)
                else -> handleUnknown(commandRef, commandId, action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing command $action: ${e.message}")
            updateCommandStatus(commandRef, commandId, false, "Error: ${e.message}")
        } finally {
            // Remove from executing set after completion
            executingCommands.remove(commandId)
        }
    }

    // ========== COMMAND HANDLERS ==========

    private fun handleLock(commandRef: DatabaseReference, commandId: String) {
        vibrate(500)
        val success = devicePolicyHelper.lockDevice()
        if (success) {
            sendBroadcast(Intent("DEVICE_LOCKED"))
            updateCommandStatus(commandRef, commandId, true, "Device locked")
        } else {
            updateCommandStatus(commandRef, commandId, false, "Lock failed")
        }
    }

    private fun handleWipe(commandRef: DatabaseReference, commandId: String) {
        vibratePattern()
        // Check if factory reset is enabled
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
                    updateCommandStatus(commandRef, commandId, success,
                        if (success) "Factory reset executed" else "Reset failed")
                } else {
                    updateCommandStatus(commandRef, commandId, false, "Factory reset disabled by admin")
                    sendBroadcast(Intent("SHOW_CONTACT_ADMIN_DIALOG"))
                }
            }
            .addOnFailureListener { e ->
                updateCommandStatus(commandRef, commandId, false, "Error checking reset status: ${e.message}")
            }
    }

    private fun handleDisableReset(commandRef: DatabaseReference, commandId: String) {
        val success = devicePolicyHelper.disableFactoryReset()
        if (success) {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("factoryResetEnabled")
                .setValue(false)
        }
        updateCommandStatus(commandRef, commandId, success,
            if (success) "Factory reset disabled" else "Failed to disable")
    }

    private fun handleEnableReset(commandRef: DatabaseReference, commandId: String) {
        val success = devicePolicyHelper.enableFactoryReset()
        if (success) {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("factoryResetEnabled")
                .setValue(true)
        }
        updateCommandStatus(commandRef, commandId, success,
            if (success) "Factory reset enabled" else "Failed to enable")
    }

    private fun handleLocate(commandRef: DatabaseReference, commandId: String) {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        vibrate(200)
        updateCommandStatus(commandRef, commandId, true, "Location request sent")
    }

    private fun handleSiren(enable: Boolean, commandRef: DatabaseReference, commandId: String) {
        if (enable) {
            playSiren()
            vibratePattern()
            updateCommandStatus(commandRef, commandId, true, "Siren activated")
        } else {
            stopSiren()
            updateCommandStatus(commandRef, commandId, true, "Siren deactivated")
        }
    }

    private fun handleFullBlankLock(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🔒 FULL_BLANK_LOCK: Enabling")
        vibratePattern()

        // Update lock status in Firebase
        val userRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        userRef.child("isFullBlankLocked").setValue(true)
        userRef.child("isLostMessageLocked").setValue(false)
        userRef.child("isAppLocked").setValue(true)

        devicePolicyHelper.lockDevice()
        sendBroadcast(Intent("SHOW_FULL_BLANK_LOCK"))
        updateCommandStatus(commandRef, commandId, true, "Full blank lock enabled")
    }

    private fun handleLostMessageLock(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "📱 LOST_MESSAGE_LOCK: Enabling")
        vibratePattern()

        // Update lock status in Firebase
        val userRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        userRef.child("isLostMessageLocked").setValue(true)
        userRef.child("isFullBlankLocked").setValue(false)
        userRef.child("isAppLocked").setValue(true)

        devicePolicyHelper.lockDevice()
        sendBroadcast(Intent("SHOW_LOST_MESSAGE_LOCK"))
        updateCommandStatus(commandRef, commandId, true, "Lost message lock enabled")
    }

    private fun handleUnlockApp(commandRef: DatabaseReference, commandId: String) {
        Log.d(TAG, "🔓 UNLOCK_APP: Disabling all locks")

        // Update lock status in Firebase
        val userRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        userRef.child("isFullBlankLocked").setValue(false)
        userRef.child("isLostMessageLocked").setValue(false)
        userRef.child("isAppLocked").setValue(false)

        sendBroadcast(Intent("REMOVE_LOCK_OVERLAY"))
        updateCommandStatus(commandRef, commandId, true, "App unlocked")
    }

    private fun handleUnknown(commandRef: DatabaseReference, commandId: String, action: String) {
        Log.w(TAG, "⚠️ Unknown command: $action")
        updateCommandStatus(commandRef, commandId, false, "Unknown command: $action")
    }

    // ========== HELPER METHODS ==========

    private fun updateCommandStatus(commandRef: DatabaseReference, commandId: String, success: Boolean, message: String) {
        val updates = mapOf(
            "status" to if (success) "executed" else "failed",
            "result" to message,
            "executedAt" to ServerValue.TIMESTAMP
        )
        commandRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Command $commandId: $message")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update status for $commandId: ${e.message}")
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
            Log.e(TAG, "Vibrate error: ${e.message}")
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
            Log.e(TAG, "Pattern error: ${e.message}")
        }
    }

    // ========== SIREN METHODS ==========
    private fun playSiren() {
        try {
            stopSiren()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@CommandListenerService, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            Log.d(TAG, "🔊 Siren playing")
        } catch (e: Exception) {
            Log.e(TAG, "Siren error: ${e.message}")
            // Fallback: use notification sound
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@CommandListenerService, notificationUri)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "🔊 Fallback siren playing")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback siren also failed: ${e2.message}")
            }
        }
    }

    private fun stopSiren() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "🔇 Siren stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroying")

        // Clean up listeners
        commandsListener?.let {
            database.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .child(userMobile)
                .child("commands")
                .removeEventListener(it)
        }

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "✅ Wake lock released")
            }
        }

        // Stop siren
        stopSiren()

        Log.d(TAG, "🛑 Service destroyed")
    }
}