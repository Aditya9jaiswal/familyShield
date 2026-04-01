package com.example.familyshield.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.admin.AdminDashboardActivity
import com.example.familyshield.admin.AdminSignupActivity
import com.example.familyshield.databinding.ActivityLoginBinding
import com.example.familyshield.family.FamilyDashboardActivity
import com.example.familyshield.family.UserSignupActivity
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var database: DatabaseReference
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference.child("familyshield")
        sessionManager = SessionManager(this)

        // Check if already logged in
        if (sessionManager.isLoggedIn() && sessionManager.hasValidSession()) {
            navigateBasedOnUserType()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            login()
        }

        binding.tvNewUser.setOnClickListener {
            showUserTypeDialog()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showUserTypeDialog() {
        val options = arrayOf("Admin", "Family Member")
        AlertDialog.Builder(this)
            .setTitle("Select User Type")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AdminSignupActivity::class.java))
                    1 -> startActivity(Intent(this, UserSignupActivity::class.java))
                }
            }
            .show()
    }

    private fun showForgotPasswordDialog() {
        AlertDialog.Builder(this)
            .setTitle("Forgot Password?")
            .setMessage("Please contact your administrator to reset your password.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun login() {
        val input = binding.edtEmailMobile.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        // Validate input
        when {
            input.isEmpty() -> {
                binding.edtEmailMobile.error = "Enter email or mobile number"
                binding.edtEmailMobile.requestFocus()
                return
            }
            password.isEmpty() -> {
                binding.edtPassword.error = "Enter password"
                binding.edtPassword.requestFocus()
                return
            }
            password.length < 6 -> {
                binding.edtPassword.error = "Password must be at least 6 characters"
                binding.edtPassword.requestFocus()
                return
            }
        }

        // Show loading state
        setLoadingState(true)

        // Check admin login first
        checkAdminLogin(input, password)
    }

    private fun checkAdminLogin(input: String, password: String) {
        // First check by mobile number (direct key)
        database.child("admins").child(input).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    validateAdminPassword(snapshot, password, input)
                } else {
                    // Check admin by email
                    checkAdminByEmail(input, password)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Database error: ${error.message}")
                setLoadingState(false)
            }
        })
    }

    // ✅ FIXED: Password ko String aur Long dono format me read karta hai
    private fun validateAdminPassword(snapshot: DataSnapshot, password: String, mobile: String) {
        try {
            // Password ko safely read karo - String ya Long dono support
            val storedPassword = when (val value = snapshot.child("password").getValue()) {
                is String -> value
                is Long -> value.toString()
                is Int -> value.toString()
                else -> null
            }

            if (password == storedPassword) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                val email = snapshot.child("email").getValue(String::class.java) ?: ""

                sessionManager.saveAdminLogin(mobile, email, name)
                showSuccess("Admin Login Successful")
                navigateToAdminDashboard()
            } else {
                showError("Invalid password")
                setLoadingState(false)
            }
        } catch (e: Exception) {
            showError("Error validating password")
            setLoadingState(false)
        }
    }

    private fun checkAdminByEmail(input: String, password: String) {
        database.child("admins")
            .orderByChild("email")
            .equalTo(input)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (adminSnap in snapshot.children) {
                            try {
                                // ✅ Password ko safely read karo
                                val storedPassword = when (val value = adminSnap.child("password").getValue()) {
                                    is String -> value
                                    is Long -> value.toString()
                                    is Int -> value.toString()
                                    else -> ""
                                }

                                if (password == storedPassword) {
                                    val mobile = adminSnap.key ?: ""
                                    val name = adminSnap.child("name").getValue(String::class.java) ?: "Admin"
                                    val email = adminSnap.child("email").getValue(String::class.java) ?: ""

                                    sessionManager.saveAdminLogin(mobile, email, name)
                                    showSuccess("Admin Login Successful")
                                    navigateToAdminDashboard()
                                    return
                                }
                            } catch (e: Exception) {
                                // Skip this admin, continue to next
                            }
                        }
                        showError("Invalid password")
                        setLoadingState(false)
                    } else {
                        // Not an admin, check user login
                        checkUserLogin(input, password)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Database error: ${error.message}")
                    setLoadingState(false)
                }
            })
    }

    private fun checkUserLogin(input: String, password: String) {
        database.child("admins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var found = false

                    for (adminSnap in snapshot.children) {
                        val adminMobile = adminSnap.key ?: ""
                        val users = adminSnap.child("users")

                        for (userSnap in users.children) {
                            try {
                                val email = userSnap.child("email").getValue(String::class.java) ?: ""
                                val mobile = userSnap.child("mobile").getValue(String::class.java) ?: ""

                                // ✅ Password ko safely read karo
                                val storedPassword = when (val value = userSnap.child("password").getValue()) {
                                    is String -> value
                                    is Long -> value.toString()
                                    is Int -> value.toString()
                                    else -> ""
                                }

                                if ((input == email || input == mobile) && password == storedPassword) {
                                    val name = userSnap.child("name").getValue(String::class.java) ?: "User"

                                    sessionManager.saveUserLogin(adminMobile, mobile, email, name)
                                    showSuccess("User Login Successful")
                                    navigateToUserDashboard()
                                    found = true
                                    return
                                }
                            } catch (e: Exception) {
                                // Skip this user, continue to next
                            }
                        }
                    }

                    if (!found) {
                        showError("Invalid email/mobile or password")
                        setLoadingState(false)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Database error: ${error.message}")
                    setLoadingState(false)
                }
            })
    }

    private fun navigateBasedOnUserType() {
        when {
            sessionManager.isAdmin() -> navigateToAdminDashboard()
            sessionManager.isUser() -> navigateToUserDashboard()
            else -> {
                setLoadingState(false)
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToAdminDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToUserDashboard() {
        val intent = Intent(this, FamilyDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Logging in..." else "Login"
        binding.edtEmailMobile.isEnabled = !isLoading
        binding.edtPassword.isEnabled = !isLoading
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}