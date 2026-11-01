package com.example.drivoice

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object Sun {

    const val SEOUL_LAT = 37.5665
    const val SEOUL_LON = 126.9780

    fun sunrise(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): LocalTime? = event(date, lat, lon, zone, rising = true)

    fun sunset(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): LocalTime? = event(date, lat, lon, zone, rising = false)

    fun isNight(at: ZonedDateTime, lat: Double, lon: Double): Boolean {
        val time = at.toLocalTime()
        val rise = sunrise(at.toLocalDate(), lat, lon, at.zone) ?: return false
        val set = sunset(at.toLocalDate(), lat, lon, at.zone) ?: return false
        return time.isBefore(rise) || !time.isBefore(set)
    }

    private fun event(date: LocalDate, lat: Double, lon: Double, zone: ZoneId, rising: Boolean): LocalTime? {
        val julian = date.toEpochDay() + 2440587.5 + 0.5
        val n = floor(julian - 2451545.0 + 0.0008)
        val solarNoon = n - lon / 360.0
        val anomaly = Math.toRadians((357.5291 + 0.98560028 * solarNoon) % 360)
        val center = 1.9148 * sin(anomaly) + 0.02 * sin(2 * anomaly) + 0.0003 * sin(3 * anomaly)
        val longitude = Math.toRadians((Math.toDegrees(anomaly) + center + 180 + 102.9372) % 360)
        val transit = 2451545.0 + solarNoon + 0.0053 * sin(anomaly) - 0.0069 * sin(2 * longitude)
        val declination = asin(sin(longitude) * sin(Math.toRadians(23.4397)))
        val latRad = Math.toRadians(lat)
        val cosHour = (sin(Math.toRadians(-0.833)) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))
        if (cosHour < -1 || cosHour > 1) return null
        val hour = Math.toDegrees(acos(cosHour)) / 360.0
        val julianEvent = if (rising) transit - hour else transit + hour
        val epochSeconds = ((julianEvent - 2440587.5) * 86400).toLong()
        return java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalTime()
    }
}
