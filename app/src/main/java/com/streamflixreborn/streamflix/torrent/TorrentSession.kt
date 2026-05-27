package com.streamflixreborn.streamflix.torrent

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import kotlin.math.min

/**
 * Coordinates all torrent streaming:
 * 1. Parses magnet link
 * 2. Contacts HTTP trackers for peers
 * 3. Downloads pieces via PieceClient (multiple peers in parallel)
 * 4. Serves the growing file via NanoHTTPD for ExoPlayer
 */
class TorrentSession(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val TAG = "TorrentSession"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class Progress(
        val state: State,
        val peers: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1,
        val speedBytesPerSec: Long = 0,
        val streamUrl: String? = null
    ) {
        val progressPercent: Float
            get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes * 100f else 0f
    }

    enum class State { IDLE, CONNECTING, DOWNLOADING, READY, ERROR }

    private val _progress = MutableStateFlow(Progress(State.IDLE))
    val progress: StateFlow<Progress> = _progress

    private var httpServer: TorrentHttpServer? = null
    private var cacheFile: RandomAccessFile? = null
    private var cacheFilePath: File? = null
    private var activePeers = 0
    private var peerId: ByteArray = generatePeerId()

    // Max concurrent peer connections
    private val MAX_PEERS = 5
    // How many pieces ahead of playback position to prefetch
    private val PREFETCH_PIECES = 8

    suspend fun start(magnetUri: String): String? {
        val magnetInfo = MagnetParser.parse(magnetUri)
        if (magnetInfo == null) {
            _progress.value = Progress(State.ERROR)
            return null
        }

        _progress.value = Progress(State.CONNECTING)

        // Stop previous session if any
        stop()

        val fetcher = TorrentFetcher(okHttpClient)

        // Get peers from all trackers
        val allPeers = mutableListOf<TorrentFetcher.Peer>()
        for (tracker in magnetInfo.trackers.take(3)) {
            val result = fetcher.announce(
                trackerUrl = tracker,
                infoHashBytes = magnetInfo.infoHashBytes,
                peerId = peerId
            )
            result?.peers?.let { allPeers.addAll(it) }
            if (allPeers.size >= 20) break
        }

        if (allPeers.isEmpty() && magnetInfo.webSeeds.isEmpty()) {
            Log.w(TAG, "No peers and no webseeds found")
            _progress.value = Progress(State.ERROR)
            return null
        }

        allPeers.shuffle()
        Log.d(TAG, "Found ${allPeers.size} peers")

        // For now use a reasonable estimated size (will be updated from .torrent metadata)
        // If we have web seeds, we can get the actual size via HTTP HEAD
        val estimatedSize = estimateSizeFromWebSeed(magnetInfo.webSeeds.firstOrNull())
            ?: (2L * 1024 * 1024 * 1024) // default 2GB

        // Set up cache file
        val cacheDir = File(context.cacheDir, "torrent_cache")
        cacheDir.mkdirs()
        val file = File(cacheDir, "${magnetInfo.infoHash}.bin")
        cacheFilePath = file
        val raf = RandomAccessFile(file, "rw")
        cacheFile = raf

        // Pre-allocate space (sparse on most filesystems)
        raf.setLength(estimatedSize)

        val pieceLength = 512 * 1024 // 512KB pieces (standard)
        val client = PieceClient(
            infoHash = magnetInfo.infoHashBytes,
            peerId = peerId,
            pieceLength = pieceLength,
            totalLength = estimatedSize,
            outputFile = raf
        )

        // Start HTTP server
        val server = TorrentHttpServer(file, estimatedSize, client)
        httpServer = server
        server.start()
        val streamUrl = "http://127.0.0.1:${server.listeningPort}/stream"

        _progress.value = Progress(
            state = State.DOWNLOADING,
            totalBytes = estimatedSize,
            streamUrl = streamUrl
        )

        // Start downloading pieces from peers (parallel)
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()

        scope.launch {
            val jobs = allPeers.take(MAX_PEERS).map { peer ->
                launch {
                    activePeers++
                    try {
                        client.connectAndDownload(peer) { _ ->
                            val downloaded = (client.progress * estimatedSize).toLong()
                            val now = System.currentTimeMillis()
                            val elapsed = (now - lastTime) / 1000f
                            val speed = if (elapsed > 0) ((downloaded - lastBytes) / elapsed).toLong() else 0L
                            lastBytes = downloaded
                            lastTime = now

                            _progress.value = Progress(
                                state = State.DOWNLOADING,
                                peers = activePeers,
                                downloadedBytes = downloaded,
                                totalBytes = estimatedSize,
                                speedBytesPerSec = speed,
                                streamUrl = streamUrl
                            )
                        }
                    } finally {
                        activePeers--
                    }
                }
            }

            // Also handle web seeds via HTTP range requests (fallback/supplement)
            if (magnetInfo.webSeeds.isNotEmpty()) {
                launch {
                    downloadFromWebSeed(magnetInfo.webSeeds.first(), raf, estimatedSize)
                }
            }
        }

        return streamUrl
    }

    private suspend fun estimateSizeFromWebSeed(url: String?): Long? {
        url ?: return null
        return try {
            val request = okhttp3.Request.Builder().url(url).head().build()
            val response = okHttpClient.newCall(request).execute()
            response.header("Content-Length")?.toLong()
        } catch (e: Exception) { null }
    }

    private suspend fun downloadFromWebSeed(
        url: String,
        output: RandomAccessFile,
        totalSize: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val pieceLength = 512 * 1024
            var offset = 0L
            while (offset < totalSize && isActive) {
                val end = min(offset + pieceLength - 1, totalSize - 1)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$offset-$end")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: break
                    synchronized(output) {
                        output.seek(offset)
                        output.write(bytes)
                    }
                }
                offset += pieceLength
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebSeed download failed: ${e.message}")
        }
    }

    fun stop() {
        scope.coroutineContext.cancelChildren()
        httpServer?.stop()
        httpServer = null
        try { cacheFile?.close() } catch (ignored: Exception) {}
        cacheFile = null
        activePeers = 0
        _progress.value = Progress(State.IDLE)
    }

    private fun generatePeerId(): ByteArray {
        val id = ByteArray(20)
        "-SF0001-".toByteArray().copyInto(id) // client prefix: StreamFlix
        SecureRandom().nextBytes(id.copyOfRange(8, 20))
        return id
    }
}
