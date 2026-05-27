package com.streamflixreborn.streamflix.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.streamflixreborn.streamflix.fragments.tv_show.TvShowViewModel
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.ui.components.Action
import com.streamflixreborn.streamflix.ui.components.InfoChip
import com.streamflixreborn.streamflix.ui.components.MediaOptionsBottomSheet
import com.streamflixreborn.streamflix.ui.theme.StreamflixTheme
import com.streamflixreborn.streamflix.utils.format

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TvShowComposeScreen(
    viewModel: TvShowViewModel,
    onEpisodeClick: (TvShow, Episode) -> Unit
) {
    val state by viewModel.state.collectAsState(initial = TvShowViewModel.State.Loading)
    val scrollState = rememberScrollState()
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }

    StreamflixTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val currentState = state) {
                is TvShowViewModel.State.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TvShowViewModel.State.SuccessLoading -> {
                    val tvShow = currentState.tvShow
                    var selectedSeason by remember { 
                        val lastWatchedSeason = tvShow.seasons.find { season ->
                            season.episodes.any { it.watchHistory != null || it.isWatched }
                        } ?: tvShow.seasons.firstOrNull { it.number != 0 } ?: tvShow.seasons.firstOrNull()
                        mutableStateOf(lastWatchedSeason) 
                    }

                    LaunchedEffect(selectedSeason) {
                        selectedSeason?.let { season ->
                            if (season.episodes.isEmpty()) {
                                viewModel.getSeason(tvShow, season)
                            }
                        }
                    }

                    // Immersive Backdrop
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        GlideImage(
                            model = tvShow.banner ?: tvShow.poster,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                        
                        // Favorite Toggle
                        IconButton(
                            onClick = { viewModel.toggleFavorite(tvShow) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 48.dp, end = 16.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (tvShow.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (tvShow.isFavorite) Color.Red else Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Spacer(modifier = Modifier.height(280.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = tvShow.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                tvShow.released?.let { released: java.util.Calendar ->
                                    InfoChip(text = released.format("yyyy") ?: "")
                                }
                                tvShow.rating?.let { rating: Double ->
                                    InfoChip(text = "Rating: ${String.format(java.util.Locale.US, "%.1f", rating)}")
                                }
                                InfoChip(text = "Serie TV", containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Trama",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tvShow.overview ?: "Nessuna descrizione disponibile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // Seasons Selection
                            if (tvShow.seasons.isNotEmpty()) {
                                Text(
                                    text = "Stagioni",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(tvShow.seasons) { season ->
                                        val isSelected = selectedSeason?.id == season.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedSeason = season },
                                            label = { Text("Stagione ${season.number}") }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Episodes List
                                if (selectedSeason?.episodes?.isEmpty() == true) {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    }
                                } else {
                                    selectedSeason?.episodes?.forEach { episode ->
                                        EpisodeItem(
                                            episode = episode,
                                            onClick = { onEpisodeClick(tvShow, episode) },
                                            onLongClick = { selectedEpisode = it }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                is TvShowViewModel.State.FailedLoading -> {
                    Text(
                        "Errore nel caricamento",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        selectedEpisode?.let { episode ->
            MediaOptionsBottomSheet(
                item = episode,
                onDismiss = { selectedEpisode = null },
                onAction = { action ->
                    selectedEpisode = null
                    when (action) {
                        is Action.MarkWatchedEpisode -> viewModel.markAsWatched(action.episode)
                        is Action.MarkWatchedUpTo -> viewModel.markAsWatchedUpTo(action.episode)
                        is Action.RemoveFromContinueWatchingEpisode -> viewModel.removeFromContinueWatching(action.episode)
                        Action.Cancel -> {}
                        else -> {}
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun EpisodeItem(
    episode: Episode,
    onClick: () -> Unit,
    onLongClick: (Episode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick(episode) }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(width = 120.dp, height = 70.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                GlideImage(
                    model = episode.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "E${episode.number} - ${episode.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = episode.overview ?: "Guarda questo episodio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

