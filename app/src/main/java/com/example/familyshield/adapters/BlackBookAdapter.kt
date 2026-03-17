package com.example.familyshield.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.models.UserModel
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class BlackBookAdapter(
    private val users: List<UserModel>,
    private val adminId: String
) : RecyclerView.Adapter<BlackBookAdapter.UserViewHolder>() {

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvMobile: TextView = view.findViewById(R.id.tvUserMobile)
        val tvStatus: TextView = view.findViewById(R.id.tvUserStatus)
        val btnFactorReset: Button = view.findViewById(R.id.btnFactorReset)
        val btnLock: Button = view.findViewById(R.id.btnLock)
        val btnSiren: Button = view.findViewById(R.id.btnSiren)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_blackbook, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.name
        holder.tvMobile.text = user.mobile
        holder.tvStatus.text = if (user.isActive) "Online" else "Offline"

        fun sendCommand(type: String) {
            val cmdRef = FirebaseDatabase.getInstance()
                .getReference("admins/$adminId/users/${user.mobile}/commands")
                .push()

            val data = mapOf(
                "type" to type,
                "status" to "pending",
                "timestamp" to ServerValue.TIMESTAMP
            )
            cmdRef.setValue(data)
        }

        holder.btnFactorReset.setOnClickListener { sendCommand("FACTOR_RESET") }
        holder.btnLock.setOnClickListener { sendCommand("LOCK") }
        holder.btnSiren.setOnClickListener { sendCommand("SIREN") }
    }
}