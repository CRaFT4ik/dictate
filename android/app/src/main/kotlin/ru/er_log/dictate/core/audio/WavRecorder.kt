package ru.er_log.dictate.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Records audio via [AudioRecord] at 16 kHz mono PCM s16le and produces a
 * complete WAV [ByteArray] (44-byte standard header + samples) on [stop].
 *
 * One instance can be reused: after [stop] returns, the recorder is back in
 * the initial state and [start] can be called again.
 */
public class WavRecorder(private val context: Context) {

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val READ_BUFFER_SIZE = 4096
    }

    @Volatile private var recording = false
    @Volatile private var recordingThread: Thread? = null
    private val pcmBuffer = ByteArrayOutputStream()

    /**
     * Starts recording. Must be called from any thread.
     *
     * @throws IllegalStateException if already recording or [RECORD_AUDIO] permission is missing.
     */
    public fun start() {
        check(!recording) { "Already recording" }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        ) { "RECORD_AUDIO permission not granted" }

        pcmBuffer.reset()
        recording = true

        recordingThread = thread(name = "WavRecorder", isDaemon = true) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufSize = maxOf(minBuf, READ_BUFFER_SIZE)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufSize,
            )
            try {
                audioRecord.startRecording()
                val chunk = ByteArray(READ_BUFFER_SIZE)
                while (recording) {
                    val read = audioRecord.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        synchronized(pcmBuffer) { pcmBuffer.write(chunk, 0, read) }
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    /**
     * Stops recording and returns a complete WAV [ByteArray].
     * Suspends until the recording thread has finished draining the microphone buffer,
     * so the caller always gets the full capture.
     */
    public suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
        recording = false
        recordingThread?.join()
        recordingThread = null
        val pcmBytes = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        buildWav(pcmBytes)
    }

    // WAV spec: RIFF/WAVE container, fmt chunk (PCM = AudioFormat 1), data chunk.
    // All multi-byte values are little-endian.
    private fun buildWav(pcm: ByteArray): ByteArray {
        val dataSize = pcm.size
        val totalSize = 36 + dataSize   // everything after the "RIFF" + size field itself

        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)              // PCM sub-chunk size
        buf.putShort(1)             // AudioFormat: PCM
        buf.putShort(1)             // NumChannels: mono
        buf.putInt(SAMPLE_RATE)     // SampleRate: 16000
        buf.putInt(SAMPLE_RATE * 2) // ByteRate: sampleRate * numChannels * bitsPerSample/8
        buf.putShort(2)             // BlockAlign: numChannels * bitsPerSample/8
        buf.putShort(16)            // BitsPerSample: 16

        // data sub-chunk
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        buf.put(pcm)

        return buf.array()
    }
}
