package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class TextBoundingBox(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float = 0f
)

class TesseractManager(private val context: Context) {

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false
    private val TAG = "TesseractManager"

    private var currentLanguage: String? = null

    fun preprocessImageForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        // 1. Calculate average luminance using a scaled down sample to detect dark mode (dark background with light text)
        val sampleW = 64
        val sampleH = 64
        val scaledSample = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        scaledSample.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        scaledSample.recycle()

        var totalLuminance = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuminance += lum
        }
        val avgLuminance = totalLuminance / (sampleW * sampleH)
        val isDark = avgLuminance < 120.0

        // 2. Grayscale matrix
        val cm = ColorMatrix()
        cm.setSaturation(0f)

        // 3. Invert colors if dark image (white background with black text for Tesseract)
        if (isDark) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                )
            )
            cm.postConcat(invertMatrix)
        }

        // 4. Subtle contrast enhancement (1.2f to preserve thin font strokes and umlauts)
        val contrast = 1.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return processedBitmap
    }

    suspend fun initTesseract(language: String = getAvailableLanguagesCombined()): Boolean = withContext(Dispatchers.IO) {
        try {
            val tessDir = File(context.filesDir, "tessdata")
            if (!tessDir.exists()) {
                tessDir.mkdirs()
            }

            // Copy traineddata files from assets dynamically
            try {
                val tessAssets = context.assets.list("tessdata") ?: emptyArray()
                for (fileName in tessAssets) {
                    if (fileName.endsWith(".traineddata")) {
                        copyAssetTessDataIfNotExists(tessDir, fileName)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not list assets in tessdata: ${e.message}")
            }

            val dataPath = context.filesDir.absolutePath
            val activeLang = if (language.isBlank()) getAvailableLanguagesCombined() else language

            if (isInitialized && tessApi != null && currentLanguage == activeLang) {
                return@withContext true
            }

            tessApi?.recycle()
            tessApi = null

            val api = TessBaseAPI()
            val success = api.init(dataPath, activeLang)
            if (success) {
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                api.setVariable("preserve_interword_spaces", "1")
                tessApi = api
                isInitialized = true
                currentLanguage = activeLang
                Log.d(TAG, "Tesseract initialized successfully with language: $activeLang")
                true
            } else {
                Log.e(TAG, "TessBaseAPI init returned false for lang: $activeLang")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tesseract", e)
            false
        }
    }

    private fun copyAssetTessDataIfNotExists(tessDir: File, fileName: String) {
        val outFile = File(tessDir, fileName)
        if (outFile.exists() && outFile.length() > 0) {
            return
        }

        try {
            val assetPath = "tessdata/$fileName"
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Successfully copied $fileName from assets to ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy asset $fileName: ${e.message}")
        }
    }

    suspend fun extractTextWithCoordinates(
        bitmap: Bitmap,
        language: String = getAvailableLanguagesCombined()
    ): List<TextBoundingBox> = withContext(Dispatchers.IO) {
        val activeLang = if (language.isBlank()) getAvailableLanguagesCombined() else language
        var api = tessApi
        if (!isInitialized || api == null) {
            val ok = initTesseract(activeLang)
            api = tessApi
            if (!ok || api == null) {
                Log.e(TAG, "Tesseract engine failed to initialize")
                return@withContext emptyList()
            }
        }

        val results = mutableListOf<TextBoundingBox>()

        try {
            val preprocessed = preprocessImageForOcr(bitmap)
            val safeBitmap = if (preprocessed.config != Bitmap.Config.ARGB_8888) {
                preprocessed.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                preprocessed
            }

            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(safeBitmap)
            api.getHOCRText(0) // Triggers OCR evaluation

            val iterator = api.resultIterator
            if (iterator != null) {
                val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
                do {
                    val word = iterator.getUTF8Text(level)
                    val rect: Rect? = iterator.getBoundingRect(level)
                    val confidence = iterator.confidence(level)

                    if (!word.isNullOrBlank() && rect != null) {
                        val cleanWord = word.trim()
                        if (cleanWord.isNotEmpty()) {
                            results.add(
                                TextBoundingBox(
                                    text = cleanWord,
                                    x = rect.left,
                                    y = rect.top,
                                    width = rect.width(),
                                    height = rect.height(),
                                    confidence = confidence
                                )
                            )
                        }
                    }
                } while (iterator.next(level))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text coordinates with Tesseract", e)
        }

        return@withContext results
    }

    suspend fun extractFullText(
        bitmap: Bitmap,
        language: String = getAvailableLanguagesCombined()
    ): String = withContext(Dispatchers.IO) {
        val activeLang = if (language.isBlank()) getAvailableLanguagesCombined() else language
        var api = tessApi
        if (!isInitialized || api == null) {
            val ok = initTesseract(activeLang)
            api = tessApi
            if (!ok || api == null) return@withContext ""
        }

        return@withContext try {
            val preprocessed = preprocessImageForOcr(bitmap)
            val safeBitmap = if (preprocessed.config != Bitmap.Config.ARGB_8888) {
                preprocessed.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                preprocessed
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(safeBitmap)
            api.utF8Text ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting full text", e)
            ""
        }
    }

    fun getAvailableLanguagesCombined(): String {
        try {
            val tessDir = File(context.filesDir, "tessdata")
            if (!tessDir.exists()) {
                tessDir.mkdirs()
            }

            // Copy traineddata files from assets dynamically if not existing
            try {
                val tessAssets = context.assets.list("tessdata") ?: emptyArray()
                for (fileName in tessAssets) {
                    if (fileName.endsWith(".traineddata")) {
                        copyAssetTessDataIfNotExists(tessDir, fileName)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not list assets in tessdata: ${e.message}")
            }

            val files = tessDir.listFiles { _, name -> name.endsWith(".traineddata") }
            if (files != null && files.isNotEmpty()) {
                val langs = files.map { it.name.removeSuffix(".traineddata") }.filter { it.isNotBlank() }
                if (langs.isNotEmpty()) {
                    // Sort languages so Latin/German script (LTR) comes before Arabic (RTL)
                    val priorityMap = mapOf("deu" to 1, "eng" to 2, "ara" to 99)
                    val sortedLangs = langs.sortedWith { a, b ->
                        val pA = priorityMap[a] ?: 50
                        val pB = priorityMap[b] ?: 50
                        pA.compareTo(pB)
                    }
                    return sortedLangs.joinToString("+")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering available traineddata languages", e)
        }
        return "eng"
    }

    fun release() {
        try {
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Tesseract API", e)
        }
    }
}
