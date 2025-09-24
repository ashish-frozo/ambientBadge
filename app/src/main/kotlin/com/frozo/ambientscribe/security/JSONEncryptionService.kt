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
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles JSON encryption with streaming and key rotation
 */
class JSONEncryptionService(private val context: Context) {

    companion object {
        private const val TAG = "JSONEncryptionService"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "json_encryption_key"
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

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "json_encryption_prefs",
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
        val creationDate: Date
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptionMetadata

            if (keyId != other.keyId) return false
            if (!iv.contentEquals(other.iv)) return false
            if (creationDate != other.creationDate) return false

            return true
        }

        override fun hashCode(): Int {
            var result = keyId.hashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + creationDate.hashCode()
            return result
        }
    }

    /**
     * Encrypt JSON string
     */
    suspend fun encryptJson(json: Any): String = withContext(Dispatchers.IO) {
        val gson = com.google.gson.Gson()
        val jsonString = gson.toJson(json)
        val tempInput = File.createTempFile("json_", ".tmp", context.cacheDir)
        val tempOutput = File.createTempFile("encrypted_", ".tmp", context.cacheDir)
        
        try {
            // Write JSON to temp file
            tempInput.writeText(jsonString)
            
            // Encrypt the file
            val result = encryptJSON(tempInput, tempOutput)
            if (result.isSuccess) {
                // Read encrypted data
                val encryptedData = tempOutput.readBytes()
                // Convert to Base64
                return@withContext android.util.Base64.encodeToString(encryptedData, android.util.Base64.NO_WRAP)
            } else {
                throw result.exceptionOrNull() ?: Exception("Encryption failed")
            }
        } finally {
            tempInput.delete()
            tempOutput.delete()
        }
    }

    /**
     * Encrypt JSON file
     */
    suspend fun encryptJSON(
        inputFile: File,
        outputFile: File
    ): Result<EncryptionMetadata> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Encrypting JSON file")

            // Get or generate key
            val key = getOrGenerateKey()

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

            // Create metadata
            val metadata = EncryptionMetadata(
                keyId = KEY_ALIAS,
                iv = iv,
                creationDate = Date()
            )

            Result.success(metadata)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt JSON", e)
            Result.failure(e)
        }
    }

    /**
     * Decrypt JSON file
     */
    suspend fun decryptJSON(
        inputFile: File,
        outputFile: File,
        metadata: EncryptionMetadata
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Decrypting JSON file")

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

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt JSON", e)
            Result.failure(e)
        }
    }

    private fun getOrGenerateKey(): SecretKey {
        // Check if key exists
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            // Check if key needs rotation
            val keyCreationDate = sharedPreferences.getLong("key_creation_date", 0)
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
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)
        val newKey = keyGenerator.generateKey()

        // Store key creation date
        sharedPreferences.edit()
            .putLong("key_creation_date", System.currentTimeMillis())
            .apply()

        return newKey
    }
}