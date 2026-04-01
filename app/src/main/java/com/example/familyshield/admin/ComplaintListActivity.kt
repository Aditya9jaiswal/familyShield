package com.example.familyshield.admin

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.adapters.ComplaintAdapter
import com.example.familyshield.models.Complaint
import com.example.familyshield.utils.SessionManager
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ComplaintListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoComplaints: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var sessionManager: SessionManager
    private lateinit var databaseRef: DatabaseReference
    private lateinit var adminMobile: String

    private val allComplaintsList = mutableListOf<Complaint>()
    private var filteredComplaintsList = mutableListOf<Complaint>()
    private lateinit var adapter: ComplaintAdapter
    private var currentFilter = "all"

    private var complaintsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complaint_list)

        sessionManager = SessionManager(this)
        adminMobile = intent.getStringExtra("adminMobile") ?: sessionManager.getAdminMobile() ?: run {
            Toast.makeText(this, "Admin not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        setupTabLayout()
        setupRecyclerView()

        // Directly fetch complaints from correct path
        loadComplaints()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)
        tvNoComplaints = findViewById(R.id.tvNoComplaints)
        progressBar = findViewById(R.id.progressBar)

        databaseRef = FirebaseDatabase.getInstance().reference
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "User Complaints"
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }
    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("All"))
        tabLayout.addTab(tabLayout.newTab().setText("Pending"))
        tabLayout.addTab(tabLayout.newTab().setText("Resolved"))
        tabLayout.addTab(tabLayout.newTab().setText("Rejected"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = when (tab?.position) {
                    0 -> "all"
                    1 -> "pending"
                    2 -> "resolved"
                    3 -> "rejected"
                    else -> "all"
                }
                filterComplaints()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ComplaintAdapter(filteredComplaintsList, adminMobile) {
            loadComplaints() // Refresh on update
        }
        recyclerView.adapter = adapter
    }

    // ✅ CORRECT METHOD: Fetch complaints from admin's users
    private fun loadComplaints() {
        showLoading(true)
        allComplaintsList.clear()

        // Correct path: familyshield/admins/{adminMobile}/users/
        val usersRef = databaseRef
            .child("familyshield")
            .child("admins")
            .child(adminMobile)
            .child("users")

        complaintsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allComplaintsList.clear()

                if (!snapshot.exists()) {
                    showLoading(false)
                    tvNoComplaints.visibility = View.VISIBLE
                    tvNoComplaints.text = "No users found"
                    return
                }

                // Iterate through all users under this admin
                for (userSnapshot in snapshot.children) {
                    // Get complaints node for each user
                    val complaintsSnapshot = userSnapshot.child("complaints")

                    for (complaintSnapshot in complaintsSnapshot.children) {
                        try {
                            val complaint = complaintSnapshot.getValue(Complaint::class.java)
                            if (complaint != null) {
                                // Add complaint ID to the object if needed
                                if (complaint.complaintId.isEmpty()) {
                                    complaint.complaintId = complaintSnapshot.key ?: ""
                                }
                                allComplaintsList.add(complaint)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Sort by filed date (newest first)
                allComplaintsList.sortByDescending { it.filedAt }

                // Apply current filter
                filterComplaints()
                showLoading(false)

                // Show empty state if no complaints
                if (allComplaintsList.isEmpty()) {
                    tvNoComplaints.visibility = View.VISIBLE
                    tvNoComplaints.text = "No complaints filed"
                    recyclerView.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                Toast.makeText(this@ComplaintListActivity,
                    "Failed to load complaints: ${error.message}",
                    Toast.LENGTH_SHORT).show()
                tvNoComplaints.visibility = View.VISIBLE
                tvNoComplaints.text = "Error: ${error.message}"
            }
        }

        usersRef.addValueEventListener(complaintsListener!!)
    }

    private fun filterComplaints() {
        filteredComplaintsList.clear()

        when (currentFilter) {
            "all" -> filteredComplaintsList.addAll(allComplaintsList)
            "pending" -> filteredComplaintsList.addAll(allComplaintsList.filter { it.status == "pending" })
            "resolved" -> filteredComplaintsList.addAll(allComplaintsList.filter { it.status == "resolved" })
            "rejected" -> filteredComplaintsList.addAll(allComplaintsList.filter { it.status == "rejected" })
        }

        adapter.notifyDataSetChanged()

        if (filteredComplaintsList.isEmpty()) {
            tvNoComplaints.visibility = View.VISIBLE
            tvNoComplaints.text = when (currentFilter) {
                "all" -> "No complaints found"
                "pending" -> "No pending complaints"
                "resolved" -> "No resolved complaints"
                "rejected" -> "No rejected complaints"
                else -> "No complaints"
            }
            recyclerView.visibility = View.GONE
        } else {
            tvNoComplaints.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show && filteredComplaintsList.isNotEmpty()) {
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.complaint_menu, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchComplaints(query ?: "")
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                searchComplaints(newText ?: "")
                return true
            }
        })

        return true
    }

    private fun searchComplaints(query: String) {
        if (query.isEmpty()) {
            filterComplaints()
            return
        }

        val filtered = allComplaintsList.filter { complaint ->
            complaint.userName?.contains(query, ignoreCase = true) == true ||
                    complaint.userMobile?.contains(query) == true ||
                    complaint.description?.contains(query, ignoreCase = true) == true ||
                    complaint.phoneModel?.contains(query, ignoreCase = true) == true ||
                    complaint.imeiNumber?.contains(query) == true ||
                    complaint.complaintId?.contains(query, ignoreCase = true) == true
        }

        filteredComplaintsList.clear()
        filteredComplaintsList.addAll(filtered)
        adapter.notifyDataSetChanged()

        if (filteredComplaintsList.isEmpty()) {
            tvNoComplaints.visibility = View.VISIBLE
            tvNoComplaints.text = "No complaints matching '$query'"
            recyclerView.visibility = View.GONE
        } else {
            tvNoComplaints.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadComplaints()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        complaintsListener?.let {
            databaseRef.child("familyshield")
                .child("admins")
                .child(adminMobile)
                .child("users")
                .removeEventListener(it)
        }
    }
}