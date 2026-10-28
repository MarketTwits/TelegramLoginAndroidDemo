package com.markettwits.devx.tgsignin.ui.component.tgs

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class TgsDecoderTest {

    @Before
    @After
    fun cleanUp() {
        TgsDecoder.clearCache()
    }

    @Test
    fun decodeTgsFileReturnsValidLottieJson() = runBlocking {
        val sampleFile = File("../backend/public/assets/profile-emojis/v3/0007fab99d5217100822fd5ee572c5faec3d9cc7e0b54d67cc3bb9449f5000cd.tgs")
            .takeIf { it.isFile }
            ?: File("backend/public/assets/profile-emojis/v3/0007fab99d5217100822fd5ee572c5faec3d9cc7e0b54d67cc3bb9449f5000cd.tgs")
        assertTrue("Sample TGS file should exist in repo", sampleFile.isFile)

        val json = TgsDecoder.decodeTgsToJson(sampleFile)
        assertNotNull("Decoded JSON should not be null", json)

        // Parse with JSONObject to verify valid JSON
        val parsed = JSONObject(json!!)
        assertTrue("Decoded TGS should contain Lottie version", parsed.has("v"))
        assertTrue("Decoded TGS should contain layers or assets", parsed.has("layers") || parsed.has("assets"))

        // Verify it was cached in memory
        val cached = TgsDecoder.getJsonFromCache(sampleFile.absolutePath)
        assertEquals("Cached JSON must match decoded output", json, cached)
    }

    @Test
    fun decodeTgsBytesReturnsValidJson() = runBlocking {
        val testJson = """{"v":"5.5.2","fr":60,"ip":0,"op":60,"w":512,"h":512,"layers":[]}"""
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(testJson.toByteArray(Charsets.UTF_8))
        }
        val gzippedBytes = bos.toByteArray()

        val decoded = TgsDecoder.decodeTgsBytesToJson(gzippedBytes, cacheKey = "test_bytes")
        assertEquals(testJson, decoded)

        val cached = TgsDecoder.getJsonFromCache("test_bytes")
        assertEquals(testJson, cached)
    }

    @Test
    fun decodeCorruptedBytesReturnsNullWithoutCrashing() = runBlocking {
        val corruptedBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val decoded = TgsDecoder.decodeTgsBytesToJson(corruptedBytes, cacheKey = "corrupt")
        assertNull("Corrupted gzip should return null", decoded)
    }

    @Test
    fun nonExistentFileReturnsNull() = runBlocking {
        val nonExistent = File("does_not_exist.tgs")
        val decoded = TgsDecoder.decodeTgsToJson(nonExistent)
        assertNull("Non-existent file should return null", decoded)
    }
}
