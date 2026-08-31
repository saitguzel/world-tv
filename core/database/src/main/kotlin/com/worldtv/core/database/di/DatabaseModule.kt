package com.worldtv.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.worldtv.core.database.WorldTvDatabase
import com.worldtv.core.database.dao.ChannelDao
import com.worldtv.core.database.dao.EpgDao
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.dao.UserDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorldTvDatabase =
        Room.databaseBuilder(context, WorldTvDatabase::class.java, WorldTvDatabase.NAME)
            // The catalog is a cache of a public directory: if a migration is ever
            // missed, re-downloading is cheaper than shipping a broken database. User
            // data (favourites, recents) is the part that matters, and it is small
            // enough to back up separately before any destructive fallback ships.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun provideChannelDao(db: WorldTvDatabase): ChannelDao = db.channelDao()

    @Provides fun provideStreamDao(db: WorldTvDatabase): StreamDao = db.streamDao()

    @Provides fun provideRadioDao(db: WorldTvDatabase): RadioDao = db.radioDao()

    @Provides fun provideUserDataDao(db: WorldTvDatabase): UserDataDao = db.userDataDao()

    @Provides fun provideEpgDao(db: WorldTvDatabase): EpgDao = db.epgDao()
}
