package com.streamflixreborn.streamflix.fragments.home

import android.util.Log
import com.streamflixreborn.streamflix.StreamFlixApp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.ui.UserDataNotifier
import com.streamflixreborn.streamflix.utils.HomeCacheStore
import com.streamflixreborn.streamflix.utils.ParentalControlUtils
import com.streamflixreborn.streamflix.utils.ProviderChangeNotifier
import com.streamflixreborn.streamflix.utils.UserDataCache
import com.streamflixreborn.streamflix.utils.UserDataCache.toCached
import com.streamflixreborn.streamflix.utils.UserDataCache.toEpisode
import com.streamflixreborn.streamflix.utils.UserDataCache.toMovie
import com.streamflixreborn.streamflix.utils.UserDataCache.toTvShow
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(database: AppDatabase) : ViewModel() {

    private fun <T> preserveCacheOrder(
        cached: List<T>,
        incoming: List<T>,
        idOf: (T) -> String,
    ): List<T> {
        val incomingById = incoming.associateBy(idOf)
        val orderedExisting = cached.mapNotNull { cachedItem -> incomingById[idOf(cachedItem)] }
        val appendedNew = incoming.filter { incomingItem ->
            cached.none { cachedItem -> idOf(cachedItem) == idOf(incomingItem) }
        }
        return orderedExisting + appendedNew
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    private val continueWatchingTvShowCache = ConcurrentHashMap<String, TvShow>()
    private val continueWatchingSeasonEpisodesCache = ConcurrentHashMap<String, List<Episode>>()
    private val _userDataCache = MutableStateFlow<UserDataCache.UserData?>(null)
    private var currentProvider: Provider? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = com.streamflixreborn.streamflix.utils.combine(
        _state,

        // CONTINUE WATCHING - Cache-first (faster on slow DB devices), falls back to DB
        kotlinx.coroutines.flow.combine(
            _userDataCache.transformLatest { cache: UserDataCache.UserData? ->
                if (cache != null && cache.continueWatchingMovies.isNotEmpty()) {
                    emit(cache.continueWatchingMovies.map { it.toMovie() })
                } else {
                    emitAll(database.movieDao().getWatchingMovies(UserPreferences.activeProfileId))
                }
            }.flowOn(Dispatchers.IO),
            _userDataCache.transformLatest { cache: UserDataCache.UserData? ->
                if (cache != null && cache.continueWatchingEpisodes.isNotEmpty()) {
                    emit(cache.continueWatchingEpisodes.map { it.toEpisode() })
                } else {
                    emitAll(database.episodeDao().getWatchingEpisodes(UserPreferences.activeProfileId))
                }
            }.flowOn(Dispatchers.IO),
            _userDataCache.transformLatest { cache: UserDataCache.UserData? ->
                if (cache != null && cache.continueWatchingEpisodes.isNotEmpty()) {
                    emit(cache.continueWatchingEpisodes.map { it.toEpisode() })
                } else {
                    emitAll(database.episodeDao().getNextEpisodesToWatch(UserPreferences.activeProfileId))
                }
            }.flowOn(Dispatchers.IO),
            database.tvShowDao().getAll(UserPreferences.activeProfileId).flowOn(Dispatchers.IO),
        ) { watchingMovies: List<Movie>, watchingEpisodes: List<Episode>, watchNextEpisodes: List<Episode>, tvShows: List<TvShow> ->

            val allEpisodes = (watchingEpisodes + watchNextEpisodes)
                .distinctBy { it.id }

            val seasonIds = allEpisodes.mapNotNull { it.season?.id }.distinct()

            val tvShowsMap = tvShows.associateBy { it.id }

            val seasonsMap = if (seasonIds.isEmpty()) {
                emptyMap()
            } else {
                database.seasonDao()
                    .getByIds(seasonIds)
                    .associateBy { it.id }
            }

            val enrichedEpisodes = enrichContinueWatchingEpisodes(
                episodes = allEpisodes.map { episode ->
                    val newEpisode = episode.copy(
                        tvShow = episode.tvShow?.id?.let { tvShowsMap[it] } ?: episode.tvShow,
                        season = episode.season?.id?.let { seasonsMap[it] } ?: episode.season,
                    )
                    newEpisode.merge(episode)
                    newEpisode
                }
            )

            val orderIndex = buildMap<String, Int> {
                _userDataCache.value?.continueWatchingMovies?.forEachIndexed { index, cached ->
                    put("movie:${cached.id}", index)
                }
                _userDataCache.value?.continueWatchingEpisodes?.forEachIndexed { index, cached ->
                    put("episode:${cached.id}", index)
                }
            }

            (watchingMovies + enrichedEpisodes)
                .sortedByDescending { item ->
                    when (item) {
                        is Movie -> item.watchHistory?.lastEngagementTimeUtcMillis
                            ?: item.watchedDate?.timeInMillis
                            ?: 0L
                        is Episode -> item.watchHistory?.lastEngagementTimeUtcMillis
                            ?: item.watchedDate?.timeInMillis
                            ?: 0L
                        else -> 0L
                    }
                } as List<AppAdapter.Item>
        }.flowOn(Dispatchers.IO),

        // FAVORITES - from cache first, DB as fallback
        _userDataCache.transformLatest { cache: UserDataCache.UserData? ->
            if (cache != null && cache.favoritesMovies.isNotEmpty()) {
                emit(cache.favoritesMovies.map { it.toMovie() })
            } else {
                emitAll(database.movieDao().getFavorites(UserPreferences.activeProfileId))
            }
        }.flowOn(Dispatchers.IO),
        _userDataCache.transformLatest { cache: UserDataCache.UserData? ->
            if (cache != null && cache.favoritesTvShows.isNotEmpty()) {
                emit(cache.favoritesTvShows.map { it.toTvShow() })
            } else {
                emitAll(database.tvShowDao().getFavorites(UserPreferences.activeProfileId))
            }
        }.flowOn(Dispatchers.IO),

        // MOVIES DB
        _state.transformLatest { state: State ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<Movie>()
                    if (movies.isEmpty()) {
                        emit(emptyList<Movie>())
                    } else {
                        emitAll(database.movieDao().getByIds(movies.map { it.id }, UserPreferences.activeProfileId))
                    }
                }
                else -> emit(emptyList<Movie>())
            }
        }.flowOn(Dispatchers.IO),

        // TV SHOWS DB
        _state.transformLatest { state: State ->
            when (state) {
                is State.SuccessLoading -> {
                    val tvShows = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<TvShow>()
                    if (tvShows.isEmpty()) {
                        emit(emptyList<TvShow>())
                    } else {
                        emitAll(database.tvShowDao().getByIds(tvShows.map { it.id }, UserPreferences.activeProfileId))
                    }
                }
                else -> emit(emptyList<TvShow>())
            }
        }.flowOn(Dispatchers.IO),

    ) { state: State, continueWatching: List<AppAdapter.Item>, favoritesMovies: List<Movie>, favoriteTvShows: List<TvShow>, moviesDb: List<Movie>, tvShowsDb: List<TvShow> ->

        when (state) {
            is State.SuccessLoading -> {

                val moviesMap = moviesDb.associateBy { it.id }
                val tvShowsMap = tvShowsDb.associateBy { it.id }

                fun mergeItem(item: AppAdapter.Item): AppAdapter.Item {
                    return when (item) {
                        is Movie -> moviesMap[item.id]
                            ?.takeIf { movieFromDb -> !item.isSame(movieFromDb) }
                            ?.let { movieFromDb -> item.copy().merge(movieFromDb) }
                            ?: item

                        is TvShow -> tvShowsMap[item.id]
                            ?.takeIf { tvShowFromDb -> !item.isSame(tvShowFromDb) }
                            ?.let { tvShowFromDb -> item.copy().merge(tvShowFromDb) }
                            ?: item

                        else -> item
                    }
                }

                val categories = ParentalControlUtils.filterCategories(listOfNotNull(

                    // FEATURED
                    state.categories
                        .find { it.name == Category.FEATURED }
                        ?.let { category ->
                            category.copy(
                                list = category.list.map(::mergeItem)
                            )
                        },

                    // CONTINUE WATCHING
                    Category(
                        name = Category.CONTINUE_WATCHING,
                        list = continueWatching
                            .sortedByDescending {
                                when (it) {
                                    is Episode -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    is Movie -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    else -> 0L
                                }
                            }
                            .distinctBy {
                                when (it) {
                                    is Episode -> it.tvShow?.id
                                    is Movie -> it.id
                                    else -> null
                                }
                            },
                    ),

                    // FAVORITES
                    Category(
                        name = Category.FAVORITE_MOVIES,
                        list = favoritesMovies.sortedByDescending {
                            when (it) {
                                is Movie -> it.favoritedAtMillis ?: 0L
                                else -> 0L
                            }
                        },
                    ),
                    Category(
                        name = Category.FAVORITE_TV_SHOWS,
                        list = favoriteTvShows.sortedByDescending {
                            when (it) {
                                is TvShow -> it.favoritedAtMillis ?: 0L
                                else -> 0L
                            }
                        },
                    ),
                ) + state.categories
                    .filter { it.name != Category.FEATURED }
                    .map { category ->
                        category.copy(
                            list = category.list.map(::mergeItem)
                        )
                    })
                    .filter { it.list.isNotEmpty() }

                State.SuccessLoading(categories)
            }
            else -> state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = State.Loading
    )

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val categories: List<Category>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        val initialProvider = UserPreferences.currentProvider
        if (initialProvider != null) {
            currentProvider = initialProvider
            loadUserDataCache(initialProvider)
        }
        viewModelScope.launch {
            ProviderChangeNotifier.providerChangeFlow.collect {
                getHome()
            }
        }

        viewModelScope.launch {
            UserDataNotifier.updates.collect {
                val provider = UserPreferences.currentProvider ?: return@collect
                loadUserDataCache(provider)
            }
        }
        getHome()
    }

    private suspend fun enrichContinueWatchingEpisodes(episodes: List<Episode>): List<Episode> = coroutineScope {
        val provider = UserPreferences.currentProvider ?: return@coroutineScope episodes

        episodes.map { episode ->
            async {
                val tvShowId = episode.tvShow?.id ?: return@async episode
                val resolvedTvShow = continueWatchingTvShowCache[tvShowId] ?: runCatching {
                    provider.getTvShow(tvShowId)
                }.getOrNull()?.also { fetchedTvShow ->
                    continueWatchingTvShowCache[tvShowId] = fetchedTvShow
                }

                val mergedTvShow = resolvedTvShow?.copy().apply {
                    this?.let { show ->
                        episode.tvShow?.let { existingTvShow -> show.merge(existingTvShow) }
                    }
                } ?: episode.tvShow

                val resolvedSeason = episode.season?.let { season ->
                    mergedTvShow?.seasons?.firstOrNull { it.id == season.id || it.number == season.number }
                        ?: season
                }

                val resolvedEpisode = if (UserPreferences.enableTmdb) {
                    val seasonId = resolvedSeason?.id
                        ?: episode.season?.id
                    seasonId?.let { key ->
                        continueWatchingSeasonEpisodesCache[key] ?: runCatching {
                            provider.getEpisodesBySeason(key)
                        }.getOrDefault(emptyList()).also { fetchedEpisodes ->
                            if (fetchedEpisodes.isNotEmpty()) {
                                continueWatchingSeasonEpisodesCache[key] = fetchedEpisodes
                            }
                        }
                    }?.firstOrNull { seasonEpisode ->
                        seasonEpisode.id == episode.id || seasonEpisode.number == episode.number
                    }
                } else {
                    null
                }

                episode.copy(
                    title = resolvedEpisode?.title ?: episode.title,
                    overview = resolvedEpisode?.overview ?: episode.overview,
                    poster = resolvedEpisode?.poster ?: episode.poster,
                    tvShow = mergedTvShow,
                    season = resolvedSeason,
                ).apply {
                    merge(episode)
                }
            }
        }.awaitAll()
    }

    fun getHome() = viewModelScope.launch(Dispatchers.IO) {
        val provider = UserPreferences.currentProvider ?: run {
            _state.emit(State.FailedLoading(IllegalStateException("No provider selected")))
            return@launch
        }
        currentProvider = provider
        val appContext = StreamFlixApp.instance.applicationContext
        val cachedCategories = HomeCacheStore.read(appContext, provider)
        if (!cachedCategories.isNullOrEmpty()) {
            _state.emit(State.SuccessLoading(cachedCategories))
        } else {
            _state.emit(State.Loading)
        }

        loadUserDataCache(provider)

        try {
            val categories = provider.getHome()
            HomeCacheStore.write(appContext, provider, categories)
            _state.emit(State.SuccessLoading(categories))
        } catch (e: Exception) {
            Log.e("HomeViewModel", "getHome: ", e)
            if (cachedCategories.isNullOrEmpty()) {
                _state.emit(State.FailedLoading(e))
            }
        }
    }

    private fun loadUserDataCache(provider: Provider) {
        val appContext = StreamFlixApp.instance.applicationContext
        val cached = UserDataCache.read(appContext, provider)
        _userDataCache.value = cached

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(appContext)
            val moviesDeferred = async { db.movieDao().getFavorites(UserPreferences.activeProfileId).first() }
            val tvShowsDeferred = async { db.tvShowDao().getFavorites(UserPreferences.activeProfileId).first() }
            val watchingMoviesDeferred = async { db.movieDao().getWatchingMovies(UserPreferences.activeProfileId).first() }
            val watchingEpisodesDeferred = async { db.episodeDao().getWatchingEpisodes(UserPreferences.activeProfileId).first() }

            val movies = moviesDeferred.await()
            val tvShows = tvShowsDeferred.await()
            val watchingMovies = watchingMoviesDeferred.await()
            val watchingEpisodes = watchingEpisodesDeferred.await()

            val newData = UserDataCache.UserData(
                favoritesMovies = preserveCacheOrder(
                    cached = cached?.favoritesMovies ?: emptyList(),
                    incoming = movies.filter { it.isFavorite }.map { it.toCached() },
                    idOf = { it.id },
                ),
                favoritesTvShows = preserveCacheOrder(
                    cached = cached?.favoritesTvShows ?: emptyList(),
                    incoming = tvShows.filter { it.isFavorite }.map { it.toCached() },
                    idOf = { it.id },
                ),
                continueWatchingMovies = preserveCacheOrder(
                    cached = cached?.continueWatchingMovies ?: emptyList(),
                    incoming = (movies + watchingMovies)
                        .filter { it.watchHistory != null }
                        .map { it.toCached() },
                    idOf = { it.id },
                ),
                continueWatchingEpisodes = preserveCacheOrder(
                    cached = cached?.continueWatchingEpisodes ?: emptyList(),
                    incoming = watchingEpisodes
                        .filter { it.watchHistory != null }
                        .map { it.toCached() },
                    idOf = { it.id },
                ),
            )

            UserDataCache.write(appContext, provider, newData)

            if (_userDataCache.value != newData) {
                _userDataCache.value = newData
            }
        }
    }

    fun toggleFavorite(item: Any) = viewModelScope.launch(Dispatchers.IO) {
        val appContext = StreamFlixApp.instance.applicationContext
        val db = AppDatabase.getInstance(appContext)
        when (item) {
            is Movie -> {
                val newState = !item.isFavorite
                db.movieDao().setFavoriteWithLog(item.id, newState, UserPreferences.activeProfileId)
            }
            is TvShow -> {
                val newState = !item.isFavorite
                db.tvShowDao().setFavoriteWithLog(item.id, newState, UserPreferences.activeProfileId)
            }
        }
        val provider = currentProvider ?: return@launch
        loadUserDataCache(provider)
    }

    fun markAsWatched(item: Any) = viewModelScope.launch(Dispatchers.IO) {
        val appContext = StreamFlixApp.instance.applicationContext
        val db = AppDatabase.getInstance(appContext)
        when (item) {
            is Movie -> db.movieDao().setWatched(item.id, true, UserPreferences.activeProfileId)
            is TvShow -> {
                // For TV Show, mark all episodes as watched
                val episodes = db.episodeDao().getEpisodesByTvShowId(item.id, UserPreferences.activeProfileId)
                episodes.forEach { it.isWatched = true }
                db.episodeDao().insertAll(episodes)
                db.tvShowDao().setWatching(item.id, false, UserPreferences.activeProfileId) // Consider finished
            }
            is Episode -> db.episodeDao().setWatched(item.id, true, UserPreferences.activeProfileId)
        }
        val provider = currentProvider ?: return@launch
        loadUserDataCache(provider)
    }

    fun markAsWatchedUpTo(episode: Episode) = viewModelScope.launch(Dispatchers.IO) {
        val appContext = StreamFlixApp.instance.applicationContext
        val db = AppDatabase.getInstance(appContext)
        db.episodeDao().markAsWatchedUpToHere(episode.id)
        val provider = currentProvider ?: return@launch
        loadUserDataCache(provider)
    }

    fun removeFromContinueWatching(item: Any) = viewModelScope.launch(Dispatchers.IO) {
        val appContext = StreamFlixApp.instance.applicationContext
        val db = AppDatabase.getInstance(appContext)
        when (item) {
            is Movie -> db.movieDao().removeFromContinueWatching(item.id, UserPreferences.activeProfileId)
            is TvShow -> db.episodeDao().removeFromContinueWatchingByTvShow(item.id, UserPreferences.activeProfileId)
            is Episode -> db.episodeDao().removeFromContinueWatching(item.id, UserPreferences.activeProfileId)
        }
        val provider = currentProvider ?: return@launch
        loadUserDataCache(provider)
    }
}
