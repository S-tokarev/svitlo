package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.AddressSearch
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.L10n

class AddressSearchTest {
    private val directory = AddressSearch.parseDirectory(
        """
        {"result":true,"streets":{
          "м. Буча":["вул. Шевченка","вул. Вокзальна"],
          "м. Ірпінь":["вул. Шевченка"],
          "м. Вишгород":["вул. Кургузова"],
          "смт Гостомель":["вул. Кучмів Яр"]
        }}
        """.trimIndent(),
    )

    @Test
    fun streetSearchCoversTheOblastAndPrefersTheNamedTown() {
        val kurhuzova = AddressSearch.matchStreets(directory, AddressSearch.parseQuery("Кургузова"))
        assertEquals(listOf("м. Вишгород"), kurhuzova.streets.map { it.city })

        val bucha = AddressSearch.matchStreets(directory, AddressSearch.parseQuery("Буча Шевченка"))
        assertEquals(listOf("м. Буча"), bucha.streets.map { it.city })

        val all = AddressSearch.matchStreets(directory, AddressSearch.parseQuery("вул. Шевченка"))
        assertEquals(setOf("м. Буча", "м. Ірпінь"), all.streets.map { it.city }.toSet())

        val irpin = AddressSearch.matchStreets(directory, AddressSearch.parseQuery("Ирпень Шевченка 11"))
        assertEquals("м. Ірпінь", irpin.streets.single().city)
        assertEquals("11", AddressSearch.parseQuery("Ирпень Шевченка 11").house)
    }

    @Test
    fun houseNumbersKeepTheLetterAndDropLongerNumbers() {
        assertTrue(AddressSearch.houseMatches("11", "11"))
        assertTrue(AddressSearch.houseMatches("11", "11Б"))
        assertTrue(AddressSearch.houseMatches("11б", "11Б/с.1"))
        assertFalse(AddressSearch.houseMatches("11", "111"))
        assertFalse(AddressSearch.houseMatches("11", "110"))
    }

    @Test
    fun housesUseTheShortQueueAndSkipAnotherOperator() {
        val json = """
            {"result":true,"data":{
              "3Б":{"sub_type_reason":["GPV3.1"],"cek":null},
              "11":{"sub_type_reason":["GPV1.2","GPV2.1"],"cek":null},
              "12":{"sub_type_reason":["GPV4.1"],"cek":true},
              "111":{"sub_type_reason":["GPV6.2"],"cek":null}
            }}
        """.trimIndent()
        val hits = AddressSearch.parseHouses(json, "м. Буча", "вул. Шевченка", "11")
        assertEquals(listOf("1.2", "2.1"), hits.hits.map { it.group })
        assertTrue(hits.hits.all { it.house == "11" })
        assertNull(hits.hint)

        L10n.language = AppLanguage.Uk
        val elsewhere = AddressSearch.parseHouses(json, "м. Буча", "вул. Шевченка", "12")
        assertTrue(elsewhere.hits.none { it.house == "12" })
        assertEquals(listOf("1.2", "2.1", "3.1", "6.2"), elsewhere.hits.map { it.group }.sorted())
        assertEquals("Такого будинку немає. Показано будинки цієї вулиці.", elsewhere.hint)
    }

    @Test
    fun csrfCookieHoldsTheSecondSerializedString() {
        val cookie = "25ab%3A2%3A%7Bi%3A0%3Bs%3A15%3A%22_csrf-dtek-krem%22%3Bi%3A1%3Bs%3A32%3A%22yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa%22%3B%7D"
        assertEquals("yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa", AddressSearch.csrfToken(cookie))
        val mask = ByteArray(3) { index -> (index + 1).toByte() }
        assertEquals(8, AddressSearch.maskToken("abc", mask).length)
    }
}
