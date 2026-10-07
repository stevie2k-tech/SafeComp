package com.safecomp.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safecomp.SafeCompApp
import com.safecomp.data.GeofenceConfigRepository
import com.safecomp.security.SecurityEvent
import com.safecomp.security.SecuritySnapshot
import com.safecomp.security.SecurityStateListener
import java.util.concurrent.TimeUnit

/**
 * Finishes a grace period when SafeComp isn't running in the foreground.
 *
 * Example: you walk out of the office with the app closed. Play services wakes us with a
 * geofence EXIT, which starts the 60 s grace period. Android may then freeze or kill the
 * process, so the in-process 1 s ticker never fires. This job is scheduled for the moment
 * the grace period ends. It sends a Tick, which locks the vault (in the saved state) and
 * posts the "Vault locked" notification.
 *
 * In Doze, WorkManager may run the job late. That delays only the NOTIFICATION. Access
 * checks compare against the clock directly, so the vault is locked on time regardless.
 */
class GraceExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SafeCompApp).container.securityState.dispatch(SecurityEvent.Tick)
        return Result.success()
    }
}

/** Schedules [GraceExpiryWorker] when a grace period starts, and cancels it if it's cancelled. */
class GraceExpiryScheduler(
    context: Context,
    private val configRepository: GeofenceConfigRepository,
    private val clock: () -> Long,
) : SecurityStateListener {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun onStateChanged(old: SecuritySnapshot, new: SecuritySnapshot) {
        val pending = new.pendingExit
        if (pending == old.pendingExit) return
        if (pending == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val graceMillis = (configRepository.current()?.graceSeconds ?: 0) * 1000L
        val delayMillis = (graceMillis - (clock() - pending.sinceMillis)).coerceAtLeast(0)
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GraceExpiryWorker>()
                .setInitialDelay(delayMillis + 1_000, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private companion object {
        const val WORK_NAME = "grace-expiry"
    }
}
