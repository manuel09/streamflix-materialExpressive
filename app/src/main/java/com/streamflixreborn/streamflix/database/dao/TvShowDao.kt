package com.streamflixreborn.streamflix.database.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamflixreborn.streamflix.models.TvShow
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.format

@Dao
interface TvShowDao {

    @Query("SELECT * FROM tv_shows WHERE profileId = :profileId")
    fun getAllForBackup(profileId: String = UserPreferences.activeProfileId): List<TvShow>

    @Query("SELECT * FROM tv_shows WHERE id = :id AND profileId = :profileId")
    fun getById(id: String, profileId: String = UserPreferences.activeProfileId): TvShow?

    @Query("SELECT * FROM tv_shows WHERE id = :id AND profileId = :profileId")
    fun getByIdAsFlow(id: String, profileId: String = UserPreferences.activeProfileId): Flow<TvShow?>

    @Query("SELECT * FROM tv_shows WHERE id IN (:ids) AND profileId = :profileId")
    fun getByIds(ids: List<String>, profileId: String = UserPreferences.activeProfileId): Flow<List<TvShow>>

    @Query("SELECT * FROM tv_shows WHERE isFavorite = 1 AND profileId = :profileId ORDER BY favoritedAtMillis DESC")
    fun getFavorites(profileId: String = UserPreferences.activeProfileId): Flow<List<TvShow>>

    @Query("SELECT * FROM tv_shows WHERE (isFavorite = 1 OR poster IS NULL OR poster = '' OR banner IS NULL OR banner = '') AND profileId = :profileId")
    suspend fun getArtworkRepairCandidates(profileId: String = UserPreferences.activeProfileId): List<TvShow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tvShow: TvShow)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(tvShow: TvShow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tvShows: List<TvShow>)

    @Query("SELECT * FROM tv_shows WHERE profileId = :profileId")
    fun getAll(profileId: String = UserPreferences.activeProfileId): Flow<List<TvShow>>

    @Query("SELECT * FROM tv_shows WHERE (poster IS NULL or poster = '') AND profileId = :profileId")
    suspend fun getAllWithNullPoster(profileId: String = UserPreferences.activeProfileId): List<TvShow>

    @Query("SELECT id FROM tv_shows WHERE profileId = :profileId")
    suspend fun getAllIds(profileId: String = UserPreferences.activeProfileId): List<String>

    @Query("SELECT * FROM tv_shows WHERE LOWER(title) LIKE '%' || :query || '%' AND profileId = :profileId LIMIT :limit OFFSET :offset")
    suspend fun searchTvShows(query: String, limit: Int, offset: Int, profileId: String = UserPreferences.activeProfileId): List<TvShow>

    @Query("DELETE FROM tv_shows WHERE profileId = :profileId")
    fun deleteAll(profileId: String = UserPreferences.activeProfileId)

    @Transaction
    fun save(tvShow: TvShow) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        tvShow.profileId = UserPreferences.activeProfileId
        val existing = getById(tvShow.id, tvShow.profileId)
        if (existing != null) {
            val merged = tvShow.merge(existing)
            update(merged)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME UPDATE TV Show: ${merged.title} (Fav: ${merged.isFavorite}, Watching: ${merged.isWatching}, Profile: ${tvShow.profileId})")
        } else {
            insert(tvShow)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME INSERT TV Show: ${tvShow.title} (Fav: ${tvShow.isFavorite}, Profile: ${tvShow.profileId})")
        }
    }

    @Transaction
    fun setFavoriteWithLog(id: String, favorite: Boolean) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        val profileId = UserPreferences.activeProfileId
        setFavorite(id, favorite, if (favorite) System.currentTimeMillis() else null, profileId)
        Log.d("DatabaseVerify", "[$provider] REAL-TIME Favorite Toggled: ID $id -> $favorite (Profile: $profileId)")
    }

    @Transaction
    fun upsertFavorite(tvShow: TvShow, favorite: Boolean) {
        tvShow.profileId = UserPreferences.activeProfileId
        val existing = getById(tvShow.id, tvShow.profileId)
        if (existing != null) {
            val updated = existing.copy(
                title = tvShow.title.ifBlank { existing.title },
                overview = tvShow.overview ?: existing.overview,
                released = tvShow.released?.format("yyyy-MM-dd") ?: existing.released?.format("yyyy-MM-dd"),
                runtime = tvShow.runtime ?: existing.runtime,
                trailer = tvShow.trailer ?: existing.trailer,
                quality = tvShow.quality ?: existing.quality,
                rating = tvShow.rating ?: existing.rating,
                poster = tvShow.poster ?: existing.poster,
                banner = tvShow.banner ?: existing.banner,
                imdbId = tvShow.imdbId ?: existing.imdbId,
                seasons = if (tvShow.seasons.isNotEmpty()) tvShow.seasons else existing.seasons,
                genres = if (tvShow.genres.isNotEmpty()) tvShow.genres else existing.genres,
                directors = if (tvShow.directors.isNotEmpty()) tvShow.directors else existing.directors,
                cast = if (tvShow.cast.isNotEmpty()) tvShow.cast else existing.cast,
                recommendations = if (tvShow.recommendations.isNotEmpty()) tvShow.recommendations else existing.recommendations,
                isFavorite = favorite,
            )
            updated.favoritedAtMillis = if (favorite) System.currentTimeMillis() else null
            updated.isWatching = existing.isWatching
            updated.profileId = tvShow.profileId
            update(updated)
        } else {
            tvShow.isFavorite = favorite
            tvShow.favoritedAtMillis = if (favorite) System.currentTimeMillis() else null
            insert(tvShow)
        }
    }

    @Query("UPDATE tv_shows SET isFavorite = :favorite, favoritedAtMillis = :favoritedAtMillis WHERE id = :id AND profileId = :profileId")
    fun setFavorite(id: String, favorite: Boolean, favoritedAtMillis: Long?, profileId: String)

    @Query("UPDATE tv_shows SET isWatching = :isWatching WHERE id = :id AND profileId = :profileId")
    fun setWatching(id: String, isWatching: Boolean, profileId: String = UserPreferences.activeProfileId)
}
