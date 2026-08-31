package com.worldtv.data.sync.di

import com.worldtv.data.repository.SyncTrigger
import com.worldtv.data.sync.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncTrigger(impl: SyncScheduler): SyncTrigger
}
