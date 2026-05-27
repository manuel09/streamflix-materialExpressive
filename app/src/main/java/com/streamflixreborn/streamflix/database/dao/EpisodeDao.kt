package com.streamflixreborn.streamflix.database.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamflixreborn.streamflix.models.Episode
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction
import com.streamflixreborn.streamflix.utils.UserPreferences

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE profileId = :profileId")
    fun getAllForBackup(profileId: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE id = :id AND profileId = :profileId")
    fun getById(id: String, profileId: String): Episode?

    @Query("SELECT * FROM episodes WHERE id IN (:ids) AND profileId = :profileId")
    fun getByIds(ids: List<String>, profileId: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE id IN (:ids) AND profileId = :profileId")
    fun getByIdsAsFlow(ids: List<String>, profileId: String): Flow<List<Episode>>
    @Query("SELECT * FROM episodes WHERE season = :seasonId AND profileId = :profileId")
    fun getBySeasonIdAsFlow(seasonId: String, profileId: String): Flow<List<Episode>>

    @Query("SELECT COUNT(id) > 0 FROM episodes WHERE tvShow = :tvShowId AND lastEngagementTimeUtcMillis IS NOT NULL AND profileId = :profileId")
    fun hasAnyWatchHistoryForTvShow(tvShowId: String, profileId: String): Boolean

    @Query("SELECT * FROM episodes WHERE tvShow = :tvShowId AND profileId = :profileId ORDER BY season, number")
    fun getByTvShowId(tvShowId: String, profileId: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE tvShow = :tvShowId AND profileId = :profileId ORDER BY season, number")
    fun getByTvShowIdAsFlow(tvShowId: String, profileId: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE season = :seasonId AND profileId = :profileId ORDER BY season, number")
    fun getBySeasonId(seasonId: String, profileId: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE lastEngagementTimeUtcMillis IS NOT NULL AND profileId = :profileId ORDER BY lastEngagementTimeUtcMillis DESC")
    fun getWatchingEpisodes(profileId: String): Flow<List<Episode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(episode: Episode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(episodes: List<Episode>)

    @Query("SELECT * FROM episodes WHERE tvShow = :tvShowId AND profileId = :profileId")
    fun getEpisodesByTvShowId(tvShowId: String, profileId: String): List<Episode>
    @Query("SELECT * FROM episodes WHERE tvShow = :tvShowId AND season = :season AND profileId = :profileId")
    fun getEpisodesByTvShowIdAndSeason(tvShowId: String, season: String?, profileId: String): List<Episode>
    @Update
    fun update(episode: Episode)

    @Query("DELETE FROM episodes WHERE profileId = :profileId")
    fun deleteAll(profileId: String)

    @Transaction
    fun save(episode: Episode) {
        val provider = UserPreferences.currentProvider?.name ?: "Unknown"
        episode.profileId = UserPreferences.activeProfileId
        val existing = getById(episode.id, episode.profileId)
        if (existing != null) {
            existing.merge(episode)
            update(existing)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME UPDATE Episode: ${existing.title} (Watched: ${existing.isWatched}, Hist: ${existing.watchHistory != null}, Profile: ${episode.profileId})")
        } else {
            insert(episode)
            Log.d("DatabaseVerify", "[$provider] REAL-TIME INSERT Episode: ${episode.id} (Watched: ${episode.isWatched}, Profile: ${episode.profileId})")
        }
    }

    @Query("""
        UPDATE episodes
        SET isWatched = 0
        WHERE id IN (
            SELECT episode.id
            FROM episodes episode
            LEFT JOIN seasons season ON episode.season = season.id
            JOIN (
                  SELECT episode.tvShow AS tvShow, season.number AS seasonNumber, episode.number AS number
                  FROM episodes episode
                  LEFT JOIN seasons season ON episode.season = season.id
                  WHERE episode.id = :id AND episode.profileId = :profileId
            ) episode2 ON (episode.tvShow = episode2.tvShow AND (season.number > episode2.seasonNumber OR (season.number = episode2.seasonNumber AND episode.number > episode2.number)))
            WHERE episode.profileId = :profileId
        )
    """)
    fun resetProgressionFromEpisode(id: String, profileId: String)

    @Query("""
        SELECT e.* 
        FROM episodes e
        JOIN seasons s ON s.id = e.season
        WHERE e.tvShow = :tvShowId AND s.number = :seasonNumber AND e.profileId = :profileId
        ORDER BY s.number, e.number
    """)
    fun getByTvShowIdAndSeasonNumber(tvShowId: String, seasonNumber: Int, profileId: String): List<Episode>

    @Query("UPDATE episodes SET lastPlaybackPositionMillis = NULL, durationMillis = NULL, lastEngagementTimeUtcMillis = NULL WHERE tvShow = :tvShowId AND profileId = :profileId")
    fun removeFromContinueWatchingByTvShow(tvShowId: String, profileId: String)

    @Query("UPDATE episodes SET lastPlaybackPositionMillis = NULL, durationMillis = NULL, lastEngagementTimeUtcMillis = NULL WHERE id = :id AND profileId = :profileId")
    fun removeFromContinueWatching(id: String, profileId: String)

    @Query("UPDATE episodes SET isWatched = :isWatched WHERE id = :id AND profileId = :profileId")
    fun setWatched(id: String, isWatched: Boolean, profileId: String)

    @Transaction
    fun markAsWatchedUpToHere(episodeId: String) {
        val profileId = UserPreferences.activeProfileId
        val target = getById(episodeId, profileId) ?: return
        val tvShowId = target.tvShow?.id ?: return
        markAsWatchedUpToHereQuery(tvShowId, episodeId, profileId)
    }

    @Query("""
        UPDATE episodes 
        SET isWatched = 1 
        WHERE tvShow = :tvShowId AND profileId = :profileId AND id IN (
            SELECT e.id FROM episodes e
            LEFT JOIN seasons s ON e.season = s.id
            JOIN (
                SELECT e2.tvShow as tvShow, s2.number as seasonNumber, e2.number as number
                FROM episodes e2
                LEFT JOIN seasons s2 ON e2.season = s2.id
                WHERE e2.id = :episodeId AND e2.profileId = :profileId
            ) target ON (e.tvShow = target.tvShow AND (s.number < target.seasonNumber OR (s.number = target.seasonNumber AND e.number <= target.number)))
        )
    """)
    fun markAsWatchedUpToHereQuery(tvShowId: String, episodeId: String, profileId: String)
}
