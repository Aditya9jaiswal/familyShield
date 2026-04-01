package com.example.familyshield.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.models.Complaint
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComplaintAdapter(
    private val complaints: List<Complaint>,
    private val adminMobile: String,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvComplaintUser)
        val tvUserMobile: TextView = itemView.findViewById(R.id.tvComplaintUserMobile)
        val tvComplaintType: TextView = itemView.findViewById(R.id.tvComplaintType)
        val tvPhoneDetails: TextView = itemView.findViewById(R.id.tvPhoneDetails)
        val tvImeiDetails: TextView = itemView.findViewById(R.id.tvImeiDetails)
        val tvIncidentDetails: TextView = itemView.findViewById(R.id.tvIncidentDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvComplaintStatus)
        val tvDate: TextView = itemView.findViewById(R.id.tvComplaintDate)
        val tvComplaintDesc: TextView = itemView.findViewById(R.id.tvComplaintDesc)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)  // ✅ ADD LOCATION TEXTVIEW
        val btnResolve: MaterialButton = itemView.findViewById(R.id.btnResolveComplaint)
        val btnReject: MaterialButton = itemView.findViewById(R.id.btnRejectComplaint)
        val btnViewDetails: MaterialButton = itemView.findViewById(R.id.btnViewDetails)
        val btnViewMap: MaterialButton = itemView.findViewById(R.id.btnViewMap)  // ✅ ADD MAP BUTTON
        val viewDivider: View = itemView.findViewById(R.id.viewDivider)
        val policeSection: View = itemView.findViewById(R.id.policeSection)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_complaint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val complaint = complaints[position]

        holder.tvUserName.text = complaint.userName ?: "Unknown User"
        holder.tvUserMobile.text = "📱 ${complaint.userMobile}"
        setupComplaintType(holder, complaint)

        // ✅ SET LOCATION INFO
        setupLocationInfo(holder, complaint)

        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        holder.tvDate.text = "📅 ${dateFormat.format(Date(complaint.filedAt))}"
        setStatusStyle(holder.tvStatus, complaint.status)

        val isResolved = complaint.status == "resolved" || complaint.status == "rejected"
        holder.btnResolve.visibility = if (isResolved) View.GONE else View.VISIBLE
        holder.btnReject.visibility = if (isResolved) View.GONE else View.VISIBLE
        holder.viewDivider.visibility = if (isResolved) View.GONE else View.VISIBLE

        holder.btnResolve.setOnClickListener {
            showResolutionDialog(holder.itemView, complaint)
        }

        holder.btnReject.setOnClickListener {
            updateComplaintStatus(complaint, "rejected", holder.itemView.context)
        }

        holder.btnViewDetails.setOnClickListener {
            showFullComplaintDetails(holder.itemView, complaint)
        }

        // ✅ MAP BUTTON CLICK - OPEN LOCATION
        holder.btnViewMap?.setOnClickListener {
            if (complaint.lastLatitude != 0.0 && complaint.lastLongitude != 0.0) {
                openLocationInMap(holder.itemView.context, complaint.lastLatitude, complaint.lastLongitude)
            } else {
                Toast.makeText(holder.itemView.context, "📍 Location not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ NEW METHOD: SETUP LOCATION INFO
    private fun setupLocationInfo(holder: ViewHolder, complaint: Complaint) {
        if (complaint.lastLatitude != 0.0 && complaint.lastLongitude != 0.0) {
            val locationText = String.format("📍 Lat: %.6f, Lng: %.6f", complaint.lastLatitude, complaint.lastLongitude)
            holder.tvLocation.text = locationText
            holder.tvLocation.visibility = View.VISIBLE
            holder.btnViewMap?.visibility = View.VISIBLE
        } else {
            holder.tvLocation.visibility = View.GONE
            holder.btnViewMap?.visibility = View.GONE
        }
    }

    // ✅ NEW METHOD: OPEN LOCATION IN MAP
    private fun openLocationInMap(context: Context, latitude: Double, longitude: Double) {
        try {
            val uri = android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser map
            try {
                val uri = android.net.Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Cannot open maps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupComplaintType(holder: ViewHolder, complaint: Complaint) {
        when (complaint.complaintType) {
            "lost_phone" -> {
                holder.tvComplaintType.text = "📱 LOST/STOLEN PHONE REPORT"
                holder.tvComplaintType.setTextColor(holder.itemView.context.resources.getColor(android.R.color.holo_red_dark))
                holder.policeSection.visibility = View.VISIBLE
                holder.tvComplaintDesc.visibility = View.GONE

                holder.tvPhoneDetails.text = buildString {
                    append("📱 ${complaint.phoneBrand} ${complaint.phoneModel}\n")
                    if (complaint.phoneColor.isNotEmpty()) {
                        append("🎨 ${complaint.phoneColor}\n")
                    }
                    append("📞 SIM: ${complaint.simOperator} - ${complaint.simNumber}")
                }
                holder.tvPhoneDetails.visibility = View.VISIBLE

                holder.tvImeiDetails.text = buildString {
                    append("🔢 IMEI 1: ${complaint.imeiNumber}\n")
                    if (complaint.imeiNumber2.isNotEmpty()) {
                        append("🔢 IMEI 2: ${complaint.imeiNumber2}")
                    }
                }
                holder.tvImeiDetails.visibility = View.VISIBLE

                holder.tvIncidentDetails.text = buildString {
                    append("⚠️ ${complaint.incidentType}\n")
                    append("📍 ${complaint.incidentLocation}\n")
                    if (complaint.policeStation.isNotEmpty()) {
                        append("👮 Police Station: ${complaint.policeStation}\n")
                        append("📋 FIR No: ${complaint.firNumber}")
                    }
                }
                holder.tvIncidentDetails.visibility = View.VISIBLE
            }
            else -> {
                holder.tvComplaintType.text = "📝 GENERAL COMPLAINT"
                holder.tvComplaintType.setTextColor(holder.itemView.context.resources.getColor(android.R.color.darker_gray))
                holder.policeSection.visibility = View.GONE
                holder.tvPhoneDetails.visibility = View.GONE
                holder.tvImeiDetails.visibility = View.GONE
                holder.tvIncidentDetails.visibility = View.GONE
                holder.tvComplaintDesc.visibility = View.VISIBLE
                holder.tvComplaintDesc.text = complaint.description
            }
        }
    }

    private fun setStatusStyle(textView: TextView, status: String) {
        when (status.lowercase()) {
            "pending" -> {
                textView.text = "⏳ Pending"
                textView.setBackgroundResource(R.drawable.status_pending_bg)
                textView.setTextColor(textView.context.resources.getColor(android.R.color.holo_orange_dark))
            }
            "resolved" -> {
                textView.text = "✅ Resolved"
                textView.setBackgroundResource(R.drawable.status_resolved_bg)
                textView.setTextColor(textView.context.resources.getColor(android.R.color.holo_green_dark))
            }
            "rejected" -> {
                textView.text = "❌ Rejected"
                textView.setBackgroundResource(R.drawable.status_rejected_bg)
                textView.setTextColor(textView.context.resources.getColor(android.R.color.holo_red_dark))
            }
            else -> textView.text = status
        }
    }

    private fun showResolutionDialog(view: View, complaint: Complaint) {
        val dialogView = LayoutInflater.from(view.context).inflate(R.layout.dialog_resolve_complaint, null)
        val etRemarks = dialogView.findViewById<TextView>(R.id.etRemarks)
        val etFirNumber = dialogView.findViewById<TextView>(R.id.etFirNumber)
        val firSection = dialogView.findViewById<View>(R.id.firSection)

        if (complaint.complaintType == "lost_phone") {
            firSection.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(view.context)
            .setTitle("Resolve Complaint")
            .setView(dialogView)
            .setPositiveButton("Resolve") { _, _ ->
                val remarks = etRemarks.text.toString()
                val firNumber = etFirNumber.text.toString()
                updateComplaintStatus(complaint, "resolved", view.context, remarks, firNumber)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ✅ UPDATED: CORRECT PATH FOR COMPLAINT UPDATE
    private fun updateComplaintStatus(
        complaint: Complaint,
        newStatus: String,
        context: Context,
        remarks: String = "",
        firNumber: String = ""
    ) {
        val databaseRef = FirebaseDatabase.getInstance().reference
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "resolvedAt" to System.currentTimeMillis(),
            "resolvedBy" to adminMobile
        )

        if (remarks.isNotEmpty()) updates["remarks"] = remarks
        if (firNumber.isNotEmpty()) updates["firNumber"] = firNumber

        // ✅ CORRECT PATH: familyshield/admins/{adminMobile}/users/{userMobile}/complaints/{complaintId}
        databaseRef.child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")
            .child(complaint.userMobile)
            .child("complaints")
            .child(complaint.complaintId)
            .updateChildren(updates)
            .addOnSuccessListener {
                onStatusChanged.invoke()
                val message = when (newStatus) {
                    "resolved" -> "✅ Complaint resolved successfully"
                    "rejected" -> "❌ Complaint rejected"
                    else -> "Complaint $newStatus"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ✅ UPDATED: SHOW FULL COMPLAINT DETAILS WITH LOCATION
    private fun showFullComplaintDetails(view: View, complaint: Complaint) {
        val details = StringBuilder()
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        val dateFormatPolish = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        details.append("👤 USER INFORMATION\n")
        details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        details.append("Name: ${complaint.userName}\n")
        details.append("Mobile: ${complaint.userMobile}\n")
        details.append("Email: ${complaint.userEmail}\n\n")

        // ✅ ADD LOCATION SECTION
        details.append("📍 LOCATION INFORMATION\n")
        details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        if (complaint.lastLatitude != 0.0 && complaint.lastLongitude != 0.0) {
            details.append("Latitude: ${complaint.lastLatitude}\n")
            details.append("Longitude: ${complaint.lastLongitude}\n")
            details.append("Address: ${complaint.lastAddress ?: "Not available"}\n")
            details.append("Battery Level: ${complaint.batteryLevel}%\n")
            details.append("Last Update: ${dateFormat.format(Date(complaint.filedAt))}\n\n")
        } else {
            details.append("Location not available\n\n")
        }

        if (complaint.complaintType == "lost_phone") {
            details.append("📱 PHONE DETAILS\n")
            details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            details.append("Brand: ${complaint.phoneBrand}\n")
            details.append("Model: ${complaint.phoneModel}\n")
            details.append("Color: ${complaint.phoneColor}\n")
            details.append("IMEI 1: ${complaint.imeiNumber}\n")
            if (complaint.imeiNumber2.isNotEmpty()) {
                details.append("IMEI 2: ${complaint.imeiNumber2}\n")
            }
            details.append("SIM: ${complaint.simOperator} - ${complaint.simNumber}\n\n")

            details.append("⚠️ INCIDENT DETAILS\n")
            details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            details.append("Type: ${complaint.incidentType}\n")
            if (complaint.incidentDate > 0) {
                details.append("Date: ${dateFormatPolish.format(Date(complaint.incidentDate))}\n")
            }
            details.append("Location: ${complaint.incidentLocation}\n")
            details.append("Description: ${complaint.incidentDescription}\n\n")

            if (complaint.policeStation.isNotEmpty()) {
                details.append("👮 POLICE INFORMATION\n")
                details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
                details.append("Police Station: ${complaint.policeStation}\n")
                details.append("FIR Number: ${complaint.firNumber}\n")
                details.append("Officer: ${complaint.investigatingOfficer}\n")
                details.append("Contact: ${complaint.officerContact}\n\n")
            }
        } else {
            details.append("📝 COMPLAINT DESCRIPTION\n")
            details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            details.append(complaint.description)
            details.append("\n\n")
        }

        details.append("📅 FILED ON\n")
        details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        details.append(dateFormat.format(Date(complaint.filedAt)))

        if (complaint.resolvedAt > 0) {
            details.append("\n\n✅ RESOLVED ON\n")
            details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
            details.append(dateFormat.format(Date(complaint.resolvedAt)))
            if (complaint.remarks.isNotEmpty()) {
                details.append("\n\n📝 REMARKS\n")
                details.append("━━━━━━━━━━━━━━━━━━━━━━\n")
                details.append(complaint.remarks)
            }
        }

        AlertDialog.Builder(view.context)
            .setTitle("Complete Complaint Details")
            .setMessage(details.toString())
            .setPositiveButton("Close", null)
            .setNeutralButton("View on Map") { _, _ ->
                if (complaint.lastLatitude != 0.0 && complaint.lastLongitude != 0.0) {
                    openLocationInMap(view.context, complaint.lastLatitude, complaint.lastLongitude)
                } else {
                    Toast.makeText(view.context, "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Share") { _, _ ->
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, details.toString())
                    type = "text/plain"
                }
                view.context.startActivity(Intent.createChooser(shareIntent, "Share Complaint Details"))
            }
            .show()
    }

    override fun getItemCount() = complaints.size
}