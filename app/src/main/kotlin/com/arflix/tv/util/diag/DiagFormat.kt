package com.arflix.tv.util.diag

import java.io.File
import java.io.FileOutputStream

/** Pure JVM formatting: no Android dependency, and no object serialization. */
internal object DiagFormat {
    // Mask UUID path segments even when callers supply only a path, without its UU host.
    private val token = Regex("(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=[/?#\\s]|$)")
    fun mask(value: String): String = value.replace(token, "<token>")
    private fun quote(value: String): String = buildString {
        append('"')
        for (c in mask(value)) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
    fun line(event: String, time: Long, seq: Long, id: String, level: String, fields: List<Pair<String, Any?>>): String {
        val entries = mutableListOf("\"e\":" + quote(mask(event).take(100)), "\"t\":$time", "\"seq\":$seq", "\"id\":" + quote(id), "\"lvl\":" + quote(level))
        for ((key, value) in fields.take(20)) {
            if (key in setOf("e", "t", "seq", "id", "lvl")) continue
            val encoded = when (value) {
                null -> "null"
                is Boolean -> value.toString()
                is Number -> value.toString().takeIf { it.toDoubleOrNull()?.isFinite() == true } ?: "null"
                is String -> quote(value)
                else -> "null"
            }
            val entry = quote(mask(key).take(100)) + ":" + encoded
            if ((entries.joinToString(",") + "," + entry).toByteArray(Charsets.UTF_8).size < 2900) entries.add(entry)
            else {
                // Keep a useful prefix of long strings; large dumps use explicit part records.
                if (value is String) {
                    var prefix = mask(value).take(600)
                    while (prefix.isNotEmpty()) {
                        val short = quote(mask(key).take(100)) + ":" + quote(prefix + "…")
                        if ((entries.joinToString(",") + "," + short).toByteArray(Charsets.UTF_8).size < 2900) {
                            entries.add(short)
                            break
                        }
                        prefix = prefix.dropLast(50.coerceAtMost(prefix.length))
                    }
                }
                entries.add("\"truncated\":true")
                break
            }
        }
        return entries.joinToString(",", "{", "}")
    }
}

/** Numbered generations; at most five files, each at most 10 MiB. */
internal class DiagRollingFile(private val directory: File, private val maxBytes: Long = 10L * 1024 * 1024, private val count: Int = 5) {
    private var output: FileOutputStream? = null
    private var size = 0L
    private var generation = 0L
    init {
        directory.mkdirs()
        val files = files()
        generation = files.lastOrNull()?.name?.removePrefix("ptpdiag-")?.removeSuffix(".jsonl")?.toLongOrNull() ?: 0
        val current = File(directory, "ptpdiag-$generation.jsonl")
        size = current.length()
        output = FileOutputStream(current, true)
        prune()
    }
    private fun files() = directory.listFiles().orEmpty().filter { it.name.matches(Regex("ptpdiag-\\d+\\.jsonl")) }
        .sortedBy { it.name.removePrefix("ptpdiag-").removeSuffix(".jsonl").toLong() }
    private fun prune() { files().dropLast(count).forEach { it.delete() } }
    @Synchronized fun write(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes)
        if (size + bytes.size > maxBytes) {
            output?.close()
            generation++
            output = FileOutputStream(File(directory, "ptpdiag-$generation.jsonl"), true)
            size = 0
            prune()
        }
        output!!.write(bytes)
        output!!.flush()
        size += bytes.size
    }
    @Synchronized fun sync() { output?.flush(); output?.fd?.sync() }
    @Synchronized fun close() { output?.close(); output = null }
}
