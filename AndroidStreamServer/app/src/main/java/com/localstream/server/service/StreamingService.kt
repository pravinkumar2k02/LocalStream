package com.localstream.server.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localstream.server.MainActivity
import com.localstream.server.model.DeviceSession
import com.localstream.server.model.MediaFile
import com.localstream.server.server.StreamingHttpServer
import com.localstream.server.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StreamingService : Service() {

    companion object {
        private const val CHANNEL_ID = "local_streaming_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_PIN = "EXTRA_PIN"
        const val EXTRA_USE_HTTPS = "EXTRA_USE_HTTPS"
        const val EXTRA_RATE_LIMIT = "EXTRA_RATE_LIMIT"
        const val EXTRA_MAX_FAILED = "EXTRA_MAX_FAILED"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isHttps = MutableStateFlow(true)
        val isHttps: StateFlow<Boolean> = _isHttps.asStateFlow()

        private val _activePin = MutableStateFlow("8492")
        val activePin: StateFlow<String> = _activePin.asStateFlow()

        private val _serverUrl = MutableStateFlow("https://192.168.43.1:8443")
        val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

        private val _scannedFiles = MutableStateFlow<List<MediaFile>>(emptyList())
        val scannedFiles: StateFlow<List<MediaFile>> = _scannedFiles.asStateFlow()

        private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
        val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

        private val _authorizedDevices = MutableStateFlow<List<DeviceSession>>(emptyList())
        val authorizedDevices: StateFlow<List<DeviceSession>> = _authorizedDevices.asStateFlow()

        var currentServerInstance: StreamingHttpServer? = null

        fun updateFileList(files: List<MediaFile>) {
            _scannedFiles.value = files
            currentServerInstance?.updateFiles(files)
        }

        fun updatePin(pin: String) {
            _activePin.value = pin
            currentServerInstance?.pin = pin
        }

        fun authorizeDevice(token: String, clientName: String): DeviceSession? {
            val session = currentServerInstance?.authorizeDevice(token, clientName)
            refreshDevicesList()
            return session
        }

        fun revokeDevice(token: String) {
            currentServerInstance?.revokeDevice(token)
            refreshDevicesList()
        }

        fun updateRateLimitSettings(reqPerSec: Int, maxFailed: Int, banSec: Int) {
            currentServerInstance?.updateRateLimitSettings(reqPerSec, maxFailed, banSec)
        }

        fun refreshDevicesList() {
            _authorizedDevices.value = currentServerInstance?.authorizedSessions?.values?.toList() ?: emptyList()
        }

        fun addLog(msg: String) {
            val current = _serverLogs.value.toMutableList()
            if (current.size > 60) current.removeAt(0)
            current.add(msg)
            _serverLogs.value = current
        }
    }

    private var server: StreamingHttpServer? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8443)
                val pin = intent.getStringExtra(EXTRA_PIN) ?: _activePin.value
                val useHttps = intent.getBooleanExtra(EXTRA_USE_HTTPS, true)
                val rateLimit = intent.getIntExtra(EXTRA_RATE_LIMIT, 30)
                val maxFailed = intent.getIntExtra(EXTRA_MAX_FAILED, 5)
                startServer(port, pin, useHttps, rateLimit, maxFailed)
            }
            ACTION_RESTART -> {
                val port = intent.getIntExtra(EXTRA_PORT, if (_isHttps.value) 8443 else 8080)
                val pin = intent.getStringExtra(EXTRA_PIN) ?: _activePin.value
                val useHttps = intent.getBooleanExtra(EXTRA_USE_HTTPS, true)
                val rateLimit = intent.getIntExtra(EXTRA_RATE_LIMIT, 30)
                val maxFailed = intent.getIntExtra(EXTRA_MAX_FAILED, 5)
                restartServer(port, pin, useHttps, rateLimit, maxFailed)
            }
        }
        return START_NOT_STICKY
    }

    private fun restartServer(port: Int, pin: String, useHttps: Boolean, rateLimit: Int, maxFailed: Int) {
        val existingSessions = currentServerInstance?.authorizedSessions?.values?.toList() ?: emptyList()
        server?.stop()
        server = null
        currentServerInstance = null
        _isRunning.value = false
        startServer(port, pin, useHttps, rateLimit, maxFailed)
        for (session in existingSessions) {
            server?.authorizeDevice(session.token, session.clientName)
        }
    }

    private fun startServer(port: Int, pin: String, useHttps: Boolean, rateLimit: Int, maxFailed: Int) {
        if (_isRunning.value) return

        _activePin.value = pin
        _isHttps.value = useHttps
        val ip = NetworkUtils.getPrimaryIpAddress()
        val scheme = if (useHttps) "https" else "http"
        val url = "$scheme://$ip:$port"
        _serverUrl.value = url

        server = StreamingHttpServer(
            context = applicationContext,
            port = port,
            pin = pin,
            useHttps = useHttps,
            maxRequestsPerSecond = rateLimit,
            maxFailedAttempts = maxFailed
        ).apply {
            onRequestLogged = { msg ->
                addLog(msg)
            }
            onSessionUpdated = {
                refreshDevicesList()
            }
            updateFiles(_scannedFiles.value)
            start()
        }
        currentServerInstance = server
        refreshDevicesList()
        _isRunning.value = true

        val notification = buildNotification(url, pin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        currentServerInstance = null
        _authorizedDevices.value = emptyList()
        _isRunning.value = false
    }

    private fun buildNotification(url: String, pin: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val protocol = if (_isHttps.value) "HTTPS (Encrypted)" else "HTTP"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Local Stream Server ($protocol)")
            .setContentText("URL: $url | PIN: $pin")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Streaming Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while streaming media over Wi-Fi"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
