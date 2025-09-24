package com.frozo.ambientscribe.pdf

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.frozo.ambientscribe.security.PDFEncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF export manager for ST-4.5
 * Handles file path management and export workflows
 */
class PDFExportManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PDFExportManager"
        private const val AUTHORITY = "com.frozo.ambientscribe.fileprovider"
        private const val EXPORT_DIR = "prescriptions"
        private const val TEMP_DIR = "temp"
        private const val MIME_TYPE = "application/pdf"
    }
    
    /**
     * Export PDF to external storage or share
     */
    suspend fun exportPDF(
        pdfResult: PDFResult,
        exportType: ExportType,
        targetUri: Uri? = null
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exporting PDF: ${pdfResult.filePath}, type: $exportType")
            
            // Get source file
            val sourceFile = File(pdfResult.filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Source file not found: ${pdfResult.filePath}")
                )
            }
            
            // Create export result based on type
            val result = when (exportType) {
                ExportType.SAVE_EXTERNAL -> saveToExternal(sourceFile, pdfResult.encryptionMetadata)
                ExportType.SHARE -> shareFile(sourceFile)
                ExportType.PRINT -> printFile(sourceFile, pdfResult.encryptionMetadata)
                ExportType.SAVE_CUSTOM -> {
                    if (targetUri == null) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Target URI required for SAVE_CUSTOM")
                        )
                    }
                    saveToUri(sourceFile, targetUri)
                }
            }
            
            // Log export event
            logExportEvent(pdfResult, exportType, result)
            
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save PDF to external storage
     */
    private suspend fun saveToExternal(
        sourceFile: File,
        encryptionMetadata: PDFEncryptionService.EncryptionMetadata
    ): ExportResult {
        // Create export directory
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            EXPORT_DIR
        ).apply { mkdirs() }
        
        // Create target file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val targetFile = File(exportDir, "prescription_${timestamp}.pdf")
        
        // Decrypt and copy file
        val encryptionService = PDFEncryptionService(context)
        val tempFile = createTempFile()
        
        try {
            // Decrypt to temp file
            val decryptedPath = encryptionService.decryptPdf(
                inputPath = sourceFile.absolutePath,
                encounterId = encryptionMetadata.id
            )
            File(decryptedPath).copyTo(tempFile, overwrite = true)
            
            // Copy to target
            tempFile.copyTo(targetFile, overwrite = true)
            
            return ExportResult(
                uri = Uri.fromFile(targetFile),
                type = ExportType.SAVE_EXTERNAL,
                path = targetFile.absolutePath
            )
            
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Share PDF via content provider
     */
    private fun shareFile(sourceFile: File): ExportResult {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, sourceFile)
        
        return ExportResult(
            uri = uri,
            type = ExportType.SHARE,
            path = sourceFile.absolutePath
        )
    }
    
    /**
     * Print PDF using system print service
     */
    private suspend fun printFile(
        sourceFile: File,
        encryptionMetadata: PDFEncryptionService.EncryptionMetadata
    ): ExportResult {
        // Create temp file for printing
        val tempFile = createTempFile()
        
        try {
            // Decrypt for printing
            val encryptionService = PDFEncryptionService(context)
            val decryptedPath = encryptionService.decryptPdf(
                inputPath = sourceFile.absolutePath,
                encounterId = encryptionMetadata.id
            )
            File(decryptedPath).copyTo(tempFile, overwrite = true)
            
            // Create print job
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            
            val jobName = "Prescription_${System.currentTimeMillis()}"
            val printAdapter = PDFPrintAdapter(context, Uri.fromFile(tempFile), jobName)
            
            // Configure print attributes
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            
            // Start print job
            printManager.print(jobName, printAdapter, attributes)
            
            return ExportResult(
                uri = Uri.fromFile(tempFile),
                type = ExportType.PRINT,
                path = tempFile.absolutePath
            )
            
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
    
    /**
     * Save PDF to custom URI (e.g., SAF)
     */
    private suspend fun saveToUri(sourceFile: File, targetUri: Uri): ExportResult {
        // Open output stream to URI
        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        return ExportResult(
            uri = targetUri,
            type = ExportType.SAVE_CUSTOM,
            path = DocumentFile.fromSingleUri(context, targetUri)?.uri?.path
        )
    }
    
    /**
     * Create temporary file
     */
    private fun createTempFile(): File {
        val tempDir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File.createTempFile("temp_", ".pdf", tempDir)
    }
    
    /**
     * Log export event
     */
    private fun logExportEvent(
        pdfResult: PDFResult,
        exportType: ExportType,
        result: ExportResult
    ) {
        Log.i(TAG, "PDF exported successfully: type=$exportType, path=${result.path}")
        // TODO: Add audit logging in ST-5.1
    }
    
    /**
     * Export types
     */
    enum class ExportType {
        SAVE_EXTERNAL,  // Save to external storage
        SHARE,         // Share via intent
        PRINT,         // Print via system service
        SAVE_CUSTOM    // Save to custom URI (e.g., SAF)
    }
    
    /**
     * Export result
     */
    data class ExportResult(
        val uri: Uri,
        val type: ExportType,
        val path: String?
    )

    /**
     * Get export directory
     */
    fun getExportDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            EXPORT_DIR
        ).apply { mkdirs() }
    }

    /**
     * Get temp directory
     */
    fun getTempDirectory(): File {
        return File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
    }

    /**
     * Clean up temp files
     */
    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        try {
            val tempDir = getTempDirectory()
            tempDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("temp_")) {
                    file.delete()
                }
            }
            Log.d(TAG, "Temp files cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }

    /**
     * Get export history
     */
    suspend fun getExportHistory(): List<ExportResult> = withContext(Dispatchers.IO) {
        try {
            val exportDir = getExportDirectory()
            exportDir.listFiles()?.filter { file ->
                file.isFile && file.name.startsWith("prescription_") && file.extension == "pdf"
            }?.map { file ->
                ExportResult(
                    uri = Uri.fromFile(file),
                    type = ExportType.SAVE_EXTERNAL,
                    path = file.absolutePath
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting export history", e)
            emptyList()
        }
    }
}
