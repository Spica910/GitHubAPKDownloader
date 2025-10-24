package com.github.apkdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri

object GitHubAuthHelper {

    // Default Client ID
    const val DEFAULT_CLIENT_ID = "Iv23liV89qJnQWOXmm5Q"

    // Get the configured client ID, or use default
    fun getClientId(context: Context): String {
        val prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        return prefs.getString("client_id", DEFAULT_CLIENT_ID) ?: DEFAULT_CLIENT_ID
    }

    fun isSetupComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        return prefs.contains("client_id")
    }

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"

    data class DeviceCodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("access_token", token).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        return prefs.getString("access_token", null)
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences("github_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("access_token").apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    fun openVerificationUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
