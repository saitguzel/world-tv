package com.worldtv.data.repository.di

import com.worldtv.core.common.AndroidTimeProvider
import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.network.di.MediaClient
import com.worldtv.data.health.HealthChecker
import com.worldtv.data.health.HealthStore
import com.worldtv.data.health.HttpStreamProbe
import com.worldtv.data.health.StreamProbe
import com.worldtv.data.repository.RoomHealthStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: AndroidTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindHealthStore(impl: RoomHealthStore): HealthStore
}

/**
 * The health engine is a plain Kotlin module, so its Android-side wiring lives here
 * rather than in `:data:health` — that module has no Hilt on its classpath by design.
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthEngineModule {

    @Provides
    @Singleton
    fun provideStreamProbe(
        @MediaClient client: OkHttpClient,
        time: TimeProvider,
    ): StreamProbe = HttpStreamProbe(client, time)

    @Provides
    @Singleton
    fun provideHealthChecker(
        probe: StreamProbe,
        store: HealthStore,
        time: TimeProvider,
        @IoDispatcher io: CoroutineDispatcher,
    ): HealthChecker = HealthChecker(probe, store, time, io)
}
