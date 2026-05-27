package com.streamflixreborn.streamflix.fragments.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.ui.screens.HomeComposeScreen
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.format

class HomeMobileFragment : Fragment() {

    private val viewModel: HomeViewModel by lazy {
        val providerKey = UserPreferences.currentProvider?.name ?: "default"
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(AppDatabase.getInstance(requireContext())) as T
            }
        }
        ViewModelProvider(this, factory).get(providerKey, HomeViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeComposeScreen(
                    viewModel = viewModel,
                    onItemClick = { item ->
                        when (item) {
                            is Movie -> {
                                if (item.watchHistory != null) {
                                    val action = HomeMobileFragmentDirections.actionGlobalPlayer(
                                        item.id,
                                        item.title,
                                        item.released?.format("yyyy") ?: "",
                                        Video.Type.Movie(
                                            item.id,
                                            item.title,
                                            item.released?.format("yyyy-MM-dd") ?: "",
                                            item.poster ?: "",
                                            item.imdbId
                                        )
                                    )
                                    findNavController().navigate(action)
                                } else {
                                    val action = HomeMobileFragmentDirections.actionHomeToMovie(item.id)
                                    findNavController().navigate(action)
                                }
                            }
                            is TvShow -> {
                                val action = HomeMobileFragmentDirections.actionHomeToTvShow(
                                    item.id,
                                    item.poster,
                                    item.banner
                                )
                                findNavController().navigate(action)
                            }
                            is Episode -> {
                                val tvShow = item.tvShow ?: return@HomeComposeScreen
                                if (item.watchHistory != null) {
                                    val action = HomeMobileFragmentDirections.actionGlobalPlayer(
                                        item.id,
                                        tvShow.title,
                                        "S${item.season?.number} E${item.number} - ${item.title}",
                                        Video.Type.Episode(
                                            item.id,
                                            item.number,
                                            item.title,
                                            item.poster,
                                            item.overview,
                                            Video.Type.Episode.TvShow(
                                                tvShow.id,
                                                tvShow.title,
                                                tvShow.poster,
                                                tvShow.banner,
                                                tvShow.released?.format("yyyy-MM-dd"),
                                                tvShow.imdbId
                                            ),
                                            Video.Type.Episode.Season(
                                                item.season?.number ?: 0,
                                                item.season?.title
                                            )
                                        )
                                    )
                                    findNavController().navigate(action)
                                } else {
                                    val action = HomeMobileFragmentDirections.actionHomeToTvShow(
                                        tvShow.id,
                                        tvShow.poster,
                                        tvShow.banner
                                    )
                                    findNavController().navigate(action)
                                }
                            }
                        }
                    },
                    onProviderClick = {
                        findNavController().navigate(HomeMobileFragmentDirections.actionGlobalProviders())
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getHome()
    }
}
