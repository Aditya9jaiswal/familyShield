package com.example.familyshield.models
data class AdminModel(

    var action: String = "",
    var timestamp: Long = 0L,

    var name: String = "",
    var email: String = "",
    var mobile: String = "",
    var password: String = "",
    var deviceId: String = "",
    var role: String = "admin",
    var isActive: Boolean = true,
    var createdAt: Long = 0L,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var address: String = "",
    var locationUpdatedAt: Long = 0L
)
