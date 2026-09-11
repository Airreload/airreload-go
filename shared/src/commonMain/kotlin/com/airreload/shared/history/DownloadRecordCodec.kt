package com.airreload.shared.history

object DownloadRecordCodec {
    private const val VERSION = "1"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(record: DownloadRecord): String =
        listOf(
            VERSION,
            encodeText(record.id),
            record.downloadedAt.toString(),
            encodeText(record.packageName),
            encodeText(record.label),
            encodeText(record.versionName),
            record.versionCode.toString(),
            encodeText(record.sourceHost),
            encodeText(record.outcome),
            encodeText(record.artifactName),
            record.artifactBytes.toString(),
        ).joinToString("|")

    fun decode(value: String?): DownloadRecord? {
        if (value == null) return null
        val parts = value.split('|')
        if (parts.size != 11 || parts[0] != VERSION) return null
        return try {
            DownloadRecord(
                id = decodeText(parts[1]),
                downloadedAt = parts[2].toLong(),
                packageName = decodeText(parts[3]),
                label = decodeText(parts[4]),
                versionName = decodeText(parts[5]),
                versionCode = parts[6].toLong(),
                sourceHost = decodeText(parts[7]),
                outcome = decodeText(parts[8]),
                artifactName = decodeText(parts[9]),
                artifactBytes = parts[10].toLong(),
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private fun encodeText(value: String?): String {
        val bytes = value.orEmpty().trim().encodeToByteArray()
        val result = StringBuilder((bytes.size * 4 + 2) / 3)
        var index = 0
        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xff
            val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else -1
            val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else -1
            result.append(ALPHABET[first ushr 2])
            result.append(ALPHABET[((first and 0x03) shl 4) or if (second >= 0) second ushr 4 else 0])
            if (second >= 0) {
                result.append(ALPHABET[((second and 0x0f) shl 2) or if (third >= 0) third ushr 6 else 0])
            }
            if (third >= 0) {
                result.append(ALPHABET[third and 0x3f])
            }
            index += 3
        }
        return result.toString()
    }

    private fun decodeText(value: String): String {
        if (value.isEmpty()) return ""
        if (value.length % 4 == 1) throw IllegalArgumentException("Invalid Base64URL")
        val bytes = ArrayList<Byte>((value.length * 3) / 4)
        var index = 0
        while (index < value.length) {
            val first = alphabetIndex(value[index])
            val second = alphabetIndex(value[index + 1])
            val third = if (index + 2 < value.length) alphabetIndex(value[index + 2]) else -1
            val fourth = if (index + 3 < value.length) alphabetIndex(value[index + 3]) else -1
            bytes.add(((first shl 2) or (second ushr 4)).toByte())
            if (third >= 0) {
                bytes.add((((second and 0x0f) shl 4) or (third ushr 2)).toByte())
            }
            if (fourth >= 0) {
                bytes.add((((third and 0x03) shl 6) or fourth).toByte())
            }
            index += 4
        }
        return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    }

    private fun alphabetIndex(value: Char): Int {
        val index = ALPHABET.indexOf(value)
        if (index < 0) throw IllegalArgumentException("Invalid Base64URL")
        return index
    }
}
