package com.example.simplechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.simplechat.databinding.ActivityChatMenuBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Callback
import okio.IOException
import java.io.File

class ChatMenuActivity : AppCompatActivity(), Utils.MessageListener {
    private lateinit var binding: ActivityChatMenuBinding
    private lateinit var chatsTable: TableLayout
    private lateinit var users: MutableList<String>
    private lateinit var selectedUserToBeDeleted: String
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted — you can show notifications
        } else {
            // Permission denied — you may want to disable notifications or show a message
        }
    }

    override fun onNewMessageReceived() {
        runOnUiThread {
            displayUsers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_menu) // Make sure this matches your layout file name

        binding = ActivityChatMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        GlobalVars.context = applicationContext

        checkAndRequestNotificationPermission()

        val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
        ContextCompat.startForegroundService(GlobalVars.context, intent)

        Utils.monitor = NetworkMonitor(applicationContext)
        Utils.monitor.start (
            // Trigger WebSocket reconnect or anything else
            onAvailable = {
                GlobalVars.connectedToInternet = true
                CoroutineScope(Dispatchers.IO).launch {
                    Utils.connect()
                }
                val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
                ContextCompat.startForegroundService(GlobalVars.context, intent)
            },
            onLost = {
                GlobalVars.connectedToInternet = false
                GlobalVars.context.stopService(Intent(GlobalVars.context, KeepAliveService::class.java))
            }
        )

        Utils.createNotificationChannel(GlobalVars.context)

        title = "Chats of " + GlobalVars.userName

        chatsTable = findViewById(R.id.chatsTable)

        Utils.messageListenerForChatMenu = this

        if(fileList().contains("users.txt")) {

        } else {
            val file = File(filesDir, "users.txt")
            file.createNewFile()
        }

        val fileName = "profile.txt"
        val file = File(filesDir, fileName)

        if (file.exists()) {
            val lines = file.readLines()
            if(lines.size == 0) {
                val intent = Intent(this, NewUserActivity::class.java)
                startActivity(intent)
            } else {
                GlobalVars.userName = lines.getOrNull(0)
                GlobalVars.alias = lines.getOrNull(1)
            }
        } else {
            file.createNewFile()
            val intent = Intent(this, NewUserActivity::class.java)
            startActivity(intent)
        }

        displayUsers()

        binding.addUser.setOnClickListener {
            val intent = Intent(this, AddUserActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted — safe to show notifications
                }
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> {
                    // Optionally show rationale UI before requesting again
                    requestPermissionLauncher.launch(permission)
                }
                else -> {
                    // Directly request the permission
                    requestPermissionLauncher.launch(permission)
                }
            }
        } else {
            // Permission not required on Android < 13
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.check_server -> {
                CoroutineScope(Dispatchers.Main).launch {
                    if (Utils.isWebSocketConnected()) {
                        Toast.makeText(this@ChatMenuActivity, "✅ Connected to server", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChatMenuActivity, "❌ Disconnected from server", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                true
            }
            R.id.connect_to_server -> {
                CoroutineScope(Dispatchers.Main).launch {
                    Utils.connect()
                    if (Utils.isWebSocketConnected()) {
                        Toast.makeText(this@ChatMenuActivity, "✅ Connected to server", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@ChatMenuActivity,
                            "❌ Error, couldn't connect to server",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRestart() {
        super.onRestart()

        val fileName = "profile.txt"
        val file = File(filesDir, fileName)

        val lines = file.readLines()

        if(lines.size == 0) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        title = "Chats of " + GlobalVars.userName
        displayUsers()
    }

    fun displayUsers() {
        // First we read the users file
        val file = File(filesDir, "users.txt")
        if (file.exists()) {
            users = file.readLines().toMutableList()
        }

        chatsTable.removeAllViews()
        if(users.size == 0) {
            binding.noUsersText.visibility = View.VISIBLE
        } else {
            binding.noUsersText.visibility = View.INVISIBLE
            for (user in users) {

                val tableRow = TableRow(this)

                val newTextView = TextView(this).apply {
                    text = user.split(";")[0]
                    gravity = Gravity.CENTER  // Center text inside the TextView
                    setTypeface(null, Typeface.BOLD)  // Make text bold
                    layoutParams = TableRow.LayoutParams(0, 200, 1f) // To center horizontally
                }

                newTextView.setOnLongClickListener { view ->
                    selectedUserToBeDeleted = (view as TextView).text.toString()
                    showPopupMenu(view)
                    true
                }

                newTextView.setOnClickListener { view ->
                    val clickedTextView = view as TextView
                    val text = clickedTextView.text.toString()

                    GlobalVars.activeChatUser = text

                    var activeUserPublicKeyBase64: String = ""
                    val publicKeyFile = File(filesDir, "app/" + GlobalVars.activeChatUser + "/publicKey.txt")
                    if (publicKeyFile.exists()) {
                        activeUserPublicKeyBase64 = publicKeyFile.readText(Charsets.UTF_8)
                    }
                    GlobalVars.activeUserPublicKey = Utils.Base64ToPublicKey(activeUserPublicKeyBase64)

                    val intent = Intent(this, ChatActivity::class.java)
                    startActivity(intent)
                }

                tableRow.addView(newTextView)

                val borderDrawable = GradientDrawable().apply {
                    if(user.split(";")[1] == "green") {
                        setColor(Color.parseColor("#8cff9d")) // Background color
                    } else {
                        setColor(Color.WHITE) // Background color
                    }
                    setStroke(2, Color.GRAY) // Border width and color
                }

                tableRow.background = borderDrawable

                chatsTable.addView(tableRow)
            }
        }
    }

    fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.chat_menu_popup, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.option_delete -> {
                    val file = File(filesDir, "users.txt")
                    users = file.readLines().toMutableList()

                    for(user in users) {
                        if(user.split(";")[0] == selectedUserToBeDeleted) {
                            users.remove(user)
                            break
                        }
                    }

                    file.writeText("")
                    file.writeText(users.joinToString("\n"))
                    val folder = File(filesDir, "app/" + selectedUserToBeDeleted)
                    folder.deleteRecursively()
                    displayUsers()

                    true
                }
                else -> false
            }
        }

        popup.show()
    }

}

