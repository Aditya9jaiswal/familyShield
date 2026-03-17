package com.example.familyshield.adapters

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
        val btnFactoryResetControl: MaterialButton = itemView.findViewById(R.id.btnFactoryResetControl) // ✅ NEW BUTTON
    }

    // Siren state
    private var isSirenPlaying = false
    private var mediaPlayer: MediaPlayer? = null
    private var currentSirenUser: String? = null

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

        // ========== FACTORY RESET CONTROL BUTTON ==========
        updateFactoryResetButton(holder, user)

        holder.btnFactoryResetControl.setOnClickListener {
            // Toggle state
            val newState = !user.factoryResetEnabled

            // Update button text and color immediately for better UX
            if (newState) {
                holder.btnFactoryResetControl.text = "⚙️ Factory Reset: ENABLED"
                holder.btnFactoryResetControl.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                holder.btnFactoryResetControl.text = "⚙️ Factory Reset: DISABLED"
                holder.btnFactoryResetControl.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
            }

            // Send command to admin
            listener.onFactoryResetToggle(user, newState)

            Toast.makeText(context,
                "⚙️ Sending command to ${if (newState) "enable" else "disable"} factory reset for ${user.name}",
                Toast.LENGTH_SHORT).show()
        }

        // Lock Button
        holder.btnLock.setOnClickListener {
            listener.onLockDevice(user)
            Toast.makeText(context, "🔒 Lock command sent to ${user.name}", Toast.LENGTH_SHORT).show()
        }

        // Locate Button
        holder.btnLocate.setOnClickListener {
            listener.onLocateDevice(user)
            Toast.makeText(context, "📍 Location request sent to ${user.name}", Toast.LENGTH_SHORT).show()
            fetchCurrentLocation { location ->
                if (location != null) {
                    updateUserLocationInFirebase(user, location)
                }
            }
        }

        // Siren Button
        holder.btnSiren.setOnClickListener {
            toggleSiren(user)
        }

        // View Map Button
        holder.btnViewMap.setOnClickListener {
            if (user.latitude != 0.0 && user.longitude != 0.0) {
                listener.onViewLocation(user)
            } else {
                Toast.makeText(context, "📍 Location not available", Toast.LENGTH_SHORT).show()
            }
        }

        // Call Button
        holder.btnCall.setOnClickListener {
            callUser(user)
        }

        // Factory Reset Button (Execute)
        holder.btnFactoryReset.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("⚠️ FACTORY RESET")
                .setMessage("This will ERASE ALL DATA on ${user.name}'s device!\n\nThis action CANNOT be undone.")
                .setPositiveButton("YES, RESET") { _, _ ->
                    listener.onFactoryReset(user)
                    Toast.makeText(context, "⚠️ Factory reset command sent to ${user.name}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // View Details Button
        holder.cardUser.setOnClickListener {
            showUserDetailsDialog(user)
        }
        holder.btnViewDetails.setOnClickListener {
            showUserDetailsDialog(user)
        }

        // Lost Complaint Button
        holder.btnLostComplaint.setOnClickListener {
            showSimpleComplaintDialog(user)
        }
    }

    // ========== HELPER FUNCTION FOR FACTORY RESET BUTTON ==========
    private fun updateFactoryResetButton(holder: UserViewHolder, user: UserModel) {
        if (user.factoryResetEnabled) {
            holder.btnFactoryResetControl.text = "⚙️ Factory Reset: ENABLED"
            holder.btnFactoryResetControl.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            holder.btnFactoryResetControl.text = "⚙️ Factory Reset: DISABLED"
            holder.btnFactoryResetControl.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
        }
    }

    // ========== PUBLIC METHOD TO UPDATE FROM FIREBASE ==========
    fun updateFactoryResetStatus(userMobile: String, enabled: Boolean) {
        val index = users.indexOfFirst { it.mobile == userMobile }
        if (index != -1) {
            users[index].factoryResetEnabled = enabled
            notifyItemChanged(index)
            Log.d("ADAPTER", "✅ Factory reset status updated for $userMobile: $enabled")
        }
    }

    // ========== SIREN FUNCTIONALITY ==========
    private fun toggleSiren(user: UserModel) {
        if (isSirenPlaying && currentSirenUser == user.mobile) {
            stopSiren()
            listener.onSirenToggle(user, false)
            Toast.makeText(context, "🔇 Siren stopped for ${user.name}", Toast.LENGTH_SHORT).show()
            updateSirenStatusInFirebase(user, false)
        } else {
            if (isSirenPlaying) {
                stopSiren()
            }
            startSiren(user)
            listener.onSirenToggle(user, true)
            Toast.makeText(context, "🔊 Siren started for ${user.name}", Toast.LENGTH_SHORT).show()
            updateSirenStatusInFirebase(user, true)
        }
    }

    private fun startSiren(user: UserModel) {
        try {
            mediaPlayer?.release()

            val resId = try {
                R.raw.siren_sound
            } catch (e: Exception) {
                null
            }

            if (resId != null && resId != 0) {
                mediaPlayer = MediaPlayer.create(context, resId)
            } else {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer.create(context, alarmUri)
            }

            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
            isSirenPlaying = true
            currentSirenUser = user.mobile
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Failed to play siren", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSiren() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isSirenPlaying = false
            currentSirenUser = null
        } catch (e: Exception) {
            // Ignore
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

    // ========== FETCH CURRENT LOCATION ==========
    private fun fetchCurrentLocation(callback: (android.location.Location?) -> Unit) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                callback(null)
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    callback(location)
                } else {
                    val locationRequest = LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1000
                    ).build()

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            callback(locationResult.lastLocation)
                            fusedLocationClient.removeLocationUpdates(this)
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        null
                    )
                }
            }
        } catch (e: Exception) {
            callback(null)
        }
    }

    private fun updateUserLocationInFirebase(user: UserModel, location: android.location.Location) {
        val batteryLevel = getBatteryLevel()

        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "lastLocationUpdate" to ServerValue.TIMESTAMP,
            "batteryLevel" to batteryLevel
        )

        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .updateChildren(locationData)
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ========== UPDATE SIM INFO ==========
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

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED) {

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

    // ========== CALL USER ==========
    private fun callUser(user: UserModel) {
        try {
            val number = if (user.mobile.startsWith("+91")) user.mobile else "+91${user.mobile}"
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== SIMPLE COMPLAINT DIALOG ==========
    private fun showSimpleComplaintDialog(user: UserModel) {
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
                    saveSimpleComplaintToFirebase(user, description, contact)
                    listener.onLostComplaint(user, description, contact)
                    Toast.makeText(context, "✅ Complaint filed for ${user.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter description", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun saveSimpleComplaintToFirebase(user: UserModel, description: String, contact: String) {
        val complaintRef = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("complaints")
            .push()

        val complaintId = complaintRef.key ?: ""

        val complaintData = mapOf(
            "complaintId" to complaintId,
            "userId" to user.uid,
            "userMobile" to user.mobile,
            "userName" to user.name,
            "userEmail" to user.email,
            "description" to description,
            "contactNumber" to contact,
            "adminMobile" to adminMobile,
            "status" to "filed",
            "filedAt" to ServerValue.TIMESTAMP,
            "deviceId" to user.deviceId,
            "deviceName" to user.deviceName,
            "lastLatitude" to user.latitude,
            "lastLongitude" to user.longitude,
            "lastAddress" to user.address,
            "batteryLevel" to user.batteryLevel
        )

        complaintRef.setValue(complaintData)
            .addOnSuccessListener {
                Toast.makeText(context, "📄 Complaint ID: $complaintId", Toast.LENGTH_LONG).show()
            }
    }

    // ========== USER DETAILS DIALOG ==========
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
}