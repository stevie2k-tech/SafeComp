package com.safecomp.security

/** "Who you are": whether the phone's owner has recently proved it's them. */
enum class AuthStatus {
    /** No PIN, pattern or password is set, so there's no owner to check: stay locked. */
    NO_SCREEN_LOCK,

    /** Android destroyed the vault key (the screen lock was removed at some point). */
    KEYS_DESTROYED,

    /** Fingerprint, face or PIN needed. */
    NEEDS_AUTH,

    /** Authenticated within the last [AuthPolicy.WINDOW_MILLIS]. */
    AUTHENTICATED,
}

/** What SafeComp knows right now. Times are monotonic (SystemClock.elapsedRealtime). */
data class AuthFacts(
    val deviceSecure: Boolean,
    val keysDestroyed: Boolean,
    /** When the owner last authenticated in SafeComp, or null if the session ended. */
    val authenticatedAt: Long?,
    val nowMillis: Long,
)

/**
 * The rules for "who you are". Pure Kotlin with no Android types, so every rule is
 * unit-tested, like SecurityStateMachine ("where you are"). The vault opens only when BOTH say
 * yes: [AuthStatus.AUTHENTICATED] here and INSIDE there.
 *
 * Two layers enforce the 5-minute window:
 *  - The Android Keystore (hardware) refuses to unwrap the vault keys more than 5 minutes after
 *    the owner authenticated to the phone.
 *  - This policy ends SafeComp's session sooner: when SafeComp goes to the background, the
 *    screen turns off, or the phone leaves the office. Each of those means asking again.
 */
object AuthPolicy {

    const val WINDOW_MILLIS = 5 * 60 * 1000L

    fun status(facts: AuthFacts): AuthStatus = when {
        // Order matters: without a screen lock nothing else can be true.
        !facts.deviceSecure -> AuthStatus.NO_SCREEN_LOCK
        facts.keysDestroyed -> AuthStatus.KEYS_DESTROYED
        isFresh(facts.authenticatedAt, facts.nowMillis) -> AuthStatus.AUTHENTICATED
        else -> AuthStatus.NEEDS_AUTH
    }

    /** A clock that ran backwards (it can't with a monotonic clock) is treated as expired. */
    fun isFresh(authenticatedAt: Long?, nowMillis: Long): Boolean =
        authenticatedAt != null && nowMillis >= authenticatedAt && nowMillis - authenticatedAt < WINDOW_MILLIS

    /**
     * Changing a setting needs the same authentication as opening the vault. (It's about who
     * you are, so it works even when the vault keys were destroyed.)
     */
    fun maySaveSettings(facts: AuthFacts): Boolean =
        facts.deviceSecure && isFresh(facts.authenticatedAt, facts.nowMillis)

    /**
     * Reset deletes everything. With a screen lock it needs authentication, like every setting.
     * Without one, the keys are already gone or were never created, so the files are already
     * unreadable: Reset only deletes data nobody can read, and requiring an authentication that
     * can't happen would leave the owner stuck forever. (Removing a screen lock itself needs
     * the current PIN, so a thief can't use this to wipe someone's vault.)
     */
    fun mayReset(facts: AuthFacts): Boolean = !facts.deviceSecure || maySaveSettings(facts)

    /**
     * The phone's fingerprints changed since SafeComp last checked (the fingerprint check key
     * was destroyed). Someone who knows the PIN could have added their own finger, so the next
     * unlock must use the PIN itself, after which the new fingerprint set is trusted.
     */
    fun pinOnly(fingerprintsChanged: Boolean): Boolean = fingerprintsChanged
}
