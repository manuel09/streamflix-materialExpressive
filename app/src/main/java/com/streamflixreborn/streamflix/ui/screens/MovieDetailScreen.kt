package com.streamflixreborn.streamflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.streamflixreborn.streamflix.fragments.movie.MovieViewModel
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.ui.components.InfoChip
import com.streamflixreborn.streamflix.ui.theme.StreamflixTheme
import com.streamflixreborn.streamflix.utils.format

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: MovieViewModel,
    onPlayClick: (Movie) -> Unit
) {
    val state by viewModel.state.collectAsState(initial = MovieViewModel.State.Loading)
    val scrollState = rememberScrollState()

    StreamflixTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val currentState = state) {
                is MovieViewModel.State.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is MovieViewModel.State.SuccessLoading -> {
                    val movie = currentState.movie
                    
                    // Immersive Backdrop
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        GlideImage(
                            model = movie.banner ?: movie.poster,
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
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                        
                        // Favorite Toggle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 48.dp, end = 16.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                        ) {
                            IconButton(
                                onClick = { 
                                    android.util.Log.d("UI_DEBUG", "Click sul tasto preferiti rilevato")
                                    viewModel.toggleFavorite(movie) 
                                }
                            ) {
                                Icon(
                                    imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (movie.isFavorite) Color.Red else Color.White
                                )
                            }
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
                                text = movie.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                movie.released?.let { released: java.util.Calendar ->
                                    InfoChip(text = released.format("yyyy") ?: "")
                                }
                                movie.rating?.let { rating: Double ->
                                    InfoChip(text = "Rating: ${String.format(java.util.Locale.US, "%.1f", rating)}")
                                }
                                movie.quality?.let { quality: String ->
                                    InfoChip(text = quality, containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Button(
                                onClick = { onPlayClick(movie) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guarda Ora", fontWeight = FontWeight.Bold)
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
                                text = movie.overview ?: "Nessuna descrizione disponibile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                lineHeight = 22.sp
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                is MovieViewModel.State.FailedLoading -> {
                    Text(
                        "Errore nel caricamento", 
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

