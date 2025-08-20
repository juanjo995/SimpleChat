package com.example.simplechat

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.simplechat.databinding.ActivityNewUserBinding
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

class NewUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_user) // Make sure this matches your layout file name

        binding = ActivityNewUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = "Register new user"

        binding.addButton.setOnClickListener {
            lifecycleScope.launch {
                val userName = binding.nameInputText.text.toString()

                if(userName == "") {
                    Toast.makeText(this@NewUserActivity, "Error: Empty name", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if("\n" in userName) {
                    Toast.makeText(this@NewUserActivity, "Error: The name contains line breaks", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if(userName.length > 15) {
                    Toast.makeText(this@NewUserActivity, "Error: Name too long", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (';' in userName) {
                    Toast.makeText(this@NewUserActivity, "The name can't contain \";\"", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Check if username already exists
                var message = "userExists;" + userName
                Utils.webSocket.send(message)

                Utils.waitForResponse()

                if (GlobalVars.lastMessageReceived == "Server: OK, the user exists") {
                    Toast.makeText(this@NewUserActivity, "Error, the user already exists", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val alias = Utils.generateRandomString(10)
                generateRSAKeyPair(alias)

                val file = File(filesDir, "profile.txt")
                file.writeText(userName + "\n" + alias)
                GlobalVars.userName = userName
                GlobalVars.alias = alias

                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this@NewUserActivity, "Error creating FCM token", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    GlobalVars.FCMtoken = task.result

                    message = "registerNewUser;" + userName + ";" + publicKeyToBase64(getPublicKey(alias)) + ";" + GlobalVars.FCMtoken
                    Utils.webSocket.send(message)
                }

                finish()
            }
        }
    }

    fun generateRSAKeyPair(alias: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun getPublicKey(alias: String): PublicKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val cert = keyStore.getCertificate(alias)
        return cert.publicKey
    }

    fun publicKeyToBase64(publicKey: PublicKey): String {
        val encoded = publicKey.encoded
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
}