package com.example.couplefarm.network

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random

class OnlineMqttClientTest {

    @Test
    fun testBase64EncodingAndDecoding() {
        val testStrings = listOf(
            "",
            "f",
            "fo",
            "foo",
            "foob",
            "fooba",
            "foobar",
            "¡Hola desde Couple Farm!",
            "{\"type\":\"HELLO\",\"farmerName\":\"Angel & Caro\"}"
        )

        for (str in testStrings) {
            val bytes = str.toByteArray(Charsets.UTF_8)
            val encoded = Base64Util.encode(bytes)
            val decoded = Base64Util.decode(encoded)
            assertArrayEquals("Mismatch for string: '$str'", bytes, decoded)
        }

        // Test random binary data
        val random = Random(42)
        for (size in listOf(1, 2, 3, 4, 15, 64, 1024, 8192)) {
            val bytes = ByteArray(size)
            random.nextBytes(bytes)
            val encoded = Base64Util.encode(bytes)
            val decoded = Base64Util.decode(encoded)
            assertArrayEquals("Mismatch for binary size $size", bytes, decoded)
        }
    }

    @Test
    fun testGzipPayloadCompressor() {
        val largeJson = buildString {
            append("{\"crops\":[")
            for (i in 0 until 50) {
                if (i > 0) append(",")
                append("{\"id\":\"crop_$i\",\"type\":\"WHEAT\",\"stage\":3,\"watered\":true,\"x\":${i * 1.5},\"y\":${i * 2.0}}")
            }
            append("]}")
        }

        val rawBytes = largeJson.toByteArray(Charsets.UTF_8)
        val compressedBase64 = PayloadCompressor.compress(rawBytes)

        // Verify compression achieves significant reduction (> 60% for repetitive json)
        assertTrue(
            "Expected compression to be smaller: raw ${rawBytes.size} vs compressed ${compressedBase64.length}",
            compressedBase64.length < rawBytes.size * 0.4
        )

        // Decompress and verify content
        val decompressedBytes = PayloadCompressor.decompress(compressedBase64)
        assertEquals(largeJson, String(decompressedBytes, Charsets.UTF_8))

        // Also test plain json passthrough
        val plainDecompressed = PayloadCompressor.decompress(largeJson)
        assertEquals(largeJson, String(plainDecompressed, Charsets.UTF_8))
    }

    @Test
    fun testNormalizeRoomCode() {
        assertEquals("7492", normalizeRoomCode("7492"))
        assertEquals("8214", normalizeRoomCode("  8214  "))
        assertEquals("1234", normalizeRoomCode("ROOM-1234-ONLINE"))
        assertEquals("9999", normalizeRoomCode("9999123"))
    }
}
