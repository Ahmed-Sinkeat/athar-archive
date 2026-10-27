package com.atharchive.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTiersTest {
    @Test
    fun exactPhraseThenWholeWordsThenPrefixes() {
        val tiers = MatchTiers("يقول راجي")
        val phrase = tiers.score("١ - يَقُولُ رَاجِي رَحْمَةِ الغَفُورِ")
        val words = tiers.score("راجي ثم يقول بعد ذلك")
        val prefixes = tiers.score("كرب راجينا يقولون")
        assertTrue(phrase < words && words < prefixes)
    }

    @Test
    fun typedHamzaComesFirstWithinATier() {
        val tiers = MatchTiers("إنما الأعمال")
        assertEquals(0, tiers.score("«إِنَّمَا الأعمال بالنيات»"))
        assertEquals(1, tiers.score("«انما الاعمال بالنيات»"))
        // no hamza typed: nothing to prefer
        assertEquals(0, MatchTiers("انما الاعمال").score("«انما الاعمال»"))
    }
}
