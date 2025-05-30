package com.arcam.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.arcam.R
import com.arcam.databinding.FragmentStreamBinding
import com.arcam.models.StreamConfig
import com.arcam.services.StreamingService
import com.arcam.utils.Logger
import com.arcam.utils.PreferenceManager
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import android.content.Context.RECEIVER_NOT_EXPORTED

class StreamFragment : Fragment() {

    private var _binding: FragmentStreamBinding? = null
    private val binding get() = _binding!!

    private lateinit var logger: Logger
    private lateinit var prefManager: PreferenceManager

    // Camera
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Stream state
    private var streamConfig: StreamConfig? = null
    private var isFlashOn = false
    private var currentZoom = 1.0f

    // Broadcast receiver
    private val streamStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                StreamingService.ACTION_STREAM_STATUS -> {
                    val isStreaming = intent.getBooleanExtra(StreamingService.EXTRA_IS_STREAMING, false)
                    val fps = intent.getFloatExtra(StreamingService.EXTRA_FPS, 0f)
                    val bitrate = intent.getIntExtra(StreamingService.EXTRA_BITRATE, 0)

                    updateStreamStatus(isStreaming, fps, bitrate)
                }
                StreamingService.ACTION_STREAM_URL -> {
                    val url = intent.getStringExtra(StreamingService.EXTRA_URL) ?: ""
                    binding.tvStreamUrl.text = "URL: $url"
                }
            }
        }
    }

    companion object {
        private const val TAG = "StreamFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamBinding.inflate(inflater, container, false)
        logger = Logger.getInstance(requireContext())
        prefManager = PreferenceManager(requireContext())

        logger.d(TAG, "StreamFragment created")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load stream configuration
        loadStreamConfig()

        // Setup UI based on mode
        setupUIForMode()

        // Setup controls
        setupControls()

        // Start camera if needed
        if (streamConfig?.mode != StreamConfig.Mode.AUDIO_ONLY) {
            startCamera()
        }

        // Register broadcast receiver
        registerReceivers()
    }

    private fun loadStreamConfig() {
        streamConfig = StreamConfig(
            mode = prefManager.getStreamMode(),
            quality = prefManager.getStreamQuality(),
            serverIp = prefManager.getServerIp(),
            serverPort = prefManager.getServerPort()
        )

        logger.d(TAG, "Stream config loaded: Mode=${streamConfig?.mode}, Quality=${streamConfig?.quality}")
    }

    private fun setupUIForMode() {
        when (streamConfig?.mode) {
            StreamConfig.Mode.IMAGE_ONLY -> {
                binding.previewView.visibility = View.VISIBLE
                binding.audioIndicatorLayout.visibility = View.GONE
                binding.btnCapture.visibility = View.VISIBLE
                binding.cameraControls.visibility = View.VISIBLE
            }
            StreamConfig.Mode.AUDIO_ONLY -> {
                binding.previewView.visibility = View.GONE
                binding.audioIndicatorLayout.visibility = View.VISIBLE
                binding.cameraControls.visibility = View.GONE
            }
            StreamConfig.Mode.VIDEO_ONLY, StreamConfig.Mode.AUDIO_VIDEO -> {
                binding.previewView.visibility = View.VISIBLE
                binding.audioIndicatorLayout.visibility = View.GONE
                binding.btnCapture.visibility = View.GONE
                binding.cameraControls.visibility = View.VISIBLE
            }
            else -> {}
        }
    }

    private fun setupControls() {
        // Stop streaming button
        binding.btnStopStream.setOnClickListener {
            stopStreaming()
        }

        // Camera controls
        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnZoomIn.setOnClickListener {
            zoomIn()
        }

        binding.btnZoomOut.setOnClickListener {
            zoomOut()
        }

        binding.btnCapture.setOnClickListener {
            captureImage()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                logger.e(TAG, "Error starting camera", e)
                Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // Preview use case
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        // Image capture use case (for image mode)
        if (streamConfig?.mode == StreamConfig.Mode.IMAGE_ONLY) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        }

        // Select camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = if (imageCapture != null) {
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview
                )
            }

            logger.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            logger.e(TAG, "Use case binding failed", e)
        }
    }

    private fun toggleFlash() {
        camera?.let {
            isFlashOn = !isFlashOn
            it.cameraControl.enableTorch(isFlashOn)

            binding.btnFlash.setImageResource(
                if (isFlashOn) R.drawable.ic_menu_flash_on
                else R.drawable.ic_menu_flash_off
            )

            binding.btnFlash.contentDescription = getString(
                if (isFlashOn) R.string.flash_on else R.string.flash_off
            )

            logger.d(TAG, "Flash toggled: $isFlashOn")
        }
    }

    private fun zoomIn() {
        camera?.let {
            currentZoom = min(currentZoom * 1.2f, it.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f)
            it.cameraControl.setZoomRatio(currentZoom)
            updateZoomText()
        }
    }

    private fun zoomOut() {
        camera?.let {
            currentZoom = max(currentZoom * 0.8f, 1f)
            it.cameraControl.setZoomRatio(currentZoom)
            updateZoomText()
        }
    }

    private fun updateZoomText() {
        binding.tvZoom.text = String.format("%.1fx", currentZoom)
    }

    private fun captureImage() {
        // This would capture and send a single image
        Toast.makeText(context, "Image captured", Toast.LENGTH_SHORT).show()
        logger.d(TAG, "Image capture triggered")
    }

    private fun updateStreamStatus(isStreaming: Boolean, fps: Float, bitrate: Int) {
        if (isStreaming) {
            binding.tvStatus.text = "Status: Streaming"
            binding.tvStats.text = "FPS: ${fps.toInt()} | Bitrate: ${bitrate / 1000} Kbps"
        } else {
            binding.tvStatus.text = "Status: Stopped"
            binding.tvStats.text = "FPS: 0 | Bitrate: 0 Kbps"
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(StreamingService.ACTION_STREAM_STATUS)
            addAction(StreamingService.ACTION_STREAM_URL)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(streamStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(streamStatusReceiver, filter)
        }
    }

    private fun stopStreaming() {
        logger.d(TAG, "Stopping stream")

        // Stop the streaming service
        val intent = Intent(requireContext(), StreamingService::class.java)
        requireContext().stopService(intent)

        // Navigate back to mode fragment
        requireActivity().onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister receiver
        try {
            requireContext().unregisterReceiver(streamStatusReceiver)
        } catch (e: Exception) {
            logger.e(TAG, "Error unregistering receiver", e)
        }

        // Clean up camera
        cameraExecutor.shutdown()

        _binding = null
        logger.d(TAG, "StreamFragment destroyed")
    }
}