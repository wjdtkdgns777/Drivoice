package com.example.drivoice

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Settings(
    val consented: Boolean = false,
    val sound: Boolean = true,
    val voice: Boolean = true,
    val familyShare: Boolean = true,
    val shareConditions: Boolean = false,
    val inviteCode: String = "",
)

object Store {

    private lateinit var prefs: SharedPreferences
    private lateinit var file: AtomicFile

    val trips = mutableStateListOf<Trip>()
    val ledger = mutableStateListOf<Tx>()
    var conditions by mutableStateOf(Conditions())
        private set
    var settings by mutableStateOf(Settings())
        private set
    var openTrip: Trip? = null
        private set

    fun load(context: Context) {
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        file = AtomicFile(File(context.filesDir, "$NAME.json"))
        conditions = Conditions(
            prefs.getBoolean(NO_NIGHT, true),
            prefs.getBoolean(AVOID_HIGHWAY, true),
            prefs.getBoolean(REST, true),
            prefs.getInt(REST_MINUTES, 60),
        )
        settings = Settings(
            prefs.getBoolean(CONSENTED, false),
            prefs.getBoolean(SOUND, true),
            prefs.getBoolean(VOICE, true),
            prefs.getBoolean(FAMILY_SHARE, true),
            prefs.getBoolean(SHARE_CONDITIONS, false),
            prefs.getString(INVITE_CODE, null) ?: (100_000..999_999).random().toString().also { prefs.edit().putString(INVITE_CODE, it).apply() },
        )
        if (!file.baseFile.exists()) return
        try {
            val root = JSONObject(String(file.readFully()))
            trips += root.getJSONArray(TRIPS).objects().map(::trip)
            ledger += root.getJSONArray(LEDGER).objects().map(::tx)
            openTrip = root.optJSONObject(OPEN)?.let(::trip)
        } catch (e: Exception) {
            Log.e(NAME, "ledger unreadable, starting empty", e)
            file.baseFile.renameTo(File(context.filesDir, "$NAME.broken.json"))
            trips.clear()
            ledger.clear()
        }
    }

    fun update(next: Conditions) {
        conditions = next
        prefs.edit()
            .putBoolean(NO_NIGHT, next.noNight)
            .putBoolean(AVOID_HIGHWAY, next.avoidHighway)
            .putBoolean(REST, next.rest)
            .putInt(REST_MINUTES, next.restMinutes)
            .apply()
    }

    fun update(next: Settings) {
        settings = next
        prefs.edit()
            .putBoolean(CONSENTED, next.consented)
            .putBoolean(SOUND, next.sound)
            .putBoolean(VOICE, next.voice)
            .putBoolean(FAMILY_SHARE, next.familyShare)
            .putBoolean(SHARE_CONDITIONS, next.shareConditions)
            .apply()
    }

    fun saveOpen(trip: Trip?) {
        openTrip = trip
        save()
    }

    fun add(trip: Trip?, txs: List<Tx>) {
        trip?.let {
            trips += it
            openTrip = null
        }
        ledger += txs.filter { tx -> ledger.none { it.id == tx.id } }
        save()
    }

    fun clear() {
        trips.clear()
        ledger.clear()
        openTrip = null
        save()
    }

    val balance get() = ledger.sumOf { it.miles }

    fun earnedOn(day: LocalDate) = ledger.filter { it.at.toLocalDate() == day }.sumOf { it.miles }

    private fun save() {
        val root = JSONObject()
            .put(TRIPS, JSONArray(trips.map(::json)))
            .put(LEDGER, JSONArray(ledger.map(::json)))
            .put(OPEN, openTrip?.let(::json))
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(NAME, "ledger write failed", e)
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun json(trip: Trip) = JSONObject()
        .put("id", trip.id)
        .put("startedAt", trip.startedAt.toString())
        .put("endedAt", trip.endedAt.toString())
        .put("distanceM", trip.distanceM)
        .put("nightM", trip.nightM)
        .put("highwayM", trip.highwayM)
        .put("overLimit", trip.overLimit)
        .put("rests", trip.rests)
        .put("turns", JSONArray(trip.turns.map { JSONObject().put("time", it.time.toString()).put("turn", it.turn.name).put("onTime", it.onTime).put("guided", it.guided) }))

    private fun trip(o: JSONObject) = Trip(
        o.getString("id"),
        LocalDateTime.parse(o.getString("startedAt")),
        LocalDateTime.parse(o.getString("endedAt")),
        o.getDouble("distanceM"),
        o.getDouble("nightM"),
        o.getDouble("highwayM"),
        o.getBoolean("overLimit"),
        o.getInt("rests"),
        o.getJSONArray("turns").objects().map { TurnRecord(LocalTime.parse(it.getString("time")), Turn.valueOf(it.getString("turn")), it.getBoolean("onTime"), it.getBoolean("guided")) },
    )

    private fun json(tx: Tx) = JSONObject()
        .put("id", tx.id)
        .put("at", tx.at.toString())
        .put("code", tx.code.name)
        .put("miles", tx.miles)
        .put("count", tx.count)

    private fun tx(o: JSONObject) = Tx(o.getString("id"), LocalDateTime.parse(o.getString("at")), Code.valueOf(o.getString("code")), o.getInt("miles"), o.getInt("count"))

    private const val NAME = "drivoice"
    private const val TRIPS = "trips"
    private const val LEDGER = "ledger"
    private const val OPEN = "open"
    private const val NO_NIGHT = "noNight"
    private const val AVOID_HIGHWAY = "avoidHighway"
    private const val REST = "rest"
    private const val REST_MINUTES = "restMinutes"
    private const val CONSENTED = "consented"
    private const val SOUND = "sound"
    private const val VOICE = "voice"
    private const val FAMILY_SHARE = "familyShare"
    private const val SHARE_CONDITIONS = "shareConditions"
    private const val INVITE_CODE = "inviteCode"
}
