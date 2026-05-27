package com.streamflixreborn.streamflix.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.SeasonDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.UserPreferences

@Database(
    entities = [
        Episode::class,
        Movie::class,
        Season::class,
        TvShow::class,
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao

    abstract fun tvShowDao(): TvShowDao

    abstract fun seasonDao(): SeasonDao

    abstract fun episodeDao(): EpisodeDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var currentProviderName: String? = null

        private fun sanitizeProviderName(name: String): String {
            return name.lowercase()
                .replace("[^a-z0-9]".toRegex(), "_")
                .replace("__+".toRegex(), "_")
                .trim('_')
        }

        fun setup(context: Context) {
            if (UserPreferences.currentProvider == null) return

            getInstance(context)
        }

        fun getInstance(context: Context): AppDatabase {
            val providerName = UserPreferences.currentProvider?.name
                ?: currentProviderName
                ?: throw IllegalStateException("Current provider is not set")

            return INSTANCE?.takeIf { currentProviderName == providerName } ?: synchronized(this) {
                INSTANCE?.takeIf { currentProviderName == providerName } ?: run {
                    INSTANCE?.close()
                    buildDatabase(providerName, context).also { instance ->
                        INSTANCE = instance
                        currentProviderName = providerName
                    }
                }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                currentProviderName = null
            }
        }

        fun getInstanceForProvider(providerName: String, context: Context): AppDatabase {
            return buildDatabase(providerName, context)
        }

        private fun buildDatabase(providerName: String, context: Context): AppDatabase {
            val sanitizedName = sanitizeProviderName(providerName)
            return Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = "$sanitizedName.db"
            )
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
