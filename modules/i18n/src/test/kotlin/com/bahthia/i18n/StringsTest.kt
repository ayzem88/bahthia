package com.bahthia.i18n

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringsTest {

    @AfterEach
    fun teardown() {
        LocaleStore.reset()
    }

    // ─── Locale ───

    @Test fun `Locale fromCode returns correct enum`() {
        assertEquals(Locale.AR, Locale.fromCode("ar"))
        assertEquals(Locale.EN, Locale.fromCode("en"))
        assertEquals(Locale.FA, Locale.fromCode("fa"))
    }

    @Test fun `Locale fromCode is case-insensitive`() {
        assertEquals(Locale.EN, Locale.fromCode("EN"))
        assertEquals(Locale.AR, Locale.fromCode("Ar"))
    }

    @Test fun `Locale fromCode unknown returns default`() {
        assertEquals(Locale.DEFAULT, Locale.fromCode("xx"))
        assertEquals(Locale.DEFAULT, Locale.fromCode(null))
    }

    @Test fun `Arabic and Persian are RTL`() {
        assertTrue(Locale.AR.rtl)
        assertTrue(Locale.FA.rtl)
        assertTrue(Locale.UR.rtl)
    }

    @Test fun `English and French are LTR`() {
        assertTrue(!Locale.EN.rtl)
        assertTrue(!Locale.FR.rtl)
    }

    // ─── Strings.get ───

    @Test fun `Arabic returns native translation`() {
        assertEquals("ابحث", Strings.get(Locale.AR, "search.button.search"))
        assertEquals("امسح", Strings.get(Locale.AR, "search.button.clear"))
    }

    @Test fun `English returns translated value`() {
        assertEquals("Search", Strings.get(Locale.EN, "search.button.search"))
        assertEquals("Clear",  Strings.get(Locale.EN, "search.button.clear"))
    }

    @Test fun `unknown key returns the key itself`() {
        val result = Strings.get(Locale.AR, "this.does.not.exist")
        assertEquals("this.does.not.exist", result)
    }

    @Test fun `each locale returns its own translation when present`() {
        // كلّ اللغات الـ 12 لها ترجمة لزرّ "ابحث"
        assertEquals("Search",   Strings.get(Locale.EN, "search.button.search"))
        assertEquals("Rechercher", Strings.get(Locale.FR, "search.button.search"))
        assertEquals("Ara",      Strings.get(Locale.TR, "search.button.search"))
    }

    // ─── coverage ───

    @Test fun `Arabic coverage is 100 percent`() {
        assertEquals(1.0, Strings.coverage(Locale.AR), 0.0001)
    }

    @Test fun `English coverage is partial but greater than zero`() {
        val cov = Strings.coverage(Locale.EN)
        assertTrue(cov > 0.0, "EN should have some translations")
        assertTrue(cov <= 1.0, "EN coverage capped at 1.0")
    }

    @Test fun `All declared locales have non-zero coverage`() {
        for (locale in Locale.entries) {
            val cov = Strings.coverage(locale)
            assertTrue(cov > 0.0, "$locale should have at least some translations")
        }
    }

    @Test fun `allKeys is non-empty`() {
        val keys = Strings.allKeys()
        assertTrue(keys.isNotEmpty(), "Strings.allKeys() should not be empty")
        assertTrue(keys.contains("search.button.search"))
        assertTrue(keys.contains("settings.title"))
    }

    // ─── LocaleStore + tr() ───

    @Test fun `LocaleStore default is Arabic`() {
        assertEquals(Locale.AR, LocaleStore.current())
    }

    @Test fun `LocaleStore set changes current locale`() {
        val previous = LocaleStore.set(Locale.EN)
        assertEquals(Locale.AR, previous)
        assertEquals(Locale.EN, LocaleStore.current())
    }

    @Test fun `tr uses current locale`() {
        LocaleStore.set(Locale.AR)
        assertEquals("ابحث", tr("search.button.search"))
        LocaleStore.set(Locale.EN)
        assertEquals("Search", tr("search.button.search"))
    }

    @Test fun `tr with explicit locale ignores current`() {
        LocaleStore.set(Locale.EN)
        assertEquals("ابحث", tr(Locale.AR, "search.button.search"))
    }

    @Test fun `LocaleStore reset returns to default`() {
        LocaleStore.set(Locale.EN)
        LocaleStore.reset()
        assertEquals(Locale.DEFAULT, LocaleStore.current())
    }
}
