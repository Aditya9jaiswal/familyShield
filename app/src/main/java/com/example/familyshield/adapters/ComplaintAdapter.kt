package com.example.familyshield.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.models.Complaint
import java.text.SimpleDateFormat
import java.util.*

class ComplaintAdapter(private val complaints: List<Complaint>) :
    RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUser: TextView = itemView.findViewById(R.id.tvComplaintUser)
        val tvDesc: TextView = itemView.findViewById(R.id.tvComplaintDesc)
        val tvStatus: TextView = itemView.findViewById(R.id.tvComplaintStatus)
        val tvDate: TextView = itemView.findViewById(R.id.tvComplaintDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_complaint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = complaints[position]
        holder.tvUser.text = c.userName
        holder.tvDesc.text = c.description
        holder.tvStatus.text = "Status: ${c.status}"

        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(c.filedAt))
        holder.tvDate.text = date
    }

    override fun getItemCount() = complaints.size
}