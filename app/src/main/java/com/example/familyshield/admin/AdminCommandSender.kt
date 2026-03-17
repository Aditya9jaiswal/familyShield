package com.example.familyshield.admin

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class AdminCommandSender(private val adminMobile: String) {

    private val database = FirebaseDatabase.getInstance().reference
    private val tag = "CommandSender"

    fun lockDevice(userMobile: String) = sendCommand(userMobile, "LOCK")
    fun locateDevice(userMobile: String) = sendCommand(userMobile, "LOCATE")
    fun playSiren(userMobile: String) = sendCommand(userMobile, "SIREN_ON")
    fun disableFactoryReset(userMobile: String) = sendCommand(userMobile, "DISABLE_RESET")
    fun enableFactoryReset(userMobile: String) = sendCommand(userMobile, "ENABLE_RESET")
    fun factoryReset(userMobile: String) = sendCommand(userMobile, "FACTORY_RESET")

    private fun sendCommand(userMobile: String, commandType: String) {
        val commandRef = database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .push()

        val command = mapOf(
            "commandId" to (commandRef.key ?: ""),
            "type" to commandType,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP,
            "adminMobile" to adminMobile,
            "targetUser" to userMobile
        )

        commandRef.setValue(command)
            .addOnSuccessListener {
                Log.d(tag, "✅ Command sent: $commandType to $userMobile")
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
                        "command" to (cmd.child("type").getValue(String::class.java) ?: ""),
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