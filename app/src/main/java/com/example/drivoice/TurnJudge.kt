package com.example.drivoice

import ai.pleos.playground.navi.constants.TurnType
import java.time.LocalTime

class TurnJudge(private val now: () -> LocalTime, private val onJudged: (TurnRecord) -> Unit) {

    private class Pending(val turn: Turn, var distance: Double, var onTime: Boolean?)

    private var pending: Pending? = null
    private var signal: Turn? = null
    private var highway = false

    fun onSignal(turn: Turn?) {
        signal = turn
    }

    fun onSpeedLimit(kmh: Int) {
        highway = kmh >= HIGHWAY_SPEED_LIMIT
    }

    fun onNextTurn(type: TurnType?, distance: Double) {
        val turn = type?.let(::turnOf)
        val current = pending
        if (current != null && (turn != current.turn || distance > current.distance + PASSED_JUMP_M)) {
            if (current.distance <= PASSED_WITHIN_M) onJudged(TurnRecord(now(), current.turn, current.onTime ?: (signal == current.turn)))
            pending = null
        }
        if (turn == null) return
        val next = pending ?: Pending(turn, distance, null).also { pending = it }
        if (next.onTime == null && distance <= threshold()) next.onTime = signal == turn
        next.distance = distance
    }

    fun onRouteChanged() {
        pending = null
    }

    private fun threshold() = if (highway) HIGHWAY_SIGNAL_M else ROAD_SIGNAL_M

    companion object {
        const val ROAD_SIGNAL_M = 30.0
        const val HIGHWAY_SIGNAL_M = 100.0
        private const val HIGHWAY_SPEED_LIMIT = 80
        private const val PASSED_WITHIN_M = 50.0
        private const val PASSED_JUMP_M = 30.0

        fun turnOf(type: TurnType): Turn? = when (type) {
            TurnType.TURN_LEFT, TurnType.TURN_SHARP_L, TurnType.TURN_UTURN, TurnType.TURN_ENTER_IC_L, TurnType.TURN_EXIT_IC_L -> Turn.LEFT
            TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R, TurnType.TURN_ENTER_IC_R, TurnType.TURN_EXIT_IC_R -> Turn.RIGHT
            else -> null
        }
    }
}

class UnguidedTurnJudge(private val now: () -> LocalTime, private val onJudged: (TurnRecord) -> Unit) {

    private class Sample(val atMs: Long, val heading: Double)

    private val headings = ArrayDeque<Sample>()
    private val signals = ArrayDeque<Pair<Long, Turn?>>()
    private var quietUntil = 0L

    fun onSignal(atMs: Long, turn: Turn?) {
        signals.addLast(atMs to turn)
        while (signals.size > 1 && signals[1].first < atMs - KEEP_MS) signals.removeFirst()
    }

    fun onHeading(atMs: Long, heading: Double, enabled: Boolean) {
        if (!enabled) {
            headings.clear()
            return
        }
        headings.addLast(Sample(atMs, heading))
        while (headings.first().atMs < atMs - WINDOW_MS) headings.removeFirst()
        if (atMs < quietUntil) return
        val start = headings.first()
        val delta = ((heading - start.heading + 540) % 360) - 180
        if (kotlin.math.abs(delta) < TURN_DEG) return
        val turn = if (delta > 0) Turn.RIGHT else Turn.LEFT
        onJudged(TurnRecord(now(), turn, signalled(turn, start.atMs - SIGNAL_MS, start.atMs), guided = false))
        headings.clear()
        quietUntil = atMs + WINDOW_MS
    }

    private fun signalled(turn: Turn, from: Long, to: Long): Boolean {
        val before = signals.lastOrNull { it.first <= from }?.second
        return before == turn || signals.any { it.first in from..to && it.second == turn }
    }

    companion object {
        private const val WINDOW_MS = 10_000L
        private const val SIGNAL_MS = 5_000L
        private const val KEEP_MS = 30_000L
        private const val TURN_DEG = 60.0
    }
}
