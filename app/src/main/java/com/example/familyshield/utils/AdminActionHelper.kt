package com.example.familyshield.utils

import android.content.Context
import android.widget.Toast
import com.example.familyshield.models.UserModel  // ✅ IMPORT FIX - UserModel, user nahi
import com.google.firebase.database.FirebaseDatabase

object AdminActionHelper {

    private fun commandRef(
        adminId: String,
        userId: String,
        deviceId: String
    ) =
        FirebaseDatabase.getInstance()
            .getReference("familyshield")
            .child("admins")
            .child(adminId)
            .child("users")
            .child(userId)
            .child("commands")
            .child(deviceId)

    // ⭐ Generic Command Sender (Main Function)
    private fun sendCommand(
        adminId: String,
        userId: String,
        deviceId: String,
        action: String
    ) {

        val command = mapOf(
            "action" to action,
            "timestamp" to System.currentTimeMillis()
        )

        commandRef(adminId, userId, deviceId).setValue(command)
    }

    // ⭐ Public APIs

    fun sendLockCommand(
        adminId: String,
        userId: String,
        deviceId: String
    ) = sendCommand(adminId, userId, deviceId, "lock")

    fun sendWipeCommand(
        adminId: String,
        userId: String,
        deviceId: String
    ) = sendCommand(adminId, userId, deviceId, "wipe")

    fun disableFactoryReset(
        adminId: String,
        userId: String,
        deviceId: String
    ) = sendCommand(adminId, userId, deviceId, "disable_reset")

    fun enableFactoryReset(
        adminId: String,
        userId: String,
        deviceId: String
    ) = sendCommand(adminId, userId, deviceId, "enable_reset")

    // ⭐ UI Helper - FIXED VERSION
    fun handleUserAction(
        context: Context,
        adminId: String,
        user: UserModel,  // ✅ FIXED: UserModel, user nahi
        deviceId: String
    ) {

        sendLockCommand(
            adminId,
            user.mobile,  // ✅ mobile property available
            deviceId
        )

        Toast.makeText(
            context,
            "Lock command sent to ${user.name}",  // ✅ name property available
            Toast.LENGTH_SHORT
        ).show()
    }
}