package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

object PdfBoxGenerator {

    private const val TAG = "PdfBoxGenerator"

    suspend fun createSearchablePdf(
        context: Context,
        image: Bitmap,
        ocrData: List<TextBoundingBox>,
        outputPath: String
    ): File = createSearchablePdfFromPages(
        context = context,
        pages = listOf(Pair(image, ocrData)),
        outputPath = outputPath
    )

    suspend fun createSearchablePdfFromPages(
        context: Context,
        pages: List<Pair<Bitmap, List<TextBoundingBox>>>,
        outputPath: String
    ): File = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        val document = PDDocument()

        try {
            // Load custom Arabic/English TrueType Font for search layer
            val fontBytes = try {
                context.assets.open("fonts/NotoSansArabic-Regular.ttf").readBytes()
            } catch (e: Exception) {
                try {
                    context.assets.open("pdfjs/web/standard_fonts/LiberationSans-Regular.ttf").readBytes()
                } catch (ex: Exception) {
                    null
                }
            }

            val pdfFont: PDFont = if (fontBytes != null) {
                try {
                    PDType0Font.load(document, ByteArrayInputStream(fontBytes))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed loading Type0 font, falling back to Helvetica", e)
                    PDType1Font.HELVETICA
                }
            } else {
                PDType1Font.HELVETICA
            }

            for ((bitmap, boundingBoxes) in pages) {
                val pageWidth = bitmap.width.toFloat()
                val pageHeight = bitmap.height.toFloat()

                val pageSize = PDRectangle(pageWidth, pageHeight)
                val page = PDPage(pageSize)
                document.addPage(page)

                // Render Background Image
                val pdImage = try {
                    JPEGFactory.createFromImage(document, bitmap, 0.85f)
                } catch (e: Exception) {
                    LosslessFactory.createFromImage(document, bitmap)
                }

                val contentStream = PDPageContentStream(document, page)
                contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)

                // Invisible Searchable Text Overlay (Tr = 3 -> Neither fill nor stroke text)
                if (boundingBoxes.isNotEmpty()) {
                    contentStream.beginText()
                    contentStream.appendRawCommands("3 Tr\n")

                    for (box in boundingBoxes) {
                        if (box.text.isBlank()) continue

                        val safeText = filterEncodableText(pdfFont, box.text)
                        if (safeText.isBlank()) continue

                        val pdfX = box.x.toFloat().coerceIn(0f, pageWidth - 1f)
                        val pdfY = (pageHeight - box.y.toFloat() - box.height.toFloat()).coerceIn(0f, pageHeight - 1f)

                        val fontSize = (box.height.toFloat() * 0.82f).coerceIn(6f, 120f)

                        try {
                            contentStream.setFont(pdfFont, fontSize)
                            contentStream.newLineAtOffset(pdfX, pdfY)
                            contentStream.showText(safeText)
                            // Reset text position back for next relative offset
                            contentStream.newLineAtOffset(-pdfX, -pdfY)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error drawing box text '${box.text}': ${e.message}")
                        }
                    }

                    contentStream.endText()
                }

                contentStream.close()
            }

            document.save(outputFile)
            Log.d(TAG, "Searchable PDF created successfully at ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating searchable PDF with PdfBox", e)
            throw e
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PDDocument", e)
            }
        }

        return@withContext outputFile
    }

    private fun filterEncodableText(font: PDFont, text: String): String {
        val clean = text.filter { c ->
            val code = c.code
            code in 32..126 || code in 0x0600..0x06FF || code in 0x0750..0x077F ||
                    code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFE || code in 160..255
        }.trim()

        if (clean.isEmpty()) return ""

        val sb = StringBuilder()
        for (ch in clean) {
            try {
                font.encode(ch.toString())
                sb.append(ch)
            } catch (ignored: Exception) {
                // Skip unencodable character safely
            }
        }
        return sb.toString()
    }
}
