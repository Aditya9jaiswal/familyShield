package com.example.familyshield.admin

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.databinding.ActivityAdminSignupBinding
import com.example.familyshield.utils.SessionManager
import com.google.firebase.database.FirebaseDatabase

class AdminSignupActivity : AppCompatActivity() {

    // ViewBinding use karo (recommended)
    private lateinit var binding: ActivityAdminSignupBinding
    private lateinit var sessionManager: SessionManager
    private var isSigningUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding inflate karo
        binding = ActivityAdminSignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back button click
        binding.ivBack.setOnClickListener {
            onBackPressed()
        }

        // Signup button click
        binding.btnSignup.setOnClickListener {
            if (!isSigningUp) {
                validateAndSignup()
            }
        }

        // Back to login button click
        binding.btnAdminLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Password strength checker (optional)
        binding.edtPassword.setOnKeyListener { _, _, _ ->
            checkPasswordStrength(binding.edtPassword.text.toString())
            false
        }
    }

    private fun validateAndSignup() {
        val name = binding.edtName.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val mobile = binding.edtMobile.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        // Validation
        when {
            name.isEmpty() -> {
                binding.inputName.error = "Name is required"
                binding.edtName.requestFocus()
            }
            email.isEmpty() -> {
                binding.inputEmail.error = "Email is required"
                binding.edtEmail.requestFocus()
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.inputEmail.error = "Enter valid email"
                binding.edtEmail.requestFocus()
            }
            mobile.isEmpty() -> {
                binding.inputMobile.error = "Mobile number is required"
                binding.edtMobile.requestFocus()
            }
            !mobile.matches(Regex("^[6-9]\\d{9}$")) -> {
                binding.inputMobile.error = "Enter valid 10-digit mobile number"
                binding.edtMobile.requestFocus()
            }
            password.isEmpty() -> {
                binding.inputPassword.error = "Password is required"
                binding.edtPassword.requestFocus()
            }
            password.length < 6 -> {
                binding.inputPassword.error = "Password must be at least 6 characters"
                binding.edtPassword.requestFocus()
            }
            else -> {
                // Clear errors
                binding.inputName.error = null
                binding.inputEmail.error = null
                binding.inputMobile.error = null
                binding.inputPassword.error = null

                // Check if admin already exists
                checkAdminExists(mobile, name, email, password)
            }
        }
    }

    private fun checkAdminExists(mobile: String, name: String, email: String, password: String) {
        showLoading(true)

        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(mobile)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    showLoading(false)
                    Toast.makeText(this, "Admin with this mobile already exists!", Toast.LENGTH_LONG).show()
                } else {
                    // Check if email already exists
                    checkEmailExists(mobile, name, email, password)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkEmailExists(mobile: String, name: String, email: String, password: String) {
        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .get()
            .addOnSuccessListener { snapshot ->
                var emailExists = false

                for (adminSnap in snapshot.children) {
                    val existingEmail = adminSnap.child("email").getValue(String::class.java)
                    if (email.equals(existingEmail, ignoreCase = true)) {
                        emailExists = true
                        break
                    }
                }

                if (emailExists) {
                    showLoading(false)
                    binding.inputEmail.error = "Email already registered"
                } else {
                    // Proceed with signup
                    performSignup(name, email, mobile, password)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performSignup(name: String, email: String, mobile: String, password: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val adminData = HashMap<String, Any>()
        adminData["name"] = name
        adminData["email"] = email.lowercase() // Store email in lowercase
        adminData["mobile"] = mobile
        adminData["password"] = password // In production, hash this password!
        adminData["deviceId"] = deviceId
        adminData["role"] = "admin"
        adminData["isActive"] = true
        adminData["createdAt"] = System.currentTimeMillis()

        FirebaseDatabase.getInstance().reference
            .child("familyshield")
            .child("admins")
            .child(mobile)
            .setValue(adminData)
            .addOnSuccessListener {
                // Save to session
                sessionManager.saveAdminLogin(mobile, email.lowercase(), name)

                showLoading(false)
                Toast.makeText(this, "✅ Admin Created Successfully!", Toast.LENGTH_SHORT).show()

                // Navigate to Login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "❌ Signup Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkPasswordStrength(password: String) {
        val strength1 = binding.strength1
        val strength2 = binding.strength2
        val strength3 = binding.strength3

        when {
            password.isEmpty() -> {
                strength1.background.setTint(resources.getColor(android.R.color.darker_gray))
                strength2.background.setTint(resources.getColor(android.R.color.darker_gray))
                strength3.background.setTint(resources.getColor(android.R.color.darker_gray))
            }
            password.length < 6 -> {
                strength1.background.setTint(resources.getColor(android.R.color.holo_red_dark))
                strength2.background.setTint(resources.getColor(android.R.color.darker_gray))
                strength3.background.setTint(resources.getColor(android.R.color.darker_gray))
                binding.tvPasswordHint.text = "Weak password"
            }
            password.length in 6..8 -> {
                strength1.background.setTint(resources.getColor(android.R.color.holo_orange_dark))
                strength2.background.setTint(resources.getColor(android.R.color.holo_orange_dark))
                strength3.background.setTint(resources.getColor(android.R.color.darker_gray))
                binding.tvPasswordHint.text = "Medium password"
            }
            else -> {
                strength1.background.setTint(resources.getColor(android.R.color.holo_green_dark))
                strength2.background.setTint(resources.getColor(android.R.color.holo_green_dark))
                strength3.background.setTint(resources.getColor(android.R.color.holo_green_dark))
                binding.tvPasswordHint.text = "Strong password"
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        isSigningUp = isLoading
        binding.btnSignup.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnSignup.text = if (isLoading) "Creating Account..." else "Create Admin Account"
    }
}