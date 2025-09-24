package com.frozo.ambientscribe.pdf

import com.frozo.ambientscribe.security.PDFEncryptionService

/**
 * Result data class for PDF generation
 * Contains file path and encryption metadata
 */
data class PDFResult(
    val filePath: String,
    val encryptionMetadata: PDFEncryptionService.EncryptionMetadata
)
