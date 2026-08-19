package com.atharchive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the Kotlin port agrees with the TypeScript source of truth on every
 * golden vector (scripts/gen-ar-vectors.ts → ar-normalize-vectors.tsv:
 * "input<TAB>expected" per line, \t \n \\ escaped).
 */
class ArNormalizeTest {

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    '\\' -> sb.append('\\')
                    else -> { sb.append(c); sb.append(s[i + 1]) }
                }
                i += 2
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }

    @Test
    fun matchesGoldenVectors() {
        val stream = javaClass.classLoader!!.getResourceAsStream("ar-normalize-vectors.tsv")
            ?: error("ar-normalize-vectors.tsv missing — run `pnpm app:vectors` in the repo root")
        var n = 0
        stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            val cut = line.indexOf('\t')
            if (cut < 0) return@forEachLine // blank/malformed line
            val input = unescape(line.substring(0, cut))
            val expected = unescape(line.substring(cut + 1))
            assertEquals("input: $input", expected, ArNormalize.normalize(input))
            n++
        }
        assertTrue("suspiciously few vectors ($n) — regen with `pnpm app:vectors`", n >= 150)
    }
}
