package com.safecomp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The office geofence. There is exactly one row (id = 0).
 *
 * Only the OFFICE location is stored. The employee's own location is never saved anywhere:
 * each fix is turned into a single "distance from the office" number and then discarded.
 */
@Entity(tableName = "geofence_config")
data class GeofenceConfig(
    @PrimaryKey val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    /** How long the phone must stay inside before the vault unlocks (anti-flicker). */
    val dwellSeconds: Int = DEFAULT_DWELL_SECONDS,
    /** How long the phone may be outside before the vault locks (anti-flicker). */
    val graceSeconds: Int = DEFAULT_GRACE_SECONDS,
) {
    /** Clamps every value into its allowed range. Applied on save AND on read, so a bad value
     *  in the database can never produce, say, a 5 m geofence. */
    fun validated(): GeofenceConfig = copy(
        id = 0,
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        radiusMeters = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS),
        dwellSeconds = dwellSeconds.coerceIn(MIN_DWELL_SECONDS, MAX_DWELL_SECONDS),
        graceSeconds = graceSeconds.coerceIn(MIN_GRACE_SECONDS, MAX_GRACE_SECONDS),
    )

    companion object {
        /** GPS in cities is often off by 20–50 m. Below ~100 m the vault would flicker. */
        const val MIN_RADIUS_METERS = 100f
        const val MAX_RADIUS_METERS = 1000f
        const val DEFAULT_RADIUS_METERS = 150f
        const val MIN_DWELL_SECONDS = 10
        const val MAX_DWELL_SECONDS = 120
        const val DEFAULT_DWELL_SECONDS = 30
        const val MIN_GRACE_SECONDS = 15
        const val MAX_GRACE_SECONDS = 300
        const val DEFAULT_GRACE_SECONDS = 60
    }
}
