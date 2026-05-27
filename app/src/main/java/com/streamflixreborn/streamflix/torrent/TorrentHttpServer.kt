package com.streamflixreborn.streamflix.torrent

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.RandomAccessFile

/**
 * NanoHTTPD server that serves the partially-downloaded torrent file as an HTTP stream.
 * ExoPlayer can connect to this and progressively consume video data as it's downloaded.
 *
 * NanoHTTPD is already a dependency: "org.nanohttpd:nanohttpd:2.3.1"
 */
class TorrentHttpServer(
    private val file: File,
    private val totalSize: Long,
    private val pieceClient: PieceClient
) : NanoHTTPD(0) { // Port 0 = OS assigns a free port

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/stream") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }

        val rangeHeader = session.headers["range"]
        val (startByte, endByte) = parseRange(rangeHeader, totalSize)

        val raf = RandomAccessFile(file, "r")
        raf.seek(startByte)

        val length = endByte - startByte + 1
        val inputStream = object : java.io.InputStream() {
            private var remaining = length
            private val WAIT_MAX_MS = 30_000L

            override fun read(): Int {
                if (remaining <= 0) return -1
                val waitStart = System.currentTimeMillis()
                while (true) {
                    val fileLen = file.length()
                    if (raf.filePointer < fileLen) {
                        remaining--
                        return raf.read()
                    }
                    // Data not yet downloaded — wait for it
                    if (System.currentTimeMillis() - waitStart > WAIT_MAX_MS) return -1
                    Thread.sleep(200)
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val toRead = minOf(len.toLong(), remaining).toInt()
                val waitStart = System.currentTimeMillis()
                while (true) {
                    val available = (file.length() - raf.filePointer).toInt()
                    if (available >= toRead) {
                        val count = raf.read(b, off, toRead)
                        if (count > 0) remaining -= count
                        return count
                    }
                    if (System.currentTimeMillis() - waitStart > WAIT_MAX_MS) return -1
                    Thread.sleep(200)
                }
            }

            override fun close() {
                try { raf.close() } catch (ignored: Exception) {}
            }
        }

        val contentLength = endByte - startByte + 1
        return if (rangeHeader != null) {
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                "video/mp4",
                inputStream,
                contentLength
            )
            response.addHeader("Content-Range", "bytes $startByte-$endByte/$totalSize")
            response.addHeader("Accept-Ranges", "bytes")
            response
        } else {
            val response = newFixedLengthResponse(
                Response.Status.OK,
                "video/mp4",
                inputStream,
                contentLength
            )
            response.addHeader("Accept-Ranges", "bytes")
            response
        }
    }

    private fun parseRange(rangeHeader: String?, totalSize: Long): Pair<Long, Long> {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return Pair(0L, totalSize - 1)
        }
        val range = rangeHeader.removePrefix("bytes=").split("-")
        val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = range.getOrNull(1)?.toLongOrNull() ?: (totalSize - 1)
        return Pair(start, end)
    }
}
