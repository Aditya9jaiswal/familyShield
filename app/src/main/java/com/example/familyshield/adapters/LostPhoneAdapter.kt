package com.example.familyshield.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.databinding.ItemLostPhoneBinding
import com.example.familyshield.models.LostPhone

class LostPhoneAdapter(private val list: List<LostPhone>) :
    RecyclerView.Adapter<LostPhoneAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLostPhoneBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLostPhoneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val phone = list[position]
        holder.binding.tvImei.text = "IMEI: ${phone.imei}"
        holder.binding.tvStatus.text = "Status: ${phone.status}"
        holder.binding.tvLocation.text = "Last Location: ${phone.lastLocation}"
    }

    override fun getItemCount(): Int = list.size
}
