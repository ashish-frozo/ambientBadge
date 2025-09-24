package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.frozo.ambientscribe.ai.LLMService
import com.frozo.ambientscribe.clinic.ClinicHeaderManager
import com.frozo.ambientscribe.localization.HindiTextManager
import com.frozo.ambientscribe.security.JSONEncryptionService
import com.frozo.ambientscribe.security.PDFEncryptionService
import com.itextpdf.io.font.FontProgram
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates A5 PDF prescriptions with clinic branding, multilingual support, QR codes, and encryption.
 */
class PDFGenerator(private val context: Context) {

    private val clinicHeaderManager = ClinicHeaderManager(context)
    private val pdfEncryptionService = PDFEncryptionService(context)
    private val qrCodeGenerator = QRCodeGenerator()
    private val hindiTextManager = HindiTextManager(context)
    private val jsonEncryptionService = JSONEncryptionService(context)

    private var notoSansFont: PdfFont? = null
    private var notoSansBoldFont: PdfFont? = null
    private var notoSansDevanagariFont: PdfFont? = null
    private var notoSansDevanagariBoldFont: PdfFont? = null

    init {
        loadFonts()
    }

    private fun loadFonts() {
        try {
            val notoSansRegular = FontProgramFactory.createFont("fonts/NotoSans-Regular.ttf")
            val notoSansBold = FontProgramFactory.createFont("fonts/NotoSans-Bold.ttf")
            val notoSansDevanagariRegular = FontProgramFactory.createFont("fonts/NotoSansDevanagari-Regular.ttf")
            val notoSansDevanagariBold = FontProgramFactory.createFont("fonts/NotoSansDevanagari-Bold.ttf")

            notoSansFont = PdfFontFactory.createFont(notoSansRegular, PdfEncodings.IDENTITY_H)
            notoSansBoldFont = PdfFontFactory.createFont(notoSansBold, PdfEncodings.IDENTITY_H)
            notoSansDevanagariFont = PdfFontFactory.createFont(notoSansDevanagariRegular, PdfEncodings.IDENTITY_H)
            notoSansDevanagariBoldFont = PdfFontFactory.createFont(notoSansDevanagariBold, PdfEncodings.IDENTITY_H)
        } catch (e: Exception) {
            Log.e("PDFGenerator", "Error loading fonts: ${e.message}", e)
        }
    }

    suspend fun generatePdf(encounterNote: LLMService.EncounterNote, encryptedJson: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "prescription_${System.currentTimeMillis()}.pdf"
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

                PdfWriter(FileOutputStream(file)).use { writer ->
                    PdfDocument(writer).use { pdf ->
                        Document(pdf, PageSize.A5).use { document ->
                            // Add clinic header
                            addClinicHeader(document)

                            // Add patient details
                            document.add(Paragraph("Patient ID: ${encounterNote.metadata.patientId}")
                                .setFont(notoSansFont)
                                .setFontSize(10f))
                            document.add(Paragraph("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
                                .setFont(notoSansFont)
                                .setFontSize(10f))
                            document.add(Paragraph("\n"))

                            // Add SOAP Note
                            document.add(Paragraph("SOAP Note")
                                .setFont(notoSansBoldFont)
                                .setFontSize(12f)
                                .setTextAlignment(TextAlignment.CENTER))
                            addSoapSection(document, "Subjective:", encounterNote.soap.subjective)
                            addSoapSection(document, "Objective:", encounterNote.soap.objective)
                            addSoapSection(document, "Assessment:", encounterNote.soap.assessment)
                            addSoapSection(document, "Plan:", encounterNote.soap.plan)
                            document.add(Paragraph("\n"))

                            // Add Prescription
                            document.add(Paragraph("Prescription")
                                .setFont(notoSansBoldFont)
                                .setFontSize(12f)
                                .setTextAlignment(TextAlignment.CENTER))
                            addPrescriptionTable(document, encounterNote.prescription.medications)
                            addInstructions(document, "General Instructions:", encounterNote.prescription.instructions)
                            addInstructions(document, "Follow-up Instructions:", listOf(encounterNote.prescription.followUp))
                            document.add(Paragraph("\n"))

                            // Add QR Code
                            val qrCodeBitmap = qrCodeGenerator.generateQRCode(encryptedJson, 200)
                            val qrCodeImage = Image(ImageDataFactory.create(qrCodeBitmap.toByteArray()))
                                .setWidth(UnitValue.createPointValue(100f))
                                .setHeight(UnitValue.createPointValue(100f))
                            document.add(qrCodeImage)

                            // Add verification text
                            document.add(Paragraph("Scan for encrypted encounter details")
                                .setFont(notoSansFont)
                                .setFontSize(8f)
                                .setTextAlignment(TextAlignment.CENTER))
                        }
                    }
                }

                file

            } catch (e: Exception) {
                Log.e("PDFGenerator", "Error generating PDF: ${e.message}", e)
                null
            }
        }
    }

    private fun addClinicHeader(document: Document) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
            .useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)

        // Add clinic name
        table.addCell(Cell().setBorder(Border.NO_BORDER)
            .add(Paragraph(clinicHeaderManager.getClinicName() ?: "Ambient Scribe Clinic")
                .setFont(notoSansBoldFont)
                .setFontSize(14f)
                .setTextAlignment(TextAlignment.CENTER)))

        // Add clinic address
        table.addCell(Cell().setBorder(Border.NO_BORDER)
            .add(Paragraph(clinicHeaderManager.getClinicAddress() ?: "123 Main St, Anytown, USA")
                .setFont(notoSansFont)
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)))

        // Add registration number
        table.addCell(Cell().setBorder(Border.NO_BORDER)
            .add(Paragraph("Doctor Reg No: ${clinicHeaderManager.getDoctorRegistrationNumber() ?: "DR12345"}")
                .setFont(notoSansFont)
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)))

        document.add(table)
        document.add(Paragraph("\n"))
    }

    private fun addSoapSection(document: Document, title: String, items: List<String>) {
        document.add(Paragraph(title)
            .setFont(notoSansBoldFont)
            .setFontSize(10f))

        items.forEach { item ->
            document.add(Paragraph("• $item")
                .setFont(notoSansFont)
                .setFontSize(10f)
                .setMarginLeft(10f))
        }
    }

    private fun addPrescriptionTable(document: Document, medications: List<LLMService.Medication>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(3f, 2f, 2f, 2f, 3f)))
            .useAllAvailableWidth()
            .setBorder(SolidBorder(DeviceRgb(0, 0, 0), 0.5f))

        // Add headers
        listOf("Medication", "Dosage", "Frequency", "Duration", "Instructions").forEach { header ->
            table.addCell(Cell()
                .add(Paragraph(header)
                    .setFont(notoSansBoldFont)
                    .setFontSize(9f)
                    .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(DeviceRgb(230, 230, 230))
                .setBorder(SolidBorder(DeviceRgb(0, 0, 0), 0.5f))
                .setPadding(5f))
        }

        // Add medication rows
        medications.forEach { medication ->
            listOf(
                medication.name,
                medication.dosage,
                medication.frequency,
                medication.duration,
                medication.instructions
            ).forEach { text ->
                table.addCell(Cell()
                    .add(Paragraph(text)
                        .setFont(notoSansFont)
                        .setFontSize(9f)
                        .setTextAlignment(TextAlignment.LEFT))
                    .setBorder(SolidBorder(DeviceRgb(0, 0, 0), 0.5f))
                    .setPadding(5f))
            }
        }

        document.add(table)
    }

    private fun addInstructions(document: Document, title: String, instructions: List<String>) {
        document.add(Paragraph(title)
            .setFont(notoSansBoldFont)
            .setFontSize(10f))

        instructions.forEach { instruction ->
            document.add(Paragraph("• $instruction")
                .setFont(notoSansFont)
                .setFontSize(10f)
                .setMarginLeft(10f))
        }
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}