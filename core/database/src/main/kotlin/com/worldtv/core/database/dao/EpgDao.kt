package com.worldtv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worldtv.core.database.entity.EpgSourceEntity
import com.worldtv.core.database.entity.ProgrammeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    /**
     * What is on now, plus what follows, for one channel.
     *
     * Two rows rather than a whole day: the card and the player info panel only ever
     * show now and next, and a channel with a fortnight of guide data has hundreds of
     * rows that nothing would read.
     */
    @Query(
        """
        SELECT * FROM programmes
        WHERE channelId = :channelId AND endAt > :now
        ORDER BY startAt ASC
        LIMIT 2
        """,
    )
    fun nowAndNext(channelId: String, now: Long): Flow<List<ProgrammeEntity>>

    @Query(
        """
        SELECT * FROM programmes
        WHERE channelId = :channelId AND endAt > :now
        ORDER BY startAt ASC
        LIMIT 2
        """,
    )
    suspend fun nowAndNextOnce(channelId: String, now: Long): List<ProgrammeEntity>

    /** Now/next for a set of channels at once, so a grid needs one query, not sixty. */
    @Query(
        """
        SELECT p.* FROM programmes p
        WHERE p.channelId IN (:channelIds)
          AND p.startAt <= :now AND p.endAt > :now
        """,
    )
    fun nowForChannels(channelIds: List<String>, now: Long): Flow<List<ProgrammeEntity>>

    @Query(
        """
        SELECT * FROM programmes
        WHERE channelId = :channelId AND endAt > :from AND startAt < :to
        ORDER BY startAt ASC
        """,
    )
    fun schedule(channelId: String, from: Long, to: Long): Flow<List<ProgrammeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programmes: List<ProgrammeEntity>)

    /**
     * Drops programmes that have already ended.
     *
     * Guides are the fastest-growing table in the app — a hundred channels for a
     * fortnight is well past a million rows — and yesterday's schedule has no use
     * whatsoever.
     */
    @Query("DELETE FROM programmes WHERE endAt < :before")
    suspend fun purgeEnded(before: Long): Int

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSources(sources: List<EpgSourceEntity>)

    /**
     * Distinct guide URLs and the channels each covers.
     *
     * One XMLTV file typically carries a whole country, so the sync fetches per URL
     * and not per channel — the difference is one download versus two hundred.
     */
    @Query("SELECT DISTINCT url FROM epg_sources")
    suspend fun distinctSourceUrls(): List<String>

    @Query("SELECT channelId FROM epg_sources WHERE url = :url")
    suspend fun channelsForSource(url: String): List<String>

    @Query("UPDATE epg_sources SET lastFetchedAt = :now WHERE url = :url")
    suspend fun markSourceFetched(url: String, now: Long)

    @Query("SELECT COUNT(*) FROM epg_sources")
    suspend fun sourceCount(): Int
}
