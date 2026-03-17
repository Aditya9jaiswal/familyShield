package com.example.familyshield.models

data class LocationModel(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var address: String = "",
    var updatedAt: Long = 0L
)
