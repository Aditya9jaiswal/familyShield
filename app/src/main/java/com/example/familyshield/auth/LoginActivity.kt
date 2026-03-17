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

        // ================= Login Button =================
        binding.btnLogin.setOnClickListener {
            login()
        }

        // ================= New User Text =================
        binding.tvNewUser.setOnClickListener {
            showUserTypeDialog()
        }

        // ================= Forgot Password =================
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
            // TODO: Redirect to ForgotPasswordActivity
        }
    }

    // ---------------- Show Signup Options ----------------
    private fun showUserTypeDialog() {
        val options = arrayOf("Admin", "Family Member")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select User Type")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AdminSignupActivity::class.java))
                    1 -> startActivity(Intent(this, UserSignupActivity::class.java))
                }
            }
            .setCancelable(true)
            .show()
    }

    // ---------------- Login Logic ----------------
    private fun login() {
        val input = binding.edtEmailMobile.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (input.isEmpty() || password.isEmpty()) {
            toast("Enter email/mobile and password")
            return
        }

        checkAdminLogin(input, password)
    }

    private fun checkAdminLogin(input: String, password: String) {
        val adminsRef = database.child("admins")

        adminsRef.child(input).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val storedPassword = snapshot.child("password").getValue(String::class.java)
                if (password == storedPassword) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""

                    sessionManager.saveAdminLogin(input, email, name)
                    toast("Admin Login Success")
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    finish()
                } else {
                    toast("Invalid password")
                }
            } else {
                searchAdminByEmail(input, password)
            }
        }.addOnFailureListener {
            toast("Database error")
        }
    }

    private fun searchAdminByEmail(input: String, password: String) {
        database.child("admins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (adminSnap in snapshot.children) {
                        val email = adminSnap.child("email").getValue(String::class.java) ?: ""
                        val storedPassword = adminSnap.child("password").getValue(String::class.java) ?: ""
                        val mobile = adminSnap.key ?: ""
                        val name = adminSnap.child("name").getValue(String::class.java) ?: "Admin"

                        if (input == email && password == storedPassword) {
                            sessionManager.saveAdminLogin(mobile, email, name)
                            toast("Admin Login Success")
                            startActivity(Intent(this@LoginActivity, AdminDashboardActivity::class.java))
                            finish()
                            return
                        }
                    }
                    checkUserLogin(input, password)
                }

                override fun onCancelled(error: DatabaseError) {
                    toast("Database error")
                }
            })
    }

    private fun checkUserLogin(input: String, password: String) {
        database.child("admins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (adminSnap in snapshot.children) {
                        val adminMobile = adminSnap.key ?: ""
                        val users = adminSnap.child("users")
                        for (userSnap in users.children) {
                            val email = userSnap.child("email").getValue(String::class.java) ?: ""
                            val mobile = userSnap.child("mobile").getValue(String::class.java) ?: ""
                            val name = userSnap.child("name").getValue(String::class.java) ?: "User"
                            val storedPassword = userSnap.child("password").getValue(String::class.java) ?: ""

                            if ((input == email || input == mobile) && password == storedPassword) {
                                sessionManager.saveUserLogin(adminMobile, mobile, email, name)
                                toast("User Login Success")
                                startActivity(Intent(this@LoginActivity, FamilyDashboardActivity::class.java))
                                finish()
                                return
                            }
                        }
                    }
                    toast("Invalid login details")
                }

                override fun onCancelled(error: DatabaseError) {
                    toast("Database error")
                }
            })
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}