package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.security.PDFEncryptionService
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for PDF functionality - ST-4.6, ST-4.7, ST-4.8
 * Tests PDF generation, QR codes, and encryption
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PDFIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var pdfGenerator: PDFGenerator
    private lateinit var exportManager: PDFExportManager
    private lateinit var encryptionService: PDFEncryptionService
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pdfGenerator = PDFGenerator(context)
        exportManager = PDFExportManager(context)
        encryptionService = PDFEncryptionService(context)
        
        // Create test directory
        testDir = File(context.filesDir, "pdf_test").apply { mkdirs() }
    }
    
    @After
    fun cleanup() {
        // Clean up test files
        testDir.deleteRecursively()
    }
    
    @Test
    fun testCompletePDFWorkflow() = runBlocking {
        // Create test encounter note
        val encounterNote = createTestEncounterNote()
        
        // Generate PDF
        val pdfResult = pdfGenerator.generatePrescriptionPDF(
            encounterNote = encounterNote,
            transcript = "Test transcript"
        ).getOrThrow()
        
        // Verify PDF generated
        val pdfFile = File(pdfResult.filePath)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
        
        // Verify PDF content
        verifyPDFContent(pdfFile, encounterNote)
        
        // Verify QR codes
        verifyQRCodes(pdfFile, encounterNote)
        
        // Test export workflow
        val exportResult = exportManager.exportPDF(
            pdfResult = pdfResult,
            exportType = PDFExportManager.ExportType.SAVE_EXTERNAL
        ).getOrThrow()
        
        // Verify export
        assertNotNull(exportResult.uri)
        assertNotNull(exportResult.path)
        
        // Verify exported file
        val exportedFile = File(exportResult.path!!)
        assertTrue(exportedFile.exists())
        assertTrue(exportedFile.length() > 0)
        
        // Verify encryption
        verifyEncryption(pdfFile, pdfResult.encryptionMetadata)
    }
    
    @Test
    fun testMultilingualContent() = runBlocking {
        // Create test note with multilingual content
        val encounterNote = createTestEncounterNote(
            subjective = listOf(
                "Headache",          // English
                "सिरदर्द",           // Hindi
                "తలనొప్పి"           // Telugu
            )
        )
        
        // Generate PDF
        val pdfResult = pdfGenerator.generatePrescriptionPDF(
            encounterNote = encounterNote,
            transcript = "Test transcript"
        ).getOrThrow()
        
        // Verify PDF generated
        val pdfFile = File(pdfResult.filePath)
        assertTrue(pdfFile.exists())
        
        // Verify multilingual content
        verifyMultilingualContent(pdfFile)
    }
    
    @Test
    fun testPrinterCompatibility() = runBlocking {
        // Create test note
        val encounterNote = createTestEncounterNote()
        
        // Generate PDF
        val pdfResult = pdfGenerator.generatePrescriptionPDF(
            encounterNote = encounterNote,
            transcript = "Test transcript"
        ).getOrThrow()
        
        // Create print attributes
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        
        // Verify print adapter
        val printAdapter = PDFPrintAdapter(File(pdfResult.filePath))
        verifyPrintAdapter(printAdapter, attributes)
    }
    
    private fun verifyPDFContent(pdfFile: File, encounterNote: LLMService.EncounterNote) {
        // Open PDF for verification
        val renderer = PdfRenderer(
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        )
        
        try {
            // Verify page count
            assertEquals(1, renderer.pageCount)
            
            // Get first page
            val page = renderer.openPage(0)
            
            // Create bitmap for content analysis
            val bitmap = Bitmap.createBitmap(
                page.width,
                page.height,
                Bitmap.Config.ARGB_8888
            )
            
            // Render page
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            // TODO: Add OCR verification of content
            // For now, just verify bitmap is not empty
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
            
            page.close()
            
        } finally {
            renderer.close()
        }
    }
    
    private fun verifyQRCodes(pdfFile: File, encounterNote: LLMService.EncounterNote) {
        // Open PDF
        val renderer = PdfRenderer(
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        )
        
        try {
            val page = renderer.openPage(0)
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(
                page.width,
                page.height,
                Bitmap.Config.ARGB_8888
            )
            
            // Render page
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            // Convert bitmap for QR scanning
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            // Scan for QR code
            val result = MultiFormatReader().decode(binaryBitmap)
            assertNotNull(result)
            
            // Verify QR content contains encounter ID
            assertTrue(result.text.contains(encounterNote.metadata.encounterId))
            
            page.close()
            
        } finally {
            renderer.close()
        }
    }
    
    private fun verifyEncryption(
        pdfFile: File,
        metadata: PDFEncryptionService.EncryptionMetadata
    ) {
        // Create temp file for decryption
        val decryptedFile = File.createTempFile("decrypted_", ".pdf", testDir)
        
        try {
            // Decrypt file
            val result = encryptionService.decryptPDF(
                inputFile = pdfFile,
                outputFile = decryptedFile,
                metadata = metadata
            )
            
            // Verify decryption success
            assertTrue(result.isSuccess)
            assertTrue(decryptedFile.exists())
            assertTrue(decryptedFile.length() > 0)
            
        } finally {
            decryptedFile.delete()
        }
    }
    
    private fun verifyMultilingualContent(pdfFile: File) {
        // Open PDF
        val renderer = PdfRenderer(
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        )
        
        try {
            val page = renderer.openPage(0)
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(
                page.width,
                page.height,
                Bitmap.Config.ARGB_8888
            )
            
            // Render page
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            // TODO: Add OCR verification for multilingual text
            // For now, just verify bitmap is rendered
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
            
            page.close()
            
        } finally {
            renderer.close()
        }
    }
    
    private fun verifyPrintAdapter(
        printAdapter: PDFPrintAdapter,
        attributes: PrintAttributes
    ) {
        // Create temp file for print output
        val outputFile = File.createTempFile("print_", ".pdf", testDir)
        val outputFd = ParcelFileDescriptor.open(
            outputFile,
            ParcelFileDescriptor.MODE_READ_WRITE
        )
        
        try {
            // Test layout callback
            var layoutFinished = false
            printAdapter.onLayout(
                oldAttributes = null,
                newAttributes = attributes,
                cancellationSignal = null,
                callback = object : PrintDocumentAdapter.LayoutResultCallback() {
                    override fun onLayoutFinished(
                        info: PrintDocumentInfo?,
                        changed: Boolean
                    ) {
                        layoutFinished = true
                    }
                },
                extras = null
            )
            
            assertTrue(layoutFinished)
            
            // Test write callback
            var writeFinished = false
            printAdapter.onWrite(
                pages = arrayOf(PageRange.ALL_PAGES),
                destination = outputFd,
                cancellationSignal = null,
                callback = object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>?) {
                        writeFinished = true
                    }
                }
            )
            
            assertTrue(writeFinished)
            assertTrue(outputFile.length() > 0)
            
        } finally {
            outputFd.close()
            outputFile.delete()
        }
    }
    
    private fun createTestEncounterNote(
        subjective: List<String> = listOf("Test complaint")
    ): LLMService.EncounterNote {
        return LLMService.EncounterNote(
            soap = LLMService.SOAPNote(
                subjective = subjective,
                objective = listOf("Test finding"),
                assessment = listOf("Test diagnosis"),
                plan = listOf("Test plan"),
                confidence = 0.85f
            ),
            prescription = LLMService.Prescription(
                medications = listOf(
                    LLMService.Medication(
                        name = "Test medication",
                        dosage = "500mg",
                        frequency = "twice daily",
                        duration = "3 days",
                        instructions = "Take with food",
                        confidence = 0.9f,
                        isGeneric = true
                    )
                ),
                instructions = listOf("Complete full course"),
                followUp = "Return if symptoms persist",
                confidence = 0.88f
            ),
            metadata = LLMService.EncounterMetadata(
                speakerTurns = 4,
                totalDuration = 120000,
                processingTime = 3000,
                modelVersion = "test-model",
                fallbackUsed = false,
                encounterId = "test-encounter-123",
                patientId = "test-patient-456"
            )
        )
    }
}
