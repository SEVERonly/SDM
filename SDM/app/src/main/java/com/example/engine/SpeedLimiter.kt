package com.example.engine

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Thread-safe token bucket rate limiter for dynamic download speed throttling
 */
class SpeedLimiter(
    @Volatile var maxBytesPerSecond: Long = 0L // 0 = Unlimited
) {
    private val availableTokens = AtomicLong(0L)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    suspend fun acquire(bytesRequested: Int) {
        val limit = maxBytesPerSecond
        if (limit <= 0L) return // Unlimited

        while (true) {
            refill(limit)
            val current = availableTokens.get()
            if (current >= bytesRequested) {
                if (availableTokens.compareAndSet(current, current - bytesRequested)) {
                    return
                }
            } else {
                // Not enough tokens, sleep briefly to let tokens accrue
                val deficit = bytesRequested - current
                val waitMs = ((deficit * 1000L) / limit).coerceIn(10L, 500L)
                delay(waitMs)
            }
        }
    }

    private fun refill(limit: Long) {
        val now = System.currentTimeMillis()
        val last = lastRefillTime.get()
        val elapsedMs = (now - last).coerceAtLeast(0L)

        if (elapsedMs > 50L) {
            if (lastRefillTime.compareAndSet(last, now)) {
                val newTokens = (limit * elapsedMs) / 1000L
                val maxCapacity = limit * 2 // allow small burst
                availableTokens.updateAndGet { curr ->
                    min(maxCapacity, curr + newTokens)
                }
            }
        }
    }
}
