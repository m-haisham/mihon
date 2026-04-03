package eu.kanade.tachiyomi.data.backup.qr

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.QrShareManga
import eu.kanade.tachiyomi.data.backup.models.QrSharePayload
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val MAX_QR_PAYLOAD_BYTES = 2700
const val QR_URI_SCHEME = "mihon://qr/"

class QrPayloadBuilder(
    private val sourceManager: SourceManager = Injekt.get(),
) {
    fun build(
        manga: List<Manga>,
        mangaCategoryIds: Map<Long, List<Long>>,
        categories: List<Category>,
    ): List<String> {
        val referencedCategoryIds = manga.flatMap { mangaCategoryIds[it.id].orEmpty() }.toSet()
        val backupCategories = categories
            .filter { it.id in referencedCategoryIds }
            .map(backupCategoryMapper)
        val sources = manga
            .map { m ->
                val sourceName = sourceManager.get(m.source)?.name ?: ""
                BackupSource(sourceId = m.source, name = sourceName)
            }
            .distinctBy { it.sourceId }
        val qrManga = manga.map { m ->
            QrShareManga(
                source = m.source,
                url = m.url,
                title = m.title,
                thumbnailUrl = m.thumbnailUrl,
                categories = mangaCategoryIds[m.id].orEmpty().filter { it in referencedCategoryIds },
            )
        }
        return buildChunkedUris(qrManga, backupCategories, sources)
    }

    private fun buildChunkedUris(
        qrManga: List<QrShareManga>,
        categories: List<BackupCategory>,
        sources: List<BackupSource>,
    ): List<String> {
        val singleEncoded = encodePayload(QrSharePayload(manga = qrManga, categories = categories, sources = sources))
        if (singleEncoded.length <= MAX_QR_PAYLOAD_BYTES) {
            return listOf("$QR_URI_SCHEME$singleEncoded")
        }
        val chunks = mutableListOf<List<QrShareManga>>()
        var remaining = qrManga.toMutableList()
        while (remaining.isNotEmpty()) {
            val chunk = findFittingChunk(remaining, categories, sources)
            chunks.add(chunk)
            remaining = remaining.drop(chunk.size).toMutableList()
        }
        val total = chunks.size
        return chunks.mapIndexed { index, chunk ->
            val encoded = encodePayload(QrSharePayload(manga = chunk, categories = categories, sources = sources))
            "C${index + 1}/$total:$QR_URI_SCHEME$encoded"
        }
    }

    private fun findFittingChunk(
        remaining: List<QrShareManga>,
        categories: List<BackupCategory>,
        sources: List<BackupSource>,
    ): List<QrShareManga> {
        var low = 1
        var high = remaining.size
        while (low < high) {
            val mid = (low + high + 1) / 2
            val encoded = encodePayload(QrSharePayload(manga = remaining.take(mid), categories = categories, sources = sources))
            if (encoded.length <= MAX_QR_PAYLOAD_BYTES) low = mid else high = mid - 1
        }
        return remaining.take(low)
    }

    private fun encodePayload(payload: QrSharePayload): String {
        val bytes = ProtoBuf.encodeToByteArray(QrSharePayload.serializer(), payload)
        val gzipped = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gz -> gz.write(bytes) }
            bos.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }
}
