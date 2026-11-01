package com.example.drivoice

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.hypot

data class Place(@StringRes val name: Int, val lat: Double, val lon: Double, val miles: Int)

data class RouteState(
    val destination: String? = null,
    val arrival: LocalTime? = null,
    val freeRoad: Boolean? = null,
    val freeRequested: Boolean = false,
    val arrivesAfterSunset: Boolean = false,
)

data class Summary(val turns: Int, val onTime: Int, val miles: Int, val missed: Turn?)

object Drive {

    val places = listOf(
        Place(R.string.place_mangyang, 37.4602, 127.0425, 30),
        Place(R.string.place_jukjeon, 37.3241, 127.1066, 30),
        Place(R.string.place_giheung, 37.2496, 127.1133, 30),
    )

    var driving by mutableStateOf(false)
        private set
    var sessionOpen by mutableStateOf(false)
        private set
    val liveTurns = mutableStateListOf<TurnRecord>()
    var continuousMs by mutableLongStateOf(0L)
        private set
    var summary by mutableStateOf<Summary?>(null)
    var route by mutableStateOf(RouteState())
        private set
    var waypoints by mutableIntStateOf(0)
        private set
    var pendingPlace by mutableStateOf<Place?>(null)
        private set
    var timeScale by mutableIntStateOf(1)
    var replaying by mutableStateOf(false)
        private set
    var debug by mutableStateOf("")
        private set

    private lateinit var context: Context
    private lateinit var car: Car
    private lateinit var vehicle: Vehicle
    private lateinit var navi: NaviHelper
    private lateinit var voice: Voice
    private val main = Handler(Looper.getMainLooper())
    private val judge = TurnJudge(LocalTime::now, ::onTurn)
    private val unguided = UnguidedTurnJudge(LocalTime::now, ::onTurn)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)

    private var started = false
    private var uxKnown = false
    private var doorOpen = false
    private var brake = false
    private var speedMps: Float? = null
    private var speedWatched = false
    private var replayStep: Runnable? = null
    private var signal: Turn? = null
    private var nextTurnM: Double? = null
    private var nextTurnType: TurnType? = null
    private var guided = false
    private var lat = Sun.SEOUL_LAT
    private var lon = Sun.SEOUL_LON
    private var odometerKm: Float? = null
    private var speedLimit = 0
    private var fastRoadSinceMs = 0L
    private var highway = false

    private var sessionId = ""
    private var sessionStart = LocalDateTime.now()
    private var distanceM = 0.0
    private var nightM = 0.0
    private var highwayM = 0.0
    private var overLimit = false
    private var rests = 0
    private var lastClosedMs = 0L
    private var restPrompts = 0
    private var nextPromptMs = 0L
    private var arrivedPlace: Place? = null
    private var arrivedMs = 0L
    private var routeFor: String? = null

    private class Speech(val text: String, val deadlineMs: Long, val then: (() -> Unit)?)

    private val speeches = ArrayDeque<Speech>()

    fun watch(appContext: Context) {
        if (::car.isInitialized) return
        context = appContext
        car = Car.createCar(context)
        val restrictions = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        onRestrictions(restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization)
        restrictions.registerListener { onRestrictions(it.isRequiresDistractionOptimization) }
    }

    fun watchSpeed() {
        if (speedWatched || context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) return
        @Suppress("DEPRECATION")
        speedWatched = (car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).registerCallback(speedListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
    }

    fun start(appContext: Context) {
        watch(appContext)
        if (started) return
        started = true
        voice = Voice(context)
        watchSpeed()
        Store.openTrip?.let { settle(it) }

        vehicle = Vehicle(context)
        vehicle.initialize()
        vehicle.getTurnSignal().registerTurnSignalState(signalListener)
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
        vehicle.getDoor().registerDoorPosition(driverDoor, doorListener)
        vehicle.getOdometer().registerDistanceDriven(odometerListener)

        navi = NaviHelper(context)
        navi.initialize({ navi.getTBTInfo(); navi.getDestinationInfo(); navi.getWaypointInfo() }, { Log.e(TAG, "navi", it) })
        navi.addListener(naviListener)

        main.post(tick)
    }

    fun addRestStop(place: Place) {
        if (waypoints >= MAX_WAYPOINTS) {
            say(R.string.say_waypoints_full)
            return
        }
        navi.addWaypoint(RequestWaypointInfo(place.lat, place.lon, context.getString(place.name), if (waypoints == 0) WaypointIndex.FIRST else WaypointIndex.SECOND, "", "", "", ""))
        pendingPlace = place
    }

    fun nearestPlace() = places.minBy { hypot(it.lat - lat, (it.lon - lon) * cos(Math.toRadians(lat))) }

    fun distanceKm(place: Place) = hypot(place.lat - lat, (place.lon - lon) * cos(Math.toRadians(lat))) * KM_PER_DEGREE

    fun restLimitMs() = Store.conditions.restMinutes * MINUTE_MS

    fun replay() {
        if (replaying) return
        replaying = true
        guided = true
        val legs = listOf(TurnType.TURN_RIGHT to 320.0, TurnType.TURN_LEFT to 420.0, TurnType.TURN_RIGHT to 300.0)
        var leg = 0
        var remaining = legs[0].second
        val step = object : Runnable {
            override fun run() {
                if (leg >= legs.size) {
                    onNextTurn(TurnType.TURN_ARRIVE, 0.0)
                    stopReplay()
                    return
                }
                onNextTurn(legs[leg].first, remaining)
                remaining -= REPLAY_STEP_M
                if (remaining < 0) {
                    leg++
                    remaining = legs.getOrNull(leg)?.second ?: 0.0
                }
                main.postDelayed(this, REPLAY_TICK_MS)
            }
        }
        replayStep = step
        main.post(step)
    }

    fun stopReplay() {
        replayStep?.let(main::removeCallbacks)
        replayStep = null
        replaying = false
        guided = false
        nextTurnM = null
        judge.onRouteChanged()
    }

    fun seedHistory() {
        val today = LocalDate.now()
        for (back in 6 downTo 1) {
            val end = today.minusDays(back.toLong()).atTime(15, 10)
            val turns = List(6 + back % 3) { TurnRecord(LocalTime.of(14, 30).plusMinutes(it * 3L), if (it % 2 == 0) Turn.RIGHT else Turn.LEFT, !(back == 4 && it == 1)) }
            val trip = Trip("seed-$back", end.minusMinutes(40), end, (14 + back * 3) * 1000.0, 0.0, 0.0, false, if (back % 2 == 0) 1 else 0, turns)
            Store.add(trip, Mileage.forTrip(trip, Store.conditions, Store.trips.toList(), Store.ledger.toList()))
        }
    }

    private fun onRestrictions(restricted: Boolean) = main.post {
        uxKnown = true
        driving = restricted
        updateSession()
    }

    private fun updateSession() {
        if (!sessionOpen && driving) open()
        else if (sessionOpen && !driving && doorOpen) close()
        val place = arrivedPlace
        if (place != null && !driving && doorOpen && SystemClock.elapsedRealtime() - arrivedMs <= VISIT_WINDOW_MS) {
            Store.add(null, listOf(Tx("visit-${System.currentTimeMillis()}", LocalDateTime.now(), Code.PARTNER, place.miles, 1)))
            arrivedPlace = null
        }
    }

    private fun open() {
        val gap = (SystemClock.elapsedRealtime() - lastClosedMs) * timeScale
        rests = 0
        if (lastClosedMs > 0 && gap >= REST_GAP_MS) {
            if (gap <= REST_RESET_MS && continuousMs >= MileageRule.REST_MIN * MINUTE_MS && continuousMs <= restLimitMs()) rests = 1
            continuousMs = 0
            restPrompts = 0
        }
        sessionId = "trip-${System.currentTimeMillis()}"
        sessionStart = LocalDateTime.now()
        distanceM = 0.0
        nightM = 0.0
        highwayM = 0.0
        overLimit = false
        highway = false
        liveTurns.clear()
        summary = null
        sessionOpen = true
        Store.saveOpen(snapshot())
    }

    private fun snapshot() = Trip(sessionId, sessionStart, LocalDateTime.now(), distanceM, nightM, highwayM, overLimit, rests, liveTurns.toList())

    private fun settle(trip: Trip) {
        Store.add(trip, Mileage.forTrip(trip, Store.conditions, Store.trips.toList(), Store.ledger.toList()))
    }

    private fun close() {
        sessionOpen = false
        lastClosedMs = SystemClock.elapsedRealtime()
        val trip = snapshot()
        val txs = Mileage.forTrip(trip, Store.conditions, Store.trips.toList(), Store.ledger.toList())
        Store.add(trip, txs)
        liveTurns.clear()
        val miles = txs.sumOf { it.miles }
        val missed = trip.turns.firstOrNull { !it.onTime }?.turn
        summary = Summary(trip.turns.size, trip.turns.count { it.onTime }, miles, missed)
        val text = if (trip.turns.isEmpty()) context.getString(R.string.say_summary_no_turns, miles)
        else context.getString(R.string.say_summary, trip.turns.size, trip.turns.count { it.onTime }, miles)
        say(text)
        if (missed != null) say(context.getString(R.string.say_missed_tip, context.getString(if (missed == Turn.LEFT) R.string.turn_left else R.string.turn_right)))
    }

    private fun onTurn(record: TurnRecord) {
        if (!sessionOpen) return
        liveTurns += record
        Store.saveOpen(snapshot())
        if (record.onTime && Store.settings.sound) voice.chime()
    }

    private fun onNextTurn(type: TurnType?, distance: Double) {
        val passedIc = nextTurnType in IC_ENTRIES && (nextTurnM ?: Double.MAX_VALUE) <= IC_PASSED_M && type != nextTurnType
        if (passedIc) highway = true
        nextTurnType = type
        nextTurnM = if (type == null || type == TurnType.TURN_ARRIVE) null else distance
        judge.onNextTurn(type, distance)
        debug = "$type ${distance.toInt()}m signal=$signal"
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (driving) {
                continuousMs += TICK_MS * timeScale
                if (Store.conditions.rest && continuousMs > restLimitMs()) overLimit = true
                if (Store.conditions.rest && continuousMs >= restLimitMs() * PROMPT_RATIO && restPrompts < MAX_PROMPTS && now >= nextPromptMs) askRest(now)
            }
            if (speedLimit >= HIGHWAY_LIMIT) {
                if (fastRoadSinceMs == 0L) fastRoadSinceMs = now
                if (now - fastRoadSinceMs >= HIGHWAY_HOLD_MS) highway = true
            } else {
                fastRoadSinceMs = 0L
                if (speedLimit in 1 until LEAVE_HIGHWAY_LIMIT) highway = false
            }
            speakNext(now)
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun askRest(now: Long) {
        restPrompts++
        nextPromptMs = now + REPROMPT_MS / timeScale
        val place = nearestPlace()
        say(context.getString(R.string.say_rest_question, context.getString(place.name))) {
            voice.listen { heard ->
                val no = NO_WORDS.any { heard.contains(context.getString(it)) }
                if (!no && YES_WORDS.any { heard.contains(context.getString(it)) }) addRestStop(place)
            }
        }
    }

    private fun say(@StringRes text: Int) = say(context.getString(text))

    private fun say(text: String, then: (() -> Unit)? = null) {
        if (!Store.settings.voice) return
        speeches.addLast(Speech(text, SystemClock.elapsedRealtime() + SPEECH_TTL_MS, then))
    }

    private fun speakNext(now: Long) {
        while (speeches.isNotEmpty() && speeches.first().deadlineMs < now) speeches.removeFirst()
        val next = speeches.firstOrNull() ?: return
        val stopped = speedMps?.let { it < STOPPED_MPS } ?: false
        val clear = uxKnown && (stopped || (!brake && signal == null && (nextTurnM ?: Double.MAX_VALUE) >= QUIET_TURN_M))
        if (clear && voice.speak(next.text, next.then)) speeches.removeFirst()
    }

    private fun addDistance(km: Float) {
        val previous = odometerKm
        odometerKm = km
        if (previous == null || !sessionOpen || km <= previous) return
        val meters = (km - previous) * 1000.0
        distanceM += meters
        if (Sun.isNight(ZonedDateTime.now(), lat, lon)) nightM += meters
        if (highway) highwayM += meters
        Store.saveOpen(snapshot())
    }

    private fun onDestination(info: DestinationInfo) {
        val arrival = LocalTime.now().plusSeconds(info.remainTime.toLong())
        val sunset = Sun.sunset(LocalDate.now(), lat, lon, ZonedDateTime.now().zone)
        val afterSunset = sunset != null && LocalTime.now().isBefore(sunset) && arrival.isAfter(sunset)
        val fresh = routeFor != info.name
        route = route.copy(destination = info.name, arrival = arrival, arrivesAfterSunset = afterSunset, freeRoad = if (fresh) null else route.freeRoad, freeRequested = !fresh && route.freeRequested)
        if (!fresh) return
        routeFor = info.name
        if (Store.conditions.avoidHighway) {
            navi.changeRouteOption(RouteOption.FREE)
            route = route.copy(freeRequested = true)
        }
        if (Store.conditions.noNight && afterSunset) say(R.string.say_after_sunset)
    }

    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            main.post {
                signal = when (state) {
                    TurnSignalValue.LEFT -> Turn.LEFT
                    TurnSignalValue.RIGHT -> Turn.RIGHT
                    else -> null
                }
                judge.onSignal(signal)
                unguided.onSignal(SystemClock.elapsedRealtime(), signal)
            }
        }

        override fun onFailed(e: Exception) = fail("signal", e)
    }

    private val speedListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = value.value as? Float ?: return
            main.post { speedMps = speed }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            main.post { brake = pressed == true }
        }

        override fun onFailed(e: Exception) = fail("brake", e)
    }

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            main.post {
                doorOpen = (position ?: 0) > 0
                updateSession()
            }
        }

        override fun onFailed(e: Exception) = fail("door", e)
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) {
            if (km != null) main.post { addDistance(km) }
        }

        override fun onFailed(e: Exception) = fail("odometer", e)
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val next = list.firstOrNull()
            main.post { if (!replaying) onNextTurn(next?.type, next?.distance ?: 0.0) }
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            main.post {
                this@Drive.speedLimit = speedLimit
                judge.onSpeedLimit(speedLimit)
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            main.post {
                lat = info.latitude
                lon = info.longitude
                unguided.onHeading(SystemClock.elapsedRealtime(), info.heading, sessionOpen && !guided)
            }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            main.post { if (!replaying) guided = status == NavigationStatus.ROUTE_DRIVE }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            main.post { onDestination(info) }
        }

        override fun onRouteOptionChanged(option: RouteOption) {
            main.post {
                if (option == RouteOption.FREE && route.freeRoad != true) say(R.string.say_free_road)
                route = route.copy(freeRoad = option == RouteOption.FREE)
            }
        }

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            main.post { waypoints = list.size }
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            main.post {
                arrivedPlace = pendingPlace
                arrivedMs = SystemClock.elapsedRealtime()
                pendingPlace = null
            }
        }

        override fun onReRouteEnded() {
            main.post { judge.onRouteChanged() }
        }

        override fun onRouteCancelled() {
            main.post {
                judge.onRouteChanged()
                route = RouteState()
                routeFor = null
            }
        }

        override fun onRouteEnded() {
            main.post {
                judge.onRouteChanged()
                route = RouteState()
                routeFor = null
            }
        }
    }

    private fun fail(what: String, e: Exception) {
        Log.e(TAG, what, e)
    }

    private val IC_ENTRIES = setOf(TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_C)
    private val YES_WORDS = listOf(R.string.yes_stop_by, R.string.yes_ok, R.string.yes_yes)
    private val NO_WORDS = listOf(R.string.no_not, R.string.no_dislike, R.string.no_no)

    private const val TAG = "Drivoice"
    private const val TICK_MS = 1_000L
    private const val MINUTE_MS = 60_000L
    private val REST_GAP_MS = Duration.ofMinutes(10).toMillis()
    private val REST_RESET_MS = Duration.ofHours(2).toMillis()
    private val REPROMPT_MS = Duration.ofMinutes(15).toMillis()
    private val VISIT_WINDOW_MS = Duration.ofMinutes(10).toMillis()
    private const val SPEECH_TTL_MS = 120_000L
    private const val PROMPT_RATIO = 0.9
    private const val MAX_PROMPTS = 2
    private const val MAX_WAYPOINTS = 2
    private const val QUIET_TURN_M = 500.0
    private const val STOPPED_MPS = 0.5f
    private const val HIGHWAY_LIMIT = 100
    private const val LEAVE_HIGHWAY_LIMIT = 80
    private const val HIGHWAY_HOLD_MS = 60_000L
    private const val IC_PASSED_M = 50.0
    private const val KM_PER_DEGREE = 111.32
    private const val REPLAY_STEP_M = 8.0
    private const val REPLAY_TICK_MS = 500L
}
