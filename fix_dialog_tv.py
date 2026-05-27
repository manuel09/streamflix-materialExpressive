file_path = 'app/src/main/java/com/streamflixreborn/streamflix/ui/ShowOptionsTvDialog.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Replace DAO calls
content = content.replace("database.movieDao().getById(movie.id)", "database.movieDao().getById(movie.id, UserPreferences.activeProfileId)")
content = content.replace("database.tvShowDao().getById(tvShow.id)", "database.tvShowDao().getById(tvShow.id, UserPreferences.activeProfileId)")

with open(file_path, 'w') as f:
    f.write(content)
