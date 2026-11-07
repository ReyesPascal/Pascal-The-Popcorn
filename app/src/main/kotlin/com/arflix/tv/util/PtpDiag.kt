package com.arflix.tv.util

import android.content.Context
import android.util.Log
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.diag.DiagFormat
import com.arflix.tv.util.diag.DiagRollingFile
import com.arflix.tv.util.diag.AppDiagnostics
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Best-effort personal-build diagnostics. Public logging signatures are shared by all areas. */
object PtpDiag {
    const val TAG = "PTPDiag"
    private data class Entry(val priority: Int, val event: String, val time: Long, val fields: List<Pair<String, Any?>>, val error: Throwable? = null)
    private val queue = LinkedBlockingDeque<Entry>(2000)
    private val dropped = AtomicLong()
    private val sequence = AtomicLong()
    private val processId = UUID.randomUUID().toString().take(8)
    private val lock = Any()
    private val ring = ArrayDeque<String>(50)
    private var file: DiagRollingFile? = null
    @Volatile private var initialized = false
    @Volatile private var pendingContext: Context? = null
    private val writer = if (BuildConfig.PERSONAL_BUILD) Thread({ runWriter() }, "PTPDiag-writer").apply { isDaemon = true; start() } else null

    fun d(event: String, vararg fields: Pair<String, Any?>) = emit(Log.DEBUG, event, null, fields)
    fun i(event: String, vararg fields: Pair<String, Any?>) = emit(Log.INFO, event, null, fields)
    fun w(event: String, vararg fields: Pair<String, Any?>) = emit(Log.WARN, event, null, fields)
    fun e(event: String, error: Throwable?, vararg fields: Pair<String, Any?>) = emit(Log.ERROR, event, error, fields)

    fun init(context: Context) {
        if (!BuildConfig.PERSONAL_BUILD) return
        try {
            synchronized(lock) {
                if (initialized) return
                initialized = true
                pendingContext = context.applicationContext
            }
            // Directory and disk access stay off the application/main thread.
            AppDiagnostics.install(context.applicationContext as android.app.Application)
        } catch (_: Throwable) { }
    }

    fun recent(n: Int): List<String> = synchronized(lock) { ring.toList().takeLast(n.coerceIn(0, 50)) }

    private fun emit(priority: Int, event: String, error: Throwable?, fields: Array<out Pair<String, Any?>>) {
        if (!BuildConfig.PERSONAL_BUILD) return
        try {
            val entry = Entry(priority, event, System.currentTimeMillis(), fields.take(20), error)
            synchronized(queue) {
                if (!queue.offerLast(entry)) {
                    if (queue.pollFirst() != null) dropped.incrementAndGet()
                    if (!queue.offerLast(entry)) dropped.incrementAndGet()
                }
            }
        } catch (_: Throwable) { }
    }

    private fun runWriter() {
        var window = System.nanoTime()
        var written = 0
        while (true) try {
            val entry = queue.poll(1, TimeUnit.SECONDS)
            var waitMs = 0L
            synchronized(lock) {
                val now = System.nanoTime()
                if (now - window >= 1_000_000_000L) {
                    val lost = dropped.getAndSet(0)
                    if (lost > 0) write(Entry(Log.WARN, "diag-dropped", System.currentTimeMillis(), listOf("count" to lost)))
                    window = now
                    written = 0
                }
                openFile()
                if (entry != null) {
                    if (written < 49) { write(entry); written++ } else {
                        // Over the per-second limit: keep the entry (front of queue) and wait for the next window.
                        val back = synchronized(queue) { queue.offerFirst(entry) }
                        if (!back) dropped.incrementAndGet()
                        waitMs = ((1_000_000_000L - (now - window)) / 1_000_000L).coerceIn(1L, 1000L)
                    }
                }
            }
            if (waitMs > 0) Thread.sleep(waitMs)
        } catch (_: Throwable) { /* Never terminate the app because diagnostics failed. */ }
    }

    private fun openFile() {
        val context = pendingContext ?: return
        pendingContext = null
        runCatching { context.getExternalFilesDir(null)?.let { file = DiagRollingFile(File(it, "ptpdiag")) } }
    }

    private fun write(entry: Entry) {
        val values = if (entry.error == null) entry.fields else entry.fields.take(19) + listOf("err" to entry.error.stackTraceToString())
        val seq = sequence.incrementAndGet()
        val line = DiagFormat.line(entry.event, entry.time, seq, "%08x".format(processId.hashCode() xor seq.toInt()),
            when (entry.priority) { Log.DEBUG -> "D"; Log.WARN -> "W"; Log.ERROR -> "E"; else -> "I" }, values)
        if (ring.size == 50) ring.removeFirst()
        ring.addLast(line)
        runCatching { file?.write(line) }
        runCatching { line.chunked(3500).forEach { Log.println(entry.priority, TAG, it) } }
    }

    /** Crash/freeze paths bypass queue/rate limiting and persist before returning. */
    internal fun dump(event: String, text: String, vararg fields: Pair<String, Any?>) {
        try {
            synchronized(lock) {
                openFile()
                val chunks = DiagFormat.mask(text).chunked(300).ifEmpty { listOf("") }
                chunks.forEachIndexed { index, chunk ->
                    write(Entry(Log.ERROR, event, System.currentTimeMillis(), fields.toList() + listOf("part" to index + 1, "parts" to chunks.size, "dump" to chunk)))
                }
                file?.sync()
            }
        } catch (_: Throwable) { }
    }

    internal fun flush() {
        try {
            synchronized(lock) {
                openFile()
                while (true) {
                    val entry = queue.pollFirst() ?: break
                    write(entry)
                }
                file?.sync()
            }
        } catch (_: Throwable) { }
    }
}
