package com.example.simplechat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplechat.databinding.ActivityAddUserBinding
import kotlinx.coroutines.launch
import java.io.File

class AddUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_user) // Make sure this matches your layout file name

        binding = ActivityAddUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "Add user"

        binding.addButton.setOnClickListener {
            lifecycleScope.launch {
                var userName = binding.nameInputText.text.toString()
                if(userName == GlobalVars.userName) {
                    Toast.makeText(this@AddUserActivity, "You can't add yourself", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if(userName == "") {
                    Toast.makeText(this@AddUserActivity, "Error: Empty name", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if("\n" in userName) {
                    Toast.makeText(this@AddUserActivity, "Error: The name contains line breaks", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if(userName.length > 15) {
                    Toast.makeText(this@AddUserActivity, "Error: Name too long", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (';' in userName) {
                    //println("The string contains a semicolon.")
                    Toast.makeText(this@AddUserActivity, "The name can't contain \";\"", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var file = File(filesDir, "users.txt")
                if (file.exists()) {
                    val lines = file.readLines()
                    for (line in lines) {
                        if (line.split(";")[0] == userName) {
                            //println("User already added")
                            Toast.makeText(this@AddUserActivity, "User already added", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                }

                var message = "userExists;" + userName
                Utils.webSocket.send(message)

                Utils.waitForResponse()

                if (GlobalVars.lastMessageReceived == "Server: Error, the user doesn't exists") {
                    Toast.makeText(this@AddUserActivity, GlobalVars.lastMessageReceived, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add new user to users.txt file
                file = File(filesDir, "users.txt")
                //file.appendText(userName + "\n")
                file.appendText(userName + ";white\n")

                // Create folders for the new user public key
                val dir = File(
                    filesDir,
                    "app/" + userName
                )
                dir.mkdirs()

                // Create new user publicKey file
                val publicKeyFile = File(
                    filesDir,
                    "app/" + userName + "/publicKey.txt"
                )
                publicKeyFile.createNewFile()

                message = "getPublicKey;" + userName

                Utils.webSocket.send(message)
                Utils.waitForResponse()

                publicKeyFile.writeText(GlobalVars.lastMessageReceived.toString())

                val chatFile = File(filesDir, "app/" + userName + "/chat.txt")
                chatFile.createNewFile()

                finish()
            }
        }
    }
}
