package com.streamflixreborn.streamflix.fragments.tv_show

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.ui.screens.TvShowComposeScreen
import com.streamflixreborn.streamflix.utils.format
import com.streamflixreborn.streamflix.utils.viewModelsFactory

class TvShowMobileFragment : Fragment() {

    private val args by navArgs<TvShowMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        TvShowViewModel(
            id = args.id,
            database = database,
            fallbackPoster = args.poster,
            fallbackBanner = args.banner,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TvShowComposeScreen(
                    viewModel = viewModel,
                    onEpisodeClick = { tvShow, episode ->
                        val action = TvShowMobileFragmentDirections.actionTvShowToPlayer(
                            episode.id,
                            tvShow.title,
                            "S${episode.season?.number} E${episode.number} - ${episode.title}",
                            Video.Type.Episode(
                                episode.id,
                                episode.number,
                                episode.title,
                                episode.poster,
                                episode.overview,
                                Video.Type.Episode.TvShow(
                                    tvShow.id,
                                    tvShow.title,
                                    tvShow.poster,
                                    tvShow.banner,
                                    tvShow.released?.format("yyyy-MM-dd"),
                                    tvShow.imdbId
                                ),
                                Video.Type.Episode.Season(
                                    episode.season?.number ?: 0,
                                    episode.season?.title
                                )
                            )
                        )
                        findNavController().navigate(action)
                    }
                )
            }
        }
    }
}
