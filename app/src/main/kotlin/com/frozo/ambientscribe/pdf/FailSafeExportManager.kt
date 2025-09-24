package com.frozo.ambientscribe.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.frozo.ambientscribe.security.PDFEncryptionService
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Manages fail-safe export with QR verification
 * Implements ST-4.19: Fail-safe export
 */
class FailSafeExportManager(private val context: Context) {

    companion object {
        private const val TAG = "FailSafeExportManager"
        private const val PREFS_NAME = "export_prefs"
        private const val KEY_LAST_VERIFY = "last_verify"
        private const val VERIFY_CACHE_DURATION = 5 * 60 * 1000L // 5 minutes
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Export verification result
     */
    sealed class VerificationResult {
        object Success : VerificationResult()
        data class Failure(val reason: String) : VerificationResult()
    }

    /**
     * Verify PDF before export
     */
    suspend fun verifyForExport(
        pdfFile: File,
        encryptionMetadata: PDFEncryptionService.EncryptionMetadata
    ): VerificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying PDF for export: ${pdfFile.name}")

            // Check encryption key
            if (!verifyEncryptionKey(encryptionMetadata)) {
                return@withContext VerificationResult.Failure(
                    "Encryption key not found or invalid"
                )
            }

            // Verify QR code
            val qrResult = verifyQRCode(pdfFile, encryptionMetadata)
            if (qrResult !is VerificationResult.Success) {
                return@withContext qrResult
            }

            // Cache verification result
            cacheVerificationResult(pdfFile)

            Log.i(TAG, "PDF verified successfully")
            VerificationResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify PDF", e)
            VerificationResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Check if export is allowed
     */
    suspend fun isExportAllowed(
        pdfFile: File,
        encryptionMetadata: PDFEncryptionService.EncryptionMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check cached verification
            if (hasValidVerificationCache(pdfFile)) {
                return@withContext true
            }

            // Perform full verification
            val result = verifyForExport(pdfFile, encryptionMetadata)
            return@withContext result is VerificationResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check export permission", e)
            false
        }
    }

    /**
     * Export PDF with verification
     */
    suspend fun exportWithVerification(
        pdfFile: File,
        encryptionMetadata: PDFEncryptionService.EncryptionMetadata,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exporting PDF with verification")

            // Verify export permission
            if (!isExportAllowed(pdfFile, encryptionMetadata)) {
                return@withContext Result.failure(
                    SecurityException("Export verification failed")
                )
            }

            // Decrypt PDF for export
            val encryptionService = PDFEncryptionService(context)
            val tempFile = createTempFile()

            try {
                // Decrypt to temp file
                val decryptedPath = encryptionService.decryptPdf(
                    inputPath = pdfFile.absolutePath,
                    encounterId = encryptionMetadata.id
                )
                File(decryptedPath).copyTo(tempFile, overwrite = true)

                // Copy to target
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                Log.i(TAG, "PDF exported successfully")
                Result.success(Unit)

            } finally {
                tempFile.delete()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF", e)
            Result.failure(e)
        }
    }

    /**
     * Verify encryption key
     */
    private fun verifyEncryptionKey(
        metadata: PDFEncryptionService.EncryptionMetadata
    ): Boolean {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val keyAlias = "${PDFEncryptionService.KEY_ALIAS_PREFIX}${metadata.id}"
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Verify QR code
     */
    private suspend fun verifyQRCode(
        pdfFile: File,
        metadata: PDFEncryptionService.EncryptionMetadata
    ): VerificationResult = withContext(Dispatchers.IO) {
        try {
            // Create temp file for decryption
            val tempFile = createTempFile()

            try {
                // Decrypt PDF
                val encryptionService = PDFEncryptionService(context)
                val decryptedPath = encryptionService.decryptPdf(
                    inputPath = pdfFile.absolutePath,
                    encounterId = metadata.id
                )
                File(decryptedPath).copyTo(tempFile, overwrite = true)

                // Render first page
                val renderer = android.graphics.pdf.PdfRenderer(
                    android.os.ParcelFileDescriptor.open(
                        tempFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )
                )

                try {
                    val page = renderer.openPage(0)
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        page.width,
                        page.height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    page.render(
                        bitmap,
                        null,
                        null,
                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    page.close()

                    // Scan QR code
                    val qrContent = scanQRCode(bitmap)
                        ?: return@withContext VerificationResult.Failure(
                            "QR code not found"
                        )

                    // Verify hash
                    if (!verifyQRHash(qrContent, tempFile)) {
                        return@withContext VerificationResult.Failure(
                            "QR hash verification failed"
                        )
                    }

                    VerificationResult.Success

                } finally {
                    renderer.close()
                }

            } finally {
                tempFile.delete()
            }

        } catch (e: Exception) {
            Log.e(TAG, "QR verification failed", e)
            VerificationResult.Failure(e.message ?: "QR verification failed")
        }
    }

    /**
     * Scan QR code from bitmap
     */
    private fun scanQRCode(bitmap: android.graphics.Bitmap): String? {
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

    /**
     * Verify QR hash
     */
    private fun verifyQRHash(qrContent: String, pdfFile: File): Boolean {
        // Extract hash from QR
        val hashRegex = "sha256:([a-fA-F0-9]{64})".toRegex()
        val qrHash = hashRegex.find(qrContent)?.groupValues?.get(1)
            ?: return false

        // Calculate file hash
        val digest = MessageDigest.getInstance("SHA-256")
        pdfFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        val fileHash = digest.digest().joinToString("") { 
            "%02x".format(it) 
        }

        return qrHash == fileHash
    }

    /**
     * Cache verification result
     */
    private fun cacheVerificationResult(pdfFile: File) {
        encryptedPrefs.edit()
            .putLong(
                "${pdfFile.absolutePath}_${KEY_LAST_VERIFY}",
                System.currentTimeMillis()
            )
            .apply()
    }

    /**
     * Check verification cache
     */
    private fun hasValidVerificationCache(pdfFile: File): Boolean {
        val lastVerify = encryptedPrefs.getLong(
            "${pdfFile.absolutePath}_${KEY_LAST_VERIFY}",
            0
        )
        return System.currentTimeMillis() - lastVerify <= VERIFY_CACHE_DURATION
    }

    /**
     * Create temporary file
     */
    private fun createTempFile(): File {
        val tempDir = File(context.cacheDir, "verify_temp").apply { mkdirs() }
        return File.createTempFile("verify_", ".pdf", tempDir)
    }

    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles() {
        try {
            val tempDir = File(context.cacheDir, "verify_temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp files", e)
        }
    }
}
