package com.example.familyshield.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    // Views
    private lateinit var topAppBar: Toolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvActiveUsers: TextView
    private lateinit var tvAdminName: TextView
    private lateinit var tvAdminMobile: TextView
    private lateinit var btnViewUsers: Button
    private lateinit var btnComplaints: Button
    private lateinit var btnSettings: Button
    private lateinit var btnLogout: Button
    private lateinit var rvRecentCommands: RecyclerView
    private lateinit var tvNoCommands: TextView

    // Firebase
    private lateinit var sessionManager: SessionManager
    private lateinit var databaseRef: DatabaseReference
    private lateinit var adminMobile: String
    private lateinit var commandSender: AdminCommandSender

    // Command List
    private val commandList = mutableListOf<Map<String, Any>>()
    private lateinit var commandsAdapter: RecentCommandsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        sessionManager = SessionManager(this)
        adminMobile = sessionManager.getAdminMobile() ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        databaseRef = FirebaseDatabase.getInstance().getReference("familyshield")
        commandSender = AdminCommandSender(adminMobile)

        initViews()
        setupToolbar()
        setupBottomNavigation()
        setupClickListeners()

        loadAdminInfo()
        loadDashboardStats()
        loadCommandHistory()
    }

    private fun initViews() {
        topAppBar = findViewById(R.id.topAppBar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvActiveUsers = findViewById(R.id.tvActiveUsers)
        tvAdminName = findViewById(R.id.tvAdminName)
        tvAdminMobile = findViewById(R.id.tvAdminMobile)
        btnViewUsers = findViewById(R.id.btnViewUsers)
        btnComplaints = findViewById(R.id.btnComplaints)
        btnSettings = findViewById(R.id.btnSettings)
        btnLogout = findViewById(R.id.btnLogout)
        rvRecentCommands = findViewById(R.id.rvRecentCommands)
        tvNoCommands = findViewById(R.id.tvNoCommands)

        rvRecentCommands.layoutManager = LinearLayoutManager(this)
        commandsAdapter = RecentCommandsAdapter(commandList)
        rvRecentCommands.adapter = commandsAdapter
    }

    private fun setupToolbar() {
        setSupportActionBar(topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        topAppBar.title = getString(R.string.admin_dashboard_title)
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_users -> {
                    startActivity(Intent(this, AdminUsersActivity::class.java).apply {
                        putExtra("adminMobile", adminMobile)
                    })
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintListActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java).apply {
                        putExtra("adminMobile", adminMobile)
                    })
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        btnViewUsers.setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java).apply {
                putExtra("adminMobile", adminMobile)
            })
        }
        btnComplaints.setOnClickListener {
            startActivity(Intent(this, ComplaintListActivity::class.java))
        }
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun loadAdminInfo() {
        databaseRef.child("admins").child(adminMobile).get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                tvAdminName.text = getString(R.string.welcome_admin, name)
                tvAdminMobile.text = getString(R.string.admin_mobile_email, adminMobile, email)
            }
    }

    private fun loadDashboardStats() {
        databaseRef.child("admins").child(adminMobile).child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var total = 0
                    var active = 0
                    for (user in snapshot.children) {
                        total++
                        val isActive = user.child("active").getValue(Boolean::class.java) ?: false
                        if (isActive) active++
                    }
                    tvTotalUsers.text = total.toString()
                    tvActiveUsers.text = active.toString()
                }

                override fun onCancelled(error: DatabaseError) {
                    tvTotalUsers.text = "0"
                    tvActiveUsers.text = "0"
                }
            })
    }

    private fun loadCommandHistory() {
        commandSender.getCommandHistory { history ->
            runOnUiThread {
                commandList.clear()
                commandList.addAll(history)

                if (commandList.isNotEmpty()) {
                    tvNoCommands.visibility = View.GONE
                    rvRecentCommands.visibility = View.VISIBLE
                    commandsAdapter.notifyDataSetChanged()
                } else {
                    tvNoCommands.visibility = View.VISIBLE
                    rvRecentCommands.visibility = View.GONE
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Change Password", "Notification", "Privacy", "About")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_title))
            .setItems(options) { _, which ->
                when (which) {
                    3 -> showAboutDialog()
                    else -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton("Yes") { _, _ ->
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java).apply {
                    putExtra("adminMobile", adminMobile)
                })
                true
            }
            R.id.action_help -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// RecyclerView Adapter
class RecentCommandsAdapter(private val commands: List<Map<String, Any>>) :
    RecyclerView.Adapter<RecentCommandsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCommand: TextView = itemView.findViewById(R.id.tvCommand)
        val tvUser: TextView = itemView.findViewById(R.id.tvUser)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_command, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cmd = commands[position]
        val type = cmd["command"] as? String ?: ""

        holder.tvCommand.text = when (type) {
            "LOCK" -> "🔒 Lock Device"
            "FACTORY_RESET" -> "⚠️ Factory Reset"
            "DISABLE_RESET" -> "🚫 Disable Reset"
            "ENABLE_RESET" -> "✅ Enable Reset"
            "LOCATE" -> "📍 Locate Device"
            "SIREN" -> "🔊 Play Siren"
            else -> type
        }

        holder.tvUser.text = "User: ${cmd["userMobile"]}"

        val time = cmd["timestamp"] as? Long ?: 0
        holder.tvTime.text = if (time > 0) {
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(time))
        } else "Just now"
    }

    override fun getItemCount(): Int = commands.size
}