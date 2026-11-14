package com.localstream.server.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

sealed class RateLimitResult {
    object Allowed : RateLimitResult()
    data class RateExceeded(val retryAfterSeconds: Int) : RateLimitResult()
    data class Banned(val remainingSeconds: Long) : RateLimitResult()
}

/**
 * High-performance sliding-window rate limiter & brute force protection.
 * - Enforces requests-per-second threshold per client IP.
 * - Temporarily bans client IPs that exceed maximum failed authentication attempts.
 */
class RateLimiter(
    var maxRequestsPerSecond: Int = 30,
    var maxFailedAttempts: Int = 5,
    var banDurationSeconds: Int = 60
) {
    // Client IP -> Timestamps of requests in the last second
    private val requestHistory = ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>()

    // Client IP -> Number of consecutive failed auth attempts
    private val failedAuthCounts = ConcurrentHashMap<String, Int>()

    // Client IP -> Timestamp (ms) until which the IP is banned
    private val bannedUntil = ConcurrentHashMap<String, Long>()

    /**
     * Checks whether an incoming request from the given IP is allowed.
     */
    fun checkRequest(clientIp: String): RateLimitResult {
        val now = System.currentTimeMillis()

        // 1. Check if IP is currently banned
        val banExpiry = bannedUntil[clientIp]
        if (banExpiry != null) {
            if (now < banExpiry) {
                val remainingSeconds = (banExpiry - now) / 1000L + 1
                return RateLimitResult.Banned(remainingSeconds)
            } else {
                // Ban expired
                bannedUntil.remove(clientIp)
                failedAuthCounts.remove(clientIp)
            }
        }

        // 2. Sliding window requests-per-second check
        val history = requestHistory.computeIfAbsent(clientIp) { ConcurrentLinkedQueue() }
        history.add(now)

        // Remove timestamps older than 1 second (1000ms)
        val cutoff = now - 1000L
        while (history.isNotEmpty() && (history.peek() ?: 0L) < cutoff) {
            history.poll()
        }

        if (history.size > maxRequestsPerSecond) {
            return RateLimitResult.RateExceeded(2)
        }

        return RateLimitResult.Allowed
    }

    /**
     * Records a failed authentication attempt.
     * If the threshold is reached, bans the IP for banDurationSeconds.
     */
    fun recordFailedAuth(clientIp: String): Boolean {
        val count = failedAuthCounts.compute(clientIp) { _, current -> (current ?: 0) + 1 } ?: 1
        if (count >= maxFailedAttempts) {
            val banExpiry = System.currentTimeMillis() + banDurationSeconds * 1000L
            bannedUntil[clientIp] = banExpiry
            return true // newly banned
        }
        return false
    }

    /**
     * Records a successful authentication, resetting failed attempts.
     */
    fun recordSuccessfulAuth(clientIp: String) {
        failedAuthCounts.remove(clientIp)
    }

    fun unbanIp(clientIp: String) {
        bannedUntil.remove(clientIp)
        failedAuthCounts.remove(clientIp)
    }

    fun clear() {
        requestHistory.clear()
        failedAuthCounts.clear()
        bannedUntil.clear()
    }
}
