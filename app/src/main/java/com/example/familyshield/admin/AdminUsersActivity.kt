package com.example.familyshield.admin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.adapters.UserAdapter
import com.example.familyshield.models.UserModel
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AdminUsersActivity : AppCompatActivity(), UserAdapter.OnUserActionListener {

    // Views
    private lateinit var rvUsers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyUsers: TextView

    // Data
    private lateinit var usersList: MutableList<UserModel>
    private lateinit var adapter: UserAdapter
    private lateinit var sessionManager: SessionManager

    private var adminMobile: String = ""
    private lateinit var commandSender: AdminCommandSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

        // Initialize views
        rvUsers = findViewById(R.id.rvUsers)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyUsers = findViewById(R.id.tvEmptyUsers)

        // Get admin mobile from session
        sessionManager = SessionManager(this)
        adminMobile = sessionManager.getAdminMobile() ?: ""

        if (adminMobile.isEmpty()) {
            Toast.makeText(this, "Admin not logged in!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        commandSender = AdminCommandSender(adminMobile)

        // Setup RecyclerView
        usersList = mutableListOf()
        adapter = UserAdapter(this, usersList, adminMobile, this)

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = adapter

        // Fetch users
        fetchUsersFromFirebase()
    }

    private fun fetchUsersFromFirebase() {
        progressBar.visibility = View.VISIBLE
        rvUsers.visibility = View.GONE
        tvEmptyUsers.visibility = View.GONE

        val usersRef = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")

        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList.clear()

                if (!snapshot.exists()) {
                    progressBar.visibility = View.GONE
                    tvEmptyUsers.visibility = View.VISIBLE
                    rvUsers.visibility = View.GONE
                    return
                }

                for (userSnapshot in snapshot.children) {
                    try {
                        val user = UserModel(
                            uid = userSnapshot.key ?: "",
                            name = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown",
                            email = userSnapshot.child("email").getValue(String::class.java) ?: "",
                            mobile = userSnapshot.child("mobile").getValue(String::class.java) ?: "",
                            password = userSnapshot.child("password").getValue(String::class.java) ?: "",
                            deviceId = userSnapshot.child("deviceId").getValue(String::class.java) ?: "",
                            deviceName = userSnapshot.child("deviceName").getValue(String::class.java) ?: "",
                            imei = userSnapshot.child("imei").getValue(String::class.java) ?: "",
                            isActive = userSnapshot.child("isActive").getValue(Boolean::class.java)
                                ?: userSnapshot.child("active").getValue(Boolean::class.java) ?: false,
                            isOnline = userSnapshot.child("isOnline").getValue(Boolean::class.java) ?: false,
                            isLocked = userSnapshot.child("isLocked").getValue(Boolean::class.java) ?: false,
                            factoryResetEnabled = userSnapshot.child("factoryResetEnabled").getValue(Boolean::class.java) ?: true,
                            sirenEnabled = userSnapshot.child("sirenEnabled").getValue(Boolean::class.java) ?: false,
                            latitude = userSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0,
                            longitude = userSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0,
                            address = userSnapshot.child("address").getValue(String::class.java) ?: "",
                            batteryLevel = userSnapshot.child("batteryLevel").getValue(Int::class.java) ?: 0,
                            lastLocationUpdate = userSnapshot.child("lastLocationUpdate").getValue(Long::class.java) ?: 0L,
                            createdAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                            createdBy = userSnapshot.child("createdBy").getValue(String::class.java) ?: adminMobile,
                            role = userSnapshot.child("role").getValue(String::class.java) ?: "user"
                        )
                        usersList.add(user)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                progressBar.visibility = View.GONE

                if (usersList.isEmpty()) {
                    tvEmptyUsers.visibility = View.VISIBLE
                    rvUsers.visibility = View.GONE
                } else {
                    tvEmptyUsers.visibility = View.GONE
                    rvUsers.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                tvEmptyUsers.visibility = View.VISIBLE
                tvEmptyUsers.text = "Error: ${error.message}"
            }
        })
    }

    // ========== USER ACTIONS ==========
    override fun onLockDevice(user: UserModel) {
        commandSender.lockDevice(user.mobile)
        Toast.makeText(this, "🔒 Lock command sent to ${user.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onLocateDevice(user: UserModel) {
        commandSender.locateDevice(user.mobile)
        Toast.makeText(this, "📍 Locate command sent to ${user.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onSirenToggle(user: UserModel, enable: Boolean) {
        if (enable) {
            commandSender.playSiren(user.mobile)
            Toast.makeText(this, "🔊 Siren ON for ${user.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "🔇 Siren OFF for ${user.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFactoryResetToggle(user: UserModel, enable: Boolean) {
        if (enable) {
            commandSender.enableFactoryReset(user.mobile)
            Toast.makeText(this, "✅ Factory reset enabled for ${user.name}", Toast.LENGTH_SHORT).show()
        } else {
            commandSender.disableFactoryReset(user.mobile)
            Toast.makeText(this, "🚫 Factory reset disabled for ${user.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFactoryReset(user: UserModel) {
        commandSender.factoryReset(user.mobile)
        Toast.makeText(this, "⚠️ Factory reset command sent to ${user.name}", Toast.LENGTH_SHORT).show()
    }

    // ========== ENHANCED LOST COMPLAINT - AUTO FETCH ALL INFO ==========
    override fun onLostComplaint(user: UserModel, description: String, contact: String) {
        // Show loading
        val dialog = AlertDialog.Builder(this)
            .setTitle("📄 FILING COMPLAINT")
            .setMessage("Collecting device information...")
            .setCancelable(false)
            .show()

        // Collect all information
        val deviceInfo = getDeviceInfo(user)
        val simInfo = getSimInfo()
        val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        // Create professional complaint
        val complaintText = """
            |🔴 **LOST DEVICE COMPLAINT**
            |═══════════════════════════════════════════
            |
            |📋 **COMPLAINT ID:** ${UUID.randomUUID().toString().substring(0, 8).uppercase()}
            |📅 **DATE & TIME:** $currentTime
            |
            |═══════════════════════════════════════════
            |👤 **USER DETAILS**
            |═══════════════════════════════════════════
            |▪️ **Name:** ${user.name}
            |▪️ **Mobile:** ${user.mobile}
            |▪️ **Email:** ${if (user.email.isNotEmpty()) user.email else "Not provided"}
            |▪️ **User ID:** ${user.uid}
            |▪️ **Registered On:** ${formatDate(user.createdAt)}
            |
            |═══════════════════════════════════════════
            |📱 **DEVICE DETAILS**
            |═══════════════════════════════════════════
            |▪️ **Device Model:** ${user.deviceName}
            |▪️ **Manufacturer:** ${Build.MANUFACTURER}
            |▪️ **Brand:** ${Build.BRAND}
            |▪️ **Device ID:** ${user.deviceId}
            |▪️ **IMEI Number:** ${if (user.imei.isNotEmpty()) user.imei else "Not available"}
            |▪️ **Android Version:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            |▪️ **Hardware:** ${Build.HARDWARE}
            |
            |═══════════════════════════════════════════
            |📲 **SIM INFORMATION**
            |═══════════════════════════════════════════
            |▪️ **SIM Operator:** ${simInfo["operator"]}
            |▪️ **SIM Country:** ${simInfo["country"]}
            |▪️ **SIM Serial:** ${simInfo["serial"]}
            |▪️ **Phone Number:** ${simInfo["number"]}
            |▪️ **Network Type:** ${simInfo["networkType"]}
            |
            |═══════════════════════════════════════════
            |📍 **LAST KNOWN LOCATION**
            |═══════════════════════════════════════════
            |▪️ **Latitude:** ${user.latitude}
            |▪️ **Longitude:** ${user.longitude}
            |▪️ **Address:** ${if (user.address.isNotEmpty()) user.address else "Not available"}
            |▪️ **Last Updated:** ${formatTime(user.lastLocationUpdate)}
            |
            |═══════════════════════════════════════════
            |🔋 **DEVICE STATUS**
            |═══════════════════════════════════════════
            |▪️ **Battery Level:** ${user.batteryLevel}%
            |▪️ **Online Status:** ${if (user.isOnline) "Online" else "Offline"}
            |▪️ **Lock Status:** ${if (user.isLocked) "Locked" else "Unlocked"}
            |▪️ **Factory Reset:** ${if (user.factoryResetEnabled) "Enabled" else "Disabled"}
            |▪️ **Siren Status:** ${if (user.sirenEnabled) "Enabled" else "Disabled"}
            |
            |═══════════════════════════════════════════
            |📡 **NETWORK INFORMATION**
            |═══════════════════════════════════════════
            |▪️ **WiFi Status:** ${deviceInfo["wifi"]}
            |▪️ **Bluetooth:** ${deviceInfo["bluetooth"]}
            |▪️ **Network:** ${deviceInfo["network"]}
            |▪️ **IP Address:** ${deviceInfo["ip"]}
            |
            |═══════════════════════════════════════════
            |👮 **COMPLAINT DETAILS**
            |═══════════════════════════════════════════
            |▪️ **Filed By Admin:** $adminMobile
            |▪️ **Contact Number:** $contact
            |▪️ **Description:**
            |   $description
            |
            |═══════════════════════════════════════════
            |✅ **COMPLAINT REGISTERED SUCCESSFULLY**
            |🔍 Please keep this information for reference
            |═══════════════════════════════════════════
        """.trimMargin()

        dialog.dismiss()

        // Save to Firebase
        saveComplaintToFirebase(user, complaintText, contact, deviceInfo, simInfo)
    }

    private fun getDeviceInfo(user: UserModel): Map<String, String> {
        val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val bluetoothAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        } else null

        var ipAddress = "Unknown"
        try {
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            ipAddress = String.format("%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            ipAddress = "Not available"
        }

        return mapOf(
            "wifi" to (if (wifiManager.isWifiEnabled) "Enabled" else "Disabled"),
            "bluetooth" to (if (bluetoothAdapter?.isEnabled == true) "Enabled" else "Disabled"),
            "network" to (if (isNetworkAvailable()) "Connected" else "Disconnected"),
            "ip" to ipAddress
        )
    }

    private fun getSimInfo(): Map<String, String> {
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val info = mutableMapOf<String, String>()

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            info["operator"] = tm.simOperatorName ?: "Unknown"
            info["country"] = tm.simCountryIso ?: "Unknown"
            info["serial"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.simSerialNumber ?: "Not available"
            } else {
                "Not available"
            }
            info["number"] = tm.line1Number ?: "Not available"
            info["networkType"] = when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G Edge"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                else -> "Unknown"
            }
        } else {
            info["operator"] = "Permission required"
            info["country"] = "Permission required"
            info["serial"] = "Permission required"
            info["number"] = "Permission required"
            info["networkType"] = "Permission required"
        }
        return info
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }

    private fun saveComplaintToFirebase(user: UserModel, complaintText: String, contact: String, deviceInfo: Map<String, String>, simInfo: Map<String, String>) {
        val complaintRef = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .child("complaints")
            .push()
        val complaintId = complaintRef.key ?: UUID.randomUUID().toString()

        val complaintData = mapOf(
            "complaintId" to complaintId,
            "userId" to user.uid,
            "userMobile" to user.mobile,
            "userName" to user.name,
            "userEmail" to user.email,
            "description" to complaintText,
            "contactNumber" to contact,
            "adminMobile" to adminMobile,
            "status" to "filed",
            "filedAt" to ServerValue.TIMESTAMP,
            "deviceId" to user.deviceId,
            "deviceName" to user.deviceName,
            "deviceModel" to Build.MODEL,
            "deviceManufacturer" to Build.MANUFACTURER,
            "androidVersion" to Build.VERSION.RELEASE,
            "imei" to (user.imei ?: ""),
            "simOperator" to simInfo["operator"],
            "simCountry" to simInfo["country"],
            "simSerial" to simInfo["serial"],
            "phoneNumber" to simInfo["number"],
            "networkType" to simInfo["networkType"],
            "lastLatitude" to user.latitude,
            "lastLongitude" to user.longitude,
            "lastAddress" to user.address,
            "lastLocationTime" to user.lastLocationUpdate,
            "batteryLevel" to user.batteryLevel,
            "isOnline" to user.isOnline,
            "isLocked" to user.isLocked,
            "factoryResetEnabled" to user.factoryResetEnabled,
            "wifiStatus" to deviceInfo["wifi"],
            "bluetoothStatus" to deviceInfo["bluetooth"],
            "networkStatus" to deviceInfo["network"],
            "ipAddress" to deviceInfo["ip"],
            "complaintText" to complaintText
        )

        complaintRef.setValue(complaintData)
            .addOnSuccessListener {
                showComplaintDetails(complaintText, complaintId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Failed to save complaint: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showComplaintDetails(complaintText: String, complaintId: String) {
        val scrollView = androidx.core.widget.NestedScrollView(this)
        val textView = TextView(this).apply {
            text = complaintText
            textSize = 12f
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("✅ COMPLAINT REGISTERED")
            .setView(scrollView)
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("SHARE") { _, _ ->
                shareComplaint(complaintText, complaintId)
            }
            .setNegativeButton("COPY") { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Complaint", complaintText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun shareComplaint(complaintText: String, complaintId: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "$complaintText\n\nComplaint ID: $complaintId\nFiled from FamilyShield App")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Complaint"))
    }

    override fun onViewLocation(user: UserModel) {
        try {
            val uri = Uri.parse("geo:${user.latitude},${user.longitude}?q=${user.latitude},${user.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
        } catch (e: Exception) {
            Toast.makeText(this, "Maps not installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCallUser(user: UserModel) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${user.mobile}")))
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "Unknown"
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String = when {
        timestamp == 0L -> "Never"
        else -> {
            val diff = System.currentTimeMillis() - timestamp
            when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} minutes ago"
                diff < 86400000 -> "${diff / 3600000} hours ago"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}