package com.arflix.tv.util.diag

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagCoreTest {
    private val token = "afc23524-abcd-1234-5678-0123456789ab"

    @Test fun masksTokensInUrlsPathsAndEmbeddedStackTraces() {
        for (input in listOf("https://uu.example/$token/stream", "/$token/", "failure https://uu.example/$token?x=1\nat caller", "/${token.uppercase()}")) {
            val masked = DiagFormat.mask(input)
            assertFalse(masked.contains(token, ignoreCase = true))
            assertTrue(masked.contains("<token>"))
        }
        assertEquals("https://host/a-file/stream", DiagFormat.mask("https://host/a-file/stream"))
    }

    @Test fun jsonHasMetadataEscapesScalarTypesAndMasksAllStrings() {
        val line = DiagFormat.line("app-test", 42, 7, "1234abcd", "I", listOf(
            "url" to "https://uu.example/$token/stream", "text" to "quote\" slash\\\n\t\u0000 😀",
            "bytes" to 123L, "ok" to true, "missing" to null, "object" to Any(), "nan" to Double.NaN,
            "seq" to 999))
        val json = JsonParser.parseString(line).asJsonObject
        assertEquals("app-test", json["e"].asString)
        assertEquals(42L, json["t"].asLong)
        assertEquals(7, json["seq"].asInt)
        assertEquals("1234abcd", json["id"].asString)
        assertEquals("I", json["lvl"].asString)
        assertEquals("quote\" slash\\\n\t\u0000 😀", json["text"].asString)
        assertEquals(123, json["bytes"].asInt)
        assertTrue(json["ok"].asBoolean)
        assertTrue(json["missing"].isJsonNull)
        assertTrue(json["object"].isJsonNull)
        assertTrue(json["nan"].isJsonNull)
        assertFalse(line.contains(token))
        assertFalse(line.contains('\n'))
    }

    @Test fun linesStayUnderThreeKilobytesEvenForEscapedAndMultibyteText() {
        for (text in listOf("界".repeat(10000), "\u0000".repeat(10000), "\\\"".repeat(10000), "😀".repeat(10000))) {
            val line = DiagFormat.line("app-test", 1, 1, "12345678", "E", (1..20).map { "long$it" to text })
            assertTrue(line.toByteArray(Charsets.UTF_8).size < 3000)
            assertTrue(JsonParser.parseString(line).asJsonObject["truncated"].asBoolean)
        }
    }

    @Test fun masksBeforeTruncationSoNoPartialTokenEscapes() {
        val value = "x".repeat(590) + "/" + token + "/" + "z".repeat(4000)
        val line = DiagFormat.line("app-test", 1, 1, "12345678", "I", listOf("url" to value))
        assertFalse(line.contains("afc23524"))
        assertTrue(line.contains("<token>"))
    }

    @Test fun limitsFieldCountAndProtectsEnvelope() {
        val line = DiagFormat.line("app-test", 1, 2, "12345678", "D", (1..50).map { "field$it" to it })
        val json = JsonParser.parseString(line).asJsonObject
        assertEquals(25, json.size())
        assertFalse(json.has("field21"))
    }

    @Test fun rollingFilesKeepNewestFiveAndResumeAfterRestart() {
        val dir = Files.createTempDirectory("ptpdiag-test").toFile()
        try {
            DiagRollingFile(dir, maxBytes = 12, count = 5).also { writer ->
                repeat(8) { writer.write("line-$it") }
                writer.sync()
                writer.close()
            }
            assertEquals(5, dir.listFiles()!!.size)
            assertTrue(dir.listFiles()!!.all { it.length() <= 12 })
            assertFalse(dir.resolve("ptpdiag-0.jsonl").exists())
            assertEquals("line-7\n", dir.resolve("ptpdiag-7.jsonl").readText())
            DiagRollingFile(dir, maxBytes = 12, count = 5).also { writer -> writer.write("next"); writer.sync(); writer.close() }
            assertEquals(5, dir.listFiles()!!.size)
            assertEquals("line-7\nnext\n", dir.resolve("ptpdiag-7.jsonl").readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun procStatHandlesSpacesAndParenthesesInProcessName() {
        val fields = MutableList(25) { "0" }
        fields[0] = "R"
        fields[11] = "150"
        fields[12] = "20"
        fields[17] = "34"
        fields[19] = "123456"
        assertEquals(123456L, DiagProcessStat.startTicks("123 (a process ) name) " + fields.joinToString(" ")))
        assertEquals(170L to 34, DiagProcessStat.parse("123 (a process ) name) " + fields.joinToString(" ")))
    }
}
