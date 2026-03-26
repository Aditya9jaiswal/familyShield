package com.example.familyshield.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.familyshield.admin.MyDeviceAdminReceiver

class DevicePolicyHelper(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val component = ComponentName(context, MyDeviceAdminReceiver::class.java)

    companion object {
        private const val TAG = "DevicePolicyHelper"
    }

    private fun isAdminActive(): Boolean {
        return try {
            dpm.isAdminActive(component)
        } catch (e: Exception) {
            Log.e(TAG, "Admin check error: ${e.message}")
            false
        }
    }

    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Device owner check error: ${e.message}")
            false
        }
    }

    // 🔒 Lock Device
    fun lockDevice(): Boolean {
        return try {
            if (isAdminActive()) {
                dpm.lockNow()
                Log.d(TAG, "🔒 Device locked")
                true
            } else {
                Log.e(TAG, "❌ Device admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lock error: ${e.message}")
            false
        }
    }

    // ⚠️ Factory Reset
    fun factoryReset(): Boolean {
        return try {
            if (isDeviceOwner()) {
                dpm.wipeData(0)
                Log.d(TAG, "⚠️ Factory reset executed")
                true
            } else {
                Log.e(TAG, "❌ Not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reset error: ${e.message}")
            false
        }
    }

    // 🚫 Disable Factory Reset - Unified method
    fun disableFactoryReset(): Boolean {
        return try {
            if (!isAdminActive()) {
                Log.e(TAG, "❌ Device admin not active")
                return false
            }

            // Primary method: User restriction (works on most devices)
            dpm.addUserRestriction(component, UserManager.DISALLOW_FACTORY_RESET)
            Log.d(TAG, "✅ Factory reset disabled via user restriction")

            // Secondary: Additional security for newer Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    dpm.addUserRestriction(component, UserManager.DISALLOW_SAFE_BOOT)
                    Log.d(TAG, "✅ Safe boot disabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Safe boot restriction not supported: ${e.message}")
                }
            }

            true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error disabling factory reset: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Disable error: ${e.message}")
            false
        }
    }

    // ✅ Enable Factory Reset - Unified method
    fun enableFactoryReset(): Boolean {
        return try {
            if (!isAdminActive()) {
                Log.e(TAG, "❌ Device admin not active")
                return false
            }

            // Primary method: Clear user restriction
            dpm.clearUserRestriction(component, UserManager.DISALLOW_FACTORY_RESET)
            Log.d(TAG, "✅ Factory reset enabled via user restriction")

            // Secondary: Restore safe boot if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    dpm.clearUserRestriction(component, UserManager.DISALLOW_SAFE_BOOT)
                    Log.d(TAG, "✅ Safe boot re-enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Safe boot restore not supported: ${e.message}")
                }
            }

            true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security error enabling factory reset: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Enable error: ${e.message}")
            false
        }
    }

    // 🔐 Extra Security (optional)
    fun applyExtraSecurity() {
        if (!isAdminActive()) return

        try {
            dpm.addUserRestriction(component, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(component, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.addUserRestriction(component, UserManager.DISALLOW_REMOVE_USER)
            Log.d(TAG, "🔐 Extra security applied")
        } catch (e: Exception) {
            Log.e(TAG, "Security error: ${e.message}")
        }
    }
}