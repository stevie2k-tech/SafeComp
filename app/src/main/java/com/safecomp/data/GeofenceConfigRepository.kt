package com.safecomp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GeofenceConfigRepository(private val dao: GeofenceConfigDao) {

    val config: Flow<GeofenceConfig?> = dao.observe().map { it?.validated() }

    private val mutex = Mutex()
    private var cached: GeofenceConfig? = null
    private var loaded = false

    /** The current config, read from disk once and then cached. This is a suspend call, so a
     *  geofence broadcast that starts a fresh process waits for the real config instead of
     *  being processed as "not configured". */
    suspend fun current(): GeofenceConfig? = mutex.withLock {
        if (!loaded) {
            cached = dao.get()?.validated()
            loaded = true
        }
        cached
    }

    suspend fun save(config: GeofenceConfig): GeofenceConfig {
        val valid = config.validated()
        mutex.withLock {
            dao.upsert(valid)
            cached = valid
            loaded = true
        }
        return valid
    }

    suspend fun clear() = mutex.withLock {
        dao.clear()
        cached = null
        loaded = true
    }
}
