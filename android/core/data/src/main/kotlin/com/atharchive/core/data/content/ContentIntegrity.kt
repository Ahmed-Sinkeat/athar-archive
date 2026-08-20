package com.atharchive.core.data.content

import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.SerializationException

class ContentIntegrityException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedContentSchemaException(
    val schema: Int,
    val minimumAppSchema: Int,
) : Exception("app content schema $schema requires app schema $minimumAppSchema")

object ContentDigests {
    private val Hex64 = Regex("^[0-9a-f]{64}$")

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .toHex()

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun verify(bytes: ByteArray, expectedHash: String, expectedSize: Long, label: String) {
        if (!Hex64.matches(expectedHash)) fail("$label has a malformed SHA-256")
        if (expectedSize < 0 || bytes.size.toLong() != expectedSize) {
            fail("$label size ${bytes.size} differs from expected $expectedSize")
        }
        if (sha256Hex(bytes) != expectedHash) fail("$label SHA-256 mismatch")
    }

    fun verify(file: File, expectedHash: String, expectedSize: Long, label: String) {
        if (!Hex64.matches(expectedHash)) fail("$label has a malformed SHA-256")
        if (!file.isFile || expectedSize < 0 || file.length() != expectedSize) {
            fail("$label size ${if (file.isFile) file.length() else -1} differs from expected $expectedSize")
        }
        if (sha256Hex(file) != expectedHash) fail("$label SHA-256 mismatch")
    }

    internal fun isSha256(value: String): Boolean = Hex64.matches(value)
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xff) }

object SignedRootVerifier {
    private val Base64Url = Regex("^[A-Za-z0-9_-]*$")

    fun rsaPublicKeyFromX509(bytes: ByteArray): PublicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    } catch (error: Exception) {
        throw ContentIntegrityException("malformed trusted RSA public key", error)
    }

    fun verify(envelopeBytes: ByteArray, trustedKeys: Map<String, PublicKey>): RootPayload {
        if (envelopeBytes.size > MAX_SIGNED_DOCUMENT_BYTES) fail("signed root envelope exceeds 64 KiB")
        val envelope = decodeEnvelope(envelopeBytes)
        if (envelope.envelope != 1 || envelope.payload == null) fail("malformed signed root envelope")

        val payloadBytes = decodeBase64Url(envelope.payload, "root payload")
        if (payloadBytes.size > MAX_SIGNED_DOCUMENT_BYTES) fail("signed root payload exceeds 64 KiB")

        val accepted = envelope.signatures.any { candidate ->
            if (candidate.alg != "SHA256withRSA") return@any false
            val key = trustedKeys[candidate.keyId] as? RSAPublicKey ?: return@any false
            if (key.modulus.bitLength() < 3_072) return@any false
            val value = candidate.value ?: return@any false
            val signatureBytes = try {
                decodeBase64Url(value, "root signature")
            } catch (_: ContentIntegrityException) {
                return@any false
            }
            try {
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(key)
                    update(payloadBytes)
                    verify(signatureBytes)
                }
            } catch (_: Exception) {
                false
            }
        }
        if (!accepted) fail("root signature is not trusted")

        val payload = try {
            AppContentJson.decodeFromString<RootPayload>(payloadBytes.toString(Charsets.UTF_8))
        } catch (error: SerializationException) {
            throw ContentIntegrityException("malformed signed root payload", error)
        }
        validatePayload(payload)
        return payload
    }

    private fun decodeEnvelope(bytes: ByteArray): SignedEnvelope = try {
        AppContentJson.decodeFromString<SignedEnvelope>(bytes.toString(Charsets.UTF_8))
    } catch (error: SerializationException) {
        throw ContentIntegrityException("malformed signed root envelope", error)
    }

    private fun decodeBase64Url(value: String, label: String): ByteArray {
        if (!Base64Url.matches(value) || value.length % 4 == 1) fail("malformed $label encoding")
        val decoded = try {
            Base64.getUrlDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw ContentIntegrityException("malformed $label encoding", error)
        }
        if (Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) != value) {
            fail("malformed $label encoding")
        }
        return decoded
    }

    private fun validatePayload(payload: RootPayload) {
        if (payload.schema != APP_CONTENT_SCHEMA || payload.minAppSchema != APP_CONTENT_SCHEMA) {
            throw UnsupportedContentSchemaException(payload.schema, payload.minAppSchema)
        }
        if (payload.generationId.isBlank()) fail("signed root has an empty generation ID")
        validateRootReference(payload.catalog, "catalog")
        validateRootReference(payload.tombstones, "tombstones")
    }

    private fun validateRootReference(reference: ArtifactReference, kind: String) {
        if (!ContentDigests.isSha256(reference.hash) || reference.size < 0) {
            fail("malformed signed root $kind reference")
        }
        if (reference.path != "$kind/${reference.hash}.json") {
            fail("signed root $kind path is not content-addressed")
        }
    }
}

internal fun fail(message: String): Nothing = throw ContentIntegrityException(message)
