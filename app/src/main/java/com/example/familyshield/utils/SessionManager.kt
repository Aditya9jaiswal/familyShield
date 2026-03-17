package com.example.familyshield.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("FamilyShieldPrefs", Context.MODE_PRIVATE)

    companion object {

        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_ROLE = "role"

        private const val KEY_ADMIN_MOBILE = "adminMobile"
        private const val KEY_ADMIN_NAME = "adminName"
        private const val KEY_ADMIN_EMAIL = "adminEmail"

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

        }.apply()
    }

    fun getAdminMobile(): String? {
        return prefs.getString(KEY_ADMIN_MOBILE, null)
    }

    fun getAdminName(): String? {
        return prefs.getString(KEY_ADMIN_NAME, null)
    }

    fun getAdminEmail(): String? {
        return prefs.getString(KEY_ADMIN_EMAIL, null)
    }

    fun isAdminLoggedIn(): Boolean {
        return isLoggedIn() && getRole() == "admin"
    }

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

        }.apply()
    }

    fun getUserMobile(): String? {
        return prefs.getString(KEY_USER_MOBILE, null)
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun getParentAdminMobile(): String? {
        return prefs.getString(KEY_PARENT_ADMIN_MOBILE, null)
    }

    fun isUserLoggedIn(): Boolean {
        return isLoggedIn() && getRole() == "user"
    }

    // ================= COMMON =================

    fun getRole(): String? {
        return prefs.getString(KEY_ROLE, null)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}