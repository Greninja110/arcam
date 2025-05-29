package com.arcam.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StreamConfig(
    val mode: Mode,
    val quality: Quality,
    val serverIp: String,
    val serverPort: Int
) : Parcelable {

    enum class Mode {
        IMAGE_ONLY,
        AUDIO_ONLY,
        VIDEO_ONLY,
        AUDIO_VIDEO
    }

    enum class Quality(
        val resolution: String,
        val width: Int,
        val height: Int,
        val bitrate: Int // in bps
    ) {
        LOW_480P("480p (Low)", 640, 480, 1_000_000),
        MEDIUM_720P("720p (Medium)", 1280, 720, 2_500_000),
        HIGH_1080P("1080p (High)", 1920, 1080, 5_000_000)
    }
}