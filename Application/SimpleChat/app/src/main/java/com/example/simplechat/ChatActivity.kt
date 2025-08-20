package com.example.simplechat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.simplechat.databinding.ActivityChatBinding
import java.io.File
import javax.crypto.Cipher

class ChatActivity : AppCompatActivity(), Utils.MessageListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messages: MutableList<String>
    private lateinit var messagesTable: TableLayout

    override fun onNewMessageReceived() {
        runOnUiThread {
            displayMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat) // Make sure this matches your layout file name

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = GlobalVars.activeChatUser

        messagesTable = binding.messagesTable

        Utils.messageListenerForChat = this

        binding.scrollView.post {
            binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }

        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.inputEditText.addTextChangedListener( object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if((binding.inputEditText.text?.length ?: 0) > 0) {
                    binding.messageInputLayout.hint = "Write your message (" + binding.inputEditText.text?.length.toString() + "/35)"
                } else {
                    binding.messageInputLayout.hint = "Write your message"
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        }
        )
        displayMessages()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_chat -> {
                deleteMessages()
                Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun deleteMessages() {
        val chatFile = File(filesDir, "app/" + GlobalVars.activeChatUser + "/chat.txt")
        if (chatFile.exists()) {
            chatFile.writeText("")  // This empties the file
        }
        displayMessages()
    }

    fun displayMessages() {
        Utils.setChatWhite(GlobalVars.activeChatUser.toString())
        // First we read the users file
        val chatFile = File(filesDir, "app/" + GlobalVars.activeChatUser + "/chat.txt")
        if (chatFile.exists()) {
            messages = chatFile.readLines().toMutableList()
        } else {
            return
        }

        messagesTable.removeAllViews()
        if(messages.size == 0) {
            //binding.noUsersText.visibility = View.VISIBLE
        } else {
            for (message in messages) {
                if(message != "") {
                    val tableRow = TableRow(this).apply {
                        layoutParams = TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val messageContainer = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        )
                        gravity = if (message.split(";")[0] == "me") Gravity.END else Gravity.START
                    }

                    // Create a new TextView for the new row
                    val newTextView = TextView(this).apply {
                        text = message.split(";")[2]
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    messageContainer.addView(newTextView)

                    tableRow.addView(messageContainer)

                    messagesTable.addView(tableRow)

                    val tableRow2 = TableRow(this).apply {
                        layoutParams = TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val messageContainer2 = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT
                        )
                        gravity = if (message.split(";")[0] == "me") Gravity.END else Gravity.START
                    }

                    val newTextView2 = TextView(this).apply {
                        if (message.split(";")[0] == "me" && message.split(";")[4] == "delivered") {
                            text = message.split(";")[3] + " ✓"
                        } else {
                            text = message.split(";")[3]// + "✓"
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        textSize = 10f
                    }

                    messageContainer2.addView(newTextView2)

                    // Add the TextView to the TableRow
                    tableRow2.addView(messageContainer2)

                    //tableRow2.background = borderDrawable
                    messagesTable.addView(tableRow2)
                }
            }
            binding.scrollView.post {
                binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    fun sendMessage() {
        var clearText = binding.inputEditText.text.toString()

        if(clearText == "") {
            Toast.makeText(this, "Error: Empty message", Toast.LENGTH_SHORT).show()
            return
        }

        if("\n" in clearText) {
            Toast.makeText(this, "Error: The message contains line breaks", Toast.LENGTH_SHORT).show()
            return
        }

        if(";" in clearText) {
            Toast.makeText(this, "Error: The message contains \";\"", Toast.LENGTH_SHORT).show()
            return
        }

        if(clearText.length > 35) {
            Toast.makeText(this, "Error: Message too long", Toast.LENGTH_SHORT).show()
            return
        }

        var m = Message()
        m.generateID()
        m.setTime()

        var receiver = GlobalVars.activeChatUser

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, GlobalVars.activeUserPublicKey)
        val encryptedMessageByteArray = cipher.doFinal(clearText.toByteArray(Charsets.UTF_8))
        val encryptedMessageBase64 = Base64.encodeToString(encryptedMessageByteArray, Base64.DEFAULT)

        m.payload = encryptedMessageBase64

        //val message = "sendMessage;" + receiver + ";" + encryptedMessageBase64
        val message = "sendMessage;" + receiver + ";" + m.getCSVLine()

        if(Utils.webSocket.send(message)) {
            val chatFile = File(GlobalVars.context.filesDir, "app/" + GlobalVars.activeChatUser + "/chat.txt")
            chatFile.appendText("me;" + m.ID + ";" + clearText + ";" + m.time + ";sent\n")

            displayMessages()

            binding.inputEditText.text?.clear()

            // Set chat to be the first
            val file = File(GlobalVars.context.filesDir, "users.txt")
            var users = file.readLines().toMutableList()
            for (line in users) {
                if (line.split(";")[0] == receiver) {
                    users.remove(line)
                    break
                }
            }
            users.add(0, receiver + ";white")
            file.writeText("")
            file.writeText(users.joinToString("\n"))

        } else {
            Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Utils.messageListenerForChat = null // Avoid memory leaks
    }
}
