package com.example.familyshield.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.example.familyshield.admin.MyDeviceAdminReceiver

class DevicePolicyHelper(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val component = ComponentName(context, MyDeviceAdminReceiver::class.java)

    // Check if Device Admin is Active
    private fun isAdminActive(): Boolean {
        return dpm.isAdminActive(component)
    }

    // Check if App is Device Owner
    private fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    // Lock Device
    fun lockDevice(): Boolean {
        return try {
            if (isAdminActive()) {
                dpm.lockNow()
                Log.d("FamilyShield", "🔒 Device locked successfully")
                true
            } else {
                Log.e("FamilyShield", "❌ Device admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e("FamilyShield", "❌ Lock error: ${e.message}")
            false
        }
    }

    // Factory Reset (Wipe Data)
    fun factoryReset(): Boolean {
        return try {
            if (isDeviceOwner()) {
                dpm.wipeData(0)
                Log.d("FamilyShield", "⚠️ Factory reset executed")
                true
            } else {
                Log.e("FamilyShield", "❌ App is not Device Owner")
                false
            }
        } catch (e: Exception) {
            Log.e("FamilyShield", "❌ Factory reset error: ${e.message}")
            false
        }
    }

    // Disable Factory Reset Option
    fun disableFactoryReset(): Boolean {
        return try {
            if (isDeviceOwner()) {
                dpm.addUserRestriction(component, UserManager.DISALLOW_FACTORY_RESET)
                Log.d("FamilyShield", "🚫 Factory reset disabled")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("FamilyShield", "❌ Disable reset error: ${e.message}")
            false
        }
    }

    // Enable Factory Reset Option
    fun enableFactoryReset(): Boolean {
        return try {
            if (isDeviceOwner()) {
                dpm.clearUserRestriction(component, UserManager.DISALLOW_FACTORY_RESET)
                Log.d("FamilyShield", "✅ Factory reset enabled")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("FamilyShield", "❌ Enable reset error: ${e.message}")
            false
        }
    }
}