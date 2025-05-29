package com.arcam.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.arcam.MainActivity
import com.arcam.databinding.FragmentModeBinding
import com.arcam.models.StreamConfig
import com.arcam.services.StreamingService
import com.arcam.utils.Logger
import com.arcam.utils.NetworkUtils
import com.arcam.utils.PreferenceManager

class ModeFragment : Fragment() {

    private var _binding: FragmentModeBinding? = null
    private val binding get() = _binding!!

    private lateinit var logger: Logger
    private lateinit var prefManager: PreferenceManager
    private lateinit var networkUtils: NetworkUtils

    private var selectedMode: StreamConfig.Mode? = null
    private var selectedQuality: StreamConfig.Quality? = null

    companion object {
        private const val TAG = "ModeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModeBinding.inflate(inflater, container, false)
        logger = Logger.getInstance(requireContext())
        prefManager = PreferenceManager(requireContext())
        networkUtils = NetworkUtils(requireContext())

        logger.d(TAG, "ModeFragment created")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadSavedPreferences()
        updateStreamButton()
    }

    private fun setupUI() {
        // Mode selection
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            selectedMode = when (checkedId) {
                binding.radioImageOnly.id -> StreamConfig.Mode.IMAGE_ONLY
                binding.radioAudioOnly.id -> StreamConfig.Mode.AUDIO_ONLY
                binding.radioVideoOnly.id -> StreamConfig.Mode.VIDEO_ONLY
                binding.radioAudioVideo.id -> StreamConfig.Mode.AUDIO_VIDEO
                else -> null
            }
            logger.d(TAG, "Selected mode: $selectedMode")
            updateStreamButton()
        }

        // Quality selection
        binding.radioGroupQuality.setOnCheckedChangeListener { _, checkedId ->
            selectedQuality = when (checkedId) {
                binding.radio480p.id -> StreamConfig.Quality.LOW_480P
                binding.radio720p.id -> StreamConfig.Quality.MEDIUM_720P
                binding.radio1080p.id -> StreamConfig.Quality.HIGH_1080P
                else -> null
            }
            logger.d(TAG, "Selected quality: $selectedQuality")
            updateStreamButton()
        }

        // Start stream button
        binding.btnStartStream.setOnClickListener {
            startStreaming()
        }

        // Show status button (initially hidden)
        binding.btnShowStatus.setOnClickListener {
            showStreamStatus()
        }
    }

    private fun loadSavedPreferences() {
        // Load saved mode
        val savedMode = prefManager.getStreamMode()
        when (savedMode) {
            StreamConfig.Mode.IMAGE_ONLY -> binding.radioImageOnly.isChecked = true
            StreamConfig.Mode.AUDIO_ONLY -> binding.radioAudioOnly.isChecked = true
            StreamConfig.Mode.VIDEO_ONLY -> binding.radioVideoOnly.isChecked = true
            StreamConfig.Mode.AUDIO_VIDEO -> binding.radioAudioVideo.isChecked = true
        }

        // Load saved quality
        val savedQuality = prefManager.getStreamQuality()
        when (savedQuality) {
            StreamConfig.Quality.LOW_480P -> binding.radio480p.isChecked = true
            StreamConfig.Quality.MEDIUM_720P -> binding.radio720p.isChecked = true
            StreamConfig.Quality.HIGH_1080P -> binding.radio1080p.isChecked = true
        }
    }

    private fun updateStreamButton() {
        val isEnabled = selectedMode != null && selectedQuality != null
        binding.btnStartStream.isEnabled = isEnabled

        if (isEnabled) {
            binding.btnStartStream.alpha = 1.0f
        } else {
            binding.btnStartStream.alpha = 0.5f
        }
    }

    private fun startStreaming() {
        if (selectedMode == null || selectedQuality == null) {
            logger.e(TAG, "Mode or quality not selected")
            return
        }

        // Check network connectivity
        if (!networkUtils.isNetworkAvailable()) {
            Toast.makeText(context, "No network connection available", Toast.LENGTH_SHORT).show()
            return
        }

        logger.d(TAG, "Starting stream with mode: $selectedMode, quality: $selectedQuality")

        // Save preferences
        prefManager.saveStreamMode(selectedMode!!)
        prefManager.saveStreamQuality(selectedQuality!!)

        // Create stream configuration
        val config = StreamConfig(
            mode = selectedMode!!,
            quality = selectedQuality!!,
            serverIp = prefManager.getServerIp(),
            serverPort = prefManager.getServerPort()
        )

        // Start streaming service
        val serviceIntent = Intent(requireContext(), StreamingService::class.java).apply {
            putExtra(StreamingService.EXTRA_CONFIG, config)
        }
        requireContext().startService(serviceIntent)

        // Show status button
        binding.btnShowStatus.visibility = View.VISIBLE

        // Log streaming stats
        logger.d(TAG, "=== Stream Started ===")
        logger.d(TAG, "Mode: ${selectedMode?.name}")
        logger.d(TAG, "Quality: ${selectedQuality?.resolution}")
        logger.d(TAG, "Bitrate: ${selectedQuality?.bitrate}")
        logger.d(TAG, "Server: ${config.serverIp}:${config.serverPort}")
        logger.d(TAG, "====================")

        // Navigate to stream fragment
        (activity as? MainActivity)?.navigateToStream()
    }

    private fun showStreamStatus() {
        val config = StreamConfig(
            mode = selectedMode!!,
            quality = selectedQuality!!,
            serverIp = prefManager.getServerIp(),
            serverPort = prefManager.getServerPort()
        )

        val streamUrl = when (selectedMode) {
            StreamConfig.Mode.IMAGE_ONLY -> "http://${config.serverIp}:${config.serverPort}/img"
            StreamConfig.Mode.AUDIO_ONLY -> "http://${config.serverIp}:${config.serverPort}/audio"
            StreamConfig.Mode.VIDEO_ONLY -> "http://${config.serverIp}:${config.serverPort}/video"
            StreamConfig.Mode.AUDIO_VIDEO -> "http://${config.serverIp}:${config.serverPort}/video"
            else -> ""
        }

        val status = """
            Stream URL: $streamUrl
            Mode: ${selectedMode?.name}
            Quality: ${selectedQuality?.resolution}
            Network: ${networkUtils.getNetworkType()}
        """.trimIndent()

        binding.tvStreamStatus.text = status
        binding.tvStreamStatus.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        logger.d(TAG, "ModeFragment destroyed")
    }
}