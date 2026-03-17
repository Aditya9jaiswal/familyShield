package com.example.familyshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.familyshield.admin.AdminDashboardActivity
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.family.FamilyDashboardActivity
import com.example.familyshield.utils.SessionManager

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var logo: ImageView
    private lateinit var rootLayout: ConstraintLayout

    companion object {
        private const val SPLASH_DELAY = 2000L // 2 seconds
        private const val PERMISSION_REQUEST_CODE = 101
    }

    // Only location permission for now
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sessionManager = SessionManager(this)
        logo = findViewById(R.id.imageView)
        rootLayout = findViewById(R.id.main)

        // ================= Simple Fade-in Animation =================
        val fadeIn = AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = SPLASH_DELAY
        fadeIn.fillAfter = true
        logo.startAnimation(fadeIn)

        // ================= Optional: dim background =================
        rootLayout.background.alpha = 200

        // ================= Start flow after splash =================
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndNavigate()
        }, SPLASH_DELAY)
    }

    // ======================= PERMISSION CHECK =======================
    private fun checkPermissionsAndNavigate() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            navigateNext()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                navigateNext()
            } else {
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("FamilyShield needs location permissions to work properly.")
            .setPositiveButton("Retry") { _, _ -> checkPermissionsAndNavigate() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    // ======================= NAVIGATION =======================
    private fun navigateNext() {
        when {
            sessionManager.isAdminLoggedIn() -> {
                startActivity(Intent(this, AdminDashboardActivity::class.java))
            }
            sessionManager.isUserLoggedIn() -> {
                startActivity(Intent(this, FamilyDashboardActivity::class.java))
            }
            else -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        finish()
    }
}