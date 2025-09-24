package com.frozo.ambientscribe.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.frozo.ambientscribe.pdf.QRCodeGenerator
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frozo.ambientscribe.ai.LLMService
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class JSONQRFuzzTest {

    private lateinit var context: Context
    private lateinit var jsonEncryptionService: JSONEncryptionService
    private lateinit var qrCodeGenerator: QRCodeGenerator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        jsonEncryptionService = JSONEncryptionService(context)
        qrCodeGenerator = QRCodeGenerator()
    }

    @Test
    fun testValidJSONToQRAndBack() = runBlocking {
        // Create valid JSON
        val validJson = """
            {
                "test": "data",
                "number": 123,
                "nested": {
                    "array": [1, 2, 3],
                    "string": "nested value"
                }
            }
        """.trimIndent()

        // Encrypt JSON
        val inputFile = File(context.cacheDir, "test_input.json").apply {
            writeText(validJson)
        }
        val encryptedFile = File(context.cacheDir, "test_encrypted.json")
        
        val encryptResult = jsonEncryptionService.encryptJSON(inputFile, encryptedFile)
        assertTrue(encryptResult.isSuccess)
        val metadata = encryptResult.getOrNull()
        assertNotNull(metadata)

        // Generate QR code
        val qrBitmap = qrCodeGenerator.generateQRCode(encryptedFile.readText(), 512)
        assertNotNull(qrBitmap)

        // Decode QR code
        val decodedText = decodeQRCode(qrBitmap)
        assertNotNull(decodedText)

        // Decrypt and verify
        val decryptedFile = File(context.cacheDir, "test_decrypted.json")
        encryptedFile.writeText(decodedText)
        val decryptResult = jsonEncryptionService.decryptJSON(encryptedFile, decryptedFile, metadata)
        assertTrue(decryptResult.isSuccess)

        // Verify JSON structure
        val originalJson = JSONObject(validJson)
        val decryptedJson = JSONObject(decryptedFile.readText())
        assertEquals(originalJson.toString(), decryptedJson.toString())

        // Cleanup
        inputFile.delete()
        encryptedFile.delete()
        decryptedFile.delete()
    }

    @Test
    fun testFuzzedJSONToQR() = runBlocking {
        val testCases = generateFuzzedJSON()
        
        testCases.forEach { json ->
            try {
                // Write JSON to file
                val inputFile = File(context.cacheDir, "fuzz_input.json").apply {
                    writeText(json)
                }
                val encryptedFile = File(context.cacheDir, "fuzz_encrypted.json")

                // Encrypt
                val encryptResult = jsonEncryptionService.encryptJSON(inputFile, encryptedFile)
                assertTrue(encryptResult.isSuccess)
                val metadata = encryptResult.getOrNull()
                assertNotNull(metadata)

                // Generate QR
                val qrBitmap = qrCodeGenerator.generateQRCode(encryptedFile.readText(), 512)
                assertNotNull(qrBitmap)

                // Decode QR
                val decodedText = decodeQRCode(qrBitmap)
                assertNotNull(decodedText)

                // Decrypt and verify
                val decryptedFile = File(context.cacheDir, "fuzz_decrypted.json")
                encryptedFile.writeText(decodedText)
                val decryptResult = jsonEncryptionService.decryptJSON(encryptedFile, decryptedFile, metadata)
                assertTrue(decryptResult.isSuccess)

                // Verify JSON structure
                val originalJson = JSONObject(json)
                val decryptedJson = JSONObject(decryptedFile.readText())
                assertEquals(originalJson.toString(), decryptedJson.toString())

                // Cleanup
                inputFile.delete()
                encryptedFile.delete()
                decryptedFile.delete()
            } catch (e: Exception) {
                throw AssertionError("Failed with JSON: $json", e)
            }
        }
    }

    private fun generateFuzzedJSON(): List<String> {
        return listOf(
            // Empty object
            "{}",
            
            // Basic types
            """{"string":"value"}""",
            """{"number":123}""",
            """{"boolean":true}""",
            """{"null":null}""",
            
            // Nested objects
            """{"nested":{"key":"value"}}""",
            """{"deeply":{"nested":{"object":{"here":"value"}}}}""",
            
            // Arrays
            """{"array":[1,2,3]}""",
            """{"array":["string",123,true,null,{"nested":"object"}]}""",
            
            // Special characters
            """{"special":"!@#$%^&*()_+-=[]{}|;:'\",.<>?"}""",
            """{"unicode":"Hello 世界"}""",
            """{"emoji":"🌟🚀🎉"}""",
            
            // Long strings
            """{"long":"${"a".repeat(1000)}"}""",
            
            // Deep nesting
            generateDeeplyNestedJSON(5),
            
            // Large arrays
            """{"largeArray":[${(1..100).joinToString()}]}""",
            
            // Mixed content
            """
            {
                "string": "value",
                "number": 123.456,
                "boolean": true,
                "null": null,
                "array": [1, "two", 3.0, false],
                "nested": {
                    "object": {
                        "key": "value"
                    }
                }
            }
            """.trimIndent()
        )
    }

    private fun generateDeeplyNestedJSON(depth: Int): String {
        var json = """"value""""
        repeat(depth) {
            json = """{"nested":$json}"""
        }
        return json
    }

    private fun decodeQRCode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        return try {
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}