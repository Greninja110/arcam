package com.arcam

import android.app.Application
import com.arcam.utils.Logger
import java.io.File

class ArcamApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logger
        val logger = Logger.getInstance(this)

        // Set up uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.e("UncaughtException", "Uncaught exception in thread ${thread.name}", throwable)

            // Let the default handler also handle it
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }

        // Log app startup
        logger.d("ArcamApplication", "=== Arcam Application Started ===")
        logger.d("ArcamApplication", "Version: ${BuildConfig.VERSION_NAME}")
        logger.d("ArcamApplication", "Build Type: ${BuildConfig.BUILD_TYPE}")
        logger.d("ArcamApplication", "Device: Nothing Phone 2")
        logger.d("ArcamApplication", "Android Version: ${android.os.Build.VERSION.RELEASE}")
        logger.d("ArcamApplication", "SDK Version: ${android.os.Build.VERSION.SDK_INT}")
        logger.d("ArcamApplication", "================================")

        // Clean up old log files (keep only last 7 days)
        cleanOldLogs()
    }

    private fun cleanOldLogs() {
        try {
            val logsDir = File(getExternalFilesDir(null), "logs")
            if (logsDir.exists() && logsDir.isDirectory) {
                val currentTime = System.currentTimeMillis()
                val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L

                logsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("arcam_log_")) {
                        if (currentTime - file.lastModified() > sevenDaysInMillis) {
                            file.delete()
                            Logger.getInstance(this).d("ArcamApplication", "Deleted old log file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.getInstance(this).e("ArcamApplication", "Error cleaning old logs", e)
        }
    }
}