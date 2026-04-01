package com.example.familyshield.models

import java.io.Serializable

data class Complaint(
    // Basic fields
    var complaintId: String = "",
    var userId: String = "",
    var userMobile: String = "",
    var userName: String = "",
    var userEmail: String = "",
    var description: String = "",
    var contactNumber: String = "",
    var adminMobile: String = "",
    var status: String = "pending",  // pending, resolved, rejected
    var filedAt: Long = 0,

    // Device info
    var deviceId: String = "",
    var deviceName: String = "",
    var phoneModel: String = "",
    var imeiNumber: String = "",
    var imeiNumber2: String = "",  // For dual SIM

    // Location info
    var lastLatitude: Double = 0.0,
    var lastLongitude: Double = 0.0,
    var lastAddress: String = "",
    var batteryLevel: Int = 0,

    // Complaint type specific
    var complaintType: String = "general",  // "lost_phone" or "general"
    var phoneBrand: String = "",
    var phoneColor: String = "",
    var simOperator: String = "",
    var simNumber: String = "",
    var incidentType: String = "",  // "lost" or "stolen"
    var incidentDate: Long = 0,
    var incidentLocation: String = "",
    var incidentDescription: String = "",

    // Police info
    var policeStation: String = "",
    var firNumber: String = "",
    var investigatingOfficer: String = "",
    var officerContact: String = "",

    // Resolution info
    var resolvedAt: Long = 0,
    var resolvedBy: String = "",
    var remarks: String = "",
    var isRead: Boolean = false
) : Serializable