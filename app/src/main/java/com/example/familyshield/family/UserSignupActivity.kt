package com.example.familyshield.family

import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.R
import com.example.familyshield.models.UserModel  // ✅ Import UserModel
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class UserSignupActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etMobile: EditText
    private lateinit var etPassword: EditText
    private lateinit var etAdminMobile: EditText
    private lateinit var btnSignup: Button

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_signup)

        initViews()
        setupDatabase()
        setupClickListeners()
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etMobile = findViewById(R.id.etMobile)
        etPassword = findViewById(R.id.etPassword)
        etAdminMobile = findViewById(R.id.etAdminMobile)
        btnSignup = findViewById(R.id.btnSignup)
    }

    private fun setupDatabase() {
        database = FirebaseDatabase.getInstance().reference
    }

    private fun setupClickListeners() {
        btnSignup.setOnClickListener {
            validateAndSignup()
        }
    }

    private fun validateAndSignup() {
        val name = etName.text.toString().trim()
        val mobile = etMobile.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val adminMobile = etAdminMobile.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            etName.error = "Name required"
            etName.requestFocus()
            return
        }

        if (mobile.isEmpty() || mobile.length != 10) {
            etMobile.error = "Valid 10-digit mobile required"
            etMobile.requestFocus()
            return
        }

        if (password.isEmpty() || password.length < 4) {
            etPassword.error = "Password must be at least 4 characters"
            etPassword.requestFocus()
            return
        }

        if (adminMobile.isEmpty() || adminMobile.length != 10) {
            etAdminMobile.error = "Valid admin mobile required"
            etAdminMobile.requestFocus()
            return
        }

        // Check if admin exists
        checkAdminExists(adminMobile, name, mobile, password)
    }

    private fun checkAdminExists(adminMobile: String, name: String, mobile: String, password: String) {
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // Admin exists - proceed with signup
                    performSignup(adminMobile, name, mobile, password)
                } else {
                    Toast.makeText(this, "Admin not found with this mobile", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking admin", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performSignup(adminMobile: String, name: String, mobile: String, password: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val user = UserModel(
            uid = mobile,
            name = name,
            email = "",  // Email optional for users
            mobile = mobile,
            password = password,  // In production, hash this!
            deviceId = deviceId,
            deviceName = android.os.Build.MODEL,
            isActive = true,
            isOnline = false,
            isLocked = false,
            factoryResetEnabled = true,
            sirenEnabled = false,
            latitude = 0.0,
            longitude = 0.0,
            address = "",
            batteryLevel = 0,
            createdAt = System.currentTimeMillis(),
            createdBy = adminMobile,
            role = "user"
        )

        // ✅ SAVE TO FIREBASE
        database.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(mobile)
            .setValue(user)  // ✅ Direct UserModel object save
            .addOnSuccessListener {
                Toast.makeText(this, "✅ User Created Successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Signup Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}