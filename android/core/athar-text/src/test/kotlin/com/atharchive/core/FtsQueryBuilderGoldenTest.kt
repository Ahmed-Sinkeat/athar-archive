package com.atharchive.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4.9: the TS golden list (scripts/search-golden.json, run against the live Worker by search-golden.mjs)
 * also feeds the phone's query builder: every golden query must make a match expression, and a
 * multi-word query the site's four tiers. Ranking itself is judged on the Worker and the M0 SQLite test.
 */
class FtsQueryBuilderGoldenTest {
    @Test
    fun everyGoldenQueryBuildsTiers() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "scripts/search-golden.json").exists()) dir = dir.parentFile
        if (dir == null) return // not run from inside the repository
        val queries = Regex(""""q":\s*"((?:[^"\\]|\\.)*)"""").findAll(File(dir, "scripts/search-golden.json").readText())
            .map { it.groupValues[1] }.toList()
        assertTrue("golden list has 20 queries, found ${queries.size}", queries.size == 20)
        for (q in queries) {
            assertTrue("no tiers for «$q»", FtsQueryBuilder.tiers(q).isNotEmpty())
            if (q.split(' ').size >= 2 && '"' !in q) assertTrue("«$q» should have more than one tier", FtsQueryBuilder.tiers(q).size > 1)
        }
    }
}
