package com.localstream.server.server

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.gson.Gson
import com.localstream.server.model.MediaFile
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * High-performance, lightweight embedded HTTP server supporting:
 * - GET /files (JSON listing of available media files)
 * - GET /stream/{id} & HEAD /stream/{id} (HTTP Range partial content streaming for video seeking)
 * - Basic PIN authentication token protection
 * - CORS headers for cross-origin web/client requests
 */
class StreamingHttpServer(
    private val context: Context,
    val port: Int = 8080,
    var pin: String = "8492"
) {
    companion object {
        private const val TAG = "StreamingHttpServer"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer
    }

    private val filesMap = ConcurrentHashMap<String, MediaFile>()
    private val gson = Gson()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newFixedThreadPool(16)
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Request listener for UI log display
    var onRequestLogged: ((String) -> Unit)? = null

    fun updateFiles(files: List<MediaFile>) {
        filesMap.clear()
        for (f in files) {
            filesMap[f.id] = f
        }
        log("Updated server file library with ${files.size} items.")
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                log("Streaming server started on port $port. Ready for connections.")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        threadPool.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        if (!isRunning) break
                        Log.e(TAG, "Socket accept error", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Server loop exception", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server on port $port", e)
                log("Server start failed: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        log("Streaming server stopped.")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000 // 15s timeout
            val inputStream = BufferedInputStream(socket.getInputStream())
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(outputStream, 400, "Bad Request", "text/plain", "Bad Request".toByteArray())
                return
            }

            val method = parts[0].uppercase()
            val rawPath = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrBlank()) break
                val colon = headerLine!!.indexOf(':')
                if (colon > 0) {
                    val key = headerLine!!.substring(0, colon).trim().lowercase()
                    val value = headerLine!!.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            // Parse path and query parameters
            val questionMark = rawPath.indexOf('?')
            val path = if (questionMark >= 0) rawPath.substring(0, questionMark) else rawPath
            val queryStr = if (questionMark >= 0) rawPath.substring(questionMark + 1) else ""
            val queryParams = parseQueryParams(queryStr)

            log("$method $path (Range: ${headers["range"] ?: "none"})")

            // Handle CORS OPTIONS preflight
            if (method == "OPTIONS") {
                sendCorsOptionsResponse(outputStream)
                return
            }

            // PIN Security Verification
            val requestPin = queryParams["pin"]
                ?: headers["x-pin"]
                ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()

            if (requestPin != pin) {
                log("401 Unauthorized - Invalid PIN token: '$requestPin'")
                val json = "{\"error\":\"Unauthorized: Invalid or missing PIN token\"}"
                sendResponse(outputStream, 401, "Unauthorized", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
                return
            }

            // Route Requests
            when {
                path == "/files" && method == "GET" -> {
                    handleGetFiles(outputStream)
                }
                path.startsWith("/stream/") && (method == "GET" || method == "HEAD") -> {
                    val fileId = path.removePrefix("/stream/")
                    handleStream(outputStream, fileId, method, headers["range"])
                }
                else -> {
                    val notFound = "{\"error\":\"Endpoint not found: $path\"}"
                    sendResponse(outputStream, 404, "Not Found", "application/json; charset=utf-8", notFound.toByteArray(StandardCharsets.UTF_8))
                }
            }

        } catch (e: SocketException) {
            // Client closed connection during seek or disconnect - normal behavior in video streaming
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request", e)
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun handleGetFiles(outputStream: OutputStream) {
        val list = filesMap.values.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "mimeType" to it.mimeType,
                "size" to it.size
            )
        }
        val json = gson.toJson(list)
        val data = json.toByteArray(StandardCharsets.UTF_8)
        sendResponse(outputStream, 200, "OK", "application/json; charset=utf-8", data)
    }

    private fun handleStream(outputStream: OutputStream, fileId: String, method: String, rangeHeader: String?) {
        val mediaFile = filesMap[fileId]
        if (mediaFile == null || mediaFile.uri == null) {
            val err = "{\"error\":\"File ID not found: $fileId\"}"
            sendResponse(outputStream, 404, "Not Found", "application/json; charset=utf-8", err.toByteArray(StandardCharsets.UTF_8))
            return
        }

        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(mediaFile.uri, "r")
            if (pfd == null) {
                val err = "{\"error\":\"Could not open file descriptor for $fileId\"}"
                sendResponse(outputStream, 500, "Internal Server Error", "application/json; charset=utf-8", err.toByteArray(StandardCharsets.UTF_8))
                return
            }

            val totalLength = if (pfd.statSize > 0) pfd.statSize else mediaFile.size

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Parse Range Header for seeking
                val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
                val parts = rangeSpec.split("-")
                var start: Long = 0
                var end: Long = totalLength - 1

                try {
                    if (parts[0].isNotEmpty()) {
                        start = parts[0].toLong()
                        if (parts.size > 1 && parts[1].isNotEmpty()) {
                            end = parts[1].toLong()
                        }
                    } else if (parts.size > 1 && parts[1].isNotEmpty()) {
                        // Suffix range: bytes=-500 (last 500 bytes)
                        val suffix = parts[1].toLong()
                        start = (totalLength - suffix).coerceAtLeast(0)
                        end = totalLength - 1
                    }
                } catch (e: Exception) {
                    start = 0
                    end = totalLength - 1
                }

                // Bounds validation
                if (start >= totalLength || start > end) {
                    val header = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                            "Content-Range: bytes */$totalLength\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(StandardCharsets.US_ASCII))
                    outputStream.flush()
                    return
                }

                end = end.coerceAtMost(totalLength - 1)
                val contentLength = end - start + 1

                val headers = StringBuilder()
                headers.append("HTTP/1.1 206 Partial Content\r\n")
                headers.append("Content-Type: ").append(mediaFile.mimeType).append("\r\n")
                headers.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(totalLength).append("\r\n")
                headers.append("Content-Length: ").append(contentLength).append("\r\n")
                headers.append("Accept-Ranges: bytes\r\n")
                headers.append("Access-Control-Allow-Origin: *\r\n")
                headers.append("Access-Control-Expose-Headers: Content-Range, Content-Length, Accept-Ranges\r\n")
                headers.append("Connection: close\r\n\r\n")

                outputStream.write(headers.toString().toByteArray(StandardCharsets.US_ASCII))
                outputStream.flush()

                if (method == "GET") {
                    streamFileRange(pfd, start, contentLength, outputStream)
                }
            } else {
                // Full content request (HTTP 200 OK)
                val headers = StringBuilder()
                headers.append("HTTP/1.1 200 OK\r\n")
                headers.append("Content-Type: ").append(mediaFile.mimeType).append("\r\n")
                headers.append("Content-Length: ").append(totalLength).append("\r\n")
                headers.append("Accept-Ranges: bytes\r\n")
                headers.append("Access-Control-Allow-Origin: *\r\n")
                headers.append("Access-Control-Expose-Headers: Content-Range, Content-Length, Accept-Ranges\r\n")
                headers.append("Connection: close\r\n\r\n")

                outputStream.write(headers.toString().toByteArray(StandardCharsets.US_ASCII))
                outputStream.flush()

                if (method == "GET") {
                    streamFileRange(pfd, 0, totalLength, outputStream)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error streaming file $fileId", e)
        } finally {
            try {
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun streamFileRange(pfd: ParcelFileDescriptor, offset: Long, length: Long, outputStream: OutputStream) {
        FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            channel.position(offset)

            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length

            while (remaining > 0 && isRunning) {
                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                val bytesRead = fis.read(buffer, 0, toRead)
                if (bytesRead == -1) break

                outputStream.write(buffer, 0, bytesRead)
                remaining -= bytesRead
            }
            outputStream.flush()
        }
    }

    private fun sendResponse(out: OutputStream, code: Int, status: String, contentType: String, data: ByteArray) {
        val response = "HTTP/1.1 $code $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"

        out.write(response.toByteArray(StandardCharsets.US_ASCII))
        out.write(data)
        out.flush()
    }

    private fun sendCorsOptionsResponse(out: OutputStream) {
        val response = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Range, X-PIN, Authorization, Content-Type\r\n" +
                "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"

        out.write(response.toByteArray(StandardCharsets.US_ASCII))
        out.flush()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        onRequestLogged?.invoke(message)
    }
}
