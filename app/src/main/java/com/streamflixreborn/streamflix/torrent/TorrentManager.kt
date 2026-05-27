package com.streamflixreborn.streamflix.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton entry point for the torrent engine.
 * Usage: TorrentManager.start(context, magnetLink) → URL locale per ExoPlayer
 */
object TorrentManager {

    private const val TAG = "TorrentManager"
    private var session: TorrentSession? = null

    /** Expose progress for the player UI */
    val progress: StateFlow<TorrentSession.Progress>?
        get() = session?.progress

    private fun buildOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Starts a torrent session for the given magnet link.
     * @return local HTTP URL to pass to ExoPlayer, or null on failure.
     */
    suspend fun start(context: Context, magnetUri: String): String? {
        stop() // Clean up previous session
        val okHttp = buildOkHttpClient()
        val s = TorrentSession(context, okHttp)
        session = s
        val url = s.start(magnetUri)
        if (url == null) {
            Log.e(TAG, "Failed to start torrent session for: $magnetUri")
            session = null
        } else {
            Log.i(TAG, "Torrent streaming at: $url")
        }
        return url
    }

    /** Stops the active torrent session and cleans up. */
    fun stop() {
        session?.stop()
        session = null
    }

    val isActive: Boolean get() = session != null
}
