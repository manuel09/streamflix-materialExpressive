package com.streamflixreborn.streamflix.torrent

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer

/**
 * Contacts HTTP trackers to get peer lists.
 * Uses OkHttp (already a project dependency).
 */
class TorrentFetcher(private val okHttpClient: OkHttpClient) {

    private val TAG = "TorrentFetcher"

    data class Peer(val ip: String, val port: Int)

    data class AnnounceResult(
        val peers: List<Peer>,
        val interval: Int  // seconds to next announce
    )

    /**
     * Announce to an HTTP tracker using the compact peer format.
     */
    suspend fun announce(
        trackerUrl: String,
        infoHashBytes: ByteArray,
        peerId: ByteArray,
        port: Int = 6881,
        downloaded: Long = 0,
        uploaded: Long = 0,
        left: Long = Long.MAX_VALUE,
        event: String = "started"
    ): AnnounceResult? {
        return try {
            val infoHashEncoded = urlEncodeBytes(infoHashBytes)
            val peerIdEncoded = urlEncodeBytes(peerId)

            val url = buildString {
                append(trackerUrl)
                if (trackerUrl.contains('?')) append('&') else append('?')
                append("info_hash=").append(infoHashEncoded)
                append("&peer_id=").append(peerIdEncoded)
                append("&port=").append(port)
                append("&uploaded=").append(uploaded)
                append("&downloaded=").append(downloaded)
                append("&left=").append(left)
                append("&compact=1")
                append("&event=").append(event)
                append("&numwant=50")
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StreamflixTorrent/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Tracker returned ${response.code}: $trackerUrl")
                return null
            }

            val body = response.body?.bytes() ?: return null
            parseTrackerResponse(body)
        } catch (e: Exception) {
            Log.w(TAG, "Announce failed for $trackerUrl: ${e.message}")
            null
        }
    }

    /**
     * Parses bencoded tracker response (compact peer format).
     * Minimal bencode parser for the specific tracker response structure.
     */
    private fun parseTrackerResponse(data: ByteArray): AnnounceResult? {
        return try {
            val decoded = BencodeDecoder.decode(data) as? Map<*, *> ?: return null
            val failureReason = decoded["failure reason"] as? ByteArray
            if (failureReason != null) {
                Log.w(TAG, "Tracker failure: ${String(failureReason)}")
                return null
            }

            val interval = (decoded["interval"] as? Long)?.toInt() ?: 1800
            val peers = when (val rawPeers = decoded["peers"]) {
                is ByteArray -> parseCompactPeers(rawPeers)   // compact format
                is List<*>   -> parseDictionaryPeers(rawPeers) // dict format
                else -> emptyList()
            }

            Log.d(TAG, "Got ${peers.size} peers, interval=${interval}s")
            AnnounceResult(peers, interval)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing tracker response: ${e.message}")
            null
        }
    }

    private fun parseCompactPeers(data: ByteArray): List<Peer> {
        val peers = mutableListOf<Peer>()
        val buf = ByteBuffer.wrap(data)
        while (buf.remaining() >= 6) {
            val ip = buildString {
                repeat(4) {
                    if (isNotEmpty()) append('.')
                    append(buf.get().toInt() and 0xFF)
                }
            }
            val port = buf.short.toInt() and 0xFFFF
            if (port > 0) peers.add(Peer(ip, port))
        }
        return peers
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDictionaryPeers(data: List<*>): List<Peer> {
        return data.mapNotNull { peer ->
            val p = peer as? Map<*, *> ?: return@mapNotNull null
            val ip = (p["ip"] as? ByteArray)?.let { String(it) } ?: return@mapNotNull null
            val port = (p["port"] as? Long)?.toInt() ?: return@mapNotNull null
            Peer(ip, port)
        }
    }

    private fun urlEncodeBytes(bytes: ByteArray): String {
        return buildString {
            for (b in bytes) {
                val i = b.toInt() and 0xFF
                if (i in UNRESERVED_CHARS) {
                    append(i.toChar())
                } else {
                    append('%')
                    append(HEX_CHARS[i shr 4])
                    append(HEX_CHARS[i and 0xF])
                }
            }
        }
    }

    companion object {
        private val HEX_CHARS = "0123456789ABCDEF"
        private val UNRESERVED_CHARS = buildSet {
            addAll('A'.code..'Z'.code)
            addAll('a'.code..'z'.code)
            addAll('0'.code..'9'.code)
            add('-'.code); add('_'.code); add('.'.code); add('~'.code)
        }
    }
}
