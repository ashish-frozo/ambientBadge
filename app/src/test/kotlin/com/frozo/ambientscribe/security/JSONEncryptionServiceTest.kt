package com.frozo.ambientscribe.security

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class JSONEncryptionServiceTest {

    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var jsonEncryptionService: JSONEncryptionService
    private lateinit var testInputFile: File
    private lateinit var testOutputFile: File
    private lateinit var testDecryptedFile: File

    @Before
    fun setup() {
        // Mock context
        whenever(mockContext.cacheDir).thenReturn(File("/tmp/test"))
        
        jsonEncryptionService = JSONEncryptionService(mockContext)

        // Create test files
        testInputFile = File(mockContext.cacheDir, "test_input.json")
        testOutputFile = File(mockContext.cacheDir, "test_encrypted.json")
        testDecryptedFile = File(mockContext.cacheDir, "test_decrypted.json")

        // Write test data
        testInputFile.writeText("""
            {
                "test": "data",
                "number": 123,
                "nested": {
                    "array": [1, 2, 3],
                    "string": "nested value"
                }
            }
        """.trimIndent())
    }

    @Test
    fun testEncryptionAndDecryption() = runTest {
        // Encrypt
        val encryptResult = jsonEncryptionService.encryptJSON(testInputFile, testOutputFile)
        assertTrue(encryptResult.isSuccess)
        val metadata = encryptResult.getOrNull()
        assertNotNull(metadata)
        assertTrue(testOutputFile.exists())
        assertTrue(testOutputFile.length() > 0)

        // Verify encrypted file is different from input
        assertTrue(testInputFile.readText() != testOutputFile.readText())

        // Decrypt
        val decryptResult = jsonEncryptionService.decryptJSON(testOutputFile, testDecryptedFile, metadata)
        assertTrue(decryptResult.isSuccess)
        assertTrue(testDecryptedFile.exists())

        // Verify decrypted content matches original
        assertEquals(testInputFile.readText(), testDecryptedFile.readText())
    }

    @Test
    fun testEncryptionWithEmptyFile() = runTest {
        // Create empty file
        testInputFile.writeText("")

        // Encrypt
        val encryptResult = jsonEncryptionService.encryptJSON(testInputFile, testOutputFile)
        assertTrue(encryptResult.isSuccess)
        val metadata = encryptResult.getOrNull()
        assertNotNull(metadata)

        // Decrypt
        val decryptResult = jsonEncryptionService.decryptJSON(testOutputFile, testDecryptedFile, metadata)
        assertTrue(decryptResult.isSuccess)

        // Verify decrypted content is empty
        assertEquals("", testDecryptedFile.readText())
    }

    @Test
    fun testEncryptionWithLargeFile() = runTest {
        // Create large file (1MB)
        val largeContent = StringBuilder()
        repeat(1024 * 1024) { // 1MB of data
            largeContent.append('a')
        }
        testInputFile.writeText(largeContent.toString())

        // Encrypt
        val encryptResult = jsonEncryptionService.encryptJSON(testInputFile, testOutputFile)
        assertTrue(encryptResult.isSuccess)
        val metadata = encryptResult.getOrNull()
        assertNotNull(metadata)

        // Decrypt
        val decryptResult = jsonEncryptionService.decryptJSON(testOutputFile, testDecryptedFile, metadata)
        assertTrue(decryptResult.isSuccess)

        // Verify decrypted content matches original
        assertEquals(testInputFile.readText(), testDecryptedFile.readText())
    }

    @Test
    fun testInvalidDecryption() = runTest {
        // Encrypt
        val encryptResult = jsonEncryptionService.encryptJSON(testInputFile, testOutputFile)
        assertTrue(encryptResult.isSuccess)
        val metadata = encryptResult.getOrNull()
        assertNotNull(metadata)

        // Modify encrypted file
        val modifiedContent = testOutputFile.readBytes()
        modifiedContent[modifiedContent.size - 1] = modifiedContent[modifiedContent.size - 1].inc()
        testOutputFile.writeBytes(modifiedContent)

        // Attempt to decrypt
        val decryptResult = jsonEncryptionService.decryptJSON(testOutputFile, testDecryptedFile, metadata)
        assertTrue(decryptResult.isFailure)
    }
}