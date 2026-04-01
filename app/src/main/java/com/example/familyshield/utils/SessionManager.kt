package com.example.familyshield.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("FamilyShieldPrefs", Context.MODE_PRIVATE)

    companion object {
        // Session Keys
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_ROLE = "role"

        // Admin Keys
        private const val KEY_ADMIN_MOBILE = "adminMobile"
        private const val KEY_ADMIN_NAME = "adminName"
        private const val KEY_ADMIN_EMAIL = "adminEmail"

        // User Keys
        private const val KEY_USER_MOBILE = "userMobile"
        private const val KEY_USER_NAME = "userName"
        private const val KEY_USER_EMAIL = "userEmail"
        private const val KEY_PARENT_ADMIN_MOBILE = "parentAdminMobile"
    }

    // ================= ADMIN LOGIN =================

    fun saveAdminLogin(
        adminMobile: String,
        email: String,
        name: String
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_ROLE, "admin")
            putString(KEY_ADMIN_MOBILE, adminMobile)
            putString(KEY_ADMIN_NAME, name)
            putString(KEY_ADMIN_EMAIL, email)
            // Clear any existing user data
            remove(KEY_USER_MOBILE)
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_PARENT_ADMIN_MOBILE)
        }.apply()
    }

    fun getAdminMobile(): String? = prefs.getString(KEY_ADMIN_MOBILE, null)

    fun getAdminName(): String? = prefs.getString(KEY_ADMIN_NAME, null)

    fun getAdminEmail(): String? = prefs.getString(KEY_ADMIN_EMAIL, null)

    fun isAdminLoggedIn(): Boolean = isLoggedIn() && getRole() == "admin"

    // ================= USER LOGIN =================

    fun saveUserLogin(
        parentAdminMobile: String,
        userMobile: String,
        email: String,
        name: String
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_ROLE, "user")
            putString(KEY_PARENT_ADMIN_MOBILE, parentAdminMobile)
            putString(KEY_USER_MOBILE, userMobile)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            // Clear any existing admin data
            remove(KEY_ADMIN_MOBILE)
            remove(KEY_ADMIN_NAME)
            remove(KEY_ADMIN_EMAIL)
        }.apply()
    }

    fun getUserMobile(): String? = prefs.getString(KEY_USER_MOBILE, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun getParentAdminMobile(): String? = prefs.getString(KEY_PARENT_ADMIN_MOBILE, null)

    fun isUserLoggedIn(): Boolean = isLoggedIn() && getRole() == "user"

    // ================= COMMON METHODS =================

    fun getRole(): String? = prefs.getString(KEY_ROLE, null)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getCurrentUserMobile(): String? {
        return when (getRole()) {
            "admin" -> getAdminMobile()
            "user" -> getUserMobile()
            else -> null
        }
    }

    fun getCurrentUserName(): String? {
        return when (getRole()) {
            "admin" -> getAdminName()
            "user" -> getUserName()
            else -> null
        }
    }

    fun getCurrentUserEmail(): String? {
        return when (getRole()) {
            "admin" -> getAdminEmail()
            "user" -> getUserEmail()
            else -> null
        }
    }

    fun getCurrentUserType(): String? = getRole()

    fun isAdmin(): Boolean = getRole() == "admin"

    fun isUser(): Boolean = getRole() == "user"

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // ================= HELPER METHODS =================

    fun hasValidSession(): Boolean {
        if (!isLoggedIn()) return false
        return when (getRole()) {
            "admin" -> !getAdminMobile().isNullOrEmpty()
            "user" -> !getUserMobile().isNullOrEmpty() && !getParentAdminMobile().isNullOrEmpty()
            else -> false
        }
    }

    fun getSessionSummary(): String {
        return when (getRole()) {
            "admin" -> "Admin: ${getAdminName()} (${getAdminMobile()})"
            "user" -> "User: ${getUserName()} (${getUserMobile()}) under Admin: ${getParentAdminMobile()}"
            else -> "No active session"
        }
    }
}