package com.streamflixreborn.streamflix.torrent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Connects to a BitTorrent peer via TCP and downloads pieces sequentially.
 * Implements a minimal subset of the BitTorrent Wire Protocol.
 */
class PieceClient(
    private val infoHash: ByteArray,
    private val peerId: ByteArray,
    private val pieceLength: Int,
    private val totalLength: Long,
    private val outputFile: RandomAccessFile
) {

    private val TAG = "PieceClient"
    private val BLOCK_SIZE = 16 * 1024 // 16KB per request

    private val totalPieces: Int
        get() = ((totalLength + pieceLength - 1) / pieceLength).toInt()

    private val downloadedPieces = BooleanArray(maxOf(1, totalPieces))
    private var handshakeDone = false

    val progress: Float
        get() = if (totalPieces == 0) 0f
                else downloadedPieces.count { it }.toFloat() / totalPieces

    /**
     * Attempts to connect to a peer and download pieces in order.
     * Returns when the peer disconnects, pieces are done, or on error.
     */
    suspend fun connectAndDownload(
        peer: TorrentFetcher.Peer,
        onPieceComplete: (pieceIndex: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(peer.ip, peer.port), 5000)
            socket.soTimeout = 30_000
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            if (!performHandshake(input, output)) {
                Log.d(TAG, "Handshake failed with ${peer.ip}:${peer.port}")
                return@withContext
            }

            sendInterested(output)

            var unchoked = false
            var peerBitfield = BooleanArray(totalPieces)

            // Message loop
            while (!socket.isClosed) {
                val msgLen = input.readInt()
                if (msgLen == 0) continue // keep-alive

                val msgId = input.read()
                val payload = if (msgLen > 1) {
                    ByteArray(msgLen - 1).also { input.readFully(it) }
                } else ByteArray(0)

                when (msgId) {
                    0 -> { // choke
                        unchoked = false
                    }
                    1 -> { // unchoke
                        unchoked = true
                        // Start requesting pieces
                        requestNextPiece(output, peerBitfield)
                    }
                    5 -> { // bitfield
                        peerBitfield = parseBitfield(payload)
                    }
                    4 -> { // have
                        val idx = java.nio.ByteBuffer.wrap(payload).int
                        if (idx in peerBitfield.indices) peerBitfield[idx] = true
                        if (unchoked) requestNextPiece(output, peerBitfield)
                    }
                    7 -> { // piece
                        handlePieceBlock(payload, onPieceComplete)
                        if (unchoked) requestNextPiece(output, peerBitfield)
                    }
                }

                // Check if all needed pieces are downloaded
                if (downloadedPieces.all { it }) break
            }
        } catch (e: Exception) {
            Log.d(TAG, "Peer ${peer.ip}:${peer.port} disconnected: ${e.message}")
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun performHandshake(input: DataInputStream, output: DataOutputStream): Boolean {
        // Send handshake
        output.writeByte(19)
        output.write("BitTorrent protocol".toByteArray())
        output.write(ByteArray(8)) // reserved
        output.write(infoHash)
        output.write(peerId)
        output.flush()

        // Read handshake
        val pstrLen = input.read()
        if (pstrLen != 19) return false
        val pstr = ByteArray(19).also { input.readFully(it) }
        if (String(pstr) != "BitTorrent protocol") return false
        input.skip(8) // reserved
        val remoteHash = ByteArray(20).also { input.readFully(it) }
        if (!remoteHash.contentEquals(infoHash)) return false
        input.skip(20) // peer id
        return true
    }

    private fun sendInterested(output: DataOutputStream) {
        output.writeInt(1)
        output.writeByte(2) // interested
        output.flush()
    }

    private fun requestNextPiece(output: DataOutputStream, peerBitfield: BooleanArray) {
        val nextPiece = downloadedPieces.indices.firstOrNull { i ->
            !downloadedPieces[i] && (i >= peerBitfield.size || peerBitfield[i])
        } ?: return

        val pieceSize = if (nextPiece == totalPieces - 1) {
            (totalLength - nextPiece.toLong() * pieceLength).toInt()
        } else pieceLength

        var offset = 0
        while (offset < pieceSize) {
            val blockSize = minOf(BLOCK_SIZE, pieceSize - offset)
            // Send REQUEST message
            output.writeInt(13)
            output.writeByte(6)
            output.writeInt(nextPiece)
            output.writeInt(offset)
            output.writeInt(blockSize)
            offset += blockSize
        }
        output.flush()
    }

    private fun handlePieceBlock(payload: ByteArray, onPieceComplete: (Int) -> Unit) {
        if (payload.size < 8) return
        val buf = java.nio.ByteBuffer.wrap(payload)
        val pieceIndex = buf.int
        val blockOffset = buf.int
        val data = ByteArray(payload.size - 8).also { buf.get(it) }

        val fileOffset = pieceIndex.toLong() * pieceLength + blockOffset
        synchronized(outputFile) {
            outputFile.seek(fileOffset)
            outputFile.write(data)
        }

        // Check if this piece is now fully downloaded
        val expectedPieceSize = if (pieceIndex == totalPieces - 1) {
            (totalLength - pieceIndex.toLong() * pieceLength).toInt()
        } else pieceLength

        // Mark complete if the block covers the end of the piece
        if (blockOffset + data.size >= expectedPieceSize) {
            if (pieceIndex in downloadedPieces.indices) {
                downloadedPieces[pieceIndex] = true
                onPieceComplete(pieceIndex)
                Log.d(TAG, "Piece $pieceIndex complete (${downloadedPieces.count { it }}/$totalPieces)")
            }
        }
    }

    private fun parseBitfield(data: ByteArray): BooleanArray {
        val result = BooleanArray(totalPieces)
        for (i in result.indices) {
            val byteIdx = i / 8
            val bitIdx = 7 - (i % 8)
            if (byteIdx < data.size) {
                result[i] = (data[byteIdx].toInt() shr bitIdx) and 1 == 1
            }
        }
        return result
    }
}
