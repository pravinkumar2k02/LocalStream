package com.localstream.server.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.localstream.server.model.DeviceSession
import com.localstream.server.model.MediaFile
import com.localstream.server.service.StreamingService
import com.localstream.server.util.NetworkAddressInfo
import com.localstream.server.util.NetworkUtils
import com.localstream.server.util.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamingViewModel(application: Application) : AndroidViewModel(application) {

    val isRunning: StateFlow<Boolean> = StreamingService.isRunning
    val isHttps: StateFlow<Boolean> = StreamingService.isHttps
    val files: StateFlow<List<MediaFile>> = StreamingService.scannedFiles
    val serverLogs: StateFlow<List<String>> = StreamingService.serverLogs
    val authorizedDevices: StateFlow<List<DeviceSession>> = StreamingService.authorizedDevices

    private val _currentPin = MutableStateFlow(generateRandomPin())
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _port = MutableStateFlow(8443)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _useHttps = MutableStateFlow(true)
    val useHttps: StateFlow<Boolean> = _useHttps.asStateFlow()

    private val _rateLimit = MutableStateFlow(30) // requests per second
    val rateLimit: StateFlow<Int> = _rateLimit.asStateFlow()

    private val _maxFailedAttempts = MutableStateFlow(5)
    val maxFailedAttempts: StateFlow<Int> = _maxFailedAttempts.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _selectedFolderName = MutableStateFlow<String?>(null)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _networkAddresses = MutableStateFlow<List<NetworkAddressInfo>>(emptyList())
    val networkAddresses: StateFlow<List<NetworkAddressInfo>> = _networkAddresses.asStateFlow()

    private val gson = Gson()

    init {
        refreshNetworkAddresses()
        StreamingService.updatePin(_currentPin.value)
    }

    fun refreshNetworkAddresses() {
        _networkAddresses.value = NetworkUtils.getAvailableNetworkAddresses()
    }

    fun regeneratePin() {
        val newPin = generateRandomPin()
        _currentPin.value = newPin
        StreamingService.updatePin(newPin)
    }

    fun setUseHttps(enabled: Boolean) {
        _useHttps.value = enabled
        _port.value = if (enabled) 8443 else 8080
        if (isRunning.value) {
            val context = getApplication<Application>()
            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_RESTART
                putExtra(StreamingService.EXTRA_PORT, _port.value)
                putExtra(StreamingService.EXTRA_PIN, _currentPin.value)
                putExtra(StreamingService.EXTRA_USE_HTTPS, _useHttps.value)
                putExtra(StreamingService.EXTRA_RATE_LIMIT, _rateLimit.value)
                putExtra(StreamingService.EXTRA_MAX_FAILED, _maxFailedAttempts.value)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun setRateLimit(limit: Int) {
        _rateLimit.value = limit.coerceIn(5, 200)
        StreamingService.updateRateLimitSettings(_rateLimit.value, _maxFailedAttempts.value, 60)
    }

    fun setMaxFailedAttempts(max: Int) {
        _maxFailedAttempts.value = max.coerceIn(3, 20)
        StreamingService.updateRateLimitSettings(_rateLimit.value, _maxFailedAttempts.value, 60)
    }

    fun onQrCodeScanned(contents: String): Pair<Boolean, String> {
        try {
            var token = ""
            var clientName = "Laptop"

            if (contents.startsWith("localstream://auth")) {
                val uri = Uri.parse(contents)
                token = uri.getQueryParameter("token") ?: ""
                clientName = uri.getQueryParameter("client") ?: "Laptop Client"
            } else if (contents.trim().startsWith("{")) {
                @Suppress("UNCHECKED_CAST")
                val map = gson.fromJson(contents, Map::class.java) as Map<String, Any>
                token = map["token"]?.toString() ?: ""
                clientName = map["client"]?.toString() ?: "Laptop Client"
            } else {
                // Raw token string
                token = contents.trim()
                clientName = "Device-${token.take(6)}"
            }

            if (token.length < 8) {
                return Pair(false, "Scanned QR code does not contain a valid session token.")
            }

            StreamingService.authorizeDevice(token, clientName)
            return Pair(true, "Authorized: $clientName")
        } catch (e: Exception) {
            return Pair(false, "Error parsing QR code: ${e.message}")
        }
    }

    fun authorizeManualToken(token: String, clientName: String = "Manual Client"): Boolean {
        if (token.isBlank()) return false
        StreamingService.authorizeDevice(token.trim(), clientName.ifBlank { "Manual Client" })
        return true
    }

    fun revokeDevice(token: String) {
        StreamingService.revokeDevice(token)
    }

    fun onFolderSelected(uri: Uri) {
        _selectedFolderUri.value = uri
        StorageHelper.takePersistablePermissions(getApplication(), uri)

        val path = uri.path ?: uri.toString()
        val displayName = path.substringAfterLast(':').substringAfterLast('/')
        _selectedFolderName.value = if (displayName.isNotBlank()) displayName else "Selected Folder"

        scanFolder(uri)
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            val scanned = StorageHelper.scanFolder(getApplication(), uri)
            StreamingService.updateFileList(scanned)
            _isScanning.value = false
        }
    }

    fun toggleServer() {
        val context = getApplication<Application>()
        if (isRunning.value) {
            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            refreshNetworkAddresses()
            val intent = Intent(context, StreamingService::class.java).apply {
                action = StreamingService.ACTION_START
                putExtra(StreamingService.EXTRA_PORT, _port.value)
                putExtra(StreamingService.EXTRA_PIN, _currentPin.value)
                putExtra(StreamingService.EXTRA_USE_HTTPS, _useHttps.value)
                putExtra(StreamingService.EXTRA_RATE_LIMIT, _rateLimit.value)
                putExtra(StreamingService.EXTRA_MAX_FAILED, _maxFailedAttempts.value)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun generateRandomPin(): String {
        return (1000..9999).random().toString()
    }
}
