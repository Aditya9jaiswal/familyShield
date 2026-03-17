package com.example.familyshield.utils

import android.util.Log
import com.example.familyshield.models.UserModel
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FirebaseHelper private constructor() {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val rootRef: DatabaseReference = database.reference
    private val tag = "FirebaseHelper"

    companion object {
        @Volatile
        private var instance: FirebaseHelper? = null

        fun getInstance(): FirebaseHelper {
            return instance ?: synchronized(this) {
                instance ?: FirebaseHelper().also { instance = it }
            }
        }
    }

    // ========== DATABASE CONNECTION ==========

    fun checkDatabaseConnection(callback: (Boolean) -> Unit) {
        rootRef.child(".info/connected").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(tag, "📶 Database connected: $connected")
                callback(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "❌ Connection failed: ${error.message}")
                callback(false)
            }
        })
    }

    // ========== USER OPERATIONS ==========

    fun getUsersUnderAdmin(adminMobile: String): Task<DataSnapshot> {
        Log.d(tag, "📥 Fetching users for admin: $adminMobile")

        val path = "familyshield/admins/$adminMobile/users"
        Log.d(tag, "📁 Path: $path")

        return rootRef
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(tag, "✅ Found ${snapshot.childrenCount} users")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "❌ Error: ${e.message}")
            }
    }

    fun getUsersUnderAdminWithListener(
        adminMobile: String,
        onDataChange: (DataSnapshot) -> Unit,
        onCancelled: (DatabaseError) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onDataChange(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                onCancelled(error)
            }
        }

        rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .addValueEventListener(listener)

        return listener
    }

    fun removeUserListener(adminMobile: String, listener: ValueEventListener) {
        rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .removeEventListener(listener)
    }

    fun getUser(adminMobile: String, userMobile: String): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .get()
    }

    // ========== COMMAND OPERATIONS ==========

    fun sendCommand(
        adminMobile: String,
        targetUser: String,
        commandType: String,
        priority: Int = 1
    ): Task<Void> {
        Log.d(tag, "📤 Sending command: $commandType to $targetUser (priority: $priority)")

        val commandRef = rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(targetUser)
            .child("commands")
            .push()

        val commandId = commandRef.key ?: "unknown"

        val command = mapOf(
            "commandId" to commandId,
            "type" to commandType,
            "status" to "pending",
            "targetUser" to targetUser,
            "issuedBy" to adminMobile,
            "issuedAt" to ServerValue.TIMESTAMP,
            "priority" to priority,
            "ttl" to (System.currentTimeMillis() + 300000) // 5 minutes
        )

        return commandRef.setValue(command)
            .addOnSuccessListener {
                Log.d(tag, "✅ Command sent: $commandType (ID: $commandId)")
            }
    }

    fun getPendingCommands(userMobile: String, adminMobile: String): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .orderByChild("status")
            .equalTo("pending")
            .get()
    }

    fun updateCommandStatus(
        adminMobile: String,
        userMobile: String,
        commandId: String,
        status: String,
        result: String = ""
    ): Task<Void> {
        val updates = mapOf(
            "status" to status,
            "executedAt" to ServerValue.TIMESTAMP,
            "result" to result
        )

        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("commands")
            .child(commandId)
            .updateChildren(updates)
    }

    // ========== LOCATION OPERATIONS ==========

    fun updateUserLocation(
        adminMobile: String,
        userMobile: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int
    ): Task<Void> {
        val locationData = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "lastLocationUpdate" to ServerValue.TIMESTAMP,
            "batteryLevel" to batteryLevel
        )

        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .updateChildren(locationData)
    }

    fun getUserLocationHistory(
        adminMobile: String,
        userMobile: String,
        limit: Int = 50
    ): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("location_history")
            .orderByChild("timestamp")
            .limitToLast(limit)
            .get()
    }

    // ========== COMPLAINT OPERATIONS ==========

    fun fileLostComplaint(
        adminMobile: String,
        user: UserModel,
        description: String,
        contactNumber: String
    ): Task<Void> {
        val complaintRef = rootRef.child("familyshield")
            .child("complaints")
            .push()

        val complaintId = complaintRef.key ?: ""

        val complaint = mapOf(
            "complaintId" to complaintId,
            "userId" to user.uid,
            "userMobile" to user.mobile,
            "userName" to user.name,
            "description" to description,
            "contactNumber" to contactNumber,
            "adminMobile" to adminMobile,
            "status" to "filed",
            "filedAt" to ServerValue.TIMESTAMP,
            "deviceId" to user.deviceId,
            "lastLocation" to mapOf(
                "lat" to user.latitude,
                "lng" to user.longitude,
                "address" to user.address
            )
        )

        return complaintRef.setValue(complaint)
    }

    fun getComplaintsForUser(userMobile: String): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("complaints")
            .orderByChild("userMobile")
            .equalTo(userMobile)
            .get()
    }

    fun getComplaintsForAdmin(adminMobile: String): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("complaints")
            .orderByChild("adminMobile")
            .equalTo(adminMobile)
            .get()
    }

    fun updateComplaintStatus(
        complaintId: String,
        status: String,
        resolution: String = ""
    ): Task<Void> {
        val updates = mapOf(
            "status" to status,
            "resolvedAt" to ServerValue.TIMESTAMP,
            "resolution" to resolution
        )

        return rootRef.child("familyshield")
            .child("complaints")
            .child(complaintId)
            .updateChildren(updates)
    }

    // ========== USER STATUS UPDATE ==========

    fun updateUserOnlineStatus(
        adminMobile: String,
        userMobile: String,
        isOnline: Boolean
    ): Task<Void> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("isOnline")
            .setValue(isOnline)
    }

    fun updateUserLockStatus(
        adminMobile: String,
        userMobile: String,
        isLocked: Boolean
    ): Task<Void> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("isLocked")
            .setValue(isLocked)
    }

    fun updateFactoryResetStatus(
        adminMobile: String,
        userMobile: String,
        enabled: Boolean
    ): Task<Void> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)
            .child("factoryResetEnabled")
            .setValue(enabled)
    }

    // ========== ADMIN OPERATIONS ==========

    fun getAdminInfo(adminMobile: String): Task<DataSnapshot> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .get()
    }

    fun updateAdminInfo(adminMobile: String, data: Map<String, Any>): Task<Void> {
        return rootRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .updateChildren(data)
    }

    // ========== UTILITY ==========

    fun getServerTimestamp(): Long {
        return ServerValue.TIMESTAMP as Long
    }

    fun generatePushKey(): String {
        return rootRef.push().key ?: UUID.randomUUID().toString()
    }

    fun formatTimestamp(timestamp: Long): String {
        return if (timestamp > 0) {
            val date = Date(timestamp)
            SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(date)
        } else {
            "Unknown"
        }
    }
}