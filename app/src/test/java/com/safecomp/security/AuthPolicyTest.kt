package com.safecomp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPolicyTest {

    private val window = AuthPolicy.WINDOW_MILLIS

    private fun facts(
        deviceSecure: Boolean = true,
        keysDestroyed: Boolean = false,
        authenticatedAt: Long? = 0,
        now: Long = 1_000,
    ) = AuthFacts(deviceSecure, keysDestroyed, authenticatedAt, now)

    @Test fun `a fresh authentication unlocks`() {
        assertEquals(AuthStatus.AUTHENTICATED, AuthPolicy.status(facts()))
    }

    @Test fun `the window is 5 minutes and ends exactly at 5 minutes`() {
        assertEquals(5 * 60 * 1000L, window)
        assertEquals(AuthStatus.AUTHENTICATED, AuthPolicy.status(facts(now = window - 1)))
        assertEquals(AuthStatus.NEEDS_AUTH, AuthPolicy.status(facts(now = window)))
    }

    @Test fun `no session means asking`() {
        assertEquals(AuthStatus.NEEDS_AUTH, AuthPolicy.status(facts(authenticatedAt = null)))
    }

    @Test fun `a clock running backwards fails closed`() {
        assertEquals(AuthStatus.NEEDS_AUTH, AuthPolicy.status(facts(authenticatedAt = 5_000, now = 4_000)))
    }

    @Test fun `no screen lock locks, whatever else is true`() {
        assertEquals(AuthStatus.NO_SCREEN_LOCK, AuthPolicy.status(facts(deviceSecure = false)))
        assertEquals(AuthStatus.NO_SCREEN_LOCK, AuthPolicy.status(facts(deviceSecure = false, keysDestroyed = true)))
    }

    @Test fun `destroyed keys stay locked even right after authenticating`() {
        assertEquals(AuthStatus.KEYS_DESTROYED, AuthPolicy.status(facts(keysDestroyed = true)))
    }

    @Test fun `settings need a fresh authentication and a screen lock`() {
        assertTrue(AuthPolicy.maySaveSettings(facts()))
        assertFalse(AuthPolicy.maySaveSettings(facts(authenticatedAt = null)))
        assertFalse(AuthPolicy.maySaveSettings(facts(now = window)))
        assertFalse(AuthPolicy.maySaveSettings(facts(deviceSecure = false)))
    }

    @Test fun `settings work after the keys were destroyed, so Reset is reachable`() {
        // Screen lock removed, then a new one set: the keys are gone, but the owner can still
        // authenticate and reset instead of being stuck forever.
        assertTrue(AuthPolicy.maySaveSettings(facts(keysDestroyed = true)))
        assertTrue(AuthPolicy.mayReset(facts(keysDestroyed = true)))
    }

    @Test fun `Reset needs authentication when there is a screen lock`() {
        assertTrue(AuthPolicy.mayReset(facts()))
        assertFalse(AuthPolicy.mayReset(facts(authenticatedAt = null)))
    }

    @Test fun `Reset works without authentication when there is no screen lock`() {
        // The keys are already destroyed or were never created: only unreadable data is deleted.
        assertTrue(AuthPolicy.mayReset(facts(deviceSecure = false, authenticatedAt = null)))
    }

    @Test fun `changed fingerprints mean PIN only`() {
        assertTrue(AuthPolicy.pinOnly(fingerprintsChanged = true))
        assertFalse(AuthPolicy.pinOnly(fingerprintsChanged = false))
    }
}
