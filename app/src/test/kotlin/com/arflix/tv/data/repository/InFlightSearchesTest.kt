package com.arflix.tv.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InFlightSearchesTest {
    @Test fun `second asker joins same ordered result and search runs once`() = runTest {
        val table = InFlightSearches<List<String>>(backgroundScope)
        val answer = CompletableDeferred<List<String>>()
        var calls = 0
        val first = table.acquire("movie|addons:1") { calls++; answer.await() }
        val second = table.acquire("movie|addons:1") { calls++; error("duplicate search") }
        assertFalse(first.joined)
        assertTrue(second.joined)
        assertSame(first.result, second.result)
        runCurrent()
        assertEquals(1, calls)
        answer.complete(listOf("UU second quality", "UU first quality"))
        assertEquals(listOf("UU second quality", "UU first quality"), second.result.await())
        assertEquals(first.result.await(), second.result.await())
        first.release()
        second.release()
        assertEquals(0, table.size)
    }

    @Test fun `page release keeps search alive for player waiter`() = runTest {
        val table = InFlightSearches<Int>(backgroundScope)
        val answer = CompletableDeferred<Int>()
        val page = table.acquire("episode") { answer.await() }
        val player = table.acquire("episode") { error("duplicate") }
        page.release()
        page.release() // Disposal and the cancelled page collector can both release.
        assertFalse(player.result.isCancelled)
        answer.complete(42)
        assertEquals(42, player.result.await())
        player.release()
    }

    @Test fun `Play keeps search alive across gap before player acquires`() = runTest {
        val table = InFlightSearches<Int>(backgroundScope)
        val answer = CompletableDeferred<Int>()
        val page = table.acquire("movie") { answer.await() }
        page.markPlayPressed()
        page.release()
        assertEquals(1, table.size)
        assertFalse(page.result.isCancelled)
        val player = table.acquire("movie") { error("duplicate") }
        assertTrue(player.joined)
        player.release()
        assertFalse(page.result.isCancelled)
        answer.complete(7)
        assertEquals(7, page.result.await())
        assertEquals(0, table.size)
    }

    @Test fun `back without Play cancels search and never caches partial result`() = runTest {
        val table = InFlightSearches<List<String>>(backgroundScope)
        val answer = CompletableDeferred<List<String>>()
        val cache = mutableMapOf<String, List<String>>()
        var stopped = false
        val page = table.acquire("movie") {
            val partial = mutableListOf("partial")
            try {
                partial += answer.await()
                partial.toList().also { cache["movie"] = it }
            } finally {
                stopped = true
            }
        }
        runCurrent()
        page.release()
        runCurrent()
        assertTrue(page.result.isCancelled)
        assertTrue(stopped)
        assertTrue(cache.isEmpty())
        assertEquals(0, table.size)
        val fresh = table.acquire("movie") { listOf("complete").also { cache["movie"] = it } }
        assertFalse(fresh.joined)
        assertEquals(listOf("complete"), fresh.result.await())
        assertEquals(listOf("complete"), cache["movie"])
        page.release() // Cannot remove a replacement entry.
        fresh.release()
    }

    @Test fun `finished and failed entries are removed without waiting for release`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val table = InFlightSearches<Int>(scope)
        val finished = table.acquire("movie") { 1 }
        assertEquals(1, finished.result.await())
        assertEquals(0, table.size)
        val failed = table.acquire("movie") { error("provider failed") }
        assertFalse(failed.joined)
        assertTrue(runCatching { failed.result.await() }.isFailure)
        assertEquals(0, table.size)
        finished.release()
        failed.release()
    }

    @Test fun `configuration and episode keys stay independent`() = runTest {
        val table = InFlightSearches<Int>(backgroundScope)
        val a = table.acquire("series|1|1|addons:1") { CompletableDeferred<Int>().await() }
        val b = table.acquire("series|1|2|addons:1") { 2 }
        val c = table.acquire("series|1|1|addons:2") { 3 }
        assertFalse(b.joined)
        assertFalse(c.joined)
        a.release()
        assertEquals(2, b.result.await())
        assertEquals(3, c.result.await())
        b.release()
        c.release()
    }

    @Test fun `late joiner receives latest snapshot then final`() = runTest {
        val table = InFlightSearches<List<String>>(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val partial = listOf("UU first")
        val final = listOf("UU first", "other addon")
        val page = table.acquire("movie") { publish ->
            publish(partial)
            finish.await()
            publish(final)
            final
        }
        runCurrent()
        val player = table.acquire("movie") { error("duplicate") }
        assertSame(partial, player.updates.value)
        val received = mutableListOf<List<String>>()
        val collector = launch { player.updates.filterNotNull().take(2).toList(received) }
        runCurrent()
        assertEquals(listOf(partial), received)
        finish.complete(Unit)
        collector.join()
        assertEquals(listOf(partial, final), received)
        assertSame(final, player.result.await())
        page.release()
        player.releaseAndClearPlay()
    }

    @Test fun `last player release clears Play and cancels search`() = runTest {
        val table = InFlightSearches<Int>(backgroundScope)
        val page = table.acquire("movie") { CompletableDeferred<Int>().await() }
        page.markPlayPressed()
        val player = table.acquire("movie") { error("duplicate") }
        page.release()
        player.releaseAndClearPlay()
        player.releaseAndClearPlay()
        player.release()
        assertTrue(player.result.isCancelled)
        assertEquals(0, table.size)
    }

    @Test fun `player release clearing Play keeps search alive for another waiter`() = runTest {
        val table = InFlightSearches<Int>(backgroundScope)
        val answer = CompletableDeferred<Int>()
        val page = table.acquire("movie") { answer.await() }
        page.markPlayPressed()
        val player = table.acquire("movie") { error("duplicate") }
        player.releaseAndClearPlay()
        player.releaseAndClearPlay()
        assertFalse(page.result.isCancelled)
        assertEquals(1, table.size)
        answer.complete(7)
        assertEquals(7, page.result.await())
        page.release()
        assertEquals(0, table.size)
    }

}
