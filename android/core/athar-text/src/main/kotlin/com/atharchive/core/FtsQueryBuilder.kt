package com.atharchive.core

/**
 * Builds FTS5 MATCH expressions without ever forwarding user syntax.
 *
 * Input is normalized and reduced to Unicode letter/number tokens first. Quotes
 * only group a closed phrase; every quote, operator and punctuation character in
 * the returned value is generated here.
 */
object FtsQueryBuilder {
    private const val MAX_INPUT_CHARS = 10_000
    private const val MAX_TOKENS = 64
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")

    fun build(raw: String): String? = build(raw, compactCandidates = false)

    /**
     * Candidate query for FTS5 detail=none indexes. Phrase positions are not
     * stored in compact mode, so closed phrases become explicit AND terms and
     * callers verify exact adjacency against the retained normalized body.
     */
    fun buildCompactCandidate(raw: String): String? = build(raw, compactCandidates = true)

    private fun build(raw: String, compactCandidates: Boolean): String? {
        if (raw.isBlank()) return null
        val input = raw.take(MAX_INPUT_CHARS)
        val parts = parseParts(input)
        if (parts.isEmpty()) return null

        val rendered = mutableListOf<String>()
        var tokenBudget = MAX_TOKENS
        for ((partIndex, part) in parts.withIndex()) {
            if (tokenBudget == 0) break
            val tokens = part.tokens.take(tokenBudget)
            tokenBudget -= tokens.size
            if (tokens.isEmpty()) continue

            if (part.closedPhrase) {
                rendered += if (compactCandidates) {
                    tokens.joinToString(" AND ", transform = ::quote)
                } else {
                    quote(tokens.joinToString(" "))
                }
                continue
            }

            var i = 0
            while (i < tokens.size) {
                val isFinalToken = partIndex == parts.lastIndex && i == tokens.lastIndex
                val token = tokens[i]

                if (token == "عبد" && i + 1 < tokens.size) {
                    val next = tokens[i + 1]
                    val separator = if (compactCandidates) " AND " else " "
                    rendered += "((${quote(token)}$separator${quote(next)}) OR ${quote(token + next)})"
                    i += 2
                    continue
                }

                rendered += renderToken(token, prefix = isFinalToken)
                i++
            }
        }

        return rendered.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    private fun renderToken(token: String, prefix: Boolean): String {
        val literal = quote(token) + if (prefix && codePointLength(token) >= 2) "*" else ""
        if (token.startsWith("ال") && codePointLength(token) > 3) {
            val withoutArticle = token.drop(2)
            val expanded = quote(withoutArticle) +
                if (prefix && codePointLength(withoutArticle) >= 2) "*" else ""
            return "($literal OR $expanded)"
        }
        return literal
    }

    private fun parseParts(input: String): List<Part> {
        val result = mutableListOf<Part>()
        val ordinary = StringBuilder()
        val phrase = StringBuilder()
        var inQuote = false

        fun flushOrdinary() {
            val tokens = tokens(ordinary.toString())
            if (tokens.isNotEmpty()) result += Part(tokens, closedPhrase = false)
            ordinary.clear()
        }

        for (char in input) {
            if (char != '"') {
                if (inQuote) phrase.append(char) else ordinary.append(char)
                continue
            }

            if (inQuote) {
                val phraseTokens = tokens(phrase.toString())
                if (phraseTokens.isNotEmpty()) {
                    flushOrdinary()
                    result += Part(phraseTokens, closedPhrase = true)
                }
                phrase.clear()
                inQuote = false
            } else {
                flushOrdinary()
                inQuote = true
            }
        }

        if (inQuote) ordinary.append(phrase)
        flushOrdinary()
        return result
    }

    private fun tokens(value: String): List<String> =
        tokenRegex.findAll(ArNormalize.normalize(value))
            .map { it.value }
            .filter { it.isNotBlank() }
            .toList()

    private fun quote(token: String): String = "\"${token.replace("\"", "\"\"")}\""

    private fun codePointLength(value: String): Int =
        value.codePointCount(0, value.length)

    private data class Part(val tokens: List<String>, val closedPhrase: Boolean)
}
