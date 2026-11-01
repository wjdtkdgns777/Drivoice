package com.example.drivoice

import ai.pleos.playground.navi.constants.TurnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TurnJudgeTest {

    private val records = mutableListOf<TurnRecord>()
    private val judge = TurnJudge({ LocalTime.NOON }) { records += it }

    private fun approach(type: TurnType, vararg distances: Double) = distances.forEach { judge.onNextTurn(type, it) }

    @Test
    fun signalBeforeThirtyMetersIsOnTime() {
        approach(TurnType.TURN_RIGHT, 300.0, 120.0)
        judge.onSignal(Turn.RIGHT)
        approach(TurnType.TURN_RIGHT, 60.0, 30.0, 5.0)
        judge.onNextTurn(TurnType.TURN_LEFT, 400.0)
        assertEquals(listOf(TurnRecord(LocalTime.NOON, Turn.RIGHT, true)), records)
    }

    @Test
    fun signalAfterThirtyMetersIsLate() {
        approach(TurnType.TURN_LEFT, 300.0, 60.0, 30.0)
        judge.onSignal(Turn.LEFT)
        approach(TurnType.TURN_LEFT, 10.0)
        judge.onNextTurn(TurnType.TURN_STRAIGHT, 900.0)
        assertEquals(listOf(TurnRecord(LocalTime.NOON, Turn.LEFT, false)), records)
    }

    @Test
    fun wrongDirectionSignalIsLate() {
        judge.onSignal(Turn.LEFT)
        approach(TurnType.TURN_RIGHT, 200.0, 25.0, 3.0)
        judge.onNextTurn(null, 0.0)
        assertEquals(false, records.single().onTime)
    }

    @Test
    fun highwayNeedsHundredMeters() {
        judge.onSpeedLimit(100)
        approach(TurnType.TURN_EXIT_IC_R, 500.0, 100.0)
        judge.onSignal(Turn.RIGHT)
        approach(TurnType.TURN_EXIT_IC_R, 60.0, 10.0)
        judge.onNextTurn(TurnType.TURN_RIGHT, 2000.0)
        assertEquals(false, records.single().onTime)
    }

    @Test
    fun nextTurnSameDirectionStillCountsPreviousOne() {
        judge.onSignal(Turn.RIGHT)
        approach(TurnType.TURN_RIGHT, 100.0, 20.0, 2.0, 350.0)
        assertEquals(1, records.size)
    }

    @Test
    fun rerouteDropsTurnNotTaken() {
        judge.onSignal(Turn.RIGHT)
        approach(TurnType.TURN_RIGHT, 100.0, 20.0)
        judge.onRouteChanged()
        judge.onNextTurn(TurnType.TURN_LEFT, 600.0)
        assertEquals(0, records.size)
    }

    @Test
    fun turnLeftFarAwayIsNotJudged() {
        approach(TurnType.TURN_RIGHT, 800.0, 400.0)
        judge.onNextTurn(TurnType.TURN_LEFT, 900.0)
        assertEquals(0, records.size)
    }
}

class UnguidedTurnJudgeTest {

    private val records = mutableListOf<TurnRecord>()
    private val judge = UnguidedTurnJudge({ LocalTime.NOON }) { records += it }

    private fun drive(from: Long, headings: List<Double>) = headings.forEachIndexed { i, h -> judge.onHeading(from + i * 1000L, h, true) }

    @Test
    fun rightTurnWithSignalIsOnTime() {
        judge.onSignal(0, Turn.RIGHT)
        drive(3_000, listOf(0.0, 0.0, 20.0, 50.0, 80.0, 90.0))
        assertEquals(listOf(TurnRecord(LocalTime.NOON, Turn.RIGHT, true, guided = false)), records)
    }

    @Test
    fun leftTurnWithoutSignalIsLate() {
        drive(0, listOf(350.0, 330.0, 300.0, 275.0))
        assertEquals(Turn.LEFT, records.single().turn)
        assertEquals(false, records.single().onTime)
    }

    @Test
    fun gentleCurveIsNotATurn() {
        drive(0, listOf(0.0, 10.0, 20.0, 30.0, 40.0))
        assertEquals(0, records.size)
    }

    @Test
    fun disabledWhileGuided() {
        listOf(0.0, 45.0, 90.0).forEachIndexed { i, h -> judge.onHeading(i * 1000L, h, false) }
        assertEquals(0, records.size)
    }
}
