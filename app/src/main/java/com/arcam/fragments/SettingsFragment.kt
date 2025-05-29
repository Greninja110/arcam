package com.arcam.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.arcam.BuildConfig
import com.arcam.R
import com.arcam.databinding.FragmentSettingsBinding
import com.arcam.utils.Logger
import com.arcam.utils.NetworkUtils
import com.arcam.utils.PreferenceManager
import kotlinx.coroutines.*
import java.io.File
import java.util.regex.Pattern

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var logger: Logger
    private lateinit var prefManager: PreferenceManager
    private lateinit var networkUtils: NetworkUtils

    private val settingsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "SettingsFragment"
        private val IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        logger = Logger.getInstance(requireContext())
        prefManager = PreferenceManager(requireContext())
        networkUtils = NetworkUtils(requireContext())

        logger.d(TAG, "SettingsFragment created")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        // Server settings
        binding.etServerIp.setText(prefManager.getServerIp())
        binding.etServerPort.setText(prefManager.getServerPort().toString())

        // Video settings
        val frameRate = prefManager.getFrameRate()
        binding.seekbarFrameRate.progress = frameRate
        binding.tvFrameRateValue.text = frameRate.toString()

        // Audio settings
        binding.switchAudio.isChecked = prefManager.isAudioEnabled()
        setupAudioSampleRateSpinner()

        // Advanced settings
        binding.switchHardwareAcceleration.isChecked = prefManager.isHardwareAccelerationEnabled()
        binding.switchAutoReconnect.isChecked = prefManager.isAutoReconnectEnabled()

        // Debug settings
        binding.switchDebugMode.isChecked = prefManager.isDebugModeEnabled()

        logger.d(TAG, "Settings loaded")
    }

    private fun setupAudioSampleRateSpinner() {
        val sampleRates = arrayOf("44100 Hz", "48000 Hz")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sampleRates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSampleRate.adapter = adapter

        val currentRate = prefManager.getAudioSampleRate()
        val position = if (currentRate == 44100) 0 else 1
        binding.spinnerSampleRate.setSelection(position)
    }

    private fun setupListeners() {
        // Save server settings
        binding.btnSaveServer.setOnClickListener {
            saveServerSettings()
        }

        // Frame rate seekbar
        binding.seekbarFrameRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvFrameRateValue.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefManager.saveFrameRate(seekBar?.progress ?: 30)
                logger.d(TAG, "Frame rate saved: ${seekBar?.progress}")
            }
        })

        // Audio switch
        binding.switchAudio.setOnCheckedChangeListener { _, isChecked ->
            prefManager.saveAudioEnabled(isChecked)
            binding.spinnerSampleRate.isEnabled = isChecked
            logger.d(TAG, "Audio enabled: $isChecked")
        }

        // Hardware acceleration switch
        binding.switchHardwareAcceleration.setOnCheckedChangeListener { _, isChecked ->
            prefManager.saveHardwareAcceleration(isChecked)
            logger.d(TAG, "Hardware acceleration: $isChecked")
        }

        // Auto reconnect switch
        binding.switchAutoReconnect.setOnCheckedChangeListener { _, isChecked ->
            prefManager.saveAutoReconnect(isChecked)
            logger.d(TAG, "Auto reconnect: $isChecked")
        }

        // Debug mode switch
        binding.switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            prefManager.saveDebugMode(isChecked)
            logger.d(TAG, "Debug mode: $isChecked")
        }

        // View logs button
        binding.btnViewLogs.setOnClickListener {
            viewLogs()
        }

        // Clear logs button
        binding.btnClearLogs.setOnClickListener {
            showClearLogsDialog()
        }

        // Reset settings button
        binding.btnResetSettings.setOnClickListener {
            showResetSettingsDialog()
        }
    }

    private fun saveServerSettings() {
        val ip = binding.etServerIp.text.toString().trim()
        val portStr = binding.etServerPort.text.toString().trim()

        // Validate IP address
        if (!IP_PATTERN.matcher(ip).matches()) {
            binding.tilServerIp.error = getString(R.string.error_invalid_ip)
            return
        } else {
            binding.tilServerIp.error = null
        }

        // Validate port
        val port = try {
            portStr.toInt()
        } catch (e: NumberFormatException) {
            binding.tilServerPort.error = getString(R.string.error_invalid_port)
            return
        }

        if (port !in 1..65535) {
            binding.tilServerPort.error = getString(R.string.error_port_range)
            return
        } else {
            binding.tilServerPort.error = null
        }

        // Save settings
        prefManager.saveServerIp(ip)
        prefManager.saveServerPort(port)

        // Test connection
        testServerConnection(ip, port)

        Toast.makeText(context, getString(R.string.success_server_saved), Toast.LENGTH_SHORT).show()
        logger.d(TAG, "Server settings saved: $ip:$port")
    }

    private fun testServerConnection(ip: String, port: Int) {
        settingsScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                networkUtils.isServerReachable(ip, 3000)
            }

            if (reachable) {
                Toast.makeText(context, "Server is reachable!", Toast.LENGTH_SHORT).show()
                logger.d(TAG, "Server $ip is reachable")
            } else {
                Toast.makeText(context, getString(R.string.error_server_unreachable), Toast.LENGTH_LONG).show()
                logger.w(TAG, "Server $ip is not reachable")
            }
        }
    }

    private fun viewLogs() {
        val logFile = logger.getLogFile()
        if (!logFile.exists()) {
            Toast.makeText(context, "No logs found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                logFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "View Logs"))
            logger.d(TAG, "Viewing logs")
        } catch (e: Exception) {
            logger.e(TAG, "Error viewing logs", e)
            Toast.makeText(context, "Error viewing logs", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearLogsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_clear_logs_title))
            .setMessage(getString(R.string.dialog_clear_logs_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                logger.clearLogs()
                Toast.makeText(context, getString(R.string.success_logs_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    private fun showResetSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_reset_title))
            .setMessage(getString(R.string.dialog_reset_message))
            .setPositiveButton(getString(R.string.dialog_reset)) { _, _ ->
                prefManager.resetToDefaults()
                loadSettings() // Reload UI with default values
                Toast.makeText(context, getString(R.string.success_settings_reset), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        settingsScope.cancel()
        _binding = null
        logger.d(TAG, "SettingsFragment destroyed")
    }
}