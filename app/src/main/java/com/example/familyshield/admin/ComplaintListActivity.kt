package com.example.familyshield.admin

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.familyshield.adapters.ComplaintAdapter
import com.example.familyshield.databinding.ActivityComplaintListBinding
import com.example.familyshield.models.Complaint
import com.google.firebase.database.*

class ComplaintListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComplaintListBinding
    private lateinit var database: DatabaseReference
    private val complaintList = ArrayList<Complaint>()
    private var userMobile: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComplaintListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get user mobile from intent
        userMobile = intent.getStringExtra("userMobile") ?: ""

        binding.recyclerComplaints.layoutManager = LinearLayoutManager(this)

        database = FirebaseDatabase.getInstance()
            .reference.child("complaints")

        fetchComplaints()
    }

    private fun fetchComplaints() {
        binding.progressBar.visibility = View.VISIBLE

        database.orderByChild("userMobile")
            .equalTo(userMobile)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    complaintList.clear()

                    for (data in snapshot.children) {
                        val complaint = data.getValue(Complaint::class.java)
                        if (complaint != null) {
                            complaintList.add(complaint)
                        }
                    }

                    binding.recyclerComplaints.adapter =
                        ComplaintAdapter(complaintList)

                    binding.tvNoComplaints.visibility =
                        if (complaintList.isEmpty()) View.VISIBLE else View.GONE

                    binding.progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = View.GONE
                }
            })
    }
}
