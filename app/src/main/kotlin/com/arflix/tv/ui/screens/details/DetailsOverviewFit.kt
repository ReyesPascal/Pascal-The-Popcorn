package com.arflix.tv.ui.screens.details

/**
 * Personal build: the details description shows only whole sentences that fit, and never ends
 * with an ellipsis.
 *
 * [fits] answers whether a candidate text fits the description box (the screen measures it with
 * a TextMeasurer at the real width, style and line limit). The result:
 * - the whole text when it fits (with a full stop added if it ends without one);
 * - otherwise the longest run of leading whole sentences that fits;
 * - if not even the first sentence fits, the first sentence cut at the last clause boundary
 *   (comma, semicolon, colon or dash) that fits, ended with a full stop;
 * - if no clause boundary fits either, the longest run of whole words that fits, with a full stop.
 *
 * The result always ends on `.`, `!` or `?` (optionally followed by a closing quote or bracket),
 * never on "…" or "...". Blank input gives "".
 */
internal fun fitOverviewToWholeSentences(text: String, fits: (String) -> Boolean): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isEmpty()) return ""

    val full = endWithSentenceStop(clean)
    if (fits(full)) return full

    val sentences = splitOverviewSentences(clean)
    var best: String? = null
    val builder = StringBuilder()
    for (sentence in sentences) {
        val candidate = if (builder.isEmpty()) sentence else "$builder $sentence"
        val finished = endWithSentenceStop(candidate)
        if (fits(finished)) {
            best = finished
            builder.setLength(0)
            builder.append(candidate)
        } else {
            break
        }
    }
    if (best != null) return best

    // Not even the first sentence fits: cut it at the last clause boundary that fits.
    val first = sentences.firstOrNull() ?: clean
    val clauseCuts = clauseBoundaryCuts(first)
    for (cut in clauseCuts.asReversed()) {
        val candidate = endWithSentenceStop(cut)
        if (candidate.length > 1 && fits(candidate)) return candidate
    }

    // No clause boundary fits: fall back to whole words.
    val words = first.split(' ').filter { it.isNotEmpty() }
    for (count in words.size - 1 downTo 1) {
        val candidate = endWithSentenceStop(words.take(count).joinToString(" "))
        if (fits(candidate)) return candidate
    }
    return ""
}

private val sentenceEndChars = setOf('.', '!', '?')
private val closingChars = setOf('"', '\'', '”', '’', ')', ']', '»')

/** Words whose trailing "." does not end a sentence. Compared lower-case, without the dot. */
private val nonFinalAbbreviations = setOf(
    "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "ft", "vs", "etc", "e.g", "i.e",
    "no", "lt", "col", "gen", "sgt", "capt", "rev", "gov", "sen", "rep", "u.s", "u.k", "a.k.a"
)

/** Splits [text] (single-spaced) into sentences, each ending with its own punctuation. */
internal fun splitOverviewSentences(text: String): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c in sentenceEndChars) {
            // Absorb runs like "?!" and a closing quote/bracket.
            var end = i + 1
            while (end < text.length && text[end] in sentenceEndChars) end++
            val isEllipsis = c == '.' && end - i >= 2
            while (end < text.length && text[end] in closingChars) end++
            val atBoundary = end >= text.length || (text[end] == ' ' && end + 1 < text.length)
            if (atBoundary && !isEllipsis && !(c == '.' && end - i == 1 && isAbbreviationBefore(text, i))) {
                val nextStartsSentence = end >= text.length || startsLikeSentence(text, end + 1)
                if (nextStartsSentence) {
                    result += text.substring(start, end).trim()
                    start = end
                }
            }
            i = end
        } else {
            i++
        }
    }
    val tail = text.substring(start).trim()
    if (tail.isNotEmpty()) result += tail
    return result.filter { it.isNotEmpty() }
}

private fun startsLikeSentence(text: String, index: Int): Boolean {
    var j = index
    while (j < text.length && (text[j] in closingChars || text[j] == '(' || text[j] == '“' || text[j] == '‘')) j++
    if (j >= text.length) return true
    val ch = text[j]
    return ch.isUpperCase() || ch.isDigit() || !ch.isLetter()
}

private fun isAbbreviationBefore(text: String, dotIndex: Int): Boolean {
    var j = dotIndex - 1
    while (j >= 0 && text[j] != ' ') j--
    val word = text.substring(j + 1, dotIndex).trimStart('(', '"', '“')
    if (word.isEmpty()) return false
    // A single capital letter is an initial ("J. R. R. Tolkien").
    if (word.length == 1 && word[0].isUpperCase()) return true
    return word.lowercase() in nonFinalAbbreviations
}

/** Prefixes of [sentence] ending just before each clause boundary, shortest first. */
private fun clauseBoundaryCuts(sentence: String): List<String> {
    val cuts = mutableListOf<String>()
    var i = 0
    while (i < sentence.length) {
        val c = sentence[i]
        val cutAt = when {
            (c == ',' || c == ';' || c == ':') -> i
            (c == '—' || c == '–') -> i
            (c == '-' && i > 0 && sentence[i - 1] == ' ' && i + 1 < sentence.length && sentence[i + 1] == ' ') -> i
            else -> -1
        }
        if (cutAt > 0) {
            val prefix = sentence.substring(0, cutAt).trimEnd()
            if (prefix.isNotEmpty()) cuts += prefix
        }
        i++
    }
    return cuts
}

/**
 * Makes [text] end on a sentence stop: keeps an existing `.`/`!`/`?` (optionally followed by a
 * closing quote or bracket), turns a trailing ellipsis or clause mark into ".", else appends ".".
 */
internal fun endWithSentenceStop(text: String): String {
    var t = text.trimEnd()
    // Trailing ellipsis ("…" or "..."), possibly inside closing quotes, becomes a full stop.
    var closing = ""
    while (t.isNotEmpty() && t.last() in closingChars) {
        closing = t.last() + closing
        t = t.dropLast(1)
    }
    val hadEllipsis = t.endsWith("…") || t.endsWith("..")
    if (hadEllipsis) {
        t = t.trimEnd('…', '.').trimEnd()
        return if (t.isEmpty()) "" else "$t.$closing"
    }
    if (t.isNotEmpty() && t.last() in sentenceEndChars) return t + closing
    t = t.trimEnd(',', ';', ':', '-', '–', '—', ' ')
    if (t.isEmpty()) return ""
    return "$t$closing."
}
