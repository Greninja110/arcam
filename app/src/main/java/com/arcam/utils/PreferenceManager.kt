package com.arcam.utils

import android.content.Context
import android.content.SharedPreferences
import com.arcam.models.StreamConfig

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val logger = Logger.getInstance(context)

    companion object {
        private const val TAG = "PreferenceManager"
        private const val PREF_NAME = "arcam_preferences"

        // Keys
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_STREAM_MODE = "stream_mode"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_AUDIO_ENABLED = "audio_enabled"
        private const val KEY_AUDIO_SAMPLE_RATE = "audio_sample_rate"
        private const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_DEBUG_MODE = "debug_mode"

        // Default values
        private const val DEFAULT_SERVER_IP = "192.168.1.100"
        private const val DEFAULT_SERVER_PORT = 5000
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_AUDIO_SAMPLE_RATE = 44100
        private const val DEFAULT_BUFFER_SIZE = 2048 // KB
    }

    // Server settings
    fun getServerIp(): String = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
    fun saveServerIp(ip: String) {
        prefs.edit().putString(KEY_SERVER_IP, ip).apply()
        logger.d(TAG, "Server IP saved: $ip")
    }

    fun getServerPort(): Int = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
    fun saveServerPort(port: Int) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()
        logger.d(TAG, "Server port saved: $port")
    }

    // Stream settings
    fun getStreamMode(): StreamConfig.Mode {
        val modeName = prefs.getString(KEY_STREAM_MODE, StreamConfig.Mode.VIDEO_ONLY.name)
        return try {
            StreamConfig.Mode.valueOf(modeName ?: StreamConfig.Mode.VIDEO_ONLY.name)
        } catch (e: IllegalArgumentException) {
            StreamConfig.Mode.VIDEO_ONLY
        }
    }

    fun saveStreamMode(mode: StreamConfig.Mode) {
        prefs.edit().putString(KEY_STREAM_MODE, mode.name).apply()
        logger.d(TAG, "Stream mode saved: ${mode.name}")
    }

    fun getStreamQuality(): StreamConfig.Quality {
        val qualityName = prefs.getString(KEY_STREAM_QUALITY, StreamConfig.Quality.MEDIUM_720P.name)
        return try {
            StreamConfig.Quality.valueOf(qualityName ?: StreamConfig.Quality.MEDIUM_720P.name)
        } catch (e: IllegalArgumentException) {
            StreamConfig.Quality.MEDIUM_720P
        }
    }

    fun saveStreamQuality(quality: StreamConfig.Quality) {
        prefs.edit().putString(KEY_STREAM_QUALITY, quality.name).apply()
        logger.d(TAG, "Stream quality saved: ${quality.name}")
    }

    // Video settings
    fun getFrameRate(): Int = prefs.getInt(KEY_FRAME_RATE, DEFAULT_FRAME_RATE)
    fun saveFrameRate(frameRate: Int) {
        prefs.edit().putInt(KEY_FRAME_RATE, frameRate).apply()
        logger.d(TAG, "Frame rate saved: $frameRate")
    }

    // Audio settings
    fun isAudioEnabled(): Boolean = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
    fun saveAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_ENABLED, enabled).apply()
        logger.d(TAG, "Audio enabled saved: $enabled")
    }

    fun getAudioSampleRate(): Int = prefs.getInt(KEY_AUDIO_SAMPLE_RATE, DEFAULT_AUDIO_SAMPLE_RATE)
    fun saveAudioSampleRate(sampleRate: Int) {
        prefs.edit().putInt(KEY_AUDIO_SAMPLE_RATE, sampleRate).apply()
        logger.d(TAG, "Audio sample rate saved: $sampleRate")
    }

    // Advanced settings
    fun isHardwareAccelerationEnabled(): Boolean = prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true)
    fun saveHardwareAcceleration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDWARE_ACCELERATION, enabled).apply()
        logger.d(TAG, "Hardware acceleration saved: $enabled")
    }

    fun isAutoReconnectEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
    fun saveAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        logger.d(TAG, "Auto reconnect saved: $enabled")
    }

    fun getBufferSize(): Int = prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)
    fun saveBufferSize(bufferSize: Int) {
        prefs.edit().putInt(KEY_BUFFER_SIZE, bufferSize).apply()
        logger.d(TAG, "Buffer size saved: $bufferSize KB")
    }

    // Debug settings
    fun isDebugModeEnabled(): Boolean = prefs.getBoolean(KEY_DEBUG_MODE, false)
    fun saveDebugMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        logger.d(TAG, "Debug mode saved: $enabled")
    }

    // Reset
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        logger.d(TAG, "All preferences reset to defaults")

        // Log default values
        logger.d(TAG, "=== Default Settings ===")
        logger.d(TAG, "Server IP: $DEFAULT_SERVER_IP")
        logger.d(TAG, "Server Port: $DEFAULT_SERVER_PORT")
        logger.d(TAG, "Frame Rate: $DEFAULT_FRAME_RATE")
        logger.d(TAG, "Audio Sample Rate: $DEFAULT_AUDIO_SAMPLE_RATE")
        logger.d(TAG, "Buffer Size: $DEFAULT_BUFFER_SIZE KB")
        logger.d(TAG, "======================")
    }

    // Export all settings
    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "serverIp" to getServerIp(),
            "serverPort" to getServerPort(),
            "streamMode" to getStreamMode().name,
            "streamQuality" to getStreamQuality().name,
            "frameRate" to getFrameRate(),
            "audioEnabled" to isAudioEnabled(),
            "audioSampleRate" to getAudioSampleRate(),
            "hardwareAcceleration" to isHardwareAccelerationEnabled(),
            "autoReconnect" to isAutoReconnectEnabled(),
            "bufferSize" to getBufferSize(),
            "debugMode" to isDebugModeEnabled()
        )
    }

    fun logAllSettings() {
        logger.d(TAG, "=== Current Settings ===")
        exportSettings().forEach { (key, value) ->
            logger.d(TAG, "$key: $value")
        }
        logger.d(TAG, "======================")
    }
}