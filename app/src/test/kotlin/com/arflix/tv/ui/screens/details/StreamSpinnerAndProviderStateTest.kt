package com.arflix.tv.ui.screens.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Job

/** The source picker waits for pending work and reports available providers. */
class StreamSpinnerAndProviderStateTest {

    @Test
    fun `empty source searches stop for every completion order including addons last`() {
        val orders = listOf(
            listOf(0, 1), listOf(1, 0)
        )
        // Slots represent the main add-on search and home server jobs.
        for (order in orders) {
            val jobs = List(2) { Job() }
            for ((position, slot) in order.withIndex()) {
                val shouldStop = shouldStopStreamSpinner(
                    isLoadingStreams = true,
                    hasStreams = false,

                    otherSourceJobsActive = hasOtherActiveStreamJobs(jobs, jobs[slot])
                )
                org.junit.Assert.assertEquals("Order $order, finishing $slot", position == 1, shouldStop)
                jobs[slot].complete()
            }
        }
    }

    @Test
    fun `finishing job is excluded by identity while it is still active`() {
        val finishing = Job()
        assertTrue(finishing.isActive)
        assertFalse(hasOtherActiveStreamJobs(listOf(finishing, null, null), finishing))
        val other = Job()
        assertTrue(hasOtherActiveStreamJobs(listOf(finishing, other), finishing))
        other.complete()
        assertFalse(hasOtherActiveStreamJobs(listOf(finishing, other), finishing))
        finishing.complete()
    }

    @Test
    fun `failed and cancelled provider jobs do not keep the final search waiting`() {
        val main = Job()
        val failed = Job().apply { completeExceptionally(IllegalStateException("offline")) }
        val cancelled = Job().apply { cancel() }
        assertFalse(hasOtherActiveStreamJobs(listOf(main, failed, cancelled), main))
        main.complete()
    }

    @Test
    fun `last source job finding nothing stops the spinner`() {
        assertTrue(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = false,

                otherSourceJobsActive = false
            )
        )
    }

    @Test
    fun `spinner keeps running while another source job is still working`() {
        assertFalse(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = false,

                otherSourceJobsActive = true
            )
        )
    }



    @Test
    fun `sources already on screen are never taken as a reason to touch the flag`() {
        assertFalse(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = true,

                otherSourceJobsActive = false
            )
        )
    }

    @Test
    fun `no addon is the only empty setup`() {
        assertFalse(
            hasAnyStreamProvider(
                streamingAddonCount = 0,

            )
        )
        assertTrue(
            hasAnyStreamProvider(
                streamingAddonCount = 1,

            )
        )

    }
}
