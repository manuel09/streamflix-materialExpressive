package com.streamflixreborn.streamflix.database.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamflixreborn.streamflix.models.Movie
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.format

@Dao
interface MovieDao {

    @Query("SELECT * FROM movies WHERE profileId = :profileId")
    fun getAll(profileId: String = UserPreferences.activeProfileId): List<Movie>

    @Query("SELECT * FROM movies WHERE id = :id AND profileId = :profileId")
    fun getById(id: String, profileId: String = UserPreferences.activeProfileId): Movie?

    @Query("SELECT * FROM movies WHERE id = :id AND profileId = :profileId")
    fun getByIdAsFlow(id: String, profileId: String = UserPreferences.activeProfileId): Flow<Movie?>

    @Query("SELECT * FROM movies WHERE id IN (:ids) AND profileId = :profileId")
    fun getByIds(ids: List<String>, profileId: String = UserPreferences.activeProfileId): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE isFavorite = 1 AND profileId = :profileId ORDER BY favoritedAtMillis DESC")
    fun getFavorites(profileId: String = UserPreferences.activeProfileId): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE (isFavorite = 1 OR poster IS NULL OR poster = '' OR banner IS NULL OR banner = '') AND profileId = :profileId")
    suspend fun getArtworkRepairCandidates(profileId: String = UserPreferences.activeProfileId): List<Movie>

    @Query("SELECT * FROM movies WHERE lastEngagementTimeUtcMillis IS NOT NULL AND profileId = :profileId ORDER BY lastEngagementTimeUtcMillis DESC")
    fun getWatchingMovies(profileId: String = UserPreferences.activeProfileId): Flow<List<Movie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(movie: Movie)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(movies: List<Movie>)

    @Update
    fun update(movie: Movie)

    @Query("DELETE FROM movies WHERE profileId = :profileId")
    fun deleteAll(profileId: String = UserPreferences.activeProfileId)

    @Transaction
    fun save(movie: Movie) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        movie.profileId = UserPreferences.activeProfileId
        val existing = getById(movie.id, movie.profileId)
        if (existing != null) {
            val merged = movie.merge(existing)
            update(merged)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME UPDATE Movie: ${merged.title} (Fav: ${merged.isFavorite}, Watched: ${merged.isWatched}, Profile: ${movie.profileId})")
        } else {
            insert(movie)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME INSERT Movie: ${movie.title} (Fav: ${movie.isFavorite}, Profile: ${movie.profileId})")
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
    fun upsertFavorite(movie: Movie, favorite: Boolean) {
        movie.profileId = UserPreferences.activeProfileId
        val existing = getById(movie.id, movie.profileId)
        if (existing != null) {
            val updated = existing.copy(
                title = movie.title.ifBlank { existing.title },
                overview = movie.overview ?: existing.overview,
                released = movie.released?.format("yyyy-MM-dd") ?: existing.released?.format("yyyy-MM-dd"),
                runtime = movie.runtime ?: existing.runtime,
                trailer = movie.trailer ?: existing.trailer,
                quality = movie.quality ?: existing.quality,
                rating = movie.rating ?: existing.rating,
                poster = movie.poster ?: existing.poster,
                banner = movie.banner ?: existing.banner,
                imdbId = movie.imdbId ?: existing.imdbId,
                genres = if (movie.genres.isNotEmpty()) movie.genres else existing.genres,
                directors = if (movie.directors.isNotEmpty()) movie.directors else existing.directors,
                cast = if (movie.cast.isNotEmpty()) movie.cast else existing.cast,
                recommendations = if (movie.recommendations.isNotEmpty()) movie.recommendations else existing.recommendations,
                isFavorite = favorite,
            )
            updated.favoritedAtMillis = if (favorite) System.currentTimeMillis() else null
            updated.isWatched = existing.isWatched
            updated.watchedDate = existing.watchedDate
            updated.watchHistory = existing.watchHistory
            updated.profileId = movie.profileId
            update(updated)
        } else {
            movie.isFavorite = favorite
            movie.favoritedAtMillis = if (favorite) System.currentTimeMillis() else null
            insert(movie)
        }
    }

    @Query("UPDATE movies SET isFavorite = :favorite, favoritedAtMillis = :favoritedAtMillis WHERE id = :id AND profileId = :profileId")
    fun setFavorite(id: String, favorite: Boolean, favoritedAtMillis: Long?, profileId: String)

    @Query("UPDATE movies SET lastPlaybackPositionMillis = NULL, durationMillis = NULL, lastEngagementTimeUtcMillis = NULL WHERE id = :id AND profileId = :profileId")
    fun removeFromContinueWatching(id: String, profileId: String = UserPreferences.activeProfileId)

    @Query("UPDATE movies SET isWatched = :isWatched WHERE id = :id AND profileId = :profileId")
    fun setWatched(id: String, isWatched: Boolean, profileId: String = UserPreferences.activeProfileId)
}
