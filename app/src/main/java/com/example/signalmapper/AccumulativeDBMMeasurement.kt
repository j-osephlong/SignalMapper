package com.example.signalmapper

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class AccumulativeDBMMeasurement (
    val latLng: Pair<Double, Double>,
    var measurements: List<DBMMeasurement>
) {
    companion object {
        fun initial(latLng: Pair<Double, Double>, dbm: Int) : AccumulativeDBMMeasurement =
            AccumulativeDBMMeasurement(
                latLng,
                listOf(
                    DBMMeasurement(
                        latLng,
                        dbm,
                        LocalDateTime.now().toString()
                    )
                )
            )
    }
}

@Serializable
data class DBMMeasurement (
    val latLng: Pair<Double, Double>,
    val dbm: Int,
    val dateTime : String
)

fun AccumulativeDBMMeasurement.add(latLng: Pair<Double, Double>, dbm: Int) =
    this.copy(measurements = this.measurements + DBMMeasurement(
        latLng,
        dbm,
        LocalDateTime.now().toString()
    ))

/**
 * Kotlin Haversine method adapted from https://stackoverflow.com/a/16794680 Java implementation
 */
fun AccumulativeDBMMeasurement.inRange(latLng: Pair<Double, Double>, range: Int /*In meters, inclusive*/) : Boolean {
    val r = 6371; // Radius of the earth

    val latDistance = Math.toRadians(latLng.first - this.latLng.first);
    val lonDistance = Math.toRadians(latLng.second - this.latLng.second);
    val a = sin(latDistance / 2) * sin(latDistance / 2) +
            cos(Math.toRadians(this.latLng.first)) * cos(Math.toRadians(latLng.first)) *
            sin(lonDistance / 2) * sin(lonDistance / 2);
    val c = 2 * atan2(sqrt(a), sqrt(1 - a));
    val distance = r * c * 1000; // convert to meters

    return distance <= range;
}

@Serializable
data class PackagedData(
    val measurements: List<AccumulativeDBMMeasurement>
)
