package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.SecureKeyManager
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.hypot

data class CameraOcrResult(
    val text: String,
    val isOnline: Boolean,
    val engineName: String,
    val isApiKeyError: Boolean = false
)

@Composable
fun CameraOcrSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "يمكنك التقاط الصور للكتاب أو اختيار صورة من المعرض", Toast.LENGTH_LONG).show()
        }
    }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<CameraOcrResult?>(null) }
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }
    var scanStatusMessage by remember { mutableStateOf("") }
    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var preferOnlineAi by remember { mutableStateOf(true) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var userApiKeyInput by remember {
        mutableStateOf(SecureKeyManager.getGeminiApiKey(context))
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
            icon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "مفتاح Gemini API للذكاء الاصطناعي",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "أدخل مفتاح Gemini API المجاني الخاص بك للتمتع بدقة 100% في استخراج وتفريغ كافة النصوص والكتب العربية والمستندات المصورة عبر السحابة.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userApiKeyInput,
                        onValueChange = { userApiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (SecureKeyManager.hasSavedKey(context)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF4CAF50).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "✓ مفتاح محفوظ ومفعّل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (userApiKeyInput.trim().isNotBlank()) {
                            SecureKeyManager.saveGeminiApiKey(context, userApiKeyInput.trim())
                            showApiKeyDialog = false
                            Toast.makeText(context, "تم حفظ ومزامنة مفتاح Gemini API بنجاح", Toast.LENGTH_SHORT).show()
                            if (capturedBitmap != null) {
                                coroutineScope.launch {
                                    isScanning = true
                                    scanStatusMessage = "جاري إعادة التحليل باستخدام مفتاح Gemini API الجديد..."
                                    preferOnlineAi = true
                                    ocrResult = processMultiLanguageCameraOcr(context, capturedBitmap!!, true)
                                    isScanning = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "يرجى كتابة المفتاح أولاً", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ وتطبيق")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        userApiKeyInput = ""
                        SecureKeyManager.clearGeminiApiKey(context)
                        showApiKeyDialog = false
                        Toast.makeText(context, "تم مسح المفتاح", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("مسح المفتاح")
                }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (loadedBitmap != null) {
                        capturedBitmap = loadedBitmap
                        cropLeft = 0.05f
                        cropTop = 0.05f
                        cropRight = 0.95f
                        cropBottom = 0.95f
                        ocrResult = null
                    } else {
                        Toast.makeText(context, "تعذر فتح الصورة المحفوظة", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ أثناء قراءة الصورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(fileUri)
                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (loadedBitmap != null) {
                        capturedBitmap = loadedBitmap
                        cropLeft = 0.05f
                        cropTop = 0.05f
                        cropRight = 0.95f
                        cropBottom = 0.95f
                        ocrResult = null
                    } else {
                        Toast.makeText(context, "تم استيراد الملف: ${fileUri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ أثناء فتح الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var isBatchMode by remember { mutableStateOf(false) }
    var selectedScannerMode by remember { mutableStateOf("المسح الضوئي") }
    var isHdMode by remember { mutableStateOf(true) }
    var showMoreOptionsMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top CamScanner Header Bar
                CamScannerTopBar(
                    flashEnabled = flashEnabled,
                    onToggleFlash = {
                        flashEnabled = !flashEnabled
                        camera?.cameraControl?.enableTorch(flashEnabled)
                    },
                    hdEnabled = isHdMode,
                    onToggleHd = {
                        isHdMode = !isHdMode
                        Toast.makeText(context, if (isHdMode) "وضع دقة HD مفعل" else "الوضع العادي", Toast.LENGTH_SHORT).show()
                    },
                    magicEnabled = preferOnlineAi,
                    onToggleMagic = {
                        preferOnlineAi = !preferOnlineAi
                        if (preferOnlineAi && !SecureKeyManager.hasSavedKey(context)) {
                            showApiKeyDialog = true
                        } else {
                            Toast.makeText(context, if (preferOnlineAi) "محسن الذكاء السحابي مفعل" else "المحرك المحلي أوفلاين", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoreClick = { showMoreOptionsMenu = true },
                    onClose = onDismiss
                )

                // Main Camera View Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    if (!hasCameraPermission) {
                        // Permission View inside full screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = Color(0xFF00E5A3),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "إذن الكاميرا مطلوب",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "يتطلب الماسح الضوئي الوصول إلى كاميرا الجهاز لتصوير المستندات والأوراق وقراءتها.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("منح إذن الكاميرا", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    } else if (capturedBitmap != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            InteractiveCropOverlay(
                                bitmap = capturedBitmap!!,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                isScanning = isScanning,
                                onCropChanged = { l, t, r, b ->
                                    cropLeft = l
                                    cropTop = t
                                    cropRight = r
                                    cropBottom = b
                                }
                            )

                            // Instruction hint banner at top of crop view
                            if (ocrResult == null && !isScanning) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5A3),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "اسحب الزوايا الخضراء لتحديد منتصف الصفحة أو الجزء المراد قراءته",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val lifecycleOwner = LocalLifecycleOwner.current

                        DisposableEffect(lifecycleOwner) {
                            onDispose {
                                try {
                                    if (cameraProviderFuture.isDone) {
                                        cameraProviderFuture.get().unbindAll()
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraOcr", "Error unbinding camera on dispose", e)
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    }
                                    previewViewRef = previewView

                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }

                                            val capture = ImageCapture.Builder()
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                                .build()
                                            imageCapture = capture

                                            val cameraSelector = CameraSelector.Builder()
                                                .requireLensFacing(lensFacing)
                                                .build()

                                            cameraProvider.unbindAll()
                                            camera = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                capture
                                            )
                                        } catch (exc: Exception) {
                                            Log.e("CameraOcr", "Use case binding failed", exc)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                },
                                onRelease = {
                                    try {
                                        if (cameraProviderFuture.isDone) {
                                            cameraProviderFuture.get().unbindAll()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraOcr", "Error releasing camera in AndroidView", e)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Interactive Crop Overlay over Live Camera Preview
                            InteractiveCropOverlay(
                                bitmap = null,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                isScanning = isScanning,
                                onCropChanged = { l, t, r, b ->
                                    cropLeft = l
                                    cropTop = t
                                    cropRight = r
                                    cropBottom = b
                                }
                            )

                            // Instruction hint banner at top of camera view
                            if (ocrResult == null && !isScanning) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5A3),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "اسحب الزوايا الخضراء لتحديد الجزء المراد قراءته قبل التصوير",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Framing guidelines (only for live camera stream)
                    if (capturedBitmap == null) {
                        DocumentFrameOverlay(isScanning = isScanning)
                    }

                    // Single vs Batch Pill Selector overlay near bottom of camera preview
                    if (hasCameraPermission && capturedBitmap == null && ocrResult == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            SingleBatchPillSelector(
                                isBatch = isBatchMode,
                                onModeSelected = { isBatchMode = it }
                            )
                        }
                    }

                    // Progress Loader when scanning
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xEE1C182B)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00E5A3),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = scanStatusMessage.ifEmpty { "جاري قراءة وتحليل النص في الجزء المحدد..." },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (preferOnlineAi) "مستخرج النصوص بالذكاء الاصطناعي السحابي" else "محرك ML Kit المحلي",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Control Bars
                if (capturedBitmap != null && ocrResult == null) {
                    CropControlBottomBar(
                        isScanning = isScanning,
                        preferOnlineAi = preferOnlineAi,
                        onScanCrop = {
                            if (capturedBitmap != null) {
                                coroutineScope.launch {
                                    try {
                                        isScanning = true
                                        scanStatusMessage = if (preferOnlineAi) {
                                            "جاري قراءة نص المنطقة المحددة بالسحابة..."
                                        } else {
                                            "جاري قراءة نص المنطقة المحددة محليًا..."
                                        }
                                        val croppedBmp = cropBitmapNormalized(
                                            capturedBitmap!!,
                                            cropLeft,
                                            cropTop,
                                            cropRight,
                                            cropBottom
                                        )
                                        val res = processMultiLanguageCameraOcr(context, croppedBmp, preferOnlineAi)
                                        ocrResult = res
                                        if (res.isApiKeyError) {
                                            showApiKeyDialog = true
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطأ أثناء معالجة الجزء المحدد: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isScanning = false
                                    }
                                }
                            }
                        },
                        onSelectFull = {
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                            Toast.makeText(context, "تم تحديد الصفحة كاملة", Toast.LENGTH_SHORT).show()
                        },
                        onRetake = {
                            capturedBitmap = null
                            ocrResult = null
                        }
                    )
                } else if (capturedBitmap == null) {
                    CamScannerModesBar(
                        selectedMode = selectedScannerMode,
                        onModeSelected = { selectedScannerMode = it }
                    )

                    CamScannerBottomBar(
                        onImportFiles = { filePickerLauncher.launch("*/*") },
                        onImportImages = { galleryLauncher.launch("image/*") },
                        onCapture = {
                            val capture = imageCapture
                            if (capture != null) {
                                val executor = Executors.newSingleThreadExecutor()
                                capture.takePicture(
                                    executor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val rotationDegrees = image.imageInfo.rotationDegrees
                                            val buffer = image.planes[0].buffer
                                            val bytes = ByteArray(buffer.capacity())
                                            buffer.get(bytes)
                                            val originalBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            image.close()

                                            val rotatedBmp = if (rotationDegrees != 0 && originalBmp != null) {
                                                val matrix = Matrix()
                                                matrix.postRotate(rotationDegrees.toFloat())
                                                Bitmap.createBitmap(
                                                    originalBmp,
                                                    0,
                                                    0,
                                                    originalBmp.width,
                                                    originalBmp.height,
                                                    matrix,
                                                    true
                                                )
                                            } else {
                                                originalBmp
                                            }

                                            coroutineScope.launch {
                                                if (rotatedBmp != null) {
                                                    capturedBitmap = rotatedBmp
                                                    cropLeft = 0.05f
                                                    cropTop = 0.05f
                                                    cropRight = 0.95f
                                                    cropBottom = 0.95f
                                                    ocrResult = null
                                                } else {
                                                    Toast.makeText(context, "فشل التقاط الصورة", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraOcr", "Capture failed: ${exception.message}", exception)
                                            coroutineScope.launch {
                                                val previewBmp = previewViewRef?.bitmap
                                                if (previewBmp != null) {
                                                    capturedBitmap = previewBmp
                                                    cropLeft = 0.05f
                                                    cropTop = 0.05f
                                                    cropRight = 0.95f
                                                    cropBottom = 0.95f
                                                    ocrResult = null
                                                } else {
                                                    Toast.makeText(context, "خطأ في التقاط الصورة: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            } else {
                                val bmp = previewViewRef?.bitmap
                                if (bmp != null) {
                                    capturedBitmap = bmp
                                    cropLeft = 0.05f
                                    cropTop = 0.05f
                                    cropRight = 0.95f
                                    cropBottom = 0.95f
                                    ocrResult = null
                                } else {
                                    Toast.makeText(context, "الكاميرا غير جاهزة بعد، يمكنك استيراد صور أو ملفات", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onAllFeatures = { showApiKeyDialog = true }
                    )
                }
            }

            // OCR Result Bottom Sheet overlay when text is recognized
            if (ocrResult != null) {
                val res = ocrResult!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (res.isOnline) Color(0xFFE3F2FD) else Color(0xFFE8F5E9),
                                        shape = CircleShape,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (res.isOnline) Icons.Default.Language else Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (res.isOnline) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = res.engineName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.isOnline) Color(0xFF1565C0) else Color(0xFF2E7D32)
                                    )
                                }

                                Text(
                                    text = "${res.text.length} حرف",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = res.text.ifBlank { "(لم يتم اكتشاف نصوص في المستند المصور)" },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { copyTextToClipboardLocal(context, res.text) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ النص", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { shareTextLocal(context, res.text, "النص المصور بالماسح") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        ocrResult = null
                                        capturedBitmap = null
                                        isScanning = false
                                    },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                        .size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "تصوير جديد",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // More Options Dropdown Menu
            if (showMoreOptionsMenu) {
                DropdownMenu(
                    expanded = showMoreOptionsMenu,
                    onDismissRequest = { showMoreOptionsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("إعدادات Gemini API Key") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        onClick = {
                            showMoreOptionsMenu = false
                            showApiKeyDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("تبديل عدسة الكاميرا") },
                        leadingIcon = { Icon(Icons.Default.Cameraswitch, contentDescription = null) },
                        onClick = {
                            showMoreOptionsMenu = false
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DocumentFrameOverlay(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    
    val laserPositionY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_anim"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_anim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val marginHoriz = width * 0.08f
        val marginVert = height * 0.12f

        val frameLeft = marginHoriz
        val frameTop = marginVert
        val frameRight = width - marginHoriz
        val frameBottom = height - marginVert
        val frameWidth = frameRight - frameLeft
        val frameHeight = frameBottom - frameTop

        // Dark dim exterior
        val dimColor = Color.Black.copy(alpha = 0.45f)
        drawRect(color = dimColor, topLeft = Offset(0f, 0f), size = Size(width, frameTop))
        drawRect(color = dimColor, topLeft = Offset(0f, frameBottom), size = Size(width, height - frameBottom))
        drawRect(color = dimColor, topLeft = Offset(0f, frameTop), size = Size(frameLeft, frameHeight))
        drawRect(color = dimColor, topLeft = Offset(frameRight, frameTop), size = Size(width - frameRight, frameHeight))

        // Dotted rectangular guideline
        val strokeColor = if (isScanning) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.8f)
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        )

        // Corner Frame Brackets
        val cornerLen = 28.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val cornerColor = Color(0xFF00FFCC)

        // Top-Left
        drawLine(cornerColor, Offset(frameLeft, frameTop), Offset(frameLeft + cornerLen, frameTop), cornerStroke)
        drawLine(cornerColor, Offset(frameLeft, frameTop), Offset(frameLeft, frameTop + cornerLen), cornerStroke)

        // Top-Right
        drawLine(cornerColor, Offset(frameRight, frameTop), Offset(frameRight - cornerLen, frameTop), cornerStroke)
        drawLine(cornerColor, Offset(frameRight, frameTop), Offset(frameRight, frameTop + cornerLen), cornerStroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(frameLeft, frameBottom), Offset(frameLeft + cornerLen, frameBottom), cornerStroke)
        drawLine(cornerColor, Offset(frameLeft, frameBottom), Offset(frameLeft, frameBottom - cornerLen), cornerStroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(frameRight, frameBottom), Offset(frameRight - cornerLen, frameBottom), cornerStroke)
        drawLine(cornerColor, Offset(frameRight, frameBottom), Offset(frameRight, frameBottom - cornerLen), cornerStroke)

        // Laser Beam Scanning Line Effect
        val laserY = frameTop + (frameHeight * laserPositionY)
        
        // Gradient glow beam
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00FFCC).copy(alpha = pulseGlow * 0.4f),
                    Color(0xFF00FFCC).copy(alpha = pulseGlow * 0.9f),
                    Color(0xFF00FFCC).copy(alpha = pulseGlow * 0.4f),
                    Color.Transparent
                ),
                startY = laserY - 18.dp.toPx(),
                endY = laserY + 18.dp.toPx()
            ),
            topLeft = Offset(frameLeft + 4.dp.toPx(), laserY - 18.dp.toPx()),
            size = Size(frameWidth - 8.dp.toPx(), 36.dp.toPx())
        )

        // Core laser line
        drawLine(
            color = Color(0xFF00FFCC),
            start = Offset(frameLeft + 2.dp.toPx(), laserY),
            end = Offset(frameRight - 2.dp.toPx(), laserY),
            strokeWidth = 3.dp.toPx()
        )
    }
}

suspend fun processMultiLanguageCameraOcr(
    context: Context,
    bitmap: Bitmap,
    preferOnline: Boolean
): CameraOcrResult = withContext(Dispatchers.IO) {
    var base64Image = ""
    try {
        val maxDim = 2048
        val scaledBmp = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaledBmp.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val apiKey = SecureKeyManager.getGeminiApiKey(context)

    var lastApiError = ""
    var isKeyError = false

    // Try Gemini AI Online OCR if requested or available
    if (preferOnline && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY" && base64Image.isNotBlank()) {
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

            Log.d("GeminiApiDebug", "Sending Gemini Request Payload (first 300 chars): ${jsonPayload.toString().take(300)}...")

            val client = OkHttpClient.Builder()
                .connectTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val candidateModels = listOf(
                "gemini-3.5-flash",
                "gemini-flash-latest"
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
                                    return@withContext CameraOcrResult(
                                        text = extractedText.trim(),
                                        isOnline = true,
                                        engineName = "Gemini AI (عربي / ألماني / إنجليزي)"
                                    )
                                }
                            }
                        }
                    } else {
                        if (response.code == 429) {
                            lastApiError = "وصلت للحد اليومي المجاني (1500 طلب). هيتجدد تلقائيًا الساعة 10:00 صباحاً بتوقيت القاهرة. تقدر تستخدم OCR المحلي (بدون إنترنت) لحد ما يتجدد."
                        } else if (response.code == 401 || response.code == 403) {
                            lastApiError = "مفتاح Gemini API لم يعد صالحًا. من فضلك أدخل مفتاح جديد."
                            isKeyError = true
                        } else if (responseStr.isNotBlank()) {
                            lastApiError = "رمز الاستجابة ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    lastApiError = e.localizedMessage ?: "خطأ بالشبكة"
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            lastApiError = e.localizedMessage ?: "خطأ غير متوقع"
            e.printStackTrace()
        }
    } else if (preferOnline && apiKey.isBlank()) {
        lastApiError = "مفتاح Gemini API غير متوفر. يرجى إدخال المفتاح لاستخدام الذكاء الاصطناعي الأونلاين."
        isKeyError = true
    }

    // Fallback: ML Kit Local OCR
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val visionText = Tasks.await(recognizer.process(inputImage))
        val resultText = visionText.text.trim()

        val noticeHeader = if (preferOnline) {
            "[ملاحظة]: تعذر الاتصال بذكاء Gemini السحابي (${if (lastApiError.isNotBlank()) lastApiError else "مفتاح API غير متوفر أو خطأ بالشبكة"}). تم استخدام المحرك المحلي ML Kit (مخصص للألمانية والإنجليزية والرموز).\n\nلاستخراج النصوص والكتب العربية المصورة بدقة 100%، اضغط على زر [مفتاح API] وأدخل مفتاح Gemini المجاني الخاص بك.\n\n--------------------------------------\n\n"
        } else {
            "[المحرك المحلي ML Kit]: مخصص للكلمات اللاتينية والألمانية والإنجليزية والأرقام.\n\nللحصول على استخراج كامل للكتب والنصوص العربية، يرجى تفعيل [ذكاء أونلاين].\n\n--------------------------------------\n\n"
        }

        val finalText = if (resultText.isNotBlank()) {
            noticeHeader + resultText
        } else {
            noticeHeader + "لم يتم العثور على أرقام أو كلمات ألمانية/إنجليزية بالصورة."
        }

        return@withContext CameraOcrResult(
            text = finalText,
            isOnline = false,
            engineName = "ML Kit محلي (ألماني / إنجليزي)",
            isApiKeyError = isKeyError
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext CameraOcrResult(
            text = "خطأ أثناء معالجة الصورة محلياً: ${e.localizedMessage}",
            isOnline = false,
            engineName = "محلي"
        )
    }
}

fun copyTextToClipboardLocal(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OCR Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "تم نسخ النص للحافظة بنجاح", Toast.LENGTH_SHORT).show()
}

fun shareTextLocal(context: Context, text: String, title: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, title)
    context.startActivity(shareIntent)
}

@Composable
fun CamScannerTopBar(
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    hdEnabled: Boolean,
    onToggleHd: () -> Unit,
    magicEnabled: Boolean,
    onToggleMagic: () -> Unit,
    onMoreClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "المزيد",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                onClick = onToggleHd,
                shape = RoundedCornerShape(4.dp),
                color = if (hdEnabled) Color(0xFF00E5A3) else Color.Transparent,
                border = BorderStroke(1.dp, if (hdEnabled) Color(0xFF00E5A3) else Color.White)
            ) {
                Text(
                    text = "HD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hdEnabled) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            IconButton(onClick = onToggleMagic, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "تحسين ذكي",
                    tint = if (magicEnabled) Color(0xFF00E5A3) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onToggleFlash, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "الفلاش",
                    tint = if (flashEnabled) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SingleBatchPillSelector(
    isBatch: Boolean,
    onModeSelected: (Boolean) -> Unit
) {
    Surface(
        color = Color(0xFF2B2B2B).copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (!isBatch) Color(0xFF666677) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(false) }
            ) {
                Text(
                    text = "فردي",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (!isBatch) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = if (isBatch) Color(0xFF666677) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(true) }
            ) {
                Text(
                    text = "دفعة",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isBatch) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun CamScannerModesBar(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf(
        "إلى Word",
        "التوقيع",
        "المسح الضوئي",
        "المحو الذكي",
        "الهوية",
        "إلى Excel",
        "ترجمة",
        "OCR نصوص"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(modes) { mode ->
            val isSelected = mode == selectedMode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onModeSelected(mode) }
            ) {
                Text(
                    text = mode,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF00E5A3) else Color.White.copy(alpha = 0.7f)
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp, 3.dp)
                            .background(Color(0xFF00E5A3), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun CamScannerBottomBar(
    onImportFiles: () -> Unit,
    onImportImages: () -> Unit,
    onCapture: () -> Unit,
    onAllFeatures: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Import Files
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onImportFiles() }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.InsertDriveFile,
                contentDescription = "استيراد ملفات",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "استيراد ملفات",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        // Import Images
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onImportImages() }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Collections,
                contentDescription = "استيراد صور",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "استيراد صور",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        // Shutter Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(4.dp, Color(0xFF00E5A3), CircleShape)
                .clickable { onCapture() }
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, CircleShape)
            )
        }

        // All Features
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onAllFeatures() }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "كل المميزات",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "كل المميزات",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class CropHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

fun cropBitmapNormalized(
    bmp: Bitmap,
    leftRatio: Float,
    topRatio: Float,
    rightRatio: Float,
    bottomRatio: Float
): Bitmap {
    val l = leftRatio.coerceIn(0f, 1f)
    val t = topRatio.coerceIn(0f, 1f)
    val r = rightRatio.coerceIn(l + 0.01f, 1f)
    val b = bottomRatio.coerceIn(t + 0.01f, 1f)

    val startX = (l * bmp.width).toInt().coerceIn(0, bmp.width - 1)
    val startY = (t * bmp.height).toInt().coerceIn(0, bmp.height - 1)
    val endX = (r * bmp.width).toInt().coerceIn(startX + 1, bmp.width)
    val endY = (b * bmp.height).toInt().coerceIn(startY + 1, bmp.height)

    val width = (endX - startX).coerceAtLeast(1)
    val height = (endY - startY).coerceAtLeast(1)

    return Bitmap.createBitmap(bmp, startX, startY, width, height)
}

@Composable
fun CropControlBottomBar(
    isScanning: Boolean,
    preferOnlineAi: Boolean,
    onScanCrop: () -> Unit,
    onSelectFull: () -> Unit,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14121E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retake / Reselect Image Button
            OutlinedButton(
                onClick = onRetake,
                enabled = !isScanning,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.CropRotate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("صورة جديدة", fontSize = 12.sp)
            }

            // Select Full Page Button
            OutlinedButton(
                onClick = onSelectFull,
                enabled = !isScanning,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5A3)),
                border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("الصفحة كاملة", fontSize = 12.sp)
            }

            // Primary Scan Button
            Button(
                onClick = onScanCrop,
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.4f)
            ) {
                Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "مسح الضوئي",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun InteractiveCropOverlay(
    bitmap: Bitmap?,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    isScanning: Boolean,
    onCropChanged: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    val laserPositionY by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_anim"
    )

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (bitmap != null) Modifier.background(Color.Black) else Modifier)
    ) {
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }

        if (containerWidth > 0f && containerHeight > 0f) {
            val displayedWidth: Float
            val displayedHeight: Float
            val imageX: Float
            val imageY: Float

            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                val bmpWidth = bitmap.width.toFloat()
                val bmpHeight = bitmap.height.toFloat()
                val bmpAspect = bmpWidth / bmpHeight
                val containerAspect = containerWidth / containerHeight

                if (bmpAspect > containerAspect) {
                    displayedWidth = containerWidth
                    displayedHeight = containerWidth / bmpAspect
                    imageX = 0f
                    imageY = (containerHeight - displayedHeight) / 2f
                } else {
                    displayedHeight = containerHeight
                    displayedWidth = containerHeight * bmpAspect
                    imageY = 0f
                    imageX = (containerWidth - displayedWidth) / 2f
                }

                // Base Image Displayed Fit
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "صورة المستند",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                displayedWidth = containerWidth
                displayedHeight = containerHeight
                imageX = 0f
                imageY = 0f
            }

            val rectLeft = imageX + cropLeft * displayedWidth
            val rectTop = imageY + cropTop * displayedHeight
            val rectRight = imageX + cropRight * displayedWidth
            val rectBottom = imageY + cropBottom * displayedHeight

            // Touch & Visual Crop Overlay Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayedWidth, displayedHeight, imageX, imageY) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val touchX = offset.x
                                val touchY = offset.y
                                val touchRadius = 44.dp.toPx()

                                val distTL = hypot((touchX - rectLeft).toDouble(), (touchY - rectTop).toDouble()).toFloat()
                                val distTR = hypot((touchX - rectRight).toDouble(), (touchY - rectTop).toDouble()).toFloat()
                                val distBL = hypot((touchX - rectLeft).toDouble(), (touchY - rectBottom).toDouble()).toFloat()
                                val distBR = hypot((touchX - rectRight).toDouble(), (touchY - rectBottom).toDouble()).toFloat()

                                activeHandle = when {
                                    distTL < touchRadius -> CropHandle.TOP_LEFT
                                    distTR < touchRadius -> CropHandle.TOP_RIGHT
                                    distBL < touchRadius -> CropHandle.BOTTOM_LEFT
                                    distBR < touchRadius -> CropHandle.BOTTOM_RIGHT
                                    touchX in rectLeft..rectRight && touchY in rectTop..rectBottom -> CropHandle.CENTER
                                    else -> CropHandle.NONE
                                }
                            },
                            onDragEnd = { activeHandle = CropHandle.NONE },
                            onDragCancel = { activeHandle = CropHandle.NONE },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                var nLeft = cropLeft
                                var nTop = cropTop
                                var nRight = cropRight
                                var nBottom = cropBottom

                                val deltaX = dragAmount.x / displayedWidth
                                val deltaY = dragAmount.y / displayedHeight

                                val minW = 28.dp.toPx() / displayedWidth
                                val minH = 28.dp.toPx() / displayedHeight

                                when (activeHandle) {
                                    CropHandle.TOP_LEFT -> {
                                        nLeft = (nLeft + deltaX).coerceIn(0f, nRight - minW)
                                        nTop = (nTop + deltaY).coerceIn(0f, nBottom - minH)
                                    }
                                    CropHandle.TOP_RIGHT -> {
                                        nRight = (nRight + deltaX).coerceIn(nLeft + minW, 1f)
                                        nTop = (nTop + deltaY).coerceIn(0f, nBottom - minH)
                                    }
                                    CropHandle.BOTTOM_LEFT -> {
                                        nLeft = (nLeft + deltaX).coerceIn(0f, nRight - minW)
                                        nBottom = (nBottom + deltaY).coerceIn(nTop + minH, 1f)
                                    }
                                    CropHandle.BOTTOM_RIGHT -> {
                                        nRight = (nRight + deltaX).coerceIn(nLeft + minW, 1f)
                                        nBottom = (nBottom + deltaY).coerceIn(nTop + minH, 1f)
                                    }
                                    CropHandle.CENTER -> {
                                        val curW = nRight - nLeft
                                        val curH = nBottom - nTop
                                        val newL = (nLeft + deltaX).coerceIn(0f, 1f - curW)
                                        val newT = (nTop + deltaY).coerceIn(0f, 1f - curH)
                                        nLeft = newL
                                        nRight = newL + curW
                                        nTop = newT
                                        nBottom = newT + curH
                                    }
                                    CropHandle.NONE -> {}
                                }
                                onCropChanged(nLeft, nTop, nRight, nBottom)
                            }
                        )
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                // Dimmed exterior background
                val dimColor = Color.Black.copy(alpha = 0.55f)
                drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(canvasW, rectTop.coerceAtLeast(0f)))
                drawRect(dimColor, topLeft = Offset(0f, rectBottom), size = Size(canvasW, (canvasH - rectBottom).coerceAtLeast(0f)))
                drawRect(dimColor, topLeft = Offset(0f, rectTop), size = Size(rectLeft.coerceAtLeast(0f), (rectBottom - rectTop).coerceAtLeast(0f)))
                drawRect(dimColor, topLeft = Offset(rectRight, rectTop), size = Size((canvasW - rectRight).coerceAtLeast(0f), (rectBottom - rectTop).coerceAtLeast(0f)))

                val cropW = rectRight - rectLeft
                val cropH = rectBottom - rectTop

                if (cropW > 0f && cropH > 0f) {
                    // Dashed Guideline Border
                    val strokeColor = if (isScanning) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.9f)
                    drawRoundRect(
                        color = strokeColor,
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(cropW, cropH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    )

                    // Rule of Thirds Grid lines
                    val gridColor = Color.White.copy(alpha = 0.25f)
                    val strokeGrid = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    drawLine(gridColor, Offset(rectLeft + cropW / 3f, rectTop), Offset(rectLeft + cropW / 3f, rectBottom), strokeGrid.width)
                    drawLine(gridColor, Offset(rectLeft + 2 * cropW / 3f, rectTop), Offset(rectLeft + 2 * cropW / 3f, rectBottom), strokeGrid.width)
                    drawLine(gridColor, Offset(rectLeft, rectTop + cropH / 3f), Offset(rectRight, rectTop + cropH / 3f), strokeGrid.width)
                    drawLine(gridColor, Offset(rectLeft, rectTop + 2 * cropH / 3f), Offset(rectRight, rectTop + 2 * cropH / 3f), strokeGrid.width)

                    // Corner Brackets & Glowing Touch Handles (Neon Cyan #00FFCC)
                    val cornerLen = 28.dp.toPx()
                    val cornerStroke = 4.5f.dp.toPx()
                    val cornerColor = Color(0xFF00FFCC)

                    // Top-Left Corner
                    drawLine(cornerColor, Offset(rectLeft, rectTop), Offset(rectLeft + cornerLen, rectTop), cornerStroke)
                    drawLine(cornerColor, Offset(rectLeft, rectTop), Offset(rectLeft, rectTop + cornerLen), cornerStroke)
                    drawCircle(cornerColor, radius = 6.dp.toPx(), center = Offset(rectLeft, rectTop))

                    // Top-Right Corner
                    drawLine(cornerColor, Offset(rectRight, rectTop), Offset(rectRight - cornerLen, rectTop), cornerStroke)
                    drawLine(cornerColor, Offset(rectRight, rectTop), Offset(rectRight, rectTop + cornerLen), cornerStroke)
                    drawCircle(cornerColor, radius = 6.dp.toPx(), center = Offset(rectRight, rectTop))

                    // Bottom-Left Corner
                    drawLine(cornerColor, Offset(rectLeft, rectBottom), Offset(rectLeft + cornerLen, rectBottom), cornerStroke)
                    drawLine(cornerColor, Offset(rectLeft, rectBottom), Offset(rectLeft, rectBottom - cornerLen), cornerStroke)
                    drawCircle(cornerColor, radius = 6.dp.toPx(), center = Offset(rectLeft, rectBottom))

                    // Bottom-Right Corner
                    drawLine(cornerColor, Offset(rectRight, rectBottom), Offset(rectRight - cornerLen, rectBottom), cornerStroke)
                    drawLine(cornerColor, Offset(rectRight, rectBottom), Offset(rectRight, rectBottom - cornerLen), cornerStroke)
                    drawCircle(cornerColor, radius = 6.dp.toPx(), center = Offset(rectRight, rectBottom))

                    // Laser Beam Scanner Effect if scanning
                    if (isScanning) {
                        val laserY = rectTop + cropH * laserPositionY
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF00FFCC).copy(alpha = 0.35f),
                                    Color(0xFF00FFCC).copy(alpha = 0.9f),
                                    Color(0xFF00FFCC).copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                startY = laserY - 16.dp.toPx(),
                                endY = laserY + 16.dp.toPx()
                            ),
                            topLeft = Offset(rectLeft + 2.dp.toPx(), laserY - 16.dp.toPx()),
                            size = Size(cropW - 4.dp.toPx(), 32.dp.toPx())
                        )
                        drawLine(
                            color = Color(0xFF00FFCC),
                            start = Offset(rectLeft, laserY),
                            end = Offset(rectRight, laserY),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}
