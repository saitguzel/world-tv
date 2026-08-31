package com.worldtv.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Lets UI ask for a catalog refresh without depending on WorkManager.
 *
 * `:data:sync` implements this and `:app` binds it. The inversion keeps the feature
 * modules off the WorkManager classpath — they only ever need "refresh, and tell me
 * when it is running".
 */
interface SyncTrigger {
    /** Enqueues an immediate catalog sync, replacing any already queued. */
    fun syncNow()

    /** True while that sync is running, for first-run and settings progress. */
    val isSyncing: Flow<Boolean>
}
