package com.github.apkdownloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var loginButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var welcomeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if setup is complete
        if (!GitHubAuthHelper.isSetupComplete(this)) {
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)
        welcomeText = findViewById(R.id.welcomeText)

        // Check if already logged in
        if (GitHubAuthHelper.isLoggedIn(this)) {
            navigateToRepositoryList()
            return
        }

        loginButton.setOnClickListener {
            startDeviceFlow()
        }
    }

    private fun startDeviceFlow() {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        welcomeText.text = "Connecting to GitHub..."

        lifecycleScope.launch {
            try {
                // Step 1: Request device code
                val deviceCodeData = withContext(Dispatchers.IO) {
                    requestDeviceCode()
                }

                if (deviceCodeData == null) {
                    showError("Failed to connect to GitHub")
                    return@launch
                }

                progressBar.visibility = View.GONE

                // Step 2: Show user code dialog
                showUserCodeDialog(
                    deviceCodeData.userCode,
                    deviceCodeData.verificationUri,
                    deviceCodeData.deviceCode,
                    deviceCodeData.interval
                )

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                welcomeText.text = getString(R.string.app_name)
                showError("Error: ${e.message}")
            }
        }
    }

    private suspend fun requestDeviceCode(): GitHubAuthHelper.DeviceCodeResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val clientId = GitHubAuthHelper.getClientId(this@MainActivity).trim()
                android.util.Log.d("GitHub", "Requesting device code")
                android.util.Log.d("GitHub", "CLIENT_ID: '$clientId' (length: ${clientId.length})")

                val client = OkHttpClient()

                // Build the request body
                val formBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("scope", "repo")
                    .build()

                android.util.Log.d("GitHub", "Request URL: https://github.com/login/device/code")
                android.util.Log.d("GitHub", "Request body: client_id=$clientId&scope=repo")

                val request = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body()?.string()

                android.util.Log.d("GitHub", "Response code: ${response.code()}")
                android.util.Log.d("GitHub", "Response body: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)

                    // Check for error in response
                    if (json.has("error")) {
                        val error = json.getString("error")
                        val errorDesc = json.optString("error_description", error)
                        android.util.Log.e("GitHub", "API Error: $error - $errorDesc")
                        withContext(Dispatchers.Main) {
                            showError("GitHub Error: $errorDesc")
                        }
                        return@withContext null
                    }

                    GitHubAuthHelper.DeviceCodeResponse(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUri = json.getString("verification_uri"),
                        expiresIn = json.getInt("expires_in"),
                        interval = json.getInt("interval")
                    )
                } else {
                    // Parse error response
                    var errorMessage = "HTTP ${response.code()}"
                    if (responseBody != null) {
                        try {
                            val errorJson = JSONObject(responseBody)
                            if (errorJson.has("message")) {
                                errorMessage += ": ${errorJson.getString("message")}"
                            }
                            if (errorJson.has("error")) {
                                errorMessage += " (${errorJson.getString("error")})"
                            }
                        } catch (e: Exception) {
                            errorMessage += ": $responseBody"
                        }
                    }

                    android.util.Log.e("GitHub", "Request failed: $errorMessage")
                    withContext(Dispatchers.Main) {
                        showError(errorMessage)
                    }
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("GitHub", "Exception during request", e)
                withContext(Dispatchers.Main) {
                    showError("Network error: ${e.message}")
                }
                e.printStackTrace()
                null
            }
        }
    }

    private fun showUserCodeDialog(
        userCode: String,
        verificationUri: String,
        deviceCode: String,
        pollInterval: Int
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_flow, null)
        val codeTextView = dialogView.findViewById<TextView>(R.id.userCodeText)
        val copyButton = dialogView.findViewById<Button>(R.id.copyCodeButton)
        val openBrowserButton = dialogView.findViewById<Button>(R.id.openBrowserButton)

        codeTextView.text = userCode

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GitHub Code", userCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("GitHub Authentication")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("I've Authorized") { _, _ ->
                // User claims they authorized, but we'll continue polling
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                resetUI()
            }
            .create()

        openBrowserButton.setOnClickListener {
            GitHubAuthHelper.openVerificationUrl(this, verificationUri)
        }

        dialog.show()

        // Start polling for access token
        startPollingForToken(deviceCode, pollInterval)
    }

    private fun startPollingForToken(deviceCode: String, intervalSeconds: Int) {
        welcomeText.text = "Waiting for authorization..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            var attempts = 0
            val maxAttempts = 60 // Poll for up to 10 minutes (60 * 10 seconds)

            while (attempts < maxAttempts) {
                delay(intervalSeconds * 1000L)

                val token = withContext(Dispatchers.IO) {
                    pollForAccessToken(deviceCode)
                }

                when {
                    token != null -> {
                        // Success!
                        GitHubAuthHelper.saveToken(this@MainActivity, token)
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this@MainActivity,
                            "Successfully logged in!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToRepositoryList()
                        return@launch
                    }
                    attempts >= maxAttempts - 1 -> {
                        showError("Authorization timeout. Please try again.")
                        resetUI()
                        return@launch
                    }
                    else -> {
                        attempts++
                        // Continue polling
                    }
                }
            }
        }
    }

    private suspend fun pollForAccessToken(deviceCode: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val clientId = GitHubAuthHelper.getClientId(this@MainActivity)
                val client = OkHttpClient()
                val formBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()

                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body()?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)

                    // Check for errors
                    if (json.has("error")) {
                        val error = json.getString("error")
                        if (error == "authorization_pending") {
                            // Still waiting, return null to continue polling
                            return@withContext null
                        } else if (error == "slow_down") {
                            // We're polling too fast, return null
                            return@withContext null
                        } else {
                            // Other error, stop polling
                            return@withContext null
                        }
                    }

                    // Success! Log the scope received
                    val scope = json.optString("scope", "none")
                    android.util.Log.d("GitHub", "Token received with scope: $scope")

                    // Success! Return the token
                    json.optString("access_token", null)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun resetUI() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        welcomeText.text = getString(R.string.app_name)
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        welcomeText.text = getString(R.string.app_name)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToRepositoryList() {
        val intent = Intent(this, RepositoryListActivity::class.java)
        startActivity(intent)
        finish()
    }
}
