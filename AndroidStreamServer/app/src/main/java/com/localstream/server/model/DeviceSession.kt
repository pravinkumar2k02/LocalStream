package com.localstream.server.model

/**
 * Represents a client device authorized via QR code or cryptographic token.
 */
data class DeviceSession(
    val token: String,
    val clientName: String,
    val authorizedAt: Long = System.currentTimeMillis(),
    var lastSeenAt: Long = System.currentTimeMillis(),
    var ipAddress: String = ""
)
