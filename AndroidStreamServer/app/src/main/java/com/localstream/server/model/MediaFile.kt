package com.localstream.server.model

import android.net.Uri
import java.text.DecimalFormat

/**
 * Model representing a media file exposed by the Android streaming server.
 */
data class MediaFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    @Transient val uri: Uri? = null
) {
    fun getFormattedSize(): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun isVideo(): Boolean {
        val m = mimeType.lowercase()
        val n = name.lowercase()
        return m.startsWith("video/") || n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov")
    }

    fun isAudio(): Boolean {
        val m = mimeType.lowercase()
        val n = name.lowercase()
        return m.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac") || n.endsWith(".ogg")
    }
}
