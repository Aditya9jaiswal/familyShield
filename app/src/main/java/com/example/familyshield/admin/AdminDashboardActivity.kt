package com.example.familyshield.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.familyshield.R
import com.example.familyshield.auth.LoginActivity
import com.example.familyshield.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
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
    private lateinit var tvPendingComplaints: TextView
    private lateinit var tvResolvedComplaints: TextView
    private lateinit var rvRecentCommands: RecyclerView
    private lateinit var tvNoCommands: TextView
    private lateinit var btnViewUsers: MaterialButton
    private lateinit var btnComplaints: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var tvAdminName: TextView
    private lateinit var tvAdminMobile: TextView

    // Firebase
    private lateinit var sessionManager: SessionManager
    private lateinit var databaseRef: DatabaseReference
    private lateinit var adminMobile: String

    // Data
    private val commandList = mutableListOf<CommandHistory>()
    private lateinit var commandsAdapter: RecentCommandsAdapter
    private var commandsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        sessionManager = SessionManager(this)

        // Check if admin is logged in
        if (!sessionManager.isAdminLoggedIn()) {
            Toast.makeText(this, "Please login as admin", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        adminMobile = sessionManager.getAdminMobile() ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        databaseRef = FirebaseDatabase.getInstance().reference.child("familyshield")

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
        tvPendingComplaints = TextView(this) // Create dynamically since not in layout
        tvResolvedComplaints = TextView(this) // Create dynamically since not in layout
        rvRecentCommands = findViewById(R.id.rvRecentCommands)
        tvNoCommands = findViewById(R.id.tvNoCommands)
        btnViewUsers = findViewById(R.id.btnViewUsers)
        btnComplaints = findViewById(R.id.btnComplaints)
        btnSettings = findViewById(R.id.btnSettings)
        btnLogout = findViewById(R.id.btnLogout)
        tvAdminName = findViewById(R.id.tvAdminName)
        tvAdminMobile = findViewById(R.id.tvAdminMobile)

        // Hide complaint stats since not in UI
        tvPendingComplaints.visibility = View.GONE
        tvResolvedComplaints.visibility = View.GONE

        rvRecentCommands.layoutManager = LinearLayoutManager(this)
        commandsAdapter = RecentCommandsAdapter(commandList)
        rvRecentCommands.adapter = commandsAdapter
    }

    private fun setupToolbar() {
        setSupportActionBar(topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = "Admin Dashboard"
        topAppBar.setTitleTextColor(getColor(android.R.color.white))
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_users -> {
                    startActivity(Intent(this, AdminUsersActivity::class.java))
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintListActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        btnViewUsers.setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
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
        databaseRef.child("admins").child(adminMobile).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Admin"
                val mobile = snapshot.child("mobile").getValue(String::class.java) ?: adminMobile

                tvAdminName.text = "Welcome, $name"
                tvAdminMobile.text = "📱 $mobile"
            }
            override fun onCancelled(error: DatabaseError) {
                tvAdminName.text = "Welcome, Admin"
                tvAdminMobile.text = "📱 $adminMobile"
            }
        })
    }

    private fun loadDashboardStats() {
        // Load user stats
        databaseRef.child("admins").child(adminMobile).child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var total = 0
                    var active = 0
                    for (user in snapshot.children) {
                        total++
                        val isOnline = user.child("isOnline").getValue(Boolean::class.java) ?: false
                        if (isOnline) active++
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
        val commandsRef = databaseRef.child("admins").child(adminMobile).child("commands")

        commandsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commandList.clear()

                for (cmdSnapshot in snapshot.children) {
                    try {
                        val command = cmdSnapshot.getValue(CommandHistory::class.java)
                        if (command != null) {
                            commandList.add(command)
                        }
                    } catch (e: Exception) {
                        // Handle individual command parse error
                    }
                }

                // Sort by timestamp (newest first)
                commandList.sortByDescending { it.timestamp }

                // Show only last 10 commands
                val recentCommands = commandList.take(10)
                commandList.clear()
                commandList.addAll(recentCommands)

                if (commandList.isNotEmpty()) {
                    tvNoCommands.visibility = View.GONE
                    rvRecentCommands.visibility = View.VISIBLE
                    commandsAdapter.notifyDataSetChanged()
                } else {
                    tvNoCommands.visibility = View.VISIBLE
                    rvRecentCommands.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvNoCommands.visibility = View.VISIBLE
                tvNoCommands.text = "Error loading commands: ${error.message}"
                rvRecentCommands.visibility = View.GONE
            }
        }

        commandsRef.limitToLast(10).addValueEventListener(commandsListener!!)
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Change Password", "About App")
        MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChangePasswordDialog()
                    1 -> showAboutDialog()
                }
            }
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<TextView>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<TextView>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<TextView>(R.id.etConfirmPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val current = etCurrentPassword.text.toString()
                val newPwd = etNewPassword.text.toString()
                val confirm = etConfirmPassword.text.toString()

                if (current.isEmpty()) {
                    Toast.makeText(this, "Enter current password", Toast.LENGTH_SHORT).show()
                } else if (newPwd.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                } else if (newPwd != confirm) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                } else {
                    updatePassword(newPwd)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePassword(newPassword: String) {
        databaseRef.child("admins").child(adminMobile).child("password")
            .setValue(newPassword)
            .addOnSuccessListener {
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About FamilyShield")
            .setMessage("""
                FamilyShield v1.0.0
                
                A comprehensive family safety and device management app.
                
                Features:
                • Device Tracking
                • Remote Lock
                • Emergency Siren
                • Factory Reset Control
                • Complaint Management
                
                Developed with ❤️ for your family's safety
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
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
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.action_help -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandsListener?.let {
            databaseRef.child("admins").child(adminMobile).child("commands")
                .removeEventListener(it)
        }
    }
}

// Data Class for Command History
data class CommandHistory(
    val command: String = "",
    val userMobile: String = "",
    val userName: String = "",
    val timestamp: Long = 0,
    val status: String = "sent"
)

// RecyclerView Adapter
class RecentCommandsAdapter(private val commands: List<CommandHistory>) :
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

        holder.tvCommand.text = when (cmd.command) {
            "LOCK" -> "🔒 Device Locked"
            "FACTORY_RESET" -> "⚠️ Factory Reset"
            "DISABLE_RESET" -> "🚫 Factory Reset Disabled"
            "ENABLE_RESET" -> "✅ Factory Reset Enabled"
            "LOCATE" -> "📍 Location Request"
            "SIREN_ON" -> "🔊 Siren Activated"
            "SIREN_OFF" -> "🔇 Siren Deactivated"
            else -> cmd.command
        }

        holder.tvUser.text = "${cmd.userName} (${cmd.userMobile})"

        val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        holder.tvTime.text = if (cmd.timestamp > 0) {
            timeFormat.format(Date(cmd.timestamp))
        } else "Just now"
    }

    override fun getItemCount(): Int = commands.size
}