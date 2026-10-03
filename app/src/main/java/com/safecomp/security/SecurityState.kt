package com.safecomp.security

/** The only state in which the vault may be decrypted is [INSIDE]. [UNKNOWN] is treated
 *  exactly like [OUTSIDE]: if we can't prove the phone is in the office, we fail closed. */
enum class SecurityState { INSIDE, OUTSIDE, UNKNOWN }

/** Why the machine is in its current state. Shown to the user and (in Phase 5) logged. */
enum class StateReason {
    /** App just started, the config changed, or INSIDE went stale: waiting for a fresh fix. */
    CHECKING,
    NOT_CONFIGURED,
    INSIDE_CONFIRMED,
    /** Inside evidence is arriving but the dwell time hasn't passed yet. */
    CONFIRMING_INSIDE,
    OUTSIDE_FIX,
    OUTSIDE_GEOFENCE,
    PERMISSION_MISSING,
    LOCATION_OFF,
    MOCK_LOCATION,
    POOR_ACCURACY,
    NO_FIX,
}

/** Tuning values for one geofence. Times are in milliseconds of monotonic clock. */
data class GeofenceParams(
    val radiusMeters: Float,
    val dwellMillis: Long,
    val graceMillis: Long,
)

/** A scheduled move out of INSIDE that happens if the grace period runs out. */
data class PendingExit(
    val sinceMillis: Long,
    val target: SecurityState,
    val reason: StateReason,
)

data class SecuritySnapshot(
    val state: SecurityState = SecurityState.UNKNOWN,
    val reason: StateReason = StateReason.CHECKING,
    /** When continuous inside evidence started (only while not yet INSIDE). */
    val enterEvidenceSince: Long? = null,
    /** Set while INSIDE but outside evidence has started the grace period. */
    val pendingExit: PendingExit? = null,
    /** Last time INSIDE was confirmed by a fix or a geofence DWELL event. */
    val lastConfirmedAt: Long? = null,
    /** INSIDE went stale while the app was in the background. The next good inside fix
     *  restores INSIDE without a new dwell, because we already knew we were in the office. */
    val resumeInsideOnConfirm: Boolean = false,
) {
    val isUnlocked: Boolean get() = state == SecurityState.INSIDE
}

sealed interface SecurityEvent {
    /** One location reading, already reduced to a distance. Coordinates never enter the
     *  state machine, which is part of why nothing here can be used to track someone. */
    data class LocationFix(
        val distanceMeters: Float,
        val accuracyMeters: Float,
        val isMock: Boolean,
    ) : SecurityEvent

    data object GeofenceEntered : SecurityEvent
    data object GeofenceDwelled : SecurityEvent
    data object GeofenceExited : SecurityEvent

    /** The geofence broadcast's triggering location was faked. */
    data object MockLocationDetected : SecurityEvent
    data object PermissionMissing : SecurityEvent
    data object LocationServicesOff : SecurityEvent
    /** No fix within the timeout, or the stream of fixes stopped. */
    data object FixUnavailable : SecurityEvent
    data object ConfigChanged : SecurityEvent
    /** The app came to the foreground. */
    data object AppForegrounded : SecurityEvent
    /** Time passed. Lets the grace period run out without any new location data. */
    data object Tick : SecurityEvent
}
