package com.example.familyshield.admin

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.UUID

class AdminCommandSender(private val adminMobile: String) {

    private val database = FirebaseDatabase.getInstance().reference
    private val tag = "CommandSender"

    // Device Commands
    fun lockDevice(userMobile: String) = sendCommand(userMobile, "lock")
    fun locateDevice(userMobile: String) = sendCommand(userMobile, "locate")

    // Siren Commands
    fun playSiren(userMobile: String) = sendCommand(userMobile, "siren_on")
    fun stopSiren(userMobile: String) = sendCommand(userMobile, "siren_off")

    // Factory Reset Commands
    fun disableFactoryReset(userMobile: String) = sendCommand(userMobile, "disable_reset")
    fun enableFactoryReset(userMobile: String) = sendCommand(userMobile, "enable_reset")
    fun factoryReset(userMobile: String) = sendCommand(userMobile, "wipe")

    // App Lock Commands
    fun fullBlankLock(userMobile: String) = sendCommand(userMobile, "full_blank_lock")
    fun lostMessageLock(userMobile: String) = sendCommand(userMobile, "lost_message_lock")
    fun unlockApp(userMobile: String) = sendCommand(userMobile, "unlock_app")

    private fun sendCommand(userMobile: String, commandAction: String) {
        val commandId = UUID.randomUUID().toString()
        val commandRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .child(commandId)

        val command = mapOf(
            "commandId" to commandId,
            "action" to commandAction,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP,
            "adminMobile" to adminMobile,
            "targetUser" to userMobile
        )

        commandRef.setValue(command)
            .addOnSuccessListener {
                Log.d(tag, "✅ Command sent: $commandAction to $userMobile")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "❌ Failed: ${e.message}")
            }
    }

    fun getCommandHistory(callback: (List<Map<String, Any>>) -> Unit) {
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("commands")
            .orderByChild("timestamp")
            .limitToLast(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val commands = mutableListOf<Map<String, Any>>()
                for (cmd in snapshot.children) {
                    commands.add(mapOf(
                        "command" to (cmd.child("action").getValue(String::class.java) ?: ""),
                        "userMobile" to (cmd.child("targetUser").getValue(String::class.java) ?: ""),
                        "timestamp" to (cmd.child("timestamp").getValue(Long::class.java) ?: 0L),
                        "status" to (cmd.child("status").getValue(String::class.java) ?: "")
                    ))
                }
                callback(commands.reversed())
            }
            .addOnFailureListener { callback(emptyList()) }
    }
}