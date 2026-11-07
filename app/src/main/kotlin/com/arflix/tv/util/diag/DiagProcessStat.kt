package com.arflix.tv.util.diag

internal object DiagProcessStat {
    /** /proc comm may contain spaces and parentheses; field 3 starts after its LAST ')'. */
    fun startTicks(stat: String): Long = stat.substringAfterLast(')').trim().split(Regex("\\s+"))[19].toLong()
    fun parse(stat: String): Pair<Long, Int> {
        val fields = stat.substringAfterLast(')').trim().split(Regex("\\s+"))
        return (fields[11].toLong() + fields[12].toLong()) to fields[17].toInt()
    }
}
