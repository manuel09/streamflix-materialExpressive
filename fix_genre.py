import re

file_path = 'app/src/main/java/com/streamflixreborn/streamflix/fragments/genre/GenreViewModel.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Replace DAO calls
content = content.replace("database.movieDao().getByIds(movies.map { it.id })", "database.movieDao().getByIds(movies.map { it.id }, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getByIds(tvShows.map { it.id })", "database.tvShowDao().getByIds(tvShows.map { it.id }, UserPreferences.activeProfileId)")

with open(file_path, 'w') as f:
    f.write(content)
