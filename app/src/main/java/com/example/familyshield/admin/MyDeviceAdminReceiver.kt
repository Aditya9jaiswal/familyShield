package com.example.familyshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "✅ Device Admin Enabled")
        Toast.makeText(context, "FamilyShield Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "❌ Device Admin Disabled")
        Toast.makeText(context, "FamilyShield Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d("DeviceAdmin", "⚠️ Wrong password attempt detected")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d("DeviceAdmin", "✅ Password entered correctly")
    }
}