package com.github.apkdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SetupActivity : AppCompatActivity() {

    private lateinit var useDefaultButton: Button
    private lateinit var openGitHubButton: Button
    private lateinit var saveClientIdButton: Button
    private lateinit var clientIdInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        supportActionBar?.title = "Setup"

        useDefaultButton = findViewById(R.id.useDefaultButton)
        openGitHubButton = findViewById(R.id.openGitHubButton)
        saveClientIdButton = findViewById(R.id.saveClientIdButton)
        clientIdInput = findViewById(R.id.clientIdInput)

        useDefaultButton.setOnClickListener {
            // Use the default public client ID
            saveClientId(GitHubAuthHelper.DEFAULT_CLIENT_ID)
            navigateToMain()
        }

        openGitHubButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/developers"))
            startActivity(intent)
        }

        saveClientIdButton.setOnClickListener {
            val clientId = clientIdInput.text.toString().trim()
            if (clientId.isEmpty()) {
                Toast.makeText(this, "Please enter a Client ID", Toast.LENGTH_SHORT).show()
            } else if (!clientId.startsWith("Ov") && !clientId.startsWith("Iv")) {
                Toast.makeText(this, "Client ID should start with 'Ov' or 'Iv'", Toast.LENGTH_LONG).show()
            } else if (clientId.length < 20) {
                Toast.makeText(this, "Client ID seems too short. Please check.", Toast.LENGTH_LONG).show()
            } else {
                saveClientId(clientId)
                navigateToMain()
            }
        }
    }

    private fun saveClientId(clientId: String) {
        val prefs = getSharedPreferences("github_prefs", MODE_PRIVATE)
        prefs.edit().putString("client_id", clientId).apply()
        Toast.makeText(this, "Client ID saved!", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
