package com.example.drivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MileageTest {

    private val day = LocalDate.of(2026, 11, 1)
    private val noon = LocalDateTime.of(day, LocalTime.NOON)

    private fun trip(id: String = "t", at: LocalDateTime = noon, km: Double = 21.0, night: Double = 0.0, highway: Double = 0.0, onTime: Int = 0, late: Int = 0, rests: Int = 0) = Trip(
        id, at.minusMinutes(30), at, km * 1000, night * 1000, highway * 1000, false, rests,
        List(onTime) { TurnRecord(LocalTime.NOON, Turn.LEFT, true) } + List(late) { TurnRecord(LocalTime.NOON, Turn.RIGHT, false) },
    )

    private fun miles(txs: List<Tx>, code: Code) = txs.filter { it.code == code }.sumOf { it.miles }

    @Test
    fun turnsDistanceAndKeptDay() {
        val txs = Mileage.forTrip(trip(onTime = 5, late = 1), Conditions(), emptyList(), emptyList())
        assertEquals(25, miles(txs, Code.TURN))
        assertEquals(21, miles(txs, Code.DISTANCE))
        assertEquals(20, miles(txs, Code.KEPT_DAY))
    }

    @Test
    fun nightAndHighwayDistanceIsExcludedAndBreaksTheDay() {
        val txs = Mileage.forTrip(trip(km = 30.0, night = 10.0, highway = 5.0), Conditions(), emptyList(), emptyList())
        assertEquals(15, miles(txs, Code.DISTANCE))
        assertEquals(0, miles(txs, Code.KEPT_DAY))
    }

    @Test
    fun conditionsOffKeepFullDistance() {
        val txs = Mileage.forTrip(trip(km = 30.0, night = 10.0), Conditions(noNight = false), emptyList(), emptyList())
        assertEquals(30, miles(txs, Code.DISTANCE))
    }

    @Test
    fun safeWeekDoublesDistance() {
        val history = listOf(trip("h", noon.minusDays(1), onTime = 20))
        val txs = Mileage.forTrip(trip(km = 10.0), Conditions(), history, emptyList())
        assertEquals(20, miles(txs, Code.DISTANCE))
    }

    @Test
    fun dailyCapStopsAtThreeHundred() {
        val ledger = listOf(Tx("old", noon.minusHours(1), Code.DISTANCE, 290, 290))
        val txs = Mileage.forTrip(trip(onTime = 4), Conditions(), emptyList(), ledger)
        assertEquals(10, txs.sumOf { it.miles })
    }

    @Test
    fun sameTripIsNotPaidTwice() {
        val first = Mileage.forTrip(trip(onTime = 2), Conditions(), emptyList(), emptyList())
        val again = Mileage.forTrip(trip(onTime = 2), Conditions(), emptyList(), first)
        assertTrue(again.none { tx -> first.any { it.id == tx.id } })
    }

    @Test
    fun seventhKeptDayGivesStreak() {
        val ledger = (1..6).map { Tx("k$it", noon.minusDays(it.toLong()), Code.KEPT_DAY, 20, 1) }
        val txs = Mileage.forTrip(trip(), Conditions(), emptyList(), ledger)
        assertEquals(100, miles(txs, Code.STREAK))
    }

    @Test
    fun restIsPaid() {
        assertEquals(10, miles(Mileage.forTrip(trip(rests = 1), Conditions(), emptyList(), emptyList()), Code.REST))
    }

    @Test
    fun silverNeedsMilesAndSignalRate() {
        val ledger = listOf(Tx("a", noon, Code.DISTANCE, 3000, 3000))
        assertEquals(Tier.SILVER, Mileage.tier(ledger, listOf(trip(onTime = 9, late = 1)), day))
        assertEquals(Tier.GREEN, Mileage.tier(ledger, listOf(trip(onTime = 8, late = 2)), day))
    }

    @Test
    fun seoulSunsetInNovember() {
        val zone = ZoneId.of("Asia/Seoul")
        val set = Sun.sunset(day, Sun.SEOUL_LAT, Sun.SEOUL_LON, zone)!!
        assertTrue(set.isAfter(LocalTime.of(17, 25)) && set.isBefore(LocalTime.of(17, 45)))
        assertTrue(Sun.isNight(ZonedDateTime.of(day, LocalTime.of(19, 0), zone), Sun.SEOUL_LAT, Sun.SEOUL_LON))
        assertTrue(!Sun.isNight(ZonedDateTime.of(day, LocalTime.NOON, zone), Sun.SEOUL_LAT, Sun.SEOUL_LON))
    }
}
