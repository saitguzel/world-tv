package com.worldtv.data.repository

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.core.database.entity.FavoriteEntity
import com.worldtv.core.model.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: UserDataDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun isFavorite(id: String, kind: Kind): Flow<Boolean> = dao.isFavorite(id, kind.key)

    fun favoriteIds(kind: Kind): Flow<List<String>> = dao.favoriteIds(kind.key)

    suspend fun toggle(id: String, kind: Kind, currentlyFavorite: Boolean) = withContext(io) {
        if (currentlyFavorite) {
            dao.removeFavorite(id, kind.key)
        } else {
            dao.addFavorite(FavoriteEntity(id = id, kind = kind.key, addedAt = time.nowMillis()))
        }
    }

    /** Records a view and trims the history so "continue watching" stays a short row. */
    suspend fun recordWatch(id: String, kind: Kind) = withContext(io) {
        dao.recordWatch(id, kind.key, time.nowMillis())
        dao.trimRecents(keep = MAX_RECENTS)
    }

    enum class Kind(val key: String) { CHANNEL("channel"), RADIO("radio") }

    private companion object {
        const val MAX_RECENTS = 40
    }
}
