package com.streamflixreborn.streamflix.torrent

import android.util.Log
import java.net.URLDecoder

/**
 * Parses a magnet URI into its components.
 * Handles both simple and complex magnet links.
 */
object MagnetParser {

    private const val TAG = "MagnetParser"

    data class MagnetInfo(
        val infoHash: String,          // 40-char hex SHA1 or 32-char base32
        val infoHashBytes: ByteArray,  // raw 20 bytes
        val displayName: String?,
        val trackers: List<String>,    // HTTP trackers only (no UDP)
        val webSeeds: List<String>     // HTTP direct seeds (tr.getByName "ws")
    )

    fun parse(magnetUri: String): MagnetInfo? {
        if (!magnetUri.startsWith("magnet:?")) {
            Log.e(TAG, "Not a magnet URI: $magnetUri")
            return null
        }

        val params = magnetUri
            .removePrefix("magnet:?")
            .split("&")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .groupBy({ it[0] }, { URLDecoder.decode(it[1], "UTF-8") })

        // Extract info hash from xt (exact topic)
        val xt = params["xt"]?.firstOrNull() ?: return null
        val infoHash = when {
            xt.startsWith("urn:btih:") -> xt.removePrefix("urn:btih:").lowercase()
            else -> {
                Log.e(TAG, "Unknown xt format: $xt")
                return null
            }
        }

        val infoHashBytes = if (infoHash.length == 40) {
            hexToBytes(infoHash)
        } else if (infoHash.length == 32) {
            base32ToBytes(infoHash.uppercase())
        } else {
            Log.e(TAG, "Invalid info hash length: ${infoHash.length}")
            return null
        }

        val displayName = params["dn"]?.firstOrNull()

        // Only keep HTTP trackers (skip UDP)
        val trackers = (params["tr"] ?: emptyList())
            .filter { it.startsWith("http://") || it.startsWith("https://") }

        val webSeeds = (params["ws"] ?: emptyList())
            .filter { it.startsWith("http://") || it.startsWith("https://") }

        Log.d(TAG, "Parsed magnet: hash=$infoHash, name=$displayName, " +
                "httpTrackers=${trackers.size}, webSeeds=${webSeeds.size}")

        return MagnetInfo(
            infoHash = infoHash,
            infoHashBytes = infoHashBytes,
            displayName = displayName,
            trackers = trackers,
            webSeeds = webSeeds
        )
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun base32ToBytes(input: String): ByteArray {
        val result = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (char in input) {
            val idx = BASE32_CHARS.indexOf(char)
            if (idx < 0) continue
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result.add((buffer shr bitsLeft).toByte())
                buffer = buffer and ((1 shl bitsLeft) - 1)
            }
        }
        return result.toByteArray()
    }
}
