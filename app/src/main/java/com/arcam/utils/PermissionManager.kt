package com.arcam.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    private val logger = Logger.getInstance(context)

    companion object {
        private const val TAG = "PermissionManager"

        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    fun hasAllPermissions(): Boolean {
        val allGranted = REQUIRED_PERMISSIONS.all { permission ->
            hasPermission(permission)
        }

        logger.d(TAG, "All permissions granted: $allGranted")
        return allGranted
    }

    fun hasPermission(permission: String): Boolean {
        // Special handling for WRITE_EXTERNAL_STORAGE on Android 10+
        if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Not needed on Android 10+ due to scoped storage
            return true
        }

        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

        logger.d(TAG, "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
        return granted
    }

    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { !hasPermission(it) }
    }

    fun hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)

    fun hasAudioPermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasNetworkPermissions(): Boolean {
        return hasPermission(Manifest.permission.INTERNET) &&
                hasPermission(Manifest.permission.ACCESS_NETWORK_STATE) &&
                hasPermission(Manifest.permission.ACCESS_WIFI_STATE)
    }

    fun logPermissionStatus() {
        logger.d(TAG, "=== Permission Status ===")
        REQUIRED_PERMISSIONS.forEach { permission ->
            logger.d(TAG, "$permission: ${if (hasPermission(permission)) "GRANTED" else "DENIED"}")
        }
        logger.d(TAG, "=======================")
    }
}