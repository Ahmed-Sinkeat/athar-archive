package com.atharchive.core.data.content

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
internal data class LightCatalogHeader(val schema: Int, val persons: List<CatalogPerson> = emptyList())

object AppContentDocuments {
    private val Collections = setOf("book", "article", "question", "poem")

    fun decodeCatalog(bytes: ByteArray, reference: ArtifactReference): CatalogDocument {
        ContentDigests.verify(bytes, reference.hash, reference.size, "catalog")
        return checked(decode<CatalogDocument>(bytes, "catalog"))
    }

    /**
     * The light catalog, line by line, never one string: the 76 MB catalog decoded whole ran 1.2.4 out of
     * memory. Line 1 is `{schema, persons}`; each next line is an entry whose pkg is shortened (paths follow
     * from the hashes, f0 = [len, n, sha256]) and whose personName/died, when left out, are its person's.
     * The caller checks the bytes' size and SHA-256 before using what this returns.
     */
    fun decodeLightCatalog(input: InputStream): CatalogDocument = try {
        val reader = input.bufferedReader(Charsets.UTF_8)
        val header = AppContentJson.decodeFromString<LightCatalogHeader>(reader.readLine() ?: fail("light catalog is empty"))
        val persons = header.persons.associateBy { it.id }
        val entries = ArrayList<CatalogEntry>()
        while (true) {
            val line = reader.readLine() ?: break
            val light = AppContentJson.parseToJsonElement(line).jsonObject
            val p = light.getValue("pkg").jsonObject
            fun text(key: String) = p.getValue(key).jsonPrimitive.content
            val base = "content/${light.getValue("coll").jsonPrimitive.content}/${light.getValue("id").jsonPrimitive.content}"
            val person = light["person"]?.jsonPrimitive?.content?.let(persons::get)?.takeIf { "personName" !in light }
            val entry = buildJsonObject {
                light.forEach { (key, value) -> if (key != "pkg") put(key, value) }
                person?.let { put("personName", it.name); it.died?.let { died -> put("died", died) } }
                put("pkg", buildJsonObject {
                    put("path", "$base/${text("h")}.athar"); put("hash", text("h")); put("size", p.getValue("s"))
                    put("idxPath", "$base/${text("ih")}.athar.idx"); put("idxHash", text("ih")); put("idxSize", p.getValue("is"))
                    put("uncompressed", p.getValue("u")); put("blocks", p.getValue("b")); put("chapters", p.getValue("c"))
                    p["pg"]?.let { put("pages", it) }
                    p["f0"]?.jsonArray?.let { f0 ->
                        put("f0", buildJsonObject { put("off", 0); put("len", f0[0]); put("ord", 0); put("n", f0[1]); put("sha256", f0[2]) })
                    }
                })
            }
            entries += AppContentJson.decodeFromJsonElement<CatalogEntry>(entry)
        }
        checked(CatalogDocument(header.schema, entries, header.persons))
    } catch (error: SerializationException) {
        throw ContentIntegrityException("malformed light catalog", error)
    } catch (error: IllegalArgumentException) {
        throw ContentIntegrityException("malformed light catalog", error)
    } catch (error: NoSuchElementException) {
        throw ContentIntegrityException("malformed light catalog", error)
    }

    private fun checked(decoded: CatalogDocument): CatalogDocument {
        if (decoded.schema != APP_CONTENT_SCHEMA) {
            throw UnsupportedContentSchemaException(decoded.schema, decoded.schema)
        }
        // A collection this build can't read (e.g. a newer server's `person` pages) is skipped, never fatal:
        // failing the whole catalog stopped every installed app from updating.
        val (readable, skipped) = decoded.entries.partition { it.coll in Collections }
        val skippedIds = skipped.mapTo(HashSet()) { it.id }
        val catalog = decoded.copy(
            entries = readable,
            persons = decoded.persons.map { person -> person.copy(works = person.works.filter { it !in skippedIds }) },
        )
        val identities = HashSet<String>()
        catalog.entries.forEach { entry ->
            validateEntry(entry)
            if (!identities.add(entry.id)) fail("catalog contains a duplicate ${entry.coll}/${entry.id}")
        }
        val byId = catalog.entries.associateBy { it.id }
        catalog.entries.forEach { entry ->
            entry.editionOf?.let { primaryId ->
                val primary = byId[primaryId] ?: fail("missing default edition")
                if (primaryId == entry.id || primary.coll != entry.coll || primary.editionOf != null || entry.id !in primary.editions) fail("invalid edition group")
            }
            if (entry.editions.distinct().size != entry.editions.size || entry.editions.any { byId[it]?.editionOf != entry.id }) fail("invalid editions")
        }
        val personIds = HashSet<String>()
        val workIds = catalog.entries.mapTo(HashSet()) { it.id }
        catalog.persons.forEach { person ->
            requireSegment(person.id, "person ID")
            if (!personIds.add(person.id) || person.name.isBlank() || person.works.distinct().size != person.works.size || person.works.any { it !in workIds }) fail("malformed catalog person")
        }
        return catalog
    }

    fun decodeTombstones(bytes: ByteArray, reference: ArtifactReference): TombstoneDocument {
        ContentDigests.verify(bytes, reference.hash, reference.size, "tombstones")
        val decoded = decode<TombstoneDocument>(bytes, "tombstones")
        if (decoded.schema != APP_CONTENT_SCHEMA) {
            throw UnsupportedContentSchemaException(decoded.schema, decoded.schema)
        }
        val tombstones = decoded.copy(deleted = decoded.deleted.filter { it.coll in Collections })
        tombstones.deleted.forEach {
            requireCollection(it.coll)
            requireSegment(it.id, "tombstone ID")
            it.supersededBy?.let { replacement -> requireSegment(replacement, "replacement ID") }
        }
        return tombstones
    }

    private inline fun <reified T> decode(bytes: ByteArray, label: String): T = try {
        AppContentJson.decodeFromString<T>(bytes.toString(Charsets.UTF_8))
    } catch (error: SerializationException) {
        throw ContentIntegrityException("malformed $label document", error)
    }

    internal fun validateEntry(entry: CatalogEntry) {
        requireCollection(entry.coll)
        requireSegment(entry.id, "catalog entity ID")
        if (entry.v < 1 || !ContentDigests.isSha256(entry.hash) || entry.title.isBlank()) {
            fail("malformed catalog entry ${entry.coll}/${entry.id}")
        }
        if (entry.verseCount != null && (entry.coll != "poem" || entry.verseCount < 0)) fail("invalid verse count")
        val pkg = entry.pkg
        if (
            !ContentDigests.isSha256(pkg.hash) || !ContentDigests.isSha256(pkg.idxHash) ||
            pkg.size < 0 || pkg.idxSize < 0 || pkg.uncompressed < 0 || pkg.blocks < 0 || pkg.chapters < 0
        ) fail("malformed package reference for ${entry.coll}/${entry.id}")
        pkg.f0?.let { f0 ->
            if (f0.off != 0L || f0.ord != 0 || f0.len <= 0 || f0.n <= 0 || f0.len > pkg.size || f0.n > pkg.blocks ||
                !ContentDigests.isSha256(f0.sha256)) fail("malformed first frame for ${entry.coll}/${entry.id}")
        }
        val base = "content/${entry.coll}/${entry.id}"
        if (pkg.path != "$base/${pkg.hash}.athar" || pkg.idxPath != "$base/${pkg.idxHash}.athar.idx") {
            fail("package paths are not content-addressed for ${entry.coll}/${entry.id}")
        }
        entry.audio.forEach { audio ->
            requireSegment(audio.id, "audio ID")
            if (
                audio.format !in setOf("opus", "mp3") || !ContentDigests.isSha256(audio.hash) ||
                audio.seconds < 0 || audio.size < 0 ||
                audio.path != "audio/${audio.id}/${audio.hash}.${audio.format}"
            ) fail("malformed audio reference ${audio.id}")
        }
    }

    private fun requireCollection(collection: String) {
        if (collection !in Collections) fail("unsupported readable collection $collection")
    }

    private fun requireSegment(value: String, label: String) {
        if (value.isBlank() || '/' in value || '\\' in value || value == "." || value == "..") fail("unsafe $label")
    }
}

object FramedPackageDecoder {
    private val BlockTypes = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "quote", "verse", "page", "notes", "break", "img")
    private val BlockId = Regex("^[0-9a-f]{32}$")
    private val Fingerprint = Regex("^[0-9a-f]{16}$")

    fun decodeIndex(bytes: ByteArray, entry: CatalogEntry): PackageIndex {
        ContentDigests.verify(bytes, entry.pkg.idxHash, entry.pkg.idxSize, "package index")
        val index = try {
            AppContentJson.decodeFromString<PackageIndex>(bytes.toString(Charsets.UTF_8))
        } catch (error: SerializationException) {
            throw ContentIntegrityException("malformed package index", error)
        }
        if (
            index.schema != APP_CONTENT_SCHEMA || index.coll != entry.coll ||
            index.entityId != entry.id || index.v != entry.v
        ) fail("package index identity differs from catalog")

        var nextOffset = 0L
        var nextOrdinal = 0
        index.frames.forEach { frame ->
            if (
                frame.off != nextOffset || frame.ord != nextOrdinal || frame.len <= 0 || frame.n <= 0 ||
                !ContentDigests.isSha256(frame.sha256)
            ) fail("package index contains a malformed frame")
            nextOffset += frame.len
            nextOrdinal += frame.n
        }
        if (nextOffset != entry.pkg.size || nextOrdinal != entry.pkg.blocks) {
            fail("package index totals differ from catalog")
        }
        return index
    }

    fun decodeFrame(bytes: ByteArray, frame: FrameIndexEntry, entry: CatalogEntry): DecodedFrame {
        ContentDigests.verify(bytes, frame.sha256, frame.len.toLong(), "package frame ${frame.ord}")
        var header: PackageHeader? = null
        val blocks = ArrayList<PackageBlock>(frame.n)
        val footnotes = ArrayList<PackageFootnote>()
        var reachedFootnotes = false

        try {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { reader ->
                var lineNumber = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNumber++
                    if (line.isEmpty()) fail("package frame contains an empty record")
                    val record = AppContentJson.parseToJsonElement(line) as? JsonObject
                        ?: fail("package frame record $lineNumber is not an object")
                    when (record["t"]?.jsonPrimitive?.content) {
                        "header" -> {
                            if (lineNumber != 1 || frame.ord != 0 || header != null) fail("package header is misplaced")
                            header = AppContentJson.decodeFromJsonElement<PackageHeader>(record)
                        }
                        "fn" -> {
                            reachedFootnotes = true
                            footnotes += AppContentJson.decodeFromJsonElement<PackageFootnote>(record)
                        }
                        null -> fail("package frame record $lineNumber has no type")
                        else -> {
                            if (reachedFootnotes) fail("package block appears after footnotes")
                            blocks += AppContentJson.decodeFromJsonElement<PackageBlock>(record)
                        }
                    }
                }
            }
        } catch (error: ContentIntegrityException) {
            throw error
        } catch (error: IOException) {
            throw ContentIntegrityException("package frame is not valid gzip", error)
        } catch (error: SerializationException) {
            throw ContentIntegrityException("package frame contains malformed records", error)
        } catch (error: IllegalArgumentException) {
            throw ContentIntegrityException("package frame contains malformed records", error)
        }

        if ((frame.ord == 0) != (header != null)) fail("first package frame must carry exactly one header")
        header?.let { validateHeader(it, entry) }
        if (blocks.size != frame.n) fail("package frame block count differs from index")
        blocks.forEachIndexed { position, block -> validateBlock(block, frame.ord + position) }
        footnotes.forEach { footnote ->
            if (footnote.t != "fn" || footnote.id.isBlank()) fail("malformed package footnote")
            validateSpans(footnote.x, footnote.sp)
        }
        return DecodedFrame(header, blocks, footnotes)
    }

    private fun validateHeader(header: PackageHeader, entry: CatalogEntry) {
        if (
            header.t != "header" || header.schema != APP_CONTENT_SCHEMA || header.coll != entry.coll ||
            header.id != entry.id || header.v != entry.v || header.blocks != entry.pkg.blocks ||
            header.chapters.size != entry.pkg.chapters || header.footnotes < 0
        ) fail("package header differs from catalog")
    }

    private fun validateBlock(block: PackageBlock, expectedOrdinal: Int) {
        if (
            block.t !in BlockTypes || block.a.isBlank() || block.i != expectedOrdinal ||
            !BlockId.matches(block.id) || !Fingerprint.matches(block.fp)
        ) fail("malformed package block $expectedOrdinal")
        when (block.t) {
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "quote" ->
                if (block.x == null) fail("text block $expectedOrdinal has no text")
            "verse" -> if (block.x == null && (block.s == null || block.j == null)) {
                fail("verse block $expectedOrdinal has no verse text")
            }
            "page" -> if (block.p == null) fail("page block $expectedOrdinal has no page number")
            "img" -> if (block.src?.startsWith("https://") != true) fail("image block $expectedOrdinal has no https source")
        }
        if (block.commentary.isNotEmpty() && (block.t != "verse" || block.commentary.any { it.sourceLabel.isBlank() || it.text.isBlank() })) fail("invalid verse commentary")
        block.x?.let { validateSpans(it, block.sp) }
        if (block.x == null && block.sp.isNotEmpty()) fail("block $expectedOrdinal has spans without text")
    }

    private fun validateSpans(text: String, spans: List<InlineSpan>) {
        spans.forEach { span ->
            if (
                span.k !in setOf("strong", "emphasis", "link", "sup", "entityRef") ||
                span.s < 0 || span.e < span.s || span.e > text.length ||
                (span.k in setOf("link", "entityRef") && span.target.isNullOrBlank())
            ) fail("inline span lies outside its UTF-16 text")
        }
    }
}
