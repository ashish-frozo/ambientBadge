package com.frozo.ambientscribe.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles PDF encryption using Android Keystore
 */
class PDFEncryptionService(private val context: Context) {

    companion object {
        private const val TAG = "PDFEncryptionService"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "pdf_encryption_key_"
        private const val KEY_ROTATION_INTERVAL = 180L * 24 * 60 * 60 * 1000 // 180 days
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val BUFFER_SIZE = 8192
    }

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "pdf_encryption_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Encryption metadata
     */
    data class EncryptionMetadata(
        val keyId: String,
        val iv: ByteArray,
        val creationDate: Date,
        val id: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptionMetadata

            if (keyId != other.keyId) return false
            if (!iv.contentEquals(other.iv)) return false
            if (creationDate != other.creationDate) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            var result = keyId.hashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + creationDate.hashCode()
            result = 31 * result + id.hashCode()
            return result
        }
    }

    /**
     * Encrypt PDF file
     */
    suspend fun encryptPdf(
        inputPath: String,
        encounterId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val inputFile = File(inputPath)
            val outputFile = File(inputFile.parent, "${inputFile.nameWithoutExtension}_encrypted.pdf")

            // Get or generate key
            val keyAlias = "${KEY_ALIAS_PREFIX}${encounterId}"
            val key = getOrGenerateKey(keyAlias)

            // Generate IV
            val iv = ByteArray(GCM_IV_LENGTH).apply {
                SecureRandom().nextBytes(this)
            }

            // Create cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_LENGTH, iv)
                )
            }

            // Encrypt file
            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    // Write IV
                    output.write(iv)

                    // Encrypt data
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null) {
                            output.write(encryptedBytes)
                        }
                    }

                    // Write final block
                    val finalBytes = cipher.doFinal()
                    output.write(finalBytes)
                }
            }

            // Store metadata
            val metadata = EncryptionMetadata(
                keyId = keyAlias,
                iv = iv,
                creationDate = Date(),
                id = encounterId
            )
            storeMetadata(encounterId, metadata)

            outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting PDF: ${e.message}", e)
            throw e
        }
    }

    /**
     * Decrypt PDF file
     */
    suspend fun decryptPdf(
        inputPath: String,
        encounterId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val inputFile = File(inputPath)
            val outputFile = File(inputFile.parent, "${inputFile.nameWithoutExtension}_decrypted.pdf")

            // Get metadata
            val metadata = getMetadata(encounterId)
                ?: throw IllegalStateException("No metadata found for encounter: $encounterId")

            // Get key
            val key = keyStore.getKey(metadata.keyId, null) as SecretKey

            // Create cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_LENGTH, metadata.iv)
                )
            }

            // Decrypt file
            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    // Skip IV
                    input.skip(GCM_IV_LENGTH.toLong())

                    // Decrypt data
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (decryptedBytes != null) {
                            output.write(decryptedBytes)
                        }
                    }

                    // Write final block
                    val finalBytes = cipher.doFinal()
                    output.write(finalBytes)
                }
            }

            outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting PDF: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get creation date
     */
    fun getCreationDate(filePath: String): Date? {
        val file = File(filePath)
        val encounterId = file.nameWithoutExtension.substringBefore("_encrypted")
        return getMetadata(encounterId)?.creationDate
    }

    private fun getOrGenerateKey(keyAlias: String): SecretKey {
        // Check if key exists
        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) {
            // Check if key needs rotation
            val keyCreationDate = encryptedPrefs.getLong("${keyAlias}_creation_date", 0)
            if (System.currentTimeMillis() - keyCreationDate < KEY_ROTATION_INTERVAL) {
                return existingKey
            }
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)
        val newKey = keyGenerator.generateKey()

        // Store key creation date
        encryptedPrefs.edit()
            .putLong("${keyAlias}_creation_date", System.currentTimeMillis())
            .apply()

        return newKey
    }

    private fun storeMetadata(encounterId: String, metadata: EncryptionMetadata) {
        encryptedPrefs.edit()
            .putString("${encounterId}_key_id", metadata.keyId)
            .putString("${encounterId}_iv", metadata.iv.joinToString(","))
            .putLong("${encounterId}_creation_date", metadata.creationDate.time)
            .apply()
    }

    private fun getMetadata(encounterId: String): EncryptionMetadata? {
        val keyId = encryptedPrefs.getString("${encounterId}_key_id", null) ?: return null
        val ivString = encryptedPrefs.getString("${encounterId}_iv", null) ?: return null
        val creationDate = encryptedPrefs.getLong("${encounterId}_creation_date", 0)
        if (creationDate == 0L) return null

        val iv = ivString.split(",").map { it.toByte() }.toByteArray()

        return EncryptionMetadata(
            keyId = keyId,
            iv = iv,
            creationDate = Date(creationDate),
            id = encounterId
        )
    }

    /**
     * Get key alias for encounter
     */
    fun getKeyAlias(encounterId: String): String? {
        return encryptedPrefs.getString("${encounterId}_key_id", null)
    }

    /**
     * Check if file is encrypted
     */
    fun isFileEncrypted(filePath: String): Boolean {
        val file = File(filePath)
        return file.nameWithoutExtension.endsWith("_encrypted")
    }

    /**
     * Get encryption status
     */
    fun getEncryptionStatus(encounterId: String): Map<String, Any> {
        val metadata = getMetadata(encounterId)
        return if (metadata != null) {
            mapOf(
                "key_id" to metadata.keyId,
                "creation_date" to metadata.creationDate.time,
                "needs_rotation" to needsKeyRotation(metadata.keyId)
            )
        } else {
            mapOf(
                "key_id" to "",
                "creation_date" to 0L,
                "needs_rotation" to false
            )
        }
    }

    /**
     * Check if key needs rotation
     */
    private fun needsKeyRotation(keyAlias: String): Boolean {
        val keyCreationDate = encryptedPrefs.getLong("${keyAlias}_creation_date", 0)
        return System.currentTimeMillis() - keyCreationDate >= KEY_ROTATION_INTERVAL
    }
}