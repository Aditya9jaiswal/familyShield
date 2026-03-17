package com.example.familyshield.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.familyshield.services.CommandListenerService
import com.example.familyshield.services.LocationService
import com.example.familyshield.utils.SessionManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "📱 Device boot completed")

            val sessionManager = SessionManager(context)

            if (sessionManager.isUserLoggedIn()) {
                Log.d("BootReceiver", "✅ User logged in, starting services")

                // Start CommandListenerService
                val commandIntent = Intent(context, CommandListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(commandIntent)
                } else {
                    context.startService(commandIntent)
                }

                // Start LocationService
                val locationIntent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(locationIntent)
                } else {
                    context.startService(locationIntent)
                }
            }
        }
    }
}