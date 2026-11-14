package com.localstream.server.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.localstream.server.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object StorageHelper {

    /**
     * Persists URI permissions so the app can continue accessing the folder across restarts.
     */
    fun takePersistablePermissions(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Scans the selected folder and returns the list of media and general files.
     */
    suspend fun scanFolder(context: Context, treeUri: Uri): List<MediaFile> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val results = mutableListOf<MediaFile>()

        fun scanDoc(doc: DocumentFile) {
            val children = doc.listFiles()
            for (child in children) {
                if (child.isDirectory) {
                    // Recursively scan subfolders
                    scanDoc(child)
                } else if (child.isFile) {
                    val name = child.name ?: "Unknown"
                    val size = child.length()
                    if (size > 0) {
                        val mimeType = resolveMimeType(context, child.uri, name, child.type)
                        val id = (results.size + 1).toString()
                        results.add(
                            MediaFile(
                                id = id,
                                name = name,
                                mimeType = mimeType,
                                size = size,
                                uri = child.uri
                            )
                        )
                    }
                }
            }
        }

        scanDoc(root)
        results
    }

    /**
     * Resolves accurate MIME type for a file, supporting MP4, WebM, MKV, MP3, etc.
     */
    fun resolveMimeType(context: Context, uri: Uri, fileName: String, docType: String?): String {
        if (!docType.isNullOrBlank() && docType != "application/octet-stream") {
            return docType
        }

        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex > 0 && dotIndex < fileName.length - 1) {
            val extension = fileName.substring(dotIndex + 1).lowercase(Locale.ROOT)
            val mimeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!mimeFromExt.isNullOrBlank()) {
                return mimeFromExt
            }

            // Fallback for common media extensions
            return when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "flv" -> "video/x-flv"
                "ts" -> "video/mp2t"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg", "oga" -> "audio/ogg"
                "flac" -> "audio/flac"
                "aac" -> "audio/aac"
                "m4a" -> "audio/mp4"
                else -> "application/octet-stream"
            }
        }

        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }
}
