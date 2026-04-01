package com.example.familyshield.admin

import android.content.Intent
import android.net.Uri
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

class AdminUsersActivity : AppCompatActivity(), UserAdapter.OnUserActionListener {

    // Views
    private lateinit var rvUsers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyUsers: TextView

    // Data
    private lateinit var usersList: MutableList<UserModel>
    private lateinit var adapter: UserAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var commandSender: AdminCommandSender  // ✅ Now works

    private var adminMobile: String = ""

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

        commandSender = AdminCommandSender(adminMobile)  // ✅ Now works

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
                            isFullBlankLocked = userSnapshot.child("isFullBlankLocked").getValue(Boolean::class.java) ?: false,
                            isLostMessageLocked = userSnapshot.child("isLostMessageLocked").getValue(Boolean::class.java) ?: false,
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
            commandSender.stopSiren(user.mobile)
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

    // ========== ✅ APP LOCK METHODS ==========
    override fun onFullBlankLock(user: UserModel) {
        commandSender.fullBlankLock(user.mobile)
        Toast.makeText(this, "⬛ Full Blank Lock applied to ${user.name}", Toast.LENGTH_SHORT).show()
        updateUserLockStatus(user.mobile, true, "full_blank")
    }

    override fun onLostMessageLock(user: UserModel) {
        commandSender.lostMessageLock(user.mobile)
        Toast.makeText(this, "📱 Lost Phone Lock applied to ${user.name}", Toast.LENGTH_SHORT).show()
        updateUserLockStatus(user.mobile, true, "lost_message")
    }

    override fun onUnlockApp(user: UserModel) {
        commandSender.unlockApp(user.mobile)
        Toast.makeText(this, "🔓 App unlocked for ${user.name}", Toast.LENGTH_SHORT).show()
        updateUserLockStatus(user.mobile, false, "none")
    }

    private fun updateUserLockStatus(userMobile: String, isLocked: Boolean, lockType: String) {
        val userRef = FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(userMobile)

        userRef.child("isAppLocked").setValue(isLocked)
        userRef.child("appLockType").setValue(lockType)

        when (lockType) {
            "full_blank" -> {
                userRef.child("isFullBlankLocked").setValue(true)
                userRef.child("isLostMessageLocked").setValue(false)
            }
            "lost_message" -> {
                userRef.child("isLostMessageLocked").setValue(true)
                userRef.child("isFullBlankLocked").setValue(false)
            }
            "none" -> {
                userRef.child("isFullBlankLocked").setValue(false)
                userRef.child("isLostMessageLocked").setValue(false)
            }
        }
    }

    // ========== LOST COMPLAINT ==========
    override fun onLostComplaint(user: UserModel, description: String, contact: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📄 FILING COMPLAINT")
            .setMessage("Collecting device information...")
            .setCancelable(false)
            .show()

        val simInfo = getSimInfo()
        val currentTime = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

        val complaintText = """
            |🔴 LOST DEVICE COMPLAINT
            |═══════════════════════════════════════════
            |📅 DATE & TIME: $currentTime
            |👤 USER: ${user.name} (${user.mobile})
            |📱 DEVICE: ${user.deviceName}
            |🔢 IMEI: ${user.imei}
            |📍 LOCATION: ${user.latitude}, ${user.longitude}
            |🔋 BATTERY: ${user.batteryLevel}%
            |📝 DESCRIPTION: $description
            |📞 CONTACT: $contact
        """.trimMargin()

        dialog.dismiss()
        saveComplaintToFirebase(user, complaintText, contact)
    }

    private fun getSimInfo(): Map<String, String> {
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val info = mutableMapOf<String, String>()

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            info["operator"] = tm.simOperatorName ?: "Unknown"
            info["country"] = tm.simCountryIso ?: "Unknown"
        } else {
            info["operator"] = "Permission required"
            info["country"] = "Permission required"
        }
        return info
    }

    private fun saveComplaintToFirebase(user: UserModel, complaintText: String, contact: String) {
        val complaintRef = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(user.mobile)
            .child("complaints")
            .push()

        val complaintData = mapOf(
            "complaintId" to (complaintRef.key ?: ""),
            "userId" to user.uid,
            "userMobile" to user.mobile,
            "userName" to user.name,
            "userEmail" to user.email,
            "description" to complaintText,
            "contactNumber" to contact,
            "adminMobile" to adminMobile,
            "status" to "pending",
            "filedAt" to ServerValue.TIMESTAMP,
            "lastLatitude" to user.latitude,
            "lastLongitude" to user.longitude,
            "batteryLevel" to user.batteryLevel
        )

        complaintRef.setValue(complaintData)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Complaint filed!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
}