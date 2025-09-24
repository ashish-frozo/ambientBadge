package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tool for testing printer compatibility and capabilities
 */
class PrinterTestTool(private val context: Context) {

    companion object {
        private const val TAG = "PrinterTestTool"
        private const val TEST_PAGE_FILE = "printer_test_page.pdf"
    }

    /**
     * Test result
     */
    data class TestResult(
        val success: Boolean,
        val printerName: String,
        val resolution: Int,
        val colorMode: Int,
        val mediaSize: String,
        val errors: List<String> = emptyList()
    )

    /**
     * Generate test page
     */
    suspend fun generateTestPage(): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, TEST_PAGE_FILE)
        
        try {
            PdfWriter(FileOutputStream(file)).use { writer ->
                com.itextpdf.kernel.pdf.PdfDocument(writer).use { pdf ->
                    Document(pdf, PageSize.A5).use { document ->
                        // Add title
                        document.add(Paragraph("Printer Test Page")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(16f)
                            .setTextAlignment(TextAlignment.CENTER))

                        // Add timestamp
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date())
                        document.add(Paragraph("Generated: $timestamp")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setTextAlignment(TextAlignment.CENTER))

                        // Add color test
                        document.add(Paragraph("Color Test")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(12f)
                            .setFontColor(DeviceRgb(255, 0, 0)))

                        document.add(Paragraph("This text should be red")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setFontColor(DeviceRgb(255, 0, 0)))

                        document.add(Paragraph("This text should be green")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setFontColor(DeviceRgb(0, 255, 0)))

                        document.add(Paragraph("This text should be blue")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setFontColor(DeviceRgb(0, 0, 255)))

                        // Add resolution test
                        document.add(Paragraph("\nResolution Test")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(12f))

                        document.add(Paragraph("This text is 6pt")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(6f))

                        document.add(Paragraph("This text is 8pt")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(8f))

                        document.add(Paragraph("This text is 10pt")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f))

                        document.add(Paragraph("This text is 12pt")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(12f))

                        // Add margin test
                        document.add(Paragraph("\nMargin Test")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(12f))

                        document.add(Paragraph("This text should be near the left margin")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setMarginLeft(10f))

                        document.add(Paragraph("This text should be near the right margin")
                            .setFont(PdfFontFactory.createFont())
                            .setFontSize(10f)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setMarginRight(10f))
                    }
                }
            }

            file

        } catch (e: Exception) {
            Log.e(TAG, "Error generating test page: ${e.message}", e)
            throw e
        }
    }

    /**
     * Test printer
     */
    suspend fun testPrinter(printerName: String): TestResult = withContext(Dispatchers.Main) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            // For MVP, we'll assume the printer exists and return a default test result
            if (printerName.isBlank()) {
                return@withContext TestResult(
                    success = false,
                    printerName = printerName,
                    resolution = 0,
                    colorMode = PrintAttributes.COLOR_MODE_MONOCHROME,
                    mediaSize = "unknown",
                    errors = listOf("Printer not found")
                )
            }

            val testPage = generateTestPage()
            val adapter = PDFPrintAdapter(context, Uri.fromFile(testPage), "Printer Test")
            printManager.print("Printer Test", adapter, null)

            return@withContext TestResult(
                success = true,
                printerName = printerName,
                resolution = 300, // Default to 300 DPI
                colorMode = PrintAttributes.COLOR_MODE_COLOR, // Default to color
                mediaSize = "A5" // Default to A5
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error testing printer: ${e.message}", e)
            TestResult(
                success = false,
                printerName = printerName,
                resolution = 0,
                colorMode = PrintAttributes.COLOR_MODE_MONOCHROME,
                mediaSize = "unknown",
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Get available printers
     */
    fun getAvailablePrinters(): List<String> {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        // For MVP, return a list of default printers
        return listOf("Default Printer", "PDF Printer")
    }

    /**
     * Clean up test files
     */
    fun cleanup() {
        val testPage = File(context.cacheDir, TEST_PAGE_FILE)
        if (testPage.exists()) {
            testPage.delete()
        }
    }
}