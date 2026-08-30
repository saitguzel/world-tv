package com.worldtv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.worldtv.core.database.dao.ChannelDao
import com.worldtv.core.database.dao.EpgDao
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.core.database.dao.YouTubeDao
import com.worldtv.core.database.entity.BlocklistEntity
import com.worldtv.core.database.entity.CategoryEntity
import com.worldtv.core.database.entity.ChannelEntity
import com.worldtv.core.database.entity.CountryEntity
import com.worldtv.core.database.entity.EpgSourceEntity
import com.worldtv.core.database.entity.DeviceBlacklistEntity
import com.worldtv.core.database.entity.FavoriteEntity
import com.worldtv.core.database.entity.RadioStationEntity
import com.worldtv.core.database.entity.ProgrammeEntity
import com.worldtv.core.database.entity.RecentEntity
import com.worldtv.core.database.entity.StreamEntity
import com.worldtv.core.database.entity.SyncStateEntity
import com.worldtv.core.database.entity.YouTubeStreamEntity

@Database(
    entities = [
        ChannelEntity::class,
        StreamEntity::class,
        CountryEntity::class,
        CategoryEntity::class,
        BlocklistEntity::class,
        FavoriteEntity::class,
        RecentEntity::class,
        DeviceBlacklistEntity::class,
        RadioStationEntity::class,
        YouTubeStreamEntity::class,
        SyncStateEntity::class,
        ProgrammeEntity::class,
        EpgSourceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WorldTvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun streamDao(): StreamDao
    abstract fun radioDao(): RadioDao
    abstract fun userDataDao(): UserDataDao
    abstract fun youTubeDao(): YouTubeDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val NAME = "worldtv.db"
    }
}
