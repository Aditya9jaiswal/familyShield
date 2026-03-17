package com.example.familyshield.models

data class Complaint(
    var complaintId: String = "",
    var userId: String = "",
    var userMobile: String = "",
    var userName: String = "",
    var description: String = "",
    var contactNumber: String = "",
    var adminMobile: String = "",
    var status: String = "filed",
    var filedAt: Long = 0L,
    var resolvedAt: Long = 0L,
    var resolution: String = "",
    var deviceId: String = "",
    var lastLatitude: Double = 0.0,
    var lastLongitude: Double = 0.0,
    var lastAddress: String = ""
)