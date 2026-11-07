package com.arflix.tv.network

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class NetworkMonitorTest {
    @Test fun frameworkSecurityFailureDoesNotBlockPlayback() {
        verifyFallback(SecurityException("Framework UID lookup failed"))
    }

    @Test fun frameworkRuntimeFailureUsesTheSameFallback() {
        verifyFallback(IllegalStateException("Framework unavailable"))
    }

    private fun verifyFallback(failure: RuntimeException) {
        val manager = mockk<ConnectivityManager>()
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns mockk()
        every { manager.getNetworkCapabilities(any()) } throws failure
        val monitor = NetworkMonitor(context)
        assertTrue(monitor.isConnected)
        assertEquals(NetworkType.OTHER, monitor.getNetworkType())
    }
}
