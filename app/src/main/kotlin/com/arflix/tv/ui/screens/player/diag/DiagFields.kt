package com.arflix.tv.ui.screens.player.diag

/** Leaves room for the common logger's metadata within a 3 KB JSON line, including UTF-8/escaping. */
internal object DiagFields {
    fun bounded(fields: Array<out Pair<String, Any?>>): Array<Pair<String, Any?>> {
        var available = (2300 - fields.sumOf { (key, value) ->
            key.length + 6 + if (value is String) 2 else (value?.toString()?.length ?: 4)
        }).coerceAtLeast(0)
        return fields.map { (key, value) ->
            key to if (value is String) {
                val result = StringBuilder()
                for (c in value) {
                    val cost = when {
                        c.code < 32 -> 6
                        c == '"' || c == '\\' || c == '/' -> 2
                        c.code < 128 -> 1
                        c.code < 2048 -> 2
                        else -> 3
                    }
                    if (cost > available) break
                    available -= cost
                    result.append(c)
                }
                result.toString()
            } else value
        }.toTypedArray()
    }
}
