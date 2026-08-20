package com.atharchive.core.data.content

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FramedPackageDecoderTest {
    @Test
    fun decodesTypeScriptGeneratedArabicFrameWithoutChangingUnicode() {
        val index = FramedPackageDecoder.decodeIndex(INDEX.toByteArray(), ARTICLE)
        val packageBytes = Base64.getDecoder().decode(PACKAGE_BASE64)
        ContentDigests.verify(packageBytes, ARTICLE.pkg.hash, ARTICLE.pkg.size, "package")

        val decoded = FramedPackageDecoder.decodeFrame(packageBytes, index.frames.single(), ARTICLE)

        assertNotNull(decoded.header)
        assertEquals(3, decoded.header!!.blocks)
        assertEquals(3, decoded.blocks.size)
        assertEquals(
            "الحمد لله والصلاة والسلام على رسول الله وعلى آله وصحبه ومن والاه\n" +
                "أما بعد :\n" +
                "فينشر بعض الناس عن أبي ذر أنه قال :\" إذا سافر الفقر إلى مكانٍ ما قال الكفر خذني معك\"",
            decoded.blocks.first().x,
        )
        assertEquals("f091862f3191a24bdc88ad48185b9e7e", decoded.blocks.first().id)
    }

    @Test
    fun rejectsAFrameWhoseCompressedBytesWereTampered() {
        val index = FramedPackageDecoder.decodeIndex(INDEX.toByteArray(), ARTICLE)
        val changed = Base64.getDecoder().decode(PACKAGE_BASE64).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }

        assertThrows(ContentIntegrityException::class.java) {
            FramedPackageDecoder.decodeFrame(changed, index.frames.single(), ARTICLE)
        }
    }

    @Test
    fun rejectsAStaleIndexEvenWhenItsOwnHashIsValid() {
        val changed = INDEX.replace(ARTICLE.id, "different-article")
        val changedBytes = changed.toByteArray()
        val entry = ARTICLE.copy(
            pkg = ARTICLE.pkg.copy(
                idxHash = ContentDigests.sha256Hex(changedBytes),
                idxSize = changedBytes.size.toLong(),
            ),
        )

        assertThrows(ContentIntegrityException::class.java) {
            FramedPackageDecoder.decodeIndex(changedBytes, entry)
        }
    }

    private companion object {
        const val ARTICLE_ID = "byan-kdhb-athr-idha-safr-al-fqr-ila-mkan-ma-qal-al-kfr-khdhny-mak-ala-aby-dhr--v2"
        val ARTICLE = CatalogEntry(
            id = ARTICLE_ID,
            coll = "article",
            v = 1,
            hash = "57fd270db75a779920f3a6a5e9302c9b234ddd9d1b14e158fc486a5226e55e37",
            title = "بيان كذب الأثر",
            pkg = PackageReference(
                path = "content/article/$ARTICLE_ID/4dc3bd533547faf6639c5a807184ece093c6695c31f79865213a5c52b4837506.athar",
                hash = "4dc3bd533547faf6639c5a807184ece093c6695c31f79865213a5c52b4837506",
                size = 590,
                idxPath = "content/article/$ARTICLE_ID/c52407d5398f88a4046129defa033bf02962391e38f2333adadf51c93c4799d8.athar.idx",
                idxHash = "c52407d5398f88a4046129defa033bf02962391e38f2333adadf51c93c4799d8",
                idxSize = 252,
                uncompressed = 974,
                blocks = 3,
                chapters = 1,
            ),
        )

        const val INDEX =
            """{"schema":2,"coll":"article","entityId":"$ARTICLE_ID","v":1,"frames":[{"off":0,"len":590,"ord":0,"n":3,"sha256":"4dc3bd533547faf6639c5a807184ece093c6695c31f79865213a5c52b4837506"}]}""" + "\n"

        // Exact gzip member emitted by scripts/gen-app-content.ts from the repository's
        // article slice. Keeping it as base64 makes this a normal reviewable text fixture.
        const val PACKAGE_BASE64 =
            "H4sIAAAAAAACA31S22rbQBB9z1cs86wF62av9lfqPqy0K2Qsy4ksQkPIgxPJTg39iZogl5I2xk3B/ZKZvylry01oSt92Zs7MzjlzrqECCZlR2pTgwCzJzESB9BxIpnkOElRZjZLcgAMjDRLiK1Xwsc5irqqs5COdKT5TaclVztOLko9yxSdjVfCJ4hcqt+lxWvJxprPiik/UmKtccRVfcZ2VnF964MAlSNeBOJ8m4xlI34EkU+eVKWcg312DAgkTNSrAgWpU5QYkQIcG2bt570A6nVbFtDIzG59dHxidgwMfQAK2VOMjNfidUU01LRndH3LPVGOLX07hzobUMNxTTR8ZbnFH91QzWzy1daXPp/gZH3FzeFJDi9OklpbDAtfUYMtwg3v8zuSwoDmtaIE/cHtM/jxOXmCLO/vpguEaN7Ri+GQha1rYwbcWxOQQGD7gkx24w5bmFmG753Rrnw+HvaihO2xpQZ/Y4e+u+QC8O/Z8wyda0MrW93Q3tDq+UncEstddOe1Fruh7qe9GrvKCWCdCKB0IV4RxZAbWDem5xQkvNXEQan8QmYGbwN/y0z0tj4tbadb4FbfMasRoZeX7B/MN1UcEru2N2EFsCzsK3VX2uKV5V2D4i1a4peUbOm5HR3t+7LmuL0wqPNM3KjJu0ncTZbye74k/dAJ/EAeD1AShH+okMG/pHMnY27/yxottqDl57X9usWaj5s22Xret5/aNMEaH2g8DP05E5Iu0H+pQeF5kXrZNIxMYEwod9YQKehpuzn4D+r8vac4DAAA="
    }
}
