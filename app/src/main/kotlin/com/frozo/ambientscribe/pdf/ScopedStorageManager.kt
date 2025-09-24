package com.frozo.ambientscribe.pdf

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages scoped storage for PDFs and JSON files
 * Implements ST-4.11: Scoped storage implementation
 */
class ScopedStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "ScopedStorageManager"
        private const val PDF_DIR = "prescriptions"
        private const val JSON_DIR = "json"
        private const val TEMP_DIR = "temp"
    }

    /**
     * Save PDF to app-private storage
     */
    suspend fun savePDFToPrivate(sourceFile: File): Result<File> {
        return try {
            Log.d(TAG, "Saving PDF to private storage: ${sourceFile.name}")

            // Create directories if needed
            val pdfDir = File(context.filesDir, PDF_DIR).apply { mkdirs() }
            val targetFile = File(pdfDir, sourceFile.name)

            // Copy file
            sourceFile.copyTo(targetFile, overwrite = true)

            Log.i(TAG, "PDF saved to private storage: ${targetFile.absolutePath}")
            Result.success(targetFile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PDF to private storage", e)
            Result.failure(e)
        }
    }

    /**
     * Save JSON to app-private storage
     */
    suspend fun saveJSONToPrivate(sourceFile: File): Result<File> {
        return try {
            Log.d(TAG, "Saving JSON to private storage: ${sourceFile.name}")

            // Create directories if needed
            val jsonDir = File(context.filesDir, JSON_DIR).apply { mkdirs() }
            val targetFile = File(jsonDir, sourceFile.name)

            // Copy file
            sourceFile.copyTo(targetFile, overwrite = true)

            Log.i(TAG, "JSON saved to private storage: ${targetFile.absolutePath}")
            Result.success(targetFile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save JSON to private storage", e)
            Result.failure(e)
        }
    }

    /**
     * Export PDF via SAF
     */
    suspend fun exportPDFViaSAF(sourceFile: File, targetUri: Uri): Result<Unit> {
        return try {
            Log.d(TAG, "Exporting PDF via SAF: ${sourceFile.name}")

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Failed to open output stream")

            Log.i(TAG, "PDF exported via SAF: $targetUri")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF via SAF", e)
            Result.failure(e)
        }
    }

    /**
     * Create temporary file
     */
    fun createTempFile(prefix: String, suffix: String): File {
        val tempDir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File.createTempFile(prefix, suffix, tempDir)
    }

    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles() {
        try {
            val tempDir = File(context.cacheDir, TEMP_DIR)
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp files", e)
        }
    }

    /**
     * Get private PDF directory
     */
    fun getPrivatePDFDirectory(): File {
        return File(context.filesDir, PDF_DIR).apply { mkdirs() }
    }

    /**
     * Get private JSON directory
     */
    fun getPrivateJSONDirectory(): File {
        return File(context.filesDir, JSON_DIR).apply { mkdirs() }
    }

    /**
     * List private PDFs
     */
    fun listPrivatePDFs(): List<File> {
        return getPrivatePDFDirectory()
            .listFiles { file -> file.extension.lowercase() == "pdf" }
            ?.toList()
            ?: emptyList()
    }

    /**
     * List private JSONs
     */
    fun listPrivateJSONs(): List<File> {
        return getPrivateJSONDirectory()
            .listFiles { file -> file.extension.lowercase() == "json" }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Delete private file
     */
    fun deletePrivateFile(file: File): Boolean {
        return try {
            if (file.exists() && isInPrivateStorage(file)) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete private file: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * Check if file is in private storage
     */
    private fun isInPrivateStorage(file: File): Boolean {
        val privatePath = context.filesDir.absolutePath
        return file.absolutePath.startsWith(privatePath)
    }
}
