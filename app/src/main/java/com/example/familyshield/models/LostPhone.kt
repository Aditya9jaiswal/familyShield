package com.example.familyshield.models

data class LostPhone(
    val imei: String = "",
    val userId: String = "",
    val status: String = "Reported",
    val lastLocation: String = ""
)
