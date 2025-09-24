package com.frozo.ambientscribe.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.frozo.ambientscribe.ai.LLMService
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.gson.Gson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates QR codes with SHA-256 hash verification and HMAC-based integrity checks
 */
class QRCodeGenerator {

    companion object {
        private const val TAG = "QRCodeGenerator"
        private const val DEFAULT_SIZE = 512
        private const val DEFAULT_MARGIN = 4
        private const val DEFAULT_ERROR_CORRECTION = "M"
        private const val MAX_QR_DATA_SIZE = 2953 // Maximum data capacity for QR code version 40 with M error correction
    }

    private val gson = Gson()

    /**
     * Data class for QR code segments
     */
    data class QRCodeSegment(
        val bitmap: Bitmap,
        val index: Int,
        val total: Int
    )

    /**
     * Data class for verification data
     */
    data class VerificationData(
        val encounterId: String,
        val jsonHash: String,
        val hmac: String
    )

    /**
     * Data class for QR code generation result
     */
    data class QRCodeData(
        val qrCodes: List<QRCodeSegment>,
        val verificationData: VerificationData,
        val originalJson: String
    )

    /**
     * Generate QR code for encounter note with integrity checks
     */
    suspend fun generateQRCode(encounterNote: LLMService.EncounterNote, hmacKey: ByteArray): Result<QRCodeData> {
        return try {
            // Convert encounter note to JSON
            val json = gson.toJson(encounterNote)
            
            // Generate SHA-256 hash of JSON
            val jsonHash = generateSHA256(json)
            
            // Generate HMAC
            val hmac = generateHMAC(json, hmacKey)
            
            // Create verification data
            val verificationData = VerificationData(
                encounterId = encounterNote.metadata.encounterId,
                jsonHash = jsonHash,
                hmac = hmac
            )
            
            // Split data into chunks if needed
            val qrCodes = if (json.length > MAX_QR_DATA_SIZE) {
                splitIntoQRCodes(json)
            } else {
                listOf(QRCodeSegment(
                    bitmap = generateQRCode(json),
                    index = 0,
                    total = 1
                ))
            }
            
            Result.success(QRCodeData(
                qrCodes = qrCodes,
                verificationData = verificationData,
                originalJson = json
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verify QR code data integrity
     */
    suspend fun verifyQRCode(qrData: QRCodeData, hmacKey: ByteArray): Result<Boolean> {
        return try {
            // Verify JSON hash
            val actualHash = generateSHA256(qrData.originalJson)
            if (actualHash != qrData.verificationData.jsonHash) {
                return Result.success(false)
            }
            
            // Verify HMAC
            val actualHmac = generateHMAC(qrData.originalJson, hmacKey)
            if (actualHmac != qrData.verificationData.hmac) {
                return Result.success(false)
            }
            
            Result.success(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying QR code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate QR code bitmap
     */
    fun generateQRCode(content: String, size: Int = DEFAULT_SIZE): Bitmap {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to DEFAULT_MARGIN,
                EncodeHintType.ERROR_CORRECTION to DEFAULT_ERROR_CORRECTION,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )

            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            return createBitmap(bitMatrix)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code: ${e.message}", e)
            throw e
        }
    }

    /**
     * Generate SHA-256 hash
     */
    private fun generateSHA256(content: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating SHA-256 hash: ${e.message}", e)
            throw e
        }
    }

    /**
     * Generate HMAC for data integrity
     */
    private fun generateHMAC(content: String, key: ByteArray): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key, "HmacSHA256")
            mac.init(secretKey)
            val hmac = mac.doFinal(content.toByteArray())
            hmac.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating HMAC: ${e.message}", e)
            throw e
        }
    }

    /**
     * Split data into multiple QR codes
     */
    private fun splitIntoQRCodes(data: String): List<QRCodeSegment> {
        val chunks = data.chunked(MAX_QR_DATA_SIZE)
        return chunks.mapIndexed { index, chunk ->
            QRCodeSegment(
                bitmap = generateQRCode(chunk),
                index = index,
                total = chunks.size
            )
        }
    }

    private fun createBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }
}