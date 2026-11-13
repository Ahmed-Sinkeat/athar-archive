package com.atharchive.feature.tasmee

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The on-device recitation model (wav2vec2 CTC, Quran-tuned, int8 ONNX, ~320 MB): 16 kHz audio in,
 * one row of letter log-probs per 20 ms out. Holds ~400 MB while open, so the تسميع screen opens
 * it on entry and [close]s it on leaving.
 */
class TasmeeEngine private constructor(private val session: OrtSession, val vocab: TasmeeVocab, private val dir: File) : AutoCloseable {
    /** Log-probs for [audio]; each call normalizes its own window, as the model was trained. */
    fun logProbs(audio: FloatArray): List<FloatArray> {
        val mean = audio.average().toFloat()
        val sd = sqrt(audio.fold(0.0) { a, x -> a + (x - mean) * (x - mean) } / audio.size + 1e-7).toFloat()
        val x = FloatArray(audio.size) { (audio[it] - mean) / sd }
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(x), longArrayOf(1, x.size.toLong())).use { input ->
            session.run(mapOf("audio" to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                return (out[0].value as Array<Array<FloatArray>>)[0].toList()
            }
        }
    }

    /**
     * Records until cancelled and hands [onFrames] the log-probs of each 2 s of speech. Each run
     * also sees the second before it so words at the cut aren't clipped; only the new frames go on.
     * The mic is read on its own so a slow model run never drops audio.
     */
    @SuppressLint("MissingPermission") // the screen asks for RECORD_AUDIO before listening
    suspend fun listen(onFrames: suspend (List<FloatArray>) -> Unit) = coroutineScope {
        // unbounded queue — on a phone slower than realtime it grows ~64 KB/s behind; drop or shorten hops if seen
        val hops = Channel<FloatArray>(Channel.UNLIMITED)
        val rate = 16_000
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT), rate * 4))
        // tuning aid, keeps the last listening's audio (16 kHz float) to check on the PC; drop with the log
        val dump = java.io.File(dir, "last.pcm").outputStream().buffered()
        try {
            record.startRecording()
            launch(kotlinx.coroutines.Dispatchers.IO) {
                while (true) {
                    val hop = FloatArray(Hop)
                    var n = 0
                    while (n < Hop) { ensureActive(); val r = record.read(hop, n, Hop - n, AudioRecord.READ_BLOCKING); if (r < 0) return@launch; n += r }
                    hops.send(hop)
                }
            }
            var context = FloatArray(0)
            for (hop in hops) {
                val began = System.nanoTime()
                val window = context + hop
                dump.write(java.nio.ByteBuffer.allocate(hop.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(hop) }.array())
                val frames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { logProbs(window) }
                android.util.Log.i("Tasmee", "2 s of audio took ${(System.nanoTime() - began) / 1_000_000} ms, ${hops.isEmpty.not()} waiting")
                onFrames(frames.drop(context.size / 320))
                context = hop.copyOfRange(Hop - Overlap, Hop)
            }
        } finally {
            dump.close()
            hops.close()
            record.stop()
            record.release()
        }
    }

    override fun close() = session.close()

    companion object {
        private const val Hop = 32_000
        private const val Overlap = 16_000

        /** Where the model lives; for now it is copied there by hand (adb push), later downloaded. */
        fun dir(context: Context) = File(context.getExternalFilesDir(null), "tasmee")

        /** Null when the model isn't on the device. Slow (seconds): call off the main thread. */
        fun open(context: Context): TasmeeEngine? {
            val dir = dir(context)
            val model = File(dir, "tasmee.onnx")
            val vocab = File(dir, "tasmee-vocab.json")
            if (!model.exists() || !vocab.exists()) return null
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(6) }
            return TasmeeEngine(OrtEnvironment.getEnvironment().createSession(model.path, options), loadVocab(vocab.readText()), dir)
        }
    }
}
