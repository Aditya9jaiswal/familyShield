package com.example.familyshield.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.familyshield.adapters.LostPhoneAdapter
import com.example.familyshield.databinding.ActivityLostPhoneBinding
import com.example.familyshield.models.LostPhone
import com.google.firebase.database.*

class LostPhoneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLostPhoneBinding
    private lateinit var database: DatabaseReference
    private val phoneList = ArrayList<LostPhone>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLostPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerLostPhones.layoutManager = LinearLayoutManager(this)

        database = FirebaseDatabase.getInstance()
            .reference.child("lostPhones")

        fetchLostPhones()
    }

    private fun fetchLostPhones() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                phoneList.clear()
                for (data in snapshot.children) {
                    val phone = data.getValue(LostPhone::class.java)
                    if (phone != null) phoneList.add(phone)
                }
                binding.recyclerLostPhones.adapter = LostPhoneAdapter(phoneList)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
