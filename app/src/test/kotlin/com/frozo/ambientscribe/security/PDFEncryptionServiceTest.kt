package com.frozo.ambientscribe.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for PDFEncryptionService - ST-4.4 implementation
 * Tests PDF encryption with Android Keystore
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PDFEncryptionServiceTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPrefs: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var encryptionService: PDFEncryptionService
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock shared preferences
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPrefs)
        whenever(mockSharedPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Create test directory
        testDir = createTempDir("encryption_test")
        
        encryptionService = PDFEncryptionService(mockContext)
    }
    
    @Test
    fun testPdfEncryption() = runTest {
        // Create test file
        val testData = "Test PDF content".toByteArray()
        val inputFile = File(testDir, "test.pdf").apply {
            writeBytes(testData)
        }
        
        // Encrypt file
        val encryptedPath = encryptionService.encryptPdf(
            inputPath = inputFile.absolutePath,
            encounterId = "test-encounter-123"
        )
        
        // Verify encryption success
        val encryptedFile = File(encryptedPath)
        assertTrue(encryptedFile.exists())
        assertTrue(encryptedFile.length() > 0)
        
        // Decrypt file
        val decryptedPath = encryptionService.decryptPdf(
            inputPath = encryptedPath,
            encounterId = "test-encounter-123"
        )
        
        // Verify decryption success
        val decryptedFile = File(decryptedPath)
        assertTrue(decryptedFile.exists())
        
        // Verify decrypted content matches original
        val decryptedData = decryptedFile.readBytes()
        assertTrue(testData.contentEquals(decryptedData))
    }
    
    @Test
    fun testEncryptionMetadata() = runTest {
        // Create test file
        val testData = "Test PDF content".toByteArray()
        val inputFile = File(testDir, "test.pdf").apply {
            writeBytes(testData)
        }
        
        // Encrypt file
        val encryptedPath = encryptionService.encryptPdf(
            inputPath = inputFile.absolutePath,
            encounterId = "test-encounter-123"
        )
        
        // Get encryption status
        val status = encryptionService.getEncryptionStatus("test-encounter-123")
        
        // Verify metadata
        assertTrue(status["key_id"] as String != "")
        assertTrue(status["creation_date"] as Long > 0)
        assertNotNull(status["needs_rotation"])
    }
    
    @Test
    fun testFileEncryptionStatus() = runTest {
        // Create test file
        val testData = "Test PDF content".toByteArray()
        val inputFile = File(testDir, "test.pdf").apply {
            writeBytes(testData)
        }
        
        // Encrypt file
        val encryptedPath = encryptionService.encryptPdf(
            inputPath = inputFile.absolutePath,
            encounterId = "test-encounter-123"
        )
        
        // Verify file status
        assertTrue(encryptionService.isFileEncrypted(encryptedPath))
        assertTrue(!encryptionService.isFileEncrypted(inputFile.absolutePath))
    }
    
    @Test
    fun testKeyAlias() = runTest {
        // Create test file
        val testData = "Test PDF content".toByteArray()
        val inputFile = File(testDir, "test.pdf").apply {
            writeBytes(testData)
        }
        
        // Encrypt file
        encryptionService.encryptPdf(
            inputPath = inputFile.absolutePath,
            encounterId = "test-encounter-123"
        )
        
        // Get key alias
        val keyAlias = encryptionService.getKeyAlias("test-encounter-123")
        
        // Verify key alias format
        assertNotNull(keyAlias)
        assertTrue(keyAlias.startsWith(PDFEncryptionService.KEY_ALIAS_PREFIX))
    }
    
    @Test
    fun testCreationDate() = runTest {
        // Create test file
        val testData = "Test PDF content".toByteArray()
        val inputFile = File(testDir, "test.pdf").apply {
            writeBytes(testData)
        }
        
        // Encrypt file
        val encryptedPath = encryptionService.encryptPdf(
            inputPath = inputFile.absolutePath,
            encounterId = "test-encounter-123"
        )
        
        // Get creation date
        val creationDate = encryptionService.getCreationDate(encryptedPath)
        
        // Verify creation date
        assertNotNull(creationDate)
        assertTrue(creationDate.time > 0)
    }
}