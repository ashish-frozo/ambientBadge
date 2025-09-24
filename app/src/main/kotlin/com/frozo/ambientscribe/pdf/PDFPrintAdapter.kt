package com.frozo.ambientscribe.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Adapter for printing PDF files
 */
class PDFPrintAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val jobName: String
) : PrintDocumentAdapter() {

    companion object {
        private const val TAG = "PDFPrintAdapter"
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(renderer.pageCount)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during layout: ${e.message}", e)
            callback?.onLayoutFailed("Error during layout: ${e.message}")
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        try {
            // Copy PDF file to destination
            FileInputStream(context.contentResolver.openFileDescriptor(pdfUri, "r")?.fileDescriptor).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            callback?.onWriteFinished(pages)
        } catch (e: IOException) {
            Log.e(TAG, "Error during write: ${e.message}", e)
            callback?.onWriteFailed("Error during write: ${e.message}")
        }
    }
}