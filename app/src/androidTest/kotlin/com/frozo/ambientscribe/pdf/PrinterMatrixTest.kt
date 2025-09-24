package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Printer matrix tests for various printer models
 * Implements ST-4.9: Printer matrix tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PrinterMatrixTest {

    private lateinit var context: Context
    private lateinit var printerTestTool: PrinterTestTool
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        printerTestTool = PrinterTestTool(context)
        testDir = File(context.filesDir, "printer_test").apply { mkdirs() }
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    /**
     * Test A5 format and margins
     */
    @Test
    fun testA5FormatAndMargins() = runBlocking {
        // Generate test page
        val result = printerTestTool.printTestPage()
        assertTrue(result.isSuccess)

        // Get test file
        val testFile = File(context.filesDir, "test_pages")
            .listFiles()
            ?.firstOrNull()
            ?: throw IllegalStateException("Test file not found")

        // Verify A5 format
        PdfReader(testFile).use { reader ->
            val pdfDoc = PdfDocument(reader)
            val page = pdfDoc.getPage(1)
            val pageSize = page.pageSize

            // Check A5 dimensions (148 × 210 mm)
            assertEquals(PageSize.A5.width, pageSize.width, 0.1f)
            assertEquals(PageSize.A5.height, pageSize.height, 0.1f)

            // Check margins (10mm)
            val mediaBox = page.mediaBox
            assertTrue(mediaBox.left <= 10 * 72 / 25.4f) // Convert mm to points
            assertTrue(mediaBox.bottom <= 10 * 72 / 25.4f)
            assertTrue(mediaBox.right >= pageSize.width - 10 * 72 / 25.4f)
            assertTrue(mediaBox.top >= pageSize.height - 10 * 72 / 25.4f)
        }
    }

    /**
     * Test grayscale rendering
     */
    @Test
    fun testGrayscaleRendering() = runBlocking {
        printerTestTool.printTestPage()

        val testFile = File(context.filesDir, "test_pages")
            .listFiles()
            ?.firstOrNull()
            ?: throw IllegalStateException("Test file not found")

        // Verify grayscale rendering
        val renderer = PdfRenderer(
            ParcelFileDescriptor.open(testFile, ParcelFileDescriptor.MODE_READ_ONLY)
        )

        try {
            val page = renderer.openPage(0)
            val bitmap = android.graphics.Bitmap.createBitmap(
                page.width,
                page.height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Check grayscale steps
            val grayscaleSteps = 10
            for (i in 0 until grayscaleSteps) {
                val expectedGray = i * (255f / grayscaleSteps)
                assertTrue(hasGrayscaleValue(bitmap, expectedGray.toInt()))
            }

            page.close()
        } finally {
            renderer.close()
        }
    }

    /**
     * Test Devanagari text rendering
     */
    @Test
    fun testDevanagariRendering() = runBlocking {
        printerTestTool.printTestPage()

        val testFile = File(context.filesDir, "test_pages")
            .listFiles()
            ?.firstOrNull()
            ?: throw IllegalStateException("Test file not found")

        // Verify Devanagari text
        PdfReader(testFile).use { reader ->
            val pdfDoc = PdfDocument(reader)
            val page = pdfDoc.getPage(1)

            // Extract text
            val text = page.contentStream.toString()
            assertTrue(text.contains("हिंदी"))
            assertTrue(text.contains("टैबलेट"))
        }
    }

    /**
     * Test printer discovery and capabilities
     */
    @Test
    fun testPrinterCapabilities() {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

        // Test print service availability
        assertTrue(printManager.printServices.isNotEmpty())

        // Test A5 support
        val printerInfo = printManager.printServices.first().printerId
        val caps = printManager.getSelectPrinterCapabilitiesForUser(
            context.userId,
            printerInfo
        )

        // Verify A5 media size support
        val mediaSize = PrintAttributes.MediaSize.ISO_A5
        assertTrue(caps.mediaSize == mediaSize || caps.mediaSizes.contains(mediaSize))

        // Verify resolution support
        assertTrue(caps.resolution.horizontalDpi >= 300)
        assertTrue(caps.resolution.verticalDpi >= 300)
    }

    /**
     * Test print job monitoring
     */
    @Test
    fun testPrintJobMonitoring() = runBlocking {
        var jobCompleted = false
        var jobFailed = false

        // Create print job observer
        val observer = object : android.print.PrintJobStateChangeListener() {
            override fun onPrintJobStateChanged(printJob: android.print.PrintJob) {
                when (printJob.state) {
                    android.print.PrintJobInfo.STATE_COMPLETED -> jobCompleted = true
                    android.print.PrintJobInfo.STATE_FAILED -> jobFailed = true
                }
            }
        }

        // Print test page
        printerTestTool.printTestPage()

        // Wait for job completion (max 30 seconds)
        var attempts = 0
        while (!jobCompleted && !jobFailed && attempts < 30) {
            kotlinx.coroutines.delay(1000)
            attempts++
        }

        assertTrue(jobCompleted)
        assertTrue(!jobFailed)
    }

    /**
     * Helper functions
     */
    private fun hasGrayscaleValue(bitmap: android.graphics.Bitmap, targetGray: Int): Boolean {
        val tolerance = 5 // Allow small color variations
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        return pixels.any { pixel ->
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            val gray = (r + g + b) / 3

            kotlin.math.abs(gray - targetGray) <= tolerance
        }
    }
}
