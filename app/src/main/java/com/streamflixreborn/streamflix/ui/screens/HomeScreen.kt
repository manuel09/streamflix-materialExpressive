package com.streamflixreborn.streamflix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.streamflixreborn.streamflix.fragments.home.HomeViewModel
import com.streamflixreborn.streamflix.ui.components.CategorySection
import com.streamflixreborn.streamflix.ui.theme.StreamflixTheme
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.ui.ShowOptionsMobileDialog
import com.streamflixreborn.streamflix.adapters.AppAdapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeComposeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Any) -> Unit,
    onProviderClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var selectedMediaItem by remember { mutableStateOf<Any?>(null) }

    StreamflixTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (val currentState = state) {
                    is HomeViewModel.State.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is HomeViewModel.State.SuccessLoading -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = 16.dp
                            )
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(height = 32.dp, width = 64.dp)
                                            .clickable { onProviderClick() }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = UserPreferences.currentProvider?.name?.uppercase() ?: "TMDB",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Text(
                                        text = "Streamflix",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            items(currentState.categories) { category ->
                                CategorySection(
                                    category = category,
                                    onItemClick = onItemClick,
                                    onItemLongClick = { selectedMediaItem = it }
                                )
                            }
                        }
                    }
                    is HomeViewModel.State.FailedLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Oops! Qualcosa è andato storto")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.getHome() }) {
                                Text("Riprova")
                            }
                        }
                    }
                }
            }
        }

        selectedMediaItem?.let { item ->
            val context = LocalContext.current
            ShowOptionsMobileDialog(context, item as AppAdapter.Item).show()
            selectedMediaItem = null
        }
    }
}
