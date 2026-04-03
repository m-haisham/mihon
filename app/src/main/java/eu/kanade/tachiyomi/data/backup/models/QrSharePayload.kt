package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class QrSharePayload(
    @ProtoNumber(1) val version: Int = 1,
    @ProtoNumber(2) val manga: List<QrShareManga> = emptyList(),
    @ProtoNumber(3) val categories: List<BackupCategory> = emptyList(),
    @ProtoNumber(4) val sources: List<BackupSource> = emptyList(),
)

@Serializable
data class QrShareManga(
    @ProtoNumber(1) val source: Long = 0L,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val thumbnailUrl: String? = null,
    @ProtoNumber(5) val categories: List<Long> = emptyList(),
)
