package com.streamflixreborn.streamflix.fragments.movie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.ui.screens.MovieDetailScreen
import com.streamflixreborn.streamflix.utils.format
import com.streamflixreborn.streamflix.utils.viewModelsFactory

class MovieMobileFragment : Fragment() {

    private val args by navArgs<MovieMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MovieDetailScreen(
                    viewModel = viewModel,
                    onPlayClick = { movie ->
                        val action = MovieMobileFragmentDirections.actionMovieToPlayer(
                            movie.id,
                            movie.title,
                            movie.released?.format("yyyy") ?: "",
                            com.streamflixreborn.streamflix.models.Video.Type.Movie(
                                movie.id,
                                movie.title,
                                movie.released?.format("yyyy-MM-dd") ?: "",
                                movie.poster ?: "",
                                movie.imdbId
                            )
                        )
                        findNavController().navigate(action)
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getMovie(args.id)
    }
}
