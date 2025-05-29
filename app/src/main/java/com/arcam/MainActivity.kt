package com.arcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.arcam.databinding.ActivityMainBinding
import com.arcam.fragments.ModeFragment
import com.arcam.fragments.StreamFragment
import com.arcam.fragments.SettingsFragment
import com.arcam.utils.Logger
import com.arcam.utils.PermissionManager
import com.google.android.material.navigation.NavigationBarView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logger: Logger
    private lateinit var permissionManager: PermissionManager

    // Thread pool for multi-threaded operations
    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    )

    companion object {
        private const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize logger
        logger = Logger.getInstance(this)
        logger.d(TAG, "onCreate started")
        logger.d(TAG, "Device: Nothing Phone 2 detected")
        logger.d(TAG, "Available CPU cores: ${Runtime.getRuntime().availableProcessors()}")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize permission manager
        permissionManager = PermissionManager(this)

        // Setup UI
        setupBottomNavigation()

        // Check permissions
        checkAndRequestPermissions()

        // Log system stats
        logSystemStats()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(this)

        // Set default fragment
        if (permissionManager.hasAllPermissions()) {
            loadFragment(ModeFragment())
        }
    }

    private fun checkAndRequestPermissions() {
        logger.d(TAG, "Checking permissions...")

        if (!permissionManager.hasAllPermissions()) {
            logger.w(TAG, "Permissions not granted, requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSIONS
            )
        } else {
            logger.d(TAG, "All permissions granted")
            loadFragment(ModeFragment())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                logger.d(TAG, "All permissions granted by user")
                loadFragment(ModeFragment())
            } else {
                logger.e(TAG, "Some permissions denied")
                Toast.makeText(
                    this,
                    "Camera and Microphone permissions are required for streaming",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        logger.d(TAG, "Navigation item selected: ${item.title}")

        return when (item.itemId) {
            R.id.navigation_mode -> {
                loadFragment(ModeFragment())
                true
            }
            R.id.navigation_stream -> {
                loadFragment(StreamFragment())
                true
            }
            R.id.navigation_settings -> {
                loadFragment(SettingsFragment())
                true
            }
            else -> false
        }
    }

    private fun loadFragment(fragment: Fragment) {
        threadPool.execute {
            runOnUiThread {
                try {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit()
                    logger.d(TAG, "Fragment loaded: ${fragment::class.simpleName}")
                } catch (e: Exception) {
                    logger.e(TAG, "Error loading fragment", e)
                }
            }
        }
    }

    fun navigateToStream() {
        logger.d(TAG, "Navigating to stream fragment")
        binding.bottomNavigation.selectedItemId = R.id.navigation_stream
    }

    private fun logSystemStats() {
        threadPool.execute {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
            val maxMemory = runtime.maxMemory() / 1048576L

            logger.d(TAG, "=== System Stats ===")
            logger.d(TAG, "Used Memory: $usedMemory MB")
            logger.d(TAG, "Max Memory: $maxMemory MB")
            logger.d(TAG, "Available Processors: ${runtime.availableProcessors()}")
            logger.d(TAG, "==================")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d(TAG, "onDestroy called")
        threadPool.shutdown()
    }
}