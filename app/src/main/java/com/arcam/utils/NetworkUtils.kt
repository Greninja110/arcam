package com.arcam.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class NetworkUtils(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val logger = Logger.getInstance(context)

    companion object {
        private const val TAG = "NetworkUtils"
    }

    fun isNetworkAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    fun getNetworkType(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "None"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "None"

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                else -> "Other"
            }
        } else {
            @Suppress("DEPRECATION")
            return when (connectivityManager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "Cellular"
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                ConnectivityManager.TYPE_BLUETOOTH -> "Bluetooth"
                else -> "Other"
            }
        }
    }

    fun getNetworkSpeed(): Pair<Int, Int> { // Download, Upload in Kbps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return Pair(0, 0)
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return Pair(0, 0)

            val downloadSpeed = capabilities.linkDownstreamBandwidthKbps
            val uploadSpeed = capabilities.linkUpstreamBandwidthKbps

            return Pair(downloadSpeed, uploadSpeed)
        }

        // Return estimates for older devices
        return when (getNetworkType()) {
            "WiFi" -> Pair(10000, 5000) // 10 Mbps down, 5 Mbps up
            "Cellular" -> Pair(5000, 2000) // 5 Mbps down, 2 Mbps up
            else -> Pair(1000, 500)
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        // Check for IPv4 address
                        val isIPv4 = sAddr?.indexOf(':') ?: -1 < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    fun isServerReachable(serverIp: String, timeout: Int = 3000): Boolean {
        return try {
            val address = InetAddress.getByName(serverIp)
            address.isReachable(timeout)
        } catch (e: Exception) {
            logger.e(TAG, "Error checking server reachability", e)
            false
        }
    }

    fun getNetworkInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>()

        info["isAvailable"] = isNetworkAvailable()
        info["type"] = getNetworkType()

        val (download, upload) = getNetworkSpeed()
        info["downloadSpeed"] = "$download Kbps"
        info["uploadSpeed"] = "$upload Kbps"

        info["localIp"] = getLocalIpAddress() ?: "Unknown"

        // Signal strength for cellular
        if (getNetworkType() == "Cellular" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capabilities?.signalStrength ?: -1
            } else {
                -1
            }
            info["signalStrength"] = signalStrength
        }

        logger.d(TAG, "Network info: $info")
        return info
    }

    fun logNetworkStats() {
        logger.d(TAG, "=== Network Stats ===")
        getNetworkInfo().forEach { (key, value) ->
            logger.d(TAG, "$key: $value")
        }
        logger.d(TAG, "===================")
    }
}