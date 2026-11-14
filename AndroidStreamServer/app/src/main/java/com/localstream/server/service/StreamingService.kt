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
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_PIN = "EXTRA_PIN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _activePin = MutableStateFlow("8492")
        val activePin: StateFlow<String> = _activePin.asStateFlow()

        private val _serverUrl = MutableStateFlow("http://192.168.43.1:8080")
        val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

        private val _scannedFiles = MutableStateFlow<List<MediaFile>>(emptyList())
        val scannedFiles: StateFlow<List<MediaFile>> = _scannedFiles.asStateFlow()

        private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
        val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

        var currentServerInstance: StreamingHttpServer? = null

        fun updateFileList(files: List<MediaFile>) {
            _scannedFiles.value = files
            currentServerInstance?.updateFiles(files)
        }

        fun updatePin(pin: String) {
            _activePin.value = pin
            currentServerInstance?.pin = pin
        }

        fun addLog(msg: String) {
            val current = _serverLogs.value.toMutableList()
            if (current.size > 50) current.removeAt(0)
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
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                val pin = intent.getStringExtra(EXTRA_PIN) ?: _activePin.value
                startServer(port, pin)
            }
        }
        return START_NOT_STICKY
    }

    private fun startServer(port: Int, pin: String) {
        if (_isRunning.value) return

        _activePin.value = pin
        val ip = NetworkUtils.getPrimaryIpAddress()
        val url = "http://$ip:$port"
        _serverUrl.value = url

        server = StreamingHttpServer(applicationContext, port, pin).apply {
            onRequestLogged = { msg ->
                addLog(msg)
            }
            updateFiles(_scannedFiles.value)
            start()
        }
        currentServerInstance = server
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Streaming Server Active")
            .setContentText("$url • PIN: $pin")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notifications when local media streaming server is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
