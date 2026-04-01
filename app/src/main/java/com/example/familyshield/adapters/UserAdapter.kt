package com.example.familyshield.adapters

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.models.UserModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserAdapter(
    private val context: Context,
    private val users: MutableList<UserModel>,
    private val adminMobile: String,
    private val listener: OnUserActionListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    interface OnUserActionListener {
        fun onLockDevice(user: UserModel)
        fun onLocateDevice(user: UserModel)
        fun onSirenToggle(user: UserModel, enable: Boolean)
        fun onFactoryResetToggle(user: UserModel, enable: Boolean)
        fun onFactoryReset(user: UserModel)
        fun onLostComplaint(user: UserModel, description: String, contact: String)
        fun onViewLocation(user: UserModel)
        fun onCallUser(user: UserModel)
        // ✅ APP LOCK METHODS
        fun onFullBlankLock(user: UserModel)
        fun onLostMessageLock(user: UserModel)
        fun onUnlockApp(user: UserModel)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardUser: MaterialCardView = itemView.findViewById(R.id.cardUser)
        val cardLocation: MaterialCardView = itemView.findViewById(R.id.cardLocation)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvMobile: TextView = itemView.findViewById(R.id.tvMobile)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        val tvLatitude: TextView = itemView.findViewById(R.id.tvLatitude)
        val tvLongitude: TextView = itemView.findViewById(R.id.tvLongitude)
        val tvLastUpdated: TextView = itemView.findViewById(R.id.tvLastUpdated)
        val tvBatteryLevel: TextView = itemView.findViewById(R.id.tvBatteryLevel)
        val tvDeviceOwnerStatus: TextView = itemView.findViewById(R.id.tvDeviceOwnerStatus)
        val tvSimOperator: TextView = itemView.findViewById(R.id.tvSimOperator)
        val tvSimCountry: TextView = itemView.findViewById(R.id.tvSimCountry)
        val tvNetworkType: TextView = itemView.findViewById(R.id.tvNetworkType)
        val btnLock: MaterialButton = itemView.findViewById(R.id.btnLock)
        val btnLocate: MaterialButton = itemView.findViewById(R.id.btnLocate)
        val btnSiren: MaterialButton = itemView.findViewById(R.id.btnSiren)
        val btnFactoryReset: MaterialButton = itemView.findViewById(R.id.btnFactoryReset)
        val btnLostComplaint: MaterialButton = itemView.findViewById(R.id.btnLostComplaint)
        val btnViewMap: MaterialButton = itemView.findViewById(R.id.btnViewMap)
        val btnCall: MaterialButton = itemView.findViewById(R.id.btnCall)
        val btnViewDetails: MaterialButton = itemView.findViewById(R.id.btnViewDetails)
        val btnFactoryResetControl: MaterialButton = itemView.findViewById(R.id.btnFactoryResetControl)
        // ✅ APP LOCK BUTTONS - Sab visible rahenge
        val btnFullBlankLock: MaterialButton = itemView.findViewById(R.id.btnFullBlankLock)
        val btnLostMessageLock: MaterialButton = itemView.findViewById(R.id.btnLostMessageLock)
        val btnUnlockApp: MaterialButton = itemView.findViewById(R.id.btnUnlockApp)
    }

    // ✅ DEBOUNCE MAP - Prevent multiple clicks
    private val lastClickTime = mutableMapOf<String, Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Basic Info
        holder.tvName.text = user.name.ifEmpty { "Unknown User" }
        holder.tvMobile.text = "📱 +91 ${user.mobile}"
        holder.tvEmail.text = if (user.email.isNotEmpty()) "📧 ${user.email}" else "📧 No Email"
        holder.tvAddress.text = if (user.address.isNotEmpty()) "📍 ${user.address}" else "📍 No Address"

        // Status
        updateStatus(holder, user)

        // Location
        updateLocation(holder, user)

        // Battery
        holder.tvBatteryLevel.visibility = if (user.batteryLevel > 0) View.VISIBLE else View.GONE
        holder.tvBatteryLevel.text = "🔋 ${user.batteryLevel}%"

        // SIM Info
        updateSimInfo(holder, user)

        // Device Owner Status
        if (user.isOnline) {
            holder.tvDeviceOwnerStatus.visibility = View.VISIBLE
            holder.tvDeviceOwnerStatus.text = "✅ Device Owner Active"
        } else {
            holder.tvDeviceOwnerStatus.visibility = View.GONE
        }

        // Update Siren Button UI
        updateSirenButtonUI(holder, user)

        // ✅ UPDATE APP LOCK BUTTONS UI (SAB BUTTON ALWAYS VISIBLE)
        updateAppLockButtonsUI(holder, user)

        // Factory Reset Control Button
        updateFactoryResetButton(holder, user)

        // ========== FACTORY RESET TOGGLE ==========
        holder.btnFactoryResetControl.setOnClickListener {
            if (isClickAllowed("factory_toggle_${user.mobile}")) {
                val newState = !user.factoryResetEnabled
                updateFactoryResetButton(holder, user.copy(factoryResetEnabled = newState))
                listener.onFactoryResetToggle(user, newState)
                Toast.makeText(context,
                    "⚙️ ${if (newState) "Enable" else "Disable"} factory reset command sent to ${user.name}",
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== LOCK DEVICE ==========
        holder.btnLock.setOnClickListener {
            if (isClickAllowed("lock_${user.mobile}")) {
                listener.onLockDevice(user)
                Toast.makeText(context, "🔒 Lock command sent to ${user.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== LOCATE DEVICE ==========
        holder.btnLocate.setOnClickListener {
            if (isClickAllowed("locate_${user.mobile}")) {
                listener.onLocateDevice(user)
                Toast.makeText(context, "📍 Location request sent to ${user.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== SIREN ==========
        holder.btnSiren.setOnClickListener {
            if (isClickAllowed("siren_${user.mobile}")) {
                toggleSirenCommand(user)
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== VIEW MAP ==========
        holder.btnViewMap.setOnClickListener {
            if (user.latitude != 0.0 && user.longitude != 0.0) {
                listener.onViewLocation(user)
            } else {
                Toast.makeText(context, "📍 Location not available", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== CALL USER ==========
        holder.btnCall.setOnClickListener {
            callUser(user)
        }

        // ========== FACTORY RESET (EXECUTE) ==========
        holder.btnFactoryReset.setOnClickListener {
            if (isClickAllowed("factory_reset_${user.mobile}")) {
                AlertDialog.Builder(context)
                    .setTitle("⚠️ FACTORY RESET")
                    .setMessage("This will ERASE ALL DATA on ${user.name}'s device!\n\nThis action CANNOT be undone.")
                    .setPositiveButton("YES, RESET") { _, _ ->
                        listener.onFactoryReset(user)
                        Toast.makeText(context, "⚠️ Factory reset command sent to ${user.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== VIEW DETAILS ==========
        holder.cardUser.setOnClickListener {
            showUserDetailsDialog(user)
        }
        holder.btnViewDetails.setOnClickListener {
            showUserDetailsDialog(user)
        }

        // ========== LOST COMPLAINT ==========
        holder.btnLostComplaint.setOnClickListener {
            showComplaintDialog(user)
        }

        // ========== ✅ FULL BLANK LOCK (PERSISTENT) ==========
        holder.btnFullBlankLock.setOnClickListener {
            if (isClickAllowed("full_blank_${user.mobile}")) {
                AlertDialog.Builder(context)
                    .setTitle("⚠️ FULL BLANK LOCK")
                    .setMessage("यह लॉक पूरी स्क्रीन को ब्लैंक कर देगा।\n\n" +
                            "User फोन स्विच ऑफ नहीं कर पाएगा।\n" +
                            "App बंद करने पर भी लॉक रहेगा।\n" +
                            "केवल Admin ही अनलॉक कर सकता है।\n\n" +
                            "क्या आप यह लॉक लगाना चाहते हैं?")
                    .setPositiveButton("YES, LOCK") { _, _ ->
                        listener.onFullBlankLock(user)
                        user.isAppLocked = true
                        user.isFullBlankLocked = true
                        user.isLostMessageLocked = false
                        updateAppLockButtonsUI(holder, user)
                        updateLockStatusInFirebase(user, "full_blank", true)
                        Toast.makeText(context, "⬛ Full Blank Lock applied to ${user.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== ✅ LOST PHONE LOCK (PERSISTENT) ==========
        holder.btnLostMessageLock.setOnClickListener {
            if (isClickAllowed("lost_message_${user.mobile}")) {
                AlertDialog.Builder(context)
                    .setTitle("📱 LOST PHONE LOCK")
                    .setMessage("यह लॉक स्क्रीन पर Lost Phone Message दिखाएगा।\n\n" +
                            "User को Admin से contact करने का विकल्प मिलेगा।\n" +
                            "App बंद करने पर भी लॉक रहेगा।\n\n" +
                            "क्या आप यह लॉक लगाना चाहते हैं?")
                    .setPositiveButton("YES, LOCK") { _, _ ->
                        listener.onLostMessageLock(user)
                        user.isAppLocked = true
                        user.isLostMessageLocked = true
                        user.isFullBlankLocked = false
                        updateAppLockButtonsUI(holder, user)
                        updateLockStatusInFirebase(user, "lost_message", true)
                        Toast.makeText(context, "📱 Lost Message Lock applied to ${user.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_LONG).show()
            }
        }

        // ========== ✅ UNLOCK APP (PERSISTENT) ==========
        holder.btnUnlockApp.setOnClickListener {
            if (isClickAllowed("unlock_${user.mobile}")) {
                AlertDialog.Builder(context)
                    .setTitle("🔓 UNLOCK APP")
                    .setMessage("क्या आप ${user.name} के फोन को अनलॉक करना चाहते हैं?")
                    .setPositiveButton("YES, UNLOCK") { _, _ ->
                        listener.onUnlockApp(user)
                        user.isAppLocked = false
                        user.isFullBlankLocked = false
                        user.isLostMessageLocked = false
                        updateAppLockButtonsUI(holder, user)
                        updateLockStatusInFirebase(user, "none", false)
                        Toast.makeText(context, "🔓 App unlocked for ${user.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(context, "Please wait 5 seconds", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== ✅ UPDATE LOCK STATUS IN FIREBASE ==========
    private fun updateLockStatusInFirebase(user: UserModel, lockType: String, enabled: Boolean) {
        val userRef = FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)

        when (lockType) {
            "full_blank" -> {
                userRef.child("isFullBlankLocked").setValue(enabled)
                userRef.child("isLostMessageLocked").setValue(false)
                userRef.child("isAppLocked").setValue(enabled)
            }
            "lost_message" -> {
                userRef.child("isLostMessageLocked").setValue(enabled)
                userRef.child("isFullBlankLocked").setValue(false)
                userRef.child("isAppLocked").setValue(enabled)
            }
            "none" -> {
                userRef.child("isFullBlankLocked").setValue(false)
                userRef.child("isLostMessageLocked").setValue(false)
                userRef.child("isAppLocked").setValue(false)
            }
        }
    }

    // ========== ✅ DEBOUNCE FUNCTION ==========
    private fun isClickAllowed(key: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastClickTime[key] ?: 0

        if (currentTime - lastTime >= 5000) {
            lastClickTime[key] = currentTime
            return true
        }
        return false
    }

    // ========== ✅ UPDATE APP LOCK BUTTONS UI (SAB BUTTON ALWAYS VISIBLE) ==========
    private fun updateAppLockButtonsUI(holder: UserViewHolder, user: UserModel) {
        // Sab button hamesha visible - koi hide/show nahi
        // Sirf enable/disable hota hai status ke hisaab se

        if (user.isAppLocked) {
            // Locked state: Lock buttons disabled (grey), Unlock button highlighted
            holder.btnFullBlankLock.isEnabled = false
            holder.btnLostMessageLock.isEnabled = false
            holder.btnUnlockApp.text = "🔓 UNLOCK NOW"
            holder.btnUnlockApp.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#FF9800")
            )
        } else {
            // Unlocked state: All buttons enabled
            holder.btnFullBlankLock.isEnabled = true
            holder.btnLostMessageLock.isEnabled = true
            holder.btnUnlockApp.text = "🔓 UNLOCK APP"
            holder.btnUnlockApp.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")
            )
        }
    }

    // ========== SIREN FUNCTIONALITY ==========
    private fun toggleSirenCommand(user: UserModel) {
        val newState = !user.sirenEnabled
        listener.onSirenToggle(user, newState)
        user.sirenEnabled = newState
        notifyItemChanged(users.indexOf(user))

        if (newState) {
            Toast.makeText(context, "🔊 Siren command sent to ${user.name}'s device", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "🔇 Siren stop command sent to ${user.name}'s device", Toast.LENGTH_SHORT).show()
        }
        updateSirenStatusInFirebase(user, newState)
    }

    private fun updateSirenButtonUI(holder: UserViewHolder, user: UserModel) {
        if (user.sirenEnabled) {
            holder.btnSiren.text = "🔊 Siren: ON"
            holder.btnSiren.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
        } else {
            holder.btnSiren.text = "🔇 Siren: OFF"
            holder.btnSiren.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
        }
    }

    private fun updateSirenStatusInFirebase(user: UserModel, enabled: Boolean) {
        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .child("sirenEnabled")
            .setValue(enabled)
    }

    // ========== COMPLAINT FUNCTIONALITY ==========
    private fun showComplaintDialog(user: UserModel) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_lost_complaint, null)
        val etDescription = dialogView.findViewById<TextView>(R.id.etComplaintDescription)
        val etContact = dialogView.findViewById<TextView>(R.id.etContactNumber)

        etContact.text = user.mobile

        AlertDialog.Builder(context)
            .setTitle("📄 FILE COMPLAINT")
            .setView(dialogView)
            .setPositiveButton("SUBMIT") { _, _ ->
                val description = etDescription.text.toString()
                val contact = etContact.text.toString()

                if (description.isNotEmpty()) {
                    saveComplaintToFirebase(user, description, contact)
                    listener.onLostComplaint(user, description, contact)
                    Toast.makeText(context, "✅ Complaint filed for ${user.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter description", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun saveComplaintToFirebase(user: UserModel, description: String, contact: String) {
        val complaintRef = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .child("complaints")
            .push()

        val complaintId = complaintRef.key ?: ""
        val currentTime = System.currentTimeMillis()

        val complaintData = mapOf(
            "complaintId" to complaintId,
            "userId" to user.uid,
            "userMobile" to user.mobile,
            "userName" to user.name,
            "userEmail" to user.email,
            "description" to description,
            "contactNumber" to contact,
            "adminMobile" to adminMobile,
            "status" to "pending",
            "filedAt" to currentTime,
            "filedAtTimestamp" to ServerValue.TIMESTAMP,
            "deviceId" to user.deviceId,
            "deviceName" to user.deviceName,
            "phoneModel" to user.deviceName,
            "imeiNumber" to user.deviceId,
            "lastLatitude" to user.latitude,
            "lastLongitude" to user.longitude,
            "lastAddress" to user.address,
            "batteryLevel" to user.batteryLevel,
            "isRead" to false
        )

        complaintRef.setValue(complaintData)
            .addOnSuccessListener {
                Toast.makeText(context, "📄 Complaint ID: $complaintId", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ========== SIM INFO ==========
    private fun updateSimInfo(holder: UserViewHolder, user: UserModel) {
        val simInfo = getSimInfo()
        holder.tvSimOperator.text = "SIM: ${simInfo["operator"]}"
        holder.tvSimCountry.text = "Country: ${simInfo["country"]}"
        holder.tvNetworkType.text = "Network: ${simInfo["networkType"]}"
        updateSimInfoInFirebase(user, simInfo)
    }

    private fun getSimInfo(): Map<String, String> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val info = mutableMapOf<String, String>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            info["operator"] = tm.simOperatorName ?: "No SIM"
            info["country"] = tm.simCountryIso?.uppercase() ?: "Unknown"
            info["networkType"] = when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G Edge"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                else -> "Unknown"
            }
        } else {
            info["operator"] = "Permission denied"
            info["country"] = "Permission denied"
            info["networkType"] = "Permission denied"
        }
        return info
    }

    private fun updateSimInfoInFirebase(user: UserModel, simInfo: Map<String, String>) {
        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .updateChildren(mapOf(
                "simOperator" to simInfo["operator"],
                "simCountry" to simInfo["country"],
                "networkType" to simInfo["networkType"]
            ))
    }

    // ========== STATUS UPDATE ==========
    private fun updateStatus(holder: UserViewHolder, user: UserModel) {
        val statusText = when {
            !user.isActive -> "Inactive"
            user.isLocked -> "Locked"
            user.isOnline -> "Online"
            else -> "Offline"
        }
        val statusColor = when {
            !user.isActive -> "#9E9E9E"
            user.isLocked -> "#F44336"
            user.isOnline -> "#4CAF50"
            else -> "#FF9800"
        }
        holder.tvStatus.text = "● $statusText"
        holder.tvStatus.setTextColor(android.graphics.Color.parseColor(statusColor))
    }

    // ========== LOCATION UPDATE ==========
    private fun updateLocation(holder: UserViewHolder, user: UserModel) {
        if (user.latitude != 0.0 && user.longitude != 0.0) {
            holder.tvLatitude.text = String.format("Lat: %.6f", user.latitude)
            holder.tvLongitude.text = String.format("Lng: %.6f", user.longitude)
            holder.cardLocation.visibility = View.VISIBLE
            holder.btnViewMap.visibility = View.VISIBLE
            holder.tvLastUpdated.text = "Updated: ${formatTime(user.lastLocationUpdate)}"
        } else {
            holder.cardLocation.visibility = View.GONE
            holder.btnViewMap.visibility = View.GONE
            holder.tvLastUpdated.text = "Location not available"
        }
    }

    // ========== HELPER FUNCTIONS ==========
    private fun updateFactoryResetButton(holder: UserViewHolder, user: UserModel) {
        if (user.factoryResetEnabled) {
            holder.btnFactoryResetControl.text = "⚙️ Factory Reset: ENABLED"
            holder.btnFactoryResetControl.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            holder.btnFactoryResetControl.text = "⚙️ Factory Reset: DISABLED"
            holder.btnFactoryResetControl.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
        }
    }

    private fun callUser(user: UserModel) {
        try {
            val number = if (user.mobile.startsWith("+91")) user.mobile else "+91${user.mobile}"
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUserDetailsDialog(user: UserModel) {
        val simInfo = getSimInfo()
        val details = """
            |👤 USER DETAILS
            |════════════════════
            |Name: ${user.name}
            |Mobile: +91 ${user.mobile}
            |Email: ${if (user.email.isNotEmpty()) user.email else "Not provided"}
            |User ID: ${user.uid}
            |
            |📱 DEVICE DETAILS
            |════════════════════
            |Device: ${user.deviceName}
            |Device ID: ${user.deviceId}
            |
            |📲 SIM INFORMATION
            |════════════════════
            |Operator: ${simInfo["operator"]}
            |Country: ${simInfo["country"]}
            |Network: ${simInfo["networkType"]}
            |
            |📍 LOCATION
            |════════════════════
            |Latitude: ${user.latitude}
            |Longitude: ${user.longitude}
            |Last Update: ${formatTimeFull(user.lastLocationUpdate)}
            |
            |🔋 STATUS
            |════════════════════
            |Active: ${if (user.isActive) "Yes" else "No"}
            |Online: ${if (user.isOnline) "Yes" else "No"}
            |Locked: ${if (user.isLocked) "Yes" else "No"}
            |Battery: ${user.batteryLevel}%
            |Factory Reset: ${if (user.factoryResetEnabled) "Enabled" else "Disabled"}
            |Siren: ${if (user.sirenEnabled) "ON" else "OFF"}
            |App Locked: ${if (user.isAppLocked) "YES" else "NO"}
        """.trimMargin()

        AlertDialog.Builder(context)
            .setTitle("📋 USER INFORMATION")
            .setMessage(details)
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("CALL") { _, _ ->
                callUser(user)
            }
            .show()
    }

    private fun formatTime(ts: Long): String = when {
        ts == 0L -> "Never"
        else -> {
            val diff = System.currentTimeMillis() - ts
            when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000}m ago"
                diff < 86400000 -> "${diff / 3600000}h ago"
                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
            }
        }
    }

    private fun formatTimeFull(ts: Long): String {
        if (ts == 0L) return "Never"
        return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<UserModel>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    fun updateSirenStatus(userMobile: String, enabled: Boolean) {
        val index = users.indexOfFirst { it.mobile == userMobile }
        if (index != -1) {
            users[index].sirenEnabled = enabled
            notifyItemChanged(index)
            Log.d("ADAPTER", "✅ Siren status updated for $userMobile: $enabled")
        }
    }
}