package com.atharchive.core.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val APP_CONTENT_SCHEMA = 2
internal const val MAX_SIGNED_DOCUMENT_BYTES = 64 * 1024

internal val AppContentJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
internal data class SignedEnvelope(
    val envelope: Int? = null,
    val payload: String? = null,
    val signatures: List<RootSignature> = emptyList(),
)

@Serializable
internal data class RootSignature(
    val keyId: String? = null,
    val alg: String? = null,
    val value: String? = null,
)

@Serializable
data class ArtifactReference(
    val path: String,
    val hash: String,
    val size: Long,
)

@Serializable
data class RootPayload(
    val schema: Int,
    val generationId: String,
    val catalog: ArtifactReference,
    val tombstones: ArtifactReference,
    val minAppSchema: Int,
)

@Serializable
data class PageRange(
    val from: Int,
    val to: Int,
    val vols: Int,
)

@Serializable
data class InlineSpan(
    val k: String,
    val s: Int,
    val e: Int,
    val target: String? = null,
)

@Serializable
data class ChapterRecord(
    val a: String,
    val title: String,
    val block: Int,
)

@Serializable
data class PackageReference(
    val path: String,
    val hash: String,
    val size: Long,
    val idxPath: String,
    val idxHash: String,
    val idxSize: Long,
    val uncompressed: Long,
    val blocks: Int,
    val chapters: Int,
    val pages: PageRange? = null,
)

@Serializable
data class AudioCue(
    val v: Int,
    val t: Double,
)

@Serializable
data class AudioReference(
    val id: String,
    val path: String,
    val hash: String,
    val format: String,
    val seconds: Int,
    val size: Long,
    val cues: List<AudioCue> = emptyList(),
)

@Serializable
data class CatalogEntry(
    val id: String,
    val coll: String,
    val v: Int,
    val hash: String,
    val title: String,
    val person: String? = null,
    val personName: String? = null,
    val died: Int? = null,
    val topics: List<String> = emptyList(),
    val kind: String? = null,
    val authoredYear: Int? = null,
    val publishedAt: String? = null,
    val description: String? = null,
    val excerpt: String? = null,
    val openingVerses: List<String> = emptyList(),
    val pkg: PackageReference,
    val audio: List<AudioReference> = emptyList(),
)

@Serializable
data class CatalogDocument(
    val schema: Int,
    val entries: List<CatalogEntry>,
)

@Serializable
data class Tombstone(
    val id: String,
    val coll: String,
    val at: String,
    val supersededBy: String? = null,
)

@Serializable
data class TombstoneDocument(
    val schema: Int,
    val since: String,
    val deleted: List<Tombstone>,
)

@Serializable
data class FrameIndexEntry(
    val off: Long,
    val len: Int,
    val ord: Int,
    val n: Int,
    val sha256: String,
)

@Serializable
data class PackageIndex(
    val schema: Int,
    val coll: String,
    val entityId: String,
    val v: Int,
    val frames: List<FrameIndexEntry>,
)

@Serializable
data class PackageHeader(
    val t: String,
    val schema: Int,
    val coll: String,
    val id: String,
    val v: Int,
    val blocks: Int,
    val chapters: List<ChapterRecord>,
    val footnotes: Int,
    val pages: PageRange? = null,
)

@Serializable
data class PackageBlock(
    val t: String,
    val a: String,
    val i: Int,
    val id: String,
    val fp: String,
    val p: Int? = null,
    val vol: Int? = null,
    val x: String? = null,
    val s: String? = null,
    val j: String? = null,
    val sp: List<InlineSpan> = emptyList(),
    val f: List<String> = emptyList(),
    val n: Int? = null,
    val ordered: Boolean? = null,
    val start: Int? = null,
    val depth: Int? = null,
    val continuation: Boolean? = null,
    val ha: String? = null,
)

@Serializable
data class PackageFootnote(
    val t: String,
    val id: String,
    val x: String,
    val sp: List<InlineSpan> = emptyList(),
)

data class DecodedFrame(
    val header: PackageHeader?,
    val blocks: List<PackageBlock>,
    val footnotes: List<PackageFootnote>,
)
