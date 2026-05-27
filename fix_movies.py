import re

file_path = 'app/src/main/java/com/streamflixreborn/streamflix/fragments/movies/MoviesViewModel.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Replace DAO call in state flow
content = content.replace("database.movieDao().getByIds(state.movies.map { it.id })", "database.movieDao().getByIds(state.movies.map { it.id }, UserPreferences.activeProfileId)")

with open(file_path, 'w') as f:
    f.write(content)
