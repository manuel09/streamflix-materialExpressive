import re

file_path = 'app/src/main/java/com/streamflixreborn/streamflix/fragments/home/HomeViewModel.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Replace DAO calls. Since some have arguments already and some don't, 
# this is tricky. The grep shows that the ones with arguments already are handled.
# I will focus on the ones without arguments as seen in the grep_search.
# Example: database.movieDao().getWatchingMovies()
# I need to find patterns like database.movieDao().<method>() and insert UserPreferences.activeProfileId

content = content.replace("database.movieDao().getWatchingMovies()", "database.movieDao().getWatchingMovies(UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getAll()", "database.tvShowDao().getAll(UserPreferences.activeProfileId)")
content = content.replace("database.movieDao().getFavorites()", "database.movieDao().getFavorites(UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getFavorites()", "database.tvShowDao().getFavorites(UserPreferences.activeProfileId)")
content = content.replace("database.movieDao().getByIds(movies.map { it.id })", "database.movieDao().getByIds(movies.map { it.id }, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getByIds(tvShows.map { it.id })", "database.tvShowDao().getByIds(tvShows.map { it.id }, UserPreferences.activeProfileId)")
content = content.replace("db.movieDao().getFavorites()", "db.movieDao().getFavorites(UserPreferences.activeProfileId)")
content = content.replace("db.tvShowDao().getFavorites()", "db.tvShowDao().getFavorites(UserPreferences.activeProfileId)")
content = content.replace("db.movieDao().getWatchingMovies()", "db.movieDao().getWatchingMovies(UserPreferences.activeProfileId)")
content = content.replace("db.movieDao().setFavoriteWithLog(item.id, newState)", "db.movieDao().setFavoriteWithLog(item.id, newState, UserPreferences.activeProfileId)")
content = content.replace("db.tvShowDao().setFavoriteWithLog(item.id, newState)", "db.tvShowDao().setFavoriteWithLog(item.id, newState, UserPreferences.activeProfileId)")

with open(file_path, 'w') as f:
    f.write(content)
