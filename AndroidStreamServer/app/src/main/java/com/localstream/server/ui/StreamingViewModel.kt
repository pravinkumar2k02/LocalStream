package com.localstream.server.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localstream.server.model.MediaFile
import com.localstream.server.service.StreamingService
import com.localstream.server.util.NetworkAddressInfo
import com.localstream.server.util.NetworkUtils
import com.localstream.server.util.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class StreamingViewModel(application: Application) : AndroidViewModel(application) {

    val isRunning: StateFlow<Boolean> = StreamingService.isRunning
    val files: StateFlow<List<MediaFile>> = StreamingService.scannedFiles
    val serverLogs: StateFlow<List<String>> = StreamingService.serverLogs

    private val _currentPin = MutableStateFlow(generateRandomPin())
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _selectedFolderName = MutableStateFlow<String?>(null)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _networkAddresses = MutableStateFlow<List<NetworkAddressInfo>>(emptyList())
    val networkAddresses: StateFlow<List<NetworkAddressInfo>> = _networkAddresses.asStateFlow()

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

    fun onFolderSelected(uri: Uri) {
        _selectedFolderUri.value = uri
        StorageHelper.takePersistablePermissions(getApplication(), uri)

        // Extract folder display name
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
