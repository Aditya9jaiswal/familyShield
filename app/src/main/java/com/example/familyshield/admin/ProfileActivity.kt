package com.example.familyshield.admin

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.R
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {

    // Views
    private lateinit var tvName: TextView
    private lateinit var tvMobile: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvResolvedComplaints: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var layoutChangePassword: LinearLayout
    private lateinit var layoutTwoFactor: LinearLayout

    // Firebase
    private lateinit var database: DatabaseReference
    private lateinit var sessionManager: SessionManager
    private var adminMobile: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        initFirebase()
        loadAdminData()
        loadStatistics()
        setupClickListeners()
    }

    private fun initViews() {
        tvName = findViewById(R.id.tvAdminName)
        tvMobile = findViewById(R.id.tvAdminMobile)
        tvEmail = findViewById(R.id.tvAdminEmail)
        tvCreatedAt = findViewById(R.id.tvAdminCreatedAt)
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
        tvResolvedComplaints = findViewById(R.id.tvResolvedComplaints)
        btnLogout = findViewById(R.id.btnLogout)
        layoutChangePassword = findViewById(R.id.layoutChangePassword)
        layoutTwoFactor = findViewById(R.id.layoutTwoFactor)
    }

    private fun initFirebase() {
        sessionManager = SessionManager(this)
        adminMobile = sessionManager.getAdminMobile() ?: ""

        if (adminMobile.isEmpty()) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        }

        database = FirebaseDatabase.getInstance()
            .reference
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
    }

    private fun loadAdminData() {
        showLoading(true)

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get admin info from Firebase
                val name = snapshot.child("name").getValue(String::class.java)
                    ?: sessionManager.getAdminName() ?: "Admin User"
                val mobile = snapshot.child("mobile").getValue(String::class.java) ?: adminMobile
                val email = snapshot.child("email").getValue(String::class.java)
                    ?: sessionManager.getAdminEmail() ?: "Not provided"
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                // Display formatted data
                tvName.text = name
                tvMobile.text = formatPhoneNumber(mobile)
                tvEmail.text = email
                tvCreatedAt.text = formatDate(createdAt)

                showLoading(false)
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                // Fallback to session data
                tvName.text = sessionManager.getAdminName() ?: "Admin User"
                tvMobile.text = formatPhoneNumber(adminMobile)
                tvEmail.text = sessionManager.getAdminEmail() ?: "Not provided"
                tvCreatedAt.text = "Information unavailable"

                Toast.makeText(
                    this@ProfileActivity,
                    "Using cached data: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun loadStatistics() {
        // Load total users count
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userCount = snapshot.childrenCount
                tvTotalUsers.text = userCount.toString()
                loadComplaintsStatistics()
            }

            override fun onCancelled(error: DatabaseError) {
                tvTotalUsers.text = "0"
                loadComplaintsStatistics()
            }
        })
    }

    private fun loadComplaintsStatistics() {
        var totalComplaints = 0
        var resolvedComplaints = 0

        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val complaintsSnapshot = userSnapshot.child("complaints")
                    totalComplaints += complaintsSnapshot.childrenCount.toInt()

                    for (complaintSnapshot in complaintsSnapshot.children) {
                        val status = complaintSnapshot.child("status").getValue(String::class.java)
                        if (status == "resolved") {
                            resolvedComplaints++
                        }
                    }
                }

                tvTotalComplaints.text = totalComplaints.toString()
                tvResolvedComplaints.text = resolvedComplaints.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                tvTotalComplaints.text = "0"
                tvResolvedComplaints.text = "0"
            }
        })
    }

    private fun setupClickListeners() {
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        layoutChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        layoutTwoFactor.setOnClickListener {
            showTwoFactorDialog()
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        sessionManager.clearSession()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<TextView>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<TextView>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<TextView>(R.id.etConfirmPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val currentPwd = etCurrentPassword.text.toString()
                val newPwd = etNewPassword.text.toString()
                val confirmPwd = etConfirmPassword.text.toString()

                if (validatePasswordChange(currentPwd, newPwd, confirmPwd)) {
                    updatePasswordInFirebase(newPwd)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePasswordChange(current: String, new: String, confirm: String): Boolean {
        if (current.isEmpty()) {
            Toast.makeText(this, "Please enter current password", Toast.LENGTH_SHORT).show()
            return false
        }
        if (new.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        if (new != confirm) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updatePasswordInFirebase(newPassword: String) {
        val updates = mapOf("password" to newPassword)

        database.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showTwoFactorDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Two-Factor Authentication")
            .setMessage("Enable 2FA for extra security? You'll receive a verification code on your registered mobile number.")
            .setPositiveButton("Enable") { _, _ ->
                enableTwoFactorAuth()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun enableTwoFactorAuth() {
        // Show loading
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Setting up 2FA")
            .setMessage("Sending verification code...")
            .setCancelable(false)
            .show()

        // Simulate 2FA setup (implement actual 2FA logic here)
        database.child("twoFactorEnabled").setValue(true)
            .addOnSuccessListener {
                dialog.dismiss()
                Toast.makeText(this, "2FA enabled successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                dialog.dismiss()
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        // Optional: Add progress bar to your layout
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        progressBar?.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun formatPhoneNumber(mobile: String): String {
        return if (mobile.length == 10) {
            "+91 ${mobile.substring(0, 5)} ${mobile.substring(5)}"
        } else {
            mobile
        }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "Not available"
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}