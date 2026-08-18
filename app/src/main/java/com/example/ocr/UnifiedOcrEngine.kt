package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.example.data.SecureKeyManager
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Unified OCR Engine based on TessBaseAPI logic from android-ocr (Apache 2.0).
 * Handles tessdata copying, engine initialization, multi-language OCR recognition,
 * online Gemini AI OCR extraction, and memory release using Kotlin Coroutines.
 */
class UnifiedOcrEngine(private val context: Context) {

    private var tessApi: TessBaseAPI? = null
    private var isInitialized: Boolean = false
    private var currentLanguage: String = ""

    companion object {
        private const val TAG = "UnifiedOcrEngine"
        private const val TESS_DATA_DIR = "tessdata"
        val SUPPORTED_LANGUAGES = listOf("ara", "eng", "deu")
    }

    data class RecognitionResult(
        val text: String,
        val confidence: Int,
        val boundingBoxes: List<Rect> = emptyList()
    )

    data class WordBox(
        val text: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val confidence: Int
    )

    private fun getTessDataDirectory(): File {
        val dir = File(context.filesDir, "tesseract")
        val tessData = File(dir, TESS_DATA_DIR)
        if (!tessData.exists()) {
            tessData.mkdirs()
        }
        return dir
    }

    private suspend fun copyAssetsIfRequired(): Boolean = withContext(Dispatchers.IO) {
        val rootDir = getTessDataDirectory()
        val tessDataDir = File(rootDir, TESS_DATA_DIR)
        var copiedAny = false

        for (lang in SUPPORTED_LANGUAGES) {
            val fileName = "$lang.traineddata"
            val targetFile = File(tessDataDir, fileName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    val assetPath = "$TESS_DATA_DIR/$fileName"
                    val inputStream: InputStream = context.assets.open(assetPath)
                    val outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    copiedAny = true
                    Log.d(TAG, "Copied $fileName to ${targetFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy $fileName from assets: ${e.message}")
                }
            }
        }
        return@withContext true
    }

    suspend fun initialize(language: String = "ara+eng+deu"): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && currentLanguage == language && tessApi != null) {
            return@withContext true
        }

        try {
            copyAssetsIfRequired()
            val dataPath = getTessDataDirectory().absolutePath

            tessApi?.recycle()
            tessApi = TessBaseAPI()

            val success = tessApi?.init(dataPath, language) ?: false
            if (success) {
                isInitialized = true
                currentLanguage = language
                tessApi?.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                Log.i(TAG, "Tesseract initialized successfully with language: $language")
            } else {
                Log.e(TAG, "Tesseract init failed for language: $language")
                isInitialized = false
            }
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error during Tesseract initialization", e)
            isInitialized = false
            return@withContext false
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap

        val sampleW = 64.coerceAtMost(bitmap.width)
        val sampleH = 64.coerceAtMost(bitmap.height)
        val scaledSample = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        var totalLuminance = 0.0
        val pixels = IntArray(sampleW * sampleH)
        scaledSample.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        scaledSample.recycle()

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuminance += lum
        }
        val avgLuminance = totalLuminance / (sampleW * sampleH)
        val isDark = avgLuminance < 80.0

        val cm = ColorMatrix()

        if (isDark) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(invertMatrix)
        }

        // 1. Grayscale conversion
        val grayMatrix = ColorMatrix()
        grayMatrix.setSaturation(0f)
        cm.postConcat(grayMatrix)

        // 2. Moderate contrast enhancement (factor = 1.35f)
        val contrast = 1.35f
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

        val processedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return processedBitmap
    }

    private fun cleanExtractedText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lines()
            .map { line ->
                var cleaned = line.trim()
                cleaned = cleaned.replace(Regex("^[\\s,\\|»%°#\\*\\+]+"), "")
                cleaned = cleaned.replace(Regex("[\\s,\\|»%°#\\*\\+]+$"), "")
                cleaned.trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    suspend fun recognizeText(
        bitmap: Bitmap,
        language: String = "ara+eng+deu"
    ): RecognitionResult = withContext(Dispatchers.IO) {
        if (!isInitialized || currentLanguage != language || tessApi == null) {
            val initialized = initialize(language)
            if (!initialized || tessApi == null) {
                return@withContext RecognitionResult("", 0)
            }
        }

        val api = tessApi ?: return@withContext RecognitionResult("", 0)

        return@withContext try {
            val processedBmp = preprocessBitmap(bitmap)
            api.setImage(processedBmp)

            val utf8Text = api.utF8Text ?: ""
            val confidence = api.meanConfidence()
            val cleanedText = cleanExtractedText(utf8Text)

            RecognitionResult(
                text = cleanedText,
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing OCR recognition", e)
            RecognitionResult("", 0)
        }
    }

    suspend fun recognizeWithBoundingBoxes(
        bitmap: Bitmap,
        language: String = "ara+eng+deu"
    ): List<WordBox> = withContext(Dispatchers.IO) {
        if (!isInitialized || currentLanguage != language || tessApi == null) {
            val initialized = initialize(language)
            if (!initialized || tessApi == null) {
                return@withContext emptyList()
            }
        }

        val api = tessApi ?: return@withContext emptyList()
        val wordBoxes = mutableListOf<WordBox>()

        return@withContext try {
            val processedBmp = preprocessBitmap(bitmap)
            api.setImage(processedBmp)
            api.getHOCRText(0)

            val iterator = api.resultIterator
            if (iterator != null) {
                val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
                do {
                    val rawWord = iterator.getUTF8Text(level) ?: ""
                    val cleanedWord = cleanExtractedText(rawWord)
                    val rect: Rect? = iterator.getBoundingRect(level)
                    val conf = iterator.confidence(level).toInt()

                    if (cleanedWord.isNotEmpty() && rect != null) {
                        wordBoxes.add(
                            WordBox(
                                text = cleanedWord,
                                x = rect.left,
                                y = rect.top,
                                width = rect.width(),
                                height = rect.height(),
                                confidence = conf
                            )
                        )
                    }
                } while (iterator.next(level))
            }
            wordBoxes
        } catch (e: Exception) {
            Log.e(TAG, "Error executing OCR recognition with bounding boxes", e)
            emptyList()
        }
    }

    /**
     * Extracts text using Google Gemini Vision API across configured candidate fallback models.
     * Candidate fallback models: gemini-3.7-flash, gemini-3.5-flash, gemini-flash-latest, gemini-3.1-flash-lite.
     * Returns the extracted text only, or empty string on failure.
     */
    suspend fun extractTextOnline(
        bitmap: Bitmap,
        apiKeyOverride: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.trim()?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            ?: SecureKeyManager.getGeminiApiKey(context).takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            ?: return@withContext ""

        val base64Image = try {
            val maxDim = 2048
            val scaledBmp = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val outputStream = ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compress/encode bitmap: ${e.message}")
            return@withContext ""
        }

        if (base64Image.isBlank()) return@withContext ""

        val promptText = "أنت نظام استخراج النصوص الضوئية (OCR) الأكثر دقة للكتب والمستندات المصورة. هذه صورة كتاب أو وثيقة مصورة تحتوي على أوراق ونصوص. يرجى قراءة هذه الصورة بالكامل واستخراج كافة النصوص العربية والألمانية والإنجليزية والرموز المكتوبة داخل أي صورة أو فقرة بالصفحة بدقة متناهية. حافظ على نفس ترتيب السطور والمحتوى بدون حذف أي كلمة. اكتب النص المستخرج فقط بدون أي تعليق أو مقدمات."

        try {
            val jsonPayload = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                val textPart = JSONObject().apply {
                    put("text", promptText)
                }
                val imagePart = JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    }
                    put("inlineData", inlineData)
                }

                partsArray.put(textPart)
                partsArray.put(imagePart)
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()

            val candidateModels = listOf(
                "gemini-3.7-flash",
                "gemini-3.5-flash",
                "gemini-flash-latest",
                "gemini-3.1-flash-lite"
            )

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            for (model in candidateModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    if (response.isSuccessful && responseStr.isNotBlank()) {
                        val jsonResponse = JSONObject(responseStr)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val extractedText = parts.getJSONObject(0).optString("text", "")
                                if (extractedText.isNotBlank()) {
                                    return@withContext extractedText.trim()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Model $model online OCR attempt failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online OCR failed: ${e.message}")
        }

        return@withContext ""
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun extractText(
        bitmap: Bitmap,
        language: String = "ara+eng+deu"
    ): String = recognizeText(bitmap, language).text

    /**
     * Primary public entry point for text extraction from UI.
     * Attempts online Gemini extraction first if key and internet are available.
     * Seamlessly falls back to prioritized local OCR passes:
     * 1. Arabic pass ("ara") -> returned if >= 4 Arabic characters found.
     * 2. Latin pass ("deu+eng") -> returned if not blank.
     * 3. Combined pass ("ara+eng+deu") as final fallback.
     * Returns clean extracted text only.
     */
    suspend fun scan(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val hasKey = SecureKeyManager.hasSavedKey(context) || SecureKeyManager.getGeminiApiKey(context).isNotBlank()
        if (hasKey && isNetworkAvailable()) {
            try {
                val onlineResult = extractTextOnline(bitmap)
                if (onlineResult.isNotBlank()) {
                    return@withContext onlineResult
                }
            } catch (e: Exception) {
                // Fall through to local extraction
            }
        }

        // 1. Arabic-specific pass
        val araResult = recognizeText(bitmap, language = "ara").text.trim()
        val arabicCharCount = araResult.count { it.code in 0x0600..0x06FF }
        if (arabicCharCount >= 4) {
            return@withContext araResult
        }

        // 2. Latin languages pass (German + English)
        val latinResult = recognizeText(bitmap, language = "deu+eng").text.trim()
        if (latinResult.isNotBlank()) {
            return@withContext latinResult
        }

        // 3. Fallback combined pass
        return@withContext recognizeText(bitmap, language = "ara+eng+deu").text.trim()
    }

    fun release() {
        try {
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
            currentLanguage = ""
            Log.d(TAG, "UnifiedOcrEngine resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Tesseract engine", e)
        }
    }
}
