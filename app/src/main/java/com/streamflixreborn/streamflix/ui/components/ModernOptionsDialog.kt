package com.streamflixreborn.streamflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow

@Composable
fun ModernOptionsDialog(
    show: AppAdapter.Item,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMarkAsWatched: () -> Unit
) {
    val isFavorite = when (show) {
        is Movie -> show.isFavorite
        is TvShow -> show.isFavorite
        else -> false
    }

    val isWatched = when (show) {
        is Movie -> show.isWatched
        is Episode -> show.isWatched
        else -> false
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (show) {
                    is Movie -> show.title
                    is TvShow -> show.title
                    is Episode -> show.tvShow?.title ?: "Episodio"
                    else -> "Opzioni"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pulsante Preferiti
            OptionItem(
                text = if (isFavorite) "Rimuovi dai Preferiti" else "Aggiungi ai Preferiti",
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = { onFavoriteToggle(); onDismiss() }
            )

            // Pulsante Visto
            OptionItem(
                text = if (isWatched) "Segna come non visto" else "Segna come visto",
                icon = Icons.Filled.Visibility,
                onClick = { onMarkAsWatched(); onDismiss() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Annulla")
            }
        }
    }
}

@Composable
fun OptionItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
    }
}
