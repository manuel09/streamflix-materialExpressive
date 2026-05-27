package com.streamflixreborn.streamflix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun MediaOptionsBottomSheet(
    item: Any,
    onDismiss: () -> Unit,
    onAction: (Action) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (item) {
                is TvShow -> TvShowOptions(item, onAction)
                is Movie -> MovieOptions(item, onAction)
                is Episode -> EpisodeOptions(item, onAction)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TvShowOptions(tvShow: TvShow, onAction: (Action) -> Unit) {
    val lastEpisode = tvShow.seasons.flatMap { it.episodes }
        .filter { it.watchHistory != null || it.isWatched }
        .maxByOrNull { it.watchHistory?.lastEngagementTimeUtcMillis ?: it.watchedDate?.timeInMillis ?: 0L }
        ?: tvShow.seasons.firstOrNull()?.episodes?.firstOrNull()

    GlideImage(
        model = lastEpisode?.poster ?: tvShow.poster,
        contentDescription = null,
        modifier = Modifier
            .size(width = 240.dp, height = 135.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = tvShow.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    lastEpisode?.let {
        Text(
            text = "S${it.season?.number ?: "?"} E${it.number} - ${it.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    OptionButton("Vai alla serie tv") { onAction(Action.GoToDetail(tvShow)) }
    OptionButton("Segna come visto") { onAction(Action.MarkWatched(tvShow)) }
    lastEpisode?.let {
        OptionButton("Segna come visto fino qui") { onAction(Action.MarkWatchedUpTo(it)) }
    }
    OptionButton("Rimuovi da continua a guardare", isError = true) { onAction(Action.RemoveFromContinueWatching(tvShow)) }
    OptionButton("Annulla") { onAction(Action.Cancel) }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MovieOptions(movie: Movie, onAction: (Action) -> Unit) {
    GlideImage(
        model = movie.poster,
        contentDescription = null,
        modifier = Modifier
            .size(width = 120.dp, height = 180.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = movie.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    Text(
        text = movie.released?.format("yyyy") ?: "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    val favoriteLabel = if (movie.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti"
    OptionButton(favoriteLabel) { onAction(Action.ToggleFavorite(movie)) }
    OptionButton("Segna come visto") { onAction(Action.MarkWatched(movie)) }
    OptionButton("Segna come visto fino qui") { onAction(Action.MarkWatchedUpToMovie(movie)) }
    OptionButton("Rimuovi da continua a guardare", isError = true) { onAction(Action.RemoveFromContinueWatching(movie)) }
    OptionButton("Annulla") { onAction(Action.Cancel) }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun EpisodeOptions(episode: Episode, onAction: (Action) -> Unit) {
    GlideImage(
        model = episode.poster,
        contentDescription = null,
        modifier = Modifier
            .size(width = 240.dp, height = 135.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = episode.tvShow?.title ?: "",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    
    Text(
        text = "S${episode.season?.number ?: "?"} E${episode.number} - ${episode.title}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    episode.tvShow?.let {
        OptionButton("Vai alla serie tv") { onAction(Action.GoToDetail(it)) }
    }
    OptionButton("Segna come visto") { onAction(Action.MarkWatchedEpisode(episode)) }
    OptionButton("Segna come visto fino qui") { onAction(Action.MarkWatchedUpTo(episode)) }
    OptionButton("Rimuovi da continua a guardare", isError = true) { onAction(Action.RemoveFromContinueWatchingEpisode(episode)) }
    OptionButton("Annulla") { onAction(Action.Cancel) }
}

@Composable
private fun OptionButton(
    text: String,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

sealed class Action {
    data class GoToDetail(val item: Any) : Action()
    data class MarkWatched(val item: Any) : Action()
    data class MarkWatchedEpisode(val episode: Episode) : Action()
    data class MarkWatchedUpTo(val episode: Episode) : Action()
    data class MarkWatchedUpToMovie(val movie: Movie) : Action()
    data class RemoveFromContinueWatching(val item: Any) : Action()
    data class RemoveFromContinueWatchingEpisode(val episode: Episode) : Action()
    data class ToggleFavorite(val item: Any) : Action()
    object Cancel : Action()
}
