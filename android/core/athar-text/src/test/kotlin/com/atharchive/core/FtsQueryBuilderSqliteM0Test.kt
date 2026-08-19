package com.atharchive.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** M0-only integration checks. Enable with -Dathar.m0.sqlite=true. */
class FtsQueryBuilderSqliteM0Test {
    @Before
    fun requireM0Host() {
        assumeTrue(System.getProperty("athar.m0.sqlite") == "true")
        assumeTrue(runCatching { runSql("SELECT sqlite_version();") }.isSuccess)
    }

    @Test
    fun oneThousandAdversarialInputsProduceNoFtsSyntaxErrors() {
        val fixed = listOf(
            "", "\"", "*", "(", ")", "^", ":", "-", "OR", "AND", "NOT", "NEAR",
            "' OR 1=1 --", "\"عبارة غير مغلقة", "foo:bar OR baz*", "a".repeat(10_000),
            "نص\u200Dمختلط\u200C test", "(((\" OR * NEAR :)))", "عبد الله", "الإيمان",
        )
        val alphabet = listOf(
            "ا", "ل", "إ", "أ", "ع", "ب", "د", "ه", "َ", "ُ", "ِ", "ّ", " ",
            "A", "z", "7", "\"", "'", "*", "(", ")", ":", "^", "-", ";", "\u200D",
        )
        val random = Random(0xA7A4)
        val generated = List(1_000 - fixed.size) {
            buildString {
                repeat(random.nextInt(180)) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
        }
        val queries = (fixed + generated).mapNotNull(FtsQueryBuilder::build)

        for ((index, query) in queries.withIndex()) {
            val statements = buildString {
                appendLine(".bail on")
                appendLine("CREATE VIRTUAL TABLE docs USING fts5(body, tokenize='unicode61');")
                appendLine("INSERT INTO docs(body) VALUES ('الايمان عبد الله عبدالله');")
                append("SELECT count(*) FROM docs WHERE docs MATCH '")
                append(query.sqlLiteral())
                appendLine("';")
            }
            val output = runCatching { runSql(statements) }
                .getOrElse { throw AssertionError("adversarial query $index failed: $query", it) }
                .lines()
                .filter(String::isNotBlank)
            assertEquals("generated MATCH expression $index executed", 1, output.size)
        }
    }

    @Test
    fun articleAndCompoundExpansionsRecallExpectedRowsFromFiftyRealBooks() {
        val repository = Path.of(requireNotNull(System.getProperty("athar.repoRoot")))
        val samples = sampleRealBooks(repository, perQuery = 25)
        assertEquals(25, samples.article.size)
        assertEquals(25, samples.compound.size)

        val articleHits = querySamples(samples.all, requireNotNull(FtsQueryBuilder.build("الإيمان")))
        val compoundHits = querySamples(samples.all, requireNotNull(FtsQueryBuilder.build("عبد الله")))

        assertTrue(articleHits.containsAll(samples.article.map(BookSample::id)))
        assertTrue(compoundHits.containsAll(samples.compound.map(BookSample::id)))
    }

    @Test
    fun compactDetailNoneUsesAndCandidatesThenExactBodyFilter() {
        val candidate = requireNotNull(FtsQueryBuilder.buildCompactCandidate("\"طلب العلم\""))
        val statements = buildString {
            appendLine(".bail on")
            appendLine("CREATE TABLE blocks(rowid INTEGER PRIMARY KEY, body TEXT NOT NULL);")
            appendLine("CREATE VIRTUAL TABLE docs USING fts5(body, content='', contentless_delete=1, detail=none);")
            appendLine("INSERT INTO blocks VALUES (1, 'باب طلب العلم وفضله'), (2, 'العلم في طلب الحق'), (3, 'باب الايمان');")
            appendLine("INSERT INTO docs(rowid, body) SELECT rowid, body FROM blocks;")
            append("SELECT blocks.rowid FROM docs JOIN blocks ON blocks.rowid=docs.rowid WHERE docs MATCH '")
            append(candidate.sqlLiteral())
            appendLine("' AND instr(blocks.body, 'طلب العلم') > 0 ORDER BY blocks.rowid;")
        }

        assertEquals(listOf("1"), runSql(statements).lines().filter(String::isNotBlank))
    }

    private fun querySamples(samples: List<BookSample>, query: String): Set<String> {
        val statements = buildString {
            appendLine(".bail on")
            appendLine("CREATE VIRTUAL TABLE docs USING fts5(book_id UNINDEXED, body, tokenize='unicode61');")
            for (sample in samples) {
                append("INSERT INTO docs(book_id, body) VALUES ('")
                append(sample.id.sqlLiteral())
                append("', '")
                append(sample.body.sqlLiteral())
                appendLine("');")
            }
            append("SELECT book_id FROM docs WHERE docs MATCH '")
            append(query.sqlLiteral())
            appendLine("' ORDER BY book_id;")
        }
        return runSql(statements).lines().filter(String::isNotBlank).toSet()
    }

    private fun sampleRealBooks(repository: Path, perQuery: Int): Samples {
        val content = repository.resolve("src/content")
        val paths = Files.walk(content).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
                .filter { it.parent.fileName.toString() == "book" || it.parent.fileName.toString() == "book-lg" }
                .sorted()
                .toList()
        }
        val article = mutableListOf<BookSample>()
        val compound = mutableListOf<BookSample>()

        for (path in paths) {
            if (article.size == perQuery && compound.size == perQuery) break
            val candidate = firstRelevantLine(path) ?: continue
            val (body, kind) = candidate
            val sample = BookSample(path.fileName.toString(), body)
            if (kind == Kind.ARTICLE && article.size < perQuery) article += sample
            if (kind == Kind.COMPOUND && compound.size < perQuery) compound += sample
        }
        return Samples(article, compound)
    }

    private fun firstRelevantLine(path: Path): Pair<String, Kind>? =
        Files.newBufferedReader(path).useLines { lines ->
            lines.mapNotNull { raw ->
                val normalized = ArNormalize.normalize(raw)
                val articleIndex = Regex("[\\p{L}\\p{N}]+")
                    .findAll(normalized)
                    .firstOrNull { it.value.startsWith("الايمان") || it.value.startsWith("ايمان") }
                    ?.range
                    ?.first
                val compoundMatch = Regex("عبد\\s+الله|عبدالله").find(normalized)
                when {
                    articleIndex != null -> window(normalized, articleIndex) to Kind.ARTICLE
                    compoundMatch != null -> window(normalized, compoundMatch.range.first) to Kind.COMPOUND
                    else -> null
                }
            }.firstOrNull()
        }

    private fun window(value: String, center: Int): String {
        val start = (center - 400).coerceAtLeast(0)
        val end = (center + 800).coerceAtMost(value.length)
        return value.substring(start, end)
    }

    private fun runSql(statements: String): String {
        val process = ProcessBuilder("sqlite3", ":memory:")
            .redirectErrorStream(true)
            .start()
        val writeResult = runCatching {
            process.outputStream.bufferedWriter().use { it.write(statements) }
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        check(exit == 0) { "sqlite3 exited $exit:\n$output" }
        writeResult.getOrThrow()
        return output
    }

    private fun String.sqlLiteral(): String = replace("'", "''")

    private data class BookSample(val id: String, val body: String)
    private data class Samples(val article: List<BookSample>, val compound: List<BookSample>) {
        val all: List<BookSample> = article + compound
    }
    private enum class Kind { ARTICLE, COMPOUND }
}
