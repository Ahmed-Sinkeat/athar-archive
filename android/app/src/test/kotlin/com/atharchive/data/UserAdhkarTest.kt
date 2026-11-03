package com.atharchive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAdhkarTest {
    @Test fun roundTripKeepsEveryField() {
        val state = UserAdhkarState(
            lists = listOf(UserAdhkarList("u-1", "بعد الصلاة")),
            items = listOf(
                UserDhikr("a", "سُبْحَانَ اللَّهِ «وَبِحَمْدِهِ»\nسطر ثانٍ", 33, "رواه مسلم", linkedSetOf("morning", "u-1")),
                UserDhikr("b", "أستغفر الله", 100, null, setOf("evening")),
            ),
        )
        assertEquals(state, UserAdhkarStore.decode(UserAdhkarStore.encode(state)))
        assertEquals(UserAdhkarState(), UserAdhkarStore.decode(null))
    }

    @Test fun restoreAddsMissingKeepsLocalAndMatchesListsByName() {
        val here = UserAdhkarState(
            listOf(UserAdhkarList("u-1", "بعد الصلاة")),
            listOf(UserDhikr("a", "محلي", 3, null, setOf("u-1"))),
        )
        val file = UserAdhkarState(
            listOf(UserAdhkarList("u-9", "بعد الصلاة"), UserAdhkarList("u-7", "قبل النوم")),
            listOf(
                UserDhikr("a", "قديم", 1, null, setOf("u-9")),
                UserDhikr("b", "جديد", 10, null, setOf("u-9", "morning")),
                UserDhikr("c", "للنوم", 1, null, setOf("u-7")),
            ),
        )
        val (next, added) = UserAdhkarStore.merged(here, file)
        assertEquals(2, added)
        assertEquals("محلي", next.items.first { it.id == "a" }.text)
        assertEquals(setOf("u-1", "morning"), next.items.first { it.id == "b" }.lists)
        assertEquals(listOf("u-1", "u-7"), next.lists.map { it.id })
        assertEquals(next, UserAdhkarStore.merged(next, file).first)
    }

    @Test fun reminderWaitsForTheNextTimeOfDay() {
        val now = java.time.LocalDateTime.of(2026, 10, 30, 7, 30)
        assertEquals(java.time.Duration.ofMinutes(30), AdhkarReminders.delayUntil(now, 8 * 60))
        assertEquals(java.time.Duration.ofHours(23), AdhkarReminders.delayUntil(now, 6 * 60 + 30))
        assertEquals(java.time.Duration.ofDays(1), AdhkarReminders.delayUntil(now, 7 * 60 + 30))
    }

    @Test fun topicsFallUnderSections() {
        assertEquals(setOf("علوم القرآن"), subjectsOf(listOf("تفسير القرآن")))
        assertEquals(setOf("الفقه", "الحديث"), subjectsOf(listOf("الفقه الحنبلي", "مصطلح الحديث")))
        assertTrue(subjectsOf(listOf("عام", "غير معروف")).isEmpty())
        assertEquals(15, AllCenturies.size)
        assertEquals("القرن ٨", centuryOf(728))
        assertEquals("القرن ١", centuryOf(100))
    }
}
