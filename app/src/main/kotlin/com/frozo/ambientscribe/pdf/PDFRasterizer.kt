package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handles PDF rasterization and printing
 */
class PDFRasterizer(private val context: Context) {

    companion object {
        private const val TAG = "PDFRasterizer"
        private const val RASTER_QUALITY = 300 // DPI
    }

    /**
     * Rasterization result
     */
    data class RasterResult(
        val bitmap: Bitmap,
        val pageNumber: Int,
        val totalPages: Int
    )

    /**
     * Printer capabilities
     */
    data class PrinterCapabilities(
        val supportsColor: Boolean,
        val supportsDuplex: Boolean,
        val maxResolution: Int,
        val mediaTypes: List<String>
    )

    /**
     * Rasterize PDF page
     */
    suspend fun rasterizePage(pdfUri: Uri, pageNumber: Int): RasterResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageNumber < 0 || pageNumber >= renderer.pageCount) {
                        Log.e(TAG, "Invalid page number: $pageNumber")
                        return@withContext null
                    }

                    renderer.openPage(pageNumber).use { page ->
                        // Calculate dimensions based on DPI
                        val width = (page.width * (RASTER_QUALITY / 72f)).toInt()
                        val height = (page.height * (RASTER_QUALITY / 72f)).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        RasterResult(
                            bitmap = bitmap,
                            pageNumber = pageNumber,
                            totalPages = renderer.pageCount
                        )
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error rasterizing PDF: ${e.message}", e)
            null
        }
    }

    /**
     * Get printer capabilities
     */
    fun getPrinterCapabilities(): List<PrinterCapabilities> {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        return listOf(
            PrinterCapabilities(
                supportsColor = true, // Default to true since we can't get actual capabilities
                supportsDuplex = true, // Default to true since we can't get actual capabilities
                maxResolution = 300, // Default to 300 DPI
                mediaTypes = listOf("A4", "A5") // Default supported media types
            )
        )
    }

    /**
     * Print PDF
     */
    suspend fun printPdf(pdfUri: Uri, jobName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = PDFPrintAdapter(context, pdfUri, jobName)
            printManager.print(jobName, adapter, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing PDF: ${e.message}", e)
            false
        }
    }

    /**
     * Save rasterized page
     */
    suspend fun saveRasterizedPage(result: RasterResult, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(outputFile).use { out ->
                result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error saving rasterized page: ${e.message}", e)
            false
        }
    }

    /**
     * Get page count
     */
    suspend fun getPageCount(pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: IOException) {
            Log.e(TAG, "Error getting page count: ${e.message}", e)
            0
        }
    }
}