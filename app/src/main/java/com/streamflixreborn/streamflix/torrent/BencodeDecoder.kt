package com.streamflixreborn.streamflix.torrent

/**
 * Minimal Bencode decoder for parsing BitTorrent tracker responses.
 * Supports: integers, byte strings, lists, dictionaries.
 */
object BencodeDecoder {

    fun decode(data: ByteArray): Any? {
        val pos = intArrayOf(0)
        return decodeValue(data, pos)
    }

    private fun decodeValue(data: ByteArray, pos: IntArray): Any? {
        if (pos[0] >= data.size) return null
        return when (val ch = data[pos[0]].toInt().toChar()) {
            'i'  -> decodeInt(data, pos)
            'l'  -> decodeList(data, pos)
            'd'  -> decodeDict(data, pos)
            else -> if (ch.isDigit()) decodeString(data, pos) else null
        }
    }

    private fun decodeInt(data: ByteArray, pos: IntArray): Long {
        pos[0]++ // skip 'i'
        val end = data.indexOf('e'.code.toByte(), pos[0])
        val value = String(data, pos[0], end - pos[0]).toLong()
        pos[0] = end + 1
        return value
    }

    private fun decodeString(data: ByteArray, pos: IntArray): ByteArray {
        val colon = data.indexOf(':'.code.toByte(), pos[0])
        val length = String(data, pos[0], colon - pos[0]).toInt()
        pos[0] = colon + 1
        val result = data.copyOfRange(pos[0], pos[0] + length)
        pos[0] += length
        return result
    }

    private fun decodeList(data: ByteArray, pos: IntArray): List<Any?> {
        pos[0]++ // skip 'l'
        val list = mutableListOf<Any?>()
        while (pos[0] < data.size && data[pos[0]].toInt().toChar() != 'e') {
            list.add(decodeValue(data, pos))
        }
        pos[0]++ // skip 'e'
        return list
    }

    private fun decodeDict(data: ByteArray, pos: IntArray): Map<String, Any?> {
        pos[0]++ // skip 'd'
        val map = mutableMapOf<String, Any?>()
        while (pos[0] < data.size && data[pos[0]].toInt().toChar() != 'e') {
            val key = String(decodeString(data, pos))
            val value = decodeValue(data, pos)
            map[key] = value
        }
        pos[0]++ // skip 'e'
        return map
    }

    private fun ByteArray.indexOf(byte: Byte, fromIndex: Int): Int {
        for (i in fromIndex until size) {
            if (this[i] == byte) return i
        }
        return -1
    }
}
