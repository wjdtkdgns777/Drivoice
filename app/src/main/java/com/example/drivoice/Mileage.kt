package com.example.drivoice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Turn { LEFT, RIGHT }

data class TurnRecord(val time: LocalTime, val turn: Turn, val onTime: Boolean, val guided: Boolean = true)

enum class Code { DISTANCE, TURN, TURN_UNGUIDED, KEPT_DAY, STREAK, REST, PARTNER }

data class Tx(val id: String, val at: LocalDateTime, val code: Code, val miles: Int, val count: Int)

data class Trip(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val distanceM: Double,
    val nightM: Double,
    val highwayM: Double,
    val overLimit: Boolean,
    val rests: Int,
    val turns: List<TurnRecord>,
)

data class Conditions(val noNight: Boolean = true, val avoidHighway: Boolean = true, val rest: Boolean = true, val restMinutes: Int = 60)

enum class Tier { GREEN, SILVER, GOLD }

object MileageRule {
    const val PER_KM = 1
    const val TURN_ON_TIME = 5
    const val TURN_UNGUIDED = 2
    const val KEPT_DAY = 20
    const val STREAK = 100
    const val STREAK_DAYS = 7
    const val REST = 10
    const val DAILY_CAP = 300
    const val SAFE_RATE = 0.95
    const val SILVER = 3_000
    const val SILVER_RATE = 0.9
    const val GOLD = 10_000
    const val GOLD_RATE = 0.95
    const val REST_MIN = 30
    const val REST_MAX = 120
    const val REST_STEP = 10
}

object Mileage {

    fun kept(trip: Trip, conditions: Conditions) =
        !(conditions.noNight && trip.nightM > 0) && !(conditions.avoidHighway && trip.highwayM > 0) && !(conditions.rest && trip.overLimit)

    fun signalRate(trips: List<Trip>, from: LocalDate): Double? {
        val turns = trips.filter { !it.startedAt.toLocalDate().isBefore(from) }.flatMap { it.turns }
        return if (turns.isEmpty()) null else turns.count { it.onTime }.toDouble() / turns.size
    }

    fun forTrip(trip: Trip, conditions: Conditions, history: List<Trip>, ledger: List<Tx>): List<Tx> {
        val day = trip.endedAt.toLocalDate()
        fun tx(code: Code, miles: Int, count: Int) = Tx("${trip.id}-$code", trip.endedAt, code, miles, count)

        val guided = trip.turns.count { it.guided && it.onTime }
        val unguided = trip.turns.count { !it.guided && it.onTime }
        val excludedM = (if (conditions.noNight) trip.nightM else 0.0) + (if (conditions.avoidHighway) trip.highwayM else 0.0)
        val km = ((trip.distanceM - excludedM).coerceAtLeast(0.0) / 1000).toInt()
        val safe = (signalRate(history, day.minusDays(6)) ?: 0.0) >= MileageRule.SAFE_RATE
        val keptToday = (history.filter { it.endedAt.toLocalDate() == day } + trip).all { kept(it, conditions) }
        val keptDayGiven = ledger.any { it.code == Code.KEPT_DAY && it.at.toLocalDate() == day }

        val earned = buildList {
            if (guided > 0) add(tx(Code.TURN, guided * MileageRule.TURN_ON_TIME, guided))
            if (unguided > 0) add(tx(Code.TURN_UNGUIDED, unguided * MileageRule.TURN_UNGUIDED, unguided))
            if (km > 0) add(tx(Code.DISTANCE, km * MileageRule.PER_KM * (if (safe) 2 else 1), km))
            if (keptToday && !keptDayGiven) {
                add(tx(Code.KEPT_DAY, MileageRule.KEPT_DAY, 1))
                val streak = (1 until MileageRule.STREAK_DAYS).all { back ->
                    ledger.any { it.code == Code.KEPT_DAY && it.at.toLocalDate() == day.minusDays(back.toLong()) }
                }
                val streakGiven = ledger.any { it.code == Code.STREAK && !it.at.toLocalDate().isBefore(day.minusDays(MileageRule.STREAK_DAYS - 1L)) }
                if (streak && !streakGiven) add(tx(Code.STREAK, MileageRule.STREAK, 1))
            }
            if (trip.rests > 0) add(tx(Code.REST, trip.rests * MileageRule.REST, trip.rests))
        }

        var room = MileageRule.DAILY_CAP - ledger.filter { it.at.toLocalDate() == day && it.code != Code.PARTNER }.sumOf { it.miles }
        return earned.mapNotNull { item ->
            val miles = item.miles.coerceAtMost(room.coerceAtLeast(0))
            room -= miles
            item.copy(miles = miles).takeIf { (miles > 0 || item.code == Code.KEPT_DAY) && ledger.none { it.id == item.id } }
        }
    }

    fun tier(ledger: List<Tx>, trips: List<Trip>, today: LocalDate): Tier {
        val total = ledger.sumOf { it.miles }
        val keptDays = ledger.count { it.code == Code.KEPT_DAY && !it.at.toLocalDate().isBefore(today.minusDays(89)) }
        val drivenDays = trips.filter { !it.startedAt.toLocalDate().isBefore(today.minusDays(89)) }.map { it.startedAt.toLocalDate() }.distinct().size
        val conditionRate = if (drivenDays == 0) 0.0 else keptDays.toDouble() / drivenDays
        val signal30 = signalRate(trips, today.minusDays(29)) ?: 0.0
        return when {
            total >= MileageRule.GOLD && conditionRate >= MileageRule.GOLD_RATE -> Tier.GOLD
            total >= MileageRule.SILVER && signal30 >= MileageRule.SILVER_RATE -> Tier.SILVER
            else -> Tier.GREEN
        }
    }
}
