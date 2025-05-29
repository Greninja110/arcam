package com.arcam.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.util.Size
import androidx.core.app.NotificationCompat
import com.arcam.MainActivity
import com.arcam.R
import com.arcam.models.StreamConfig
import com.arcam.utils.Logger
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class StreamingService : Service() {

    private lateinit var logger: Logger
    private var streamConfig: StreamConfig? = null

    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val threadPool = Executors.newFixedThreadPool(4) // Multi-threading

    // Camera and audio
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var audioRecord: AudioRecord? = null

    // Streaming state
    private val isStreaming = AtomicBoolean(false)
    private var streamingJob: Job? = null

    // Stats
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0f
    private var totalBytesStreamed = 0L
    private var currentBitrate = 0

    // Notification
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "arcam_streaming_channel"

    companion object {
        private const val TAG = "StreamingService"

        const val EXTRA_CONFIG = "stream_config"

        // Broadcast actions
        const val ACTION_STREAM_STATUS = "com.arcam.STREAM_STATUS"
        const val ACTION_STREAM_URL = "com.arcam.STREAM_URL"

        // Broadcast extras
        const val EXTRA_IS_STREAMING = "is_streaming"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val EXTRA_URL = "url"
    }

    override fun onCreate() {
        super.onCreate()
        logger = Logger.getInstance(this)
        logger.d(TAG, "StreamingService created")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d(TAG, "onStartCommand called")

        streamConfig = intent?.getParcelableExtra(EXTRA_CONFIG)
        if (streamConfig == null) {
            logger.e(TAG, "No stream configuration provided")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        startStreaming()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Arcam Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Streaming notification for Arcam"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arcam Streaming")
            .setContentText("Streaming ${streamConfig?.mode?.name}")
            .setSmallIcon(R.drawable.ic_stream)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startStreaming() {
        if (isStreaming.get()) {
            logger.w(TAG, "Already streaming")
            return
        }

        isStreaming.set(true)
        logger.d(TAG, "Starting stream: ${streamConfig?.mode}")

        streamingJob = serviceScope.launch {
            try {
                when (streamConfig?.mode) {
                    StreamConfig.Mode.IMAGE_ONLY -> startImageStreaming()
                    StreamConfig.Mode.AUDIO_ONLY -> startAudioStreaming()
                    StreamConfig.Mode.VIDEO_ONLY -> startVideoStreaming()
                    StreamConfig.Mode.AUDIO_VIDEO -> startAudioVideoStreaming()
                    else -> logger.e(TAG, "Unknown streaming mode")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Streaming error", e)
                stopStreaming()
            }
        }

        // Broadcast streaming URL
        val streamUrl = buildStreamUrl()
        sendBroadcast(Intent(ACTION_STREAM_URL).apply {
            putExtra(EXTRA_URL, streamUrl)
        })

        // Start stats monitoring
        startStatsMonitoring()
    }

    private fun buildStreamUrl(): String {
        val config = streamConfig ?: return ""
        val endpoint = when (config.mode) {
            StreamConfig.Mode.IMAGE_ONLY -> "img"
            StreamConfig.Mode.AUDIO_ONLY -> "audio"
            StreamConfig.Mode.VIDEO_ONLY -> "video"
            StreamConfig.Mode.AUDIO_VIDEO -> "video"
        }
        return "http://${config.serverIp}:${config.serverPort}/$endpoint"
    }

    private suspend fun startImageStreaming() = coroutineScope {
        logger.d(TAG, "Starting image streaming")

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        // Setup image reader
        val quality = streamConfig?.quality ?: StreamConfig.Quality.MEDIUM_720P
        val size = Size(quality.width, quality.height)

        imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.JPEG, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            threadPool.execute {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    sendImageToServer(bytes)

                    frameCount++
                    totalBytesStreamed += bytes.size

                    image.close()
                } catch (e: Exception) {
                    logger.e(TAG, "Error processing image", e)
                    image.close()
                }
            }
        }, null)

        // Open camera
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    logger.d(TAG, "Camera opened")
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    logger.w(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    logger.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: SecurityException) {
            logger.e(TAG, "Camera permission denied", e)
        }
    }

    private fun createCaptureSession() {
        val surface = imageReader?.surface ?: return

        cameraDevice?.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    logger.d(TAG, "Capture session configured")
                    captureSession = session
                    startRepeatingCapture()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    logger.e(TAG, "Capture session configuration failed")
                }
            },
            null
        )
    }

    private fun startRepeatingCapture() {
        val surface = imageReader?.surface ?: return
        val device = cameraDevice ?: return
        val session = captureSession ?: return

        val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        }

        session.setRepeatingRequest(captureRequest.build(), null, null)
        logger.d(TAG, "Started repeating capture")
    }

    private fun sendImageToServer(imageData: ByteArray) {
        try {
            val url = URL("http://${streamConfig?.serverIp}:${streamConfig?.serverPort}/upload_frame")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "image/jpeg")
                setRequestProperty("Content-Length", imageData.size.toString())
                connectTimeout = 5000
                readTimeout = 5000
            }

            connection.outputStream.use { output ->
                output.write(imageData)
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.w(TAG, "Server response: $responseCode")
            }

            connection.disconnect()
        } catch (e: Exception) {
            logger.e(TAG, "Error sending image to server", e)
        }
    }

    private suspend fun startAudioStreaming() = coroutineScope {
        logger.d(TAG, "Starting audio streaming")

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            logger.d(TAG, "Audio recording started")

            val audioBuffer = ByteArray(bufferSize)

            while (isStreaming.get()) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    sendAudioToServer(audioBuffer.copyOfRange(0, bytesRead))
                    totalBytesStreamed += bytesRead
                }

                yield() // Allow cancellation
            }
        } catch (e: SecurityException) {
            logger.e(TAG, "Audio permission denied", e)
        }
    }

    private fun sendAudioToServer(audioData: ByteArray) {
        threadPool.execute {
            try {
                val url = URL("http://${streamConfig?.serverIp}:${streamConfig?.serverPort}/upload_audio")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "audio/pcm")
                    setRequestProperty("Content-Length", audioData.size.toString())
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                connection.outputStream.use { output ->
                    output.write(audioData)
                    output.flush()
                }

                connection.disconnect()
            } catch (e: Exception) {
                logger.e(TAG, "Error sending audio to server", e)
            }
        }
    }

    private suspend fun startVideoStreaming() = coroutineScope {
        // Video streaming implementation would be similar to image streaming
        // but with video encoding using MediaCodec
        logger.d(TAG, "Starting video streaming")
        startImageStreaming() // Simplified for now
    }

    private suspend fun startAudioVideoStreaming() = coroutineScope {
        logger.d(TAG, "Starting audio+video streaming")

        // Launch both audio and video streaming coroutines
        launch { startVideoStreaming() }
        launch { startAudioStreaming() }
    }

    private fun startStatsMonitoring() {
        serviceScope.launch {
            while (isStreaming.get()) {
                delay(1000) // Update every second

                // Calculate FPS
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastFpsTime
                if (timeDiff >= 1000) {
                    currentFps = (frameCount * 1000f) / timeDiff
                    frameCount = 0
                    lastFpsTime = currentTime

                    // Calculate bitrate
                    currentBitrate = ((totalBytesStreamed * 8) / (timeDiff / 1000)).toInt()
                    totalBytesStreamed = 0

                    // Broadcast stats
                    sendBroadcast(Intent(ACTION_STREAM_STATUS).apply {
                        putExtra(EXTRA_IS_STREAMING, isStreaming.get())
                        putExtra(EXTRA_FPS, currentFps)
                        putExtra(EXTRA_BITRATE, currentBitrate)
                    })

                    logger.d(TAG, "Stats - FPS: ${currentFps.roundToInt()}, Bitrate: ${currentBitrate / 1000} Kbps")
                }
            }
        }
    }

    private fun stopStreaming() {
        logger.d(TAG, "Stopping streaming")

        isStreaming.set(false)
        streamingJob?.cancel()

        // Clean up camera
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()

        // Clean up audio
        audioRecord?.stop()
        audioRecord?.release()

        captureSession = null
        cameraDevice = null
        imageReader = null
        audioRecord = null

        // Send final broadcast
        sendBroadcast(Intent(ACTION_STREAM_STATUS).apply {
            putExtra(EXTRA_IS_STREAMING, false)
            putExtra(EXTRA_FPS, 0f)
            putExtra(EXTRA_BITRATE, 0)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d(TAG, "Service destroyed")

        stopStreaming()
        serviceScope.cancel()
        threadPool.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}