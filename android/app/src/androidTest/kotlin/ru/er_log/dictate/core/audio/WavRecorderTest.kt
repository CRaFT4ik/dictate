package ru.er_log.dictate.core.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
public class WavRecorderTest {

    @Test
    public fun wavHeaderIsCorrectAfterOneSecondRecording() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = WavRecorder(context)

        recorder.start()
        Thread.sleep(1_000)
        val wav = runBlocking { recorder.stop() }

        // Header must be exactly 44 bytes before the PCM data.
        assertTrue("WAV too short", wav.size >= 44)

        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        val riff = ByteArray(4).also { buf.get(it) }
        assertArrayEquals("RIFF", "RIFF".toByteArray(Charsets.US_ASCII), riff)

        val chunkSize = buf.int                         // total file size - 8
        assertEquals("chunkSize field", wav.size - 8, chunkSize)

        val wave = ByteArray(4).also { buf.get(it) }
        assertArrayEquals("WAVE", "WAVE".toByteArray(Charsets.US_ASCII), wave)

        // fmt sub-chunk
        val fmt = ByteArray(4).also { buf.get(it) }
        assertArrayEquals("fmt ", "fmt ".toByteArray(Charsets.US_ASCII), fmt)

        val fmtSize = buf.int
        assertEquals("fmt chunk size", 16, fmtSize)

        val audioFormat = buf.short
        assertEquals("PCM audio format", 1.toShort(), audioFormat)

        val channels = buf.short
        assertEquals("mono channels", 1.toShort(), channels)

        val sampleRate = buf.int
        assertEquals("sample rate", 16_000, sampleRate)

        val byteRate = buf.int
        assertEquals("byte rate", 16_000 * 2, byteRate)

        val blockAlign = buf.short
        assertEquals("block align", 2.toShort(), blockAlign)

        val bitsPerSample = buf.short
        assertEquals("bits per sample", 16.toShort(), bitsPerSample)

        // data sub-chunk
        val data = ByteArray(4).also { buf.get(it) }
        assertArrayEquals("data", "data".toByteArray(Charsets.US_ASCII), data)

        val dataSize = buf.int
        assertEquals("data size matches remaining bytes", wav.size - 44, dataSize)

        // Approximately 16000 samples × 2 bytes each in 1 second.
        // Allow ±5% tolerance.
        val expectedBytes = 16_000 * 2
        val tolerance = expectedBytes * 0.05
        assertTrue(
            "sample count within ±5% of 16000 (got ${dataSize / 2} samples)",
            dataSize >= expectedBytes - tolerance && dataSize <= expectedBytes + tolerance,
        )
    }
}
