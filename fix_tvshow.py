import re

file_path = 'app/src/main/java/com/streamflixreborn/streamflix/fragments/tv_show/TvShowViewModel.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Adjust imports: need to ensure UserPreferences is used, but it's already imported.
# Applying replacements.
# Note: In TvShowViewModel, the id is passed in constructor. The DAO methods need activeProfileId.

content = content.replace("database.episodeDao().getByTvShowIdAsFlow(id).first()", "database.episodeDao().getByTvShowIdAsFlow(id, UserPreferences.activeProfileId).first()")
content = content.replace("database.tvShowDao().getByIdAsFlow(id)", "database.tvShowDao().getByIdAsFlow(id, UserPreferences.activeProfileId)")
content = content.replace("database.episodeDao().getByTvShowIdAsFlow(id)", "database.episodeDao().getByTvShowIdAsFlow(id, UserPreferences.activeProfileId)")
content = content.replace("database.movieDao().getByIds(movies.map { it.id })", "database.movieDao().getByIds(movies.map { it.id }, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getByIds(tvShows.map { it.id })", "database.tvShowDao().getByIds(tvShows.map { it.id }, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getById(tvShow.id)", "database.tvShowDao().getById(tvShow.id, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().insert(tvShow)", "database.tvShowDao().insert(tvShow, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().setFavoriteWithLog(tvShow.id, !tvShow.isFavorite)", "database.tvShowDao().setFavoriteWithLog(tvShow.id, !tvShow.isFavorite, UserPreferences.activeProfileId)")

# Some replacements in episodeDao() calls might need adjustment.
# The user asked for "passed activeProfileId to all calls to DAO (movieDao() and tvShowDao())"
# episodeDao() isn't listed, but it seems to handle profile-specific data too.
# I'll stick to the user request.

with open(file_path, 'w') as f:
    f.write(content)
