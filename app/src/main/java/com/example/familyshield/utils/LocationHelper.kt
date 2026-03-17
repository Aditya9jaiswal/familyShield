package com.example.familyshield.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object LocationHelper {

    @SuppressLint("MissingPermission")
    fun sendCurrentLocation(context: Context) {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->

            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude

                Log.d("LocationHelper", "Lat: $lat, Lng: $lng")

                // Upload to Firebase
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val database = FirebaseDatabase.getInstance().getReference("locations")
                    val map = HashMap<String, Any>()
                    map["latitude"] = lat
                    map["longitude"] = lng
                    map["time"] = System.currentTimeMillis()

                    database.child(userId).setValue(map)
                        .addOnSuccessListener {
                            Log.d("LocationHelper", "Location uploaded successfully")
                        }
                        .addOnFailureListener {
                            Log.d("LocationHelper", "Failed to upload location")
                        }
                }

            } else {
                Log.d("LocationHelper", "Location is null")
            }

        }.addOnFailureListener {
            Log.d("LocationHelper", "Failed to get location: ${it.message}")
        }
    }
}