package com.safecomp.security

import com.safecomp.data.GeofenceConfig
import com.safecomp.data.GeofenceConfigDao
import com.safecomp.data.GeofenceConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The repository's runtime behaviour (ticker, listeners), on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityStateRepositoryTest {

    private class FakeDao(config: GeofenceConfig?) : GeofenceConfigDao {
        private val flow = MutableStateFlow(config)
        override fun observe(): Flow<GeofenceConfig?> = flow
        override suspend fun get() = flow.value
        override suspend fun upsert(config: GeofenceConfig) { flow.value = config }
        override suspend fun clear() { flow.value = null }
    }

    private class MemoryStore : SnapshotStore {
        var saved: SecuritySnapshot? = null
        override fun currentBootCount() = 1
        override fun load() = saved?.let { it to 1 }
        override fun save(snapshot: SecuritySnapshot) { saved = snapshot }
        override var lastAnnouncedUnlocked: Boolean? = null
    }

    private val office = GeofenceConfig(latitude = 0.0, longitude = 0.0, radiusMeters = 150f, dwellSeconds = 30, graceSeconds = 60)
    private val outsideFix = SecurityEvent.LocationFix(distanceMeters = 900f, accuracyMeters = 10f, isMock = false)

    private fun TestScope.repository() = SecurityStateRepository(
        configRepository = GeofenceConfigRepository(FakeDao(office)),
        store = MemoryStore(),
        clock = { testScheduler.currentTime },
        scope = backgroundScope,
    )

    @Test fun `grace period expires on time with no further location input`() = runTest {
        val repo = repository()
        repo.dispatch(SecurityEvent.GeofenceDwelled)
        repo.dispatch(outsideFix) // grace starts; no more fixes arrive after this
        runCurrent()

        advanceTimeBy(59_000)
        assertEquals(SecurityState.INSIDE, repo.snapshot.value.state)

        // The 1 s ticker alone must finish the grace period (regression: it used to stop
        // after its first tick, so locking waited for the next fix or the background job).
        advanceTimeBy(2_500)
        assertEquals(SecurityState.OUTSIDE, repo.snapshot.value.state)
    }

    @Test fun `listeners see the lock as soon as the grace period ends`() = runTest {
        val repo = repository()
        val seen = mutableListOf<SecurityState>()
        repo.addListener { _, new -> seen += new.state }
        repo.dispatch(SecurityEvent.GeofenceDwelled)
        repo.dispatch(outsideFix)
        advanceTimeBy(61_500)
        assertEquals(SecurityState.OUTSIDE, seen.last())
    }

    @Test fun `ticker stops once nothing is pending`() = runTest {
        val repo = repository()
        repo.dispatch(SecurityEvent.GeofenceDwelled)
        repo.dispatch(outsideFix)
        repo.dispatch(SecurityEvent.LocationFix(10f, 10f, false)) // back inside: grace cancelled
        advanceTimeBy(120_000)
        assertEquals(SecurityState.INSIDE, repo.snapshot.value.state)
    }

    @Test fun `listeners see changes in the order they happened, even when a listener is slow`() = runTest {
        val repo = repository()
        val seen = mutableListOf<SecurityState>()
        var first = true
        repo.addListener { _, new ->
            if (first) {
                first = false
                kotlinx.coroutines.delay(500) // e.g. a slow notification or database write
            }
            seen += new.state
        }
        // Two changes in quick succession from different sources.
        val a = launch { repo.dispatch(SecurityEvent.GeofenceDwelled) } // -> INSIDE
        runCurrent()
        val b = launch { repo.dispatch(SecurityEvent.PermissionMissing) } // -> UNKNOWN
        advanceTimeBy(1_000)
        a.join(); b.join()
        assertEquals(listOf(SecurityState.INSIDE, SecurityState.UNKNOWN), seen)
    }

    @Test fun `listeners still run when the caller is cancelled mid-dispatch`() = runTest {
        // E.g. the foreground monitor's job is cancelled because the app went to the background
        // at the same moment a fix started the grace period. The background-job listener must
        // still run, or the lock would never be scheduled.
        val repo = repository()
        val seen = mutableListOf<SecurityState>()
        repo.addListener { _, new ->
            kotlinx.coroutines.delay(300) // a slow listener before the important one
            seen += new.state
        }
        val caller = launch { repo.dispatch(SecurityEvent.GeofenceDwelled) }
        advanceTimeBy(100)
        caller.cancel() // cancelled while listeners are running
        advanceTimeBy(1_000)
        assertEquals(listOf(SecurityState.INSIDE), seen)
    }
}
