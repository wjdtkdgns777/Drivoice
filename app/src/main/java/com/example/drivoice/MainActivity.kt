package com.example.drivoice

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener

/**
 * Setup check screen: connects to the Pleos Vehicle SDK and shows live
 * brake / turn signal / wiper / gear values, plus whether the driver is "busy"
 * (the moment Drivoice should pause a story).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var statusView: TextView
    private lateinit var busyView: TextView

    private var braking = false
    private var turnSignal = TurnSignalValue.OFF
    private var wiper = WiperState.OFF
    private var gear = VehicleGear.GEAR_UNKNOWN

    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = update { braking = pressed == true }
        override fun onFailed(e: Exception) = logError("brake", e)
    }
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = update { turnSignal = state ?: TurnSignalValue.OFF }
        override fun onFailed(e: Exception) = logError("turnSignal", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = update { wiper = state ?: WiperState.OFF }
        override fun onFailed(e: Exception) = logError("wiper", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { this@MainActivity.gear = gear ?: VehicleGear.GEAR_UNKNOWN }
        override fun onFailed(e: Exception) = logError("gear", e)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply { textSize = 28f }
        busyView = TextView(this).apply { textSize = 40f }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(busyView)
            addView(statusView)
        })

        vehicle = Vehicle(this)
        vehicle.initialize()
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
        vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
        vehicle.getWiper().registerWiperState(windshield, wiperListener)
        vehicle.getGear().registerGearSelection(gearListener)
        render()
    }

    override fun onDestroy() {
        vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
        vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
        vehicle.getWiper().unregisterWiperState(windshield, wiperListener)
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.release()
        super.onDestroy()
    }

    /** Driver is busy when braking or signalling a turn: stories should pause. */
    private fun isDriverBusy() = braking || turnSignal != TurnSignalValue.OFF

    private fun update(change: () -> Unit) = runOnUiThread {
        change()
        render()
    }

    private fun render() {
        busyView.text = if (isDriverBusy()) "⏸ 운전 집중 — 이야기 일시정지" else "▶ 이야기 재생 가능"
        statusView.text = """
            브레이크: ${if (braking) "밟음" else "뗌"}
            방향지시등: $turnSignal
            와이퍼: $wiper
            기어: $gear
        """.trimIndent()
    }

    private fun logError(what: String, e: Exception) {
        Log.e(TAG, "$what listener failed", e)
        runOnUiThread { statusView.append("\n⚠ $what: ${e.message}") }
    }

    companion object {
        private const val TAG = "Drivoice"
    }
}
