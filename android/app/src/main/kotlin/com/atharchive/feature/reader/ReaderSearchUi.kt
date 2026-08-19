package com.atharchive.feature.reader

/**
 * In-book search now lives as a tab inside [ReaderPanel]; the browser-style overlay bar,
 * bottom result sheet and `‹ 3 / 14 ›` stepper it used to own were removed on 19 Aug 2026.
 */
internal fun arabic(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
