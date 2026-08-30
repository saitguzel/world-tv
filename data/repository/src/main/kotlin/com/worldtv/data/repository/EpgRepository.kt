package com.worldtv.data.repository

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.EpgDao
import com.worldtv.core.database.entity.ProgrammeEntity
import com.worldtv.core.model.NowNext
import com.worldtv.core.model.Programme
import com.worldtv.core.model.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class EpgRepository @Inject constructor(
    private val dao: EpgDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun nowAndNext(channelId: String): Flow<NowNext> =
        dao.nowAndNext(channelId, time.nowMillis()).map { rows ->
            val programmes = rows.map(ProgrammeEntity::toModel)
            val now = time.nowMillis()
            // The query returns the next two entries by start time; the first is only
            // "now" if it has actually started. A gap in the guide is common, and
            // labelling tomorrow's programme as on-air is worse than showing nothing.
            val current = programmes.firstOrNull()?.takeIf { it.isOnAt(now) }
            val next = if (current == null) programmes.firstOrNull() else programmes.getOrNull(1)
            NowNext(now = current, next = next)
        }

    suspend fun nowAndNextOnce(channelId: String): NowNext = withContext(io) {
        val now = time.nowMillis()
        val programmes = dao.nowAndNextOnce(channelId, now).map(ProgrammeEntity::toModel)
        val current = programmes.firstOrNull()?.takeIf { it.isOnAt(now) }
        NowNext(
            now = current,
            next = if (current == null) programmes.firstOrNull() else programmes.getOrNull(1),
        )
    }

    /** Now-playing titles keyed by channel, for a whole grid in one query. */
    fun nowForChannels(channelIds: List<String>): Flow<Map<String, Programme>> {
        if (channelIds.isEmpty()) return flowOf(emptyMap())
        return dao.nowForChannels(channelIds, time.nowMillis()).map { rows ->
            rows.associate { it.channelId to it.toModel() }
        }
    }

    fun schedule(channelId: String, from: Long, to: Long): Flow<List<Programme>> =
        dao.schedule(channelId, from, to).map { rows -> rows.map(ProgrammeEntity::toModel) }

    suspend fun hasGuideData(): Boolean = withContext(io) { dao.count() > 0 }
}

internal fun ProgrammeEntity.toModel(): Programme = Programme(
    channelId = channelId,
    startAt = startAt,
    endAt = endAt,
    title = title,
    description = description,
    category = category,
    episode = episode,
)

internal fun Programme.toEntity(): ProgrammeEntity = ProgrammeEntity(
    channelId = channelId,
    startAt = startAt,
    endAt = endAt,
    title = title,
    description = description,
    category = category,
    episode = episode,
)
