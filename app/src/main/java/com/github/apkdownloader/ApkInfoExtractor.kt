package com.github.apkdownloader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ApkPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?
)

data class ApkUpdateInfo(
    val isInstalled: Boolean,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val newVersionName: String?,
    val newVersionCode: Long?,
    val isUpdateAvailable: Boolean
)

class ApkInfoExtractor(private val context: Context) {

    /**
     * Extract APK package information by downloading and analyzing the APK file
     */
    suspend fun extractApkInfo(downloadUrl: String, authToken: String? = null): ApkPackageInfo? {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // Create temporary file
                tempFile = File(context.cacheDir, "temp_apk_${System.currentTimeMillis()}.apk")

                // Download APK to temp file
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                authToken?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                // Download file
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Extract package info
                val packageManager = context.packageManager
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageArchiveInfo(
                        tempFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
                }

                packageInfo?.let {
                    // Load application info to get app name
                    it.applicationInfo?.sourceDir = tempFile.absolutePath
                    it.applicationInfo?.publicSourceDir = tempFile.absolutePath

                    val appName = try {
                        packageManager.getApplicationLabel(it.applicationInfo!!).toString()
                    } catch (e: Exception) {
                        it.packageName
                    }

                    ApkPackageInfo(
                        packageName = it.packageName,
                        versionName = it.versionName ?: "Unknown",
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            it.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            it.versionCode.toLong()
                        },
                        appName = appName,
                        minSdkVersion = it.applicationInfo?.minSdkVersion,
                        targetSdkVersion = it.applicationInfo?.targetSdkVersion
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ApkInfoExtractor", "Error extracting APK info: ${e.message}", e)
                null
            } finally {
                // Clean up temp file
                tempFile?.delete()
            }
        }
    }

    /**
     * Check if APK update is available by comparing with installed version
     */
    fun checkForUpdate(packageName: String, newVersionCode: Long): ApkUpdateInfo {
        return try {
            val packageManager = context.packageManager
            val installedPackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedPackageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedPackageInfo.versionCode.toLong()
            }

            ApkUpdateInfo(
                isInstalled = true,
                installedVersionName = installedPackageInfo.versionName,
                installedVersionCode = installedVersionCode,
                newVersionName = null,
                newVersionCode = newVersionCode,
                isUpdateAvailable = newVersionCode > installedVersionCode
            )
        } catch (e: PackageManager.NameNotFoundException) {
            // App is not installed
            ApkUpdateInfo(
                isInstalled = false,
                installedVersionName = null,
                installedVersionCode = null,
                newVersionName = null,
                newVersionCode = newVersionCode,
                isUpdateAvailable = false
            )
        }
    }

    /**
     * Check if any app with given package name is installed
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
