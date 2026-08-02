package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
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

    suspend fun initTesseract(language: String = "ara+eng"): Boolean = withContext(Dispatchers.IO) {
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

            val api = TessBaseAPI()
            val success = api.init(dataPath, language)
            if (success) {
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tessApi = api
                isInitialized = true
                Log.d(TAG, "Tesseract initialized successfully with language: $language")
                true
            } else {
                Log.e(TAG, "TessBaseAPI init returned false for lang: $language")
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
        language: String = "ara+eng"
    ): List<TextBoundingBox> = withContext(Dispatchers.IO) {
        var api = tessApi
        if (!isInitialized || api == null) {
            val ok = initTesseract(language)
            api = tessApi
            if (!ok || api == null) {
                Log.e(TAG, "Tesseract engine failed to initialize")
                return@withContext emptyList()
            }
        }

        val results = mutableListOf<TextBoundingBox>()

        try {
            val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

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
        language: String = "ara+eng"
    ): String = withContext(Dispatchers.IO) {
        var api = tessApi
        if (!isInitialized || api == null) {
            val ok = initTesseract(language)
            api = tessApi
            if (!ok || api == null) return@withContext ""
        }

        return@withContext try {
            val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
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
                    return langs.joinToString("+")
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
