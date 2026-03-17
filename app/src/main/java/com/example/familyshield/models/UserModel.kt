package com.example.familyshield.models

data class UserModel(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var mobile: String = "",
    var password: String = "",
    var deviceId: String = "",
    var deviceName: String = "",
    var imei: String = "",
    var fcmToken: String = "",
    var isActive: Boolean = true,
    var isOnline: Boolean = false,
    var isLocked: Boolean = false,
    var factoryResetEnabled: Boolean = true,
    var sirenEnabled: Boolean = false,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var accuracy: Float = 0.0f,
    var address: String = "",
    var batteryLevel: Int = 0,
    var lastLocationUpdate: Long = 0L,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var lastLoginAt: Long = 0L,
    var createdBy: String = "",
    var role: String = "user"
)