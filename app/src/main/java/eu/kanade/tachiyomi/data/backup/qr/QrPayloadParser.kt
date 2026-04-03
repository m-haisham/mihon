package eu.kanade.tachiyomi.data.backup.qr

import eu.kanade.tachiyomi.data.backup.models.QrSharePayload
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.Base64
import java.util.zip.GZIPInputStream

class QrPayloadParser {
    fun parse(raw: String): ScanResult {
        return when {
            raw.startsWith("C") && raw.contains(":") -> {
                val colonIdx = raw.indexOf(":")
                val header = raw.substring(1, colonIdx)
                val parts = header.split("/")
                if (parts.size != 2) return ScanResult.Unknown
                val index = parts[0].toIntOrNull() ?: return ScanResult.Unknown
                val total = parts[1].toIntOrNull() ?: return ScanResult.Unknown
                val payloadUri = raw.substring(colonIdx + 1)
                if (!payloadUri.startsWith(QR_URI_SCHEME)) return ScanResult.Unknown
                ScanResult.Chunk(index = index, total = total, encoded = payloadUri.removePrefix(QR_URI_SCHEME))
            }
            raw.startsWith(QR_URI_SCHEME) -> {
                val payload = decodePayload(raw.removePrefix(QR_URI_SCHEME)) ?: return ScanResult.Unknown
                ScanResult.Single(payload)
            }
            else -> ScanResult.Unknown
        }
    }

    fun assembleAndDecode(encodedChunks: List<String>): QrSharePayload? {
        val payloads = encodedChunks.mapNotNull { decodePayload(it) }
        if (payloads.isEmpty()) return null
        return payloads.first().copy(manga = payloads.flatMap { it.manga })
    }

    private fun decodePayload(encoded: String): QrSharePayload? = try {
        val compressed = Base64.getUrlDecoder().decode(encoded)
        val bytes = GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
        ProtoBuf.decodeFromByteArray(QrSharePayload.serializer(), bytes)
    } catch (_: Exception) {
        null
    }

    sealed interface ScanResult {
        data class Single(val payload: QrSharePayload) : ScanResult
        data class Chunk(val index: Int, val total: Int, val encoded: String) : ScanResult
        data object Unknown : ScanResult
    }
}
