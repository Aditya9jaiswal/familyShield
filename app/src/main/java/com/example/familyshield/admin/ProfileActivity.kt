package com.example.familyshield.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.R
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvMobile: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var btnLogout: Button

    private lateinit var database: DatabaseReference
    private lateinit var sessionManager: SessionManager
    private var adminMobile: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)

        // ================= Views =================
        tvName = findViewById(R.id.tvAdminName)
        tvMobile = findViewById(R.id.tvAdminMobile)
        tvEmail = findViewById(R.id.tvAdminEmail)
        tvRole = findViewById(R.id.tvAdminRole)
        tvCreatedAt = findViewById(R.id.tvAdminCreatedAt)
        btnLogout = findViewById(R.id.btnLogout)

        // ================= Get admin from session =================
        adminMobile = sessionManager.getAdminMobile() ?: ""

        if (adminMobile.isEmpty()) {
            Toast.makeText(this, "Admin session not found", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // ================= Firebase Reference =================
        database = FirebaseDatabase.getInstance()
            .getReference("familyshield")
            .child("admins")
            .child(adminMobile)

        fetchAdminInfo()

        btnLogout.setOnClickListener {
            logoutAdmin()
        }
    }

    private fun fetchAdminInfo() {

        database.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                // Get admin info from Firebase or fallback to SessionManager
                val name = snapshot.child("name").getValue(String::class.java)
                    ?: sessionManager.getAdminName()
                val mobile = snapshot.child("mobile").getValue(String::class.java)
                    ?: adminMobile
                val email = snapshot.child("email").getValue(String::class.java)
                    ?: sessionManager.getAdminEmail()
                val role = snapshot.child("role").getValue(String::class.java) ?: "Admin"
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                // Display
                tvName.text = "Name: $name"
                tvMobile.text = "Mobile: $mobile"
                tvEmail.text = "Email: $email"
                tvRole.text = "Role: $role"

                if (createdAt != 0L) {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm")
                    val date = sdf.format(java.util.Date(createdAt))
                    tvCreatedAt.text = "Created: $date"
                } else {
                    tvCreatedAt.text = "Created: N/A"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ProfileActivity,
                    "Database Error: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Fallback to session
                tvName.text = "Name: ${sessionManager.getAdminName() ?: "N/A"}"
                tvMobile.text = "Mobile: $adminMobile"
                tvEmail.text = "Email: ${sessionManager.getAdminEmail() ?: "N/A"}"
                tvRole.text = "Role: Admin"
                tvCreatedAt.text = "Created: N/A"
            }

        })
    }

    private fun logoutAdmin() {
        sessionManager.clearSession()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}