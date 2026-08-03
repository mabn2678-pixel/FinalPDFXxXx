package com.example.ocr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max

/**
 * State Machine Enum for Camera OCR flow:
 * 1. Camera - CameraX Live View
 * 2. Crop - Interactive quadrilateral crop overlay
 * 3. Processing - OCR & Searchable PDF generation
 * 4. Success - Results Dialog
 */
enum class OcrScreenState {
    Camera,
    Crop,
    Processing,
    Success
}

@Composable
fun CameraOcrScreen(
    onBack: () -> Unit = {},
    onOpenPdfViewer: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf(OcrScreenState.Camera) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropPoints by remember {
        mutableStateOf(
            listOf(
                Offset(0.08f, 0.08f), // Top-Left
                Offset(0.92f, 0.08f), // Top-Right
                Offset(0.92f, 0.92f), // Bottom-Right
                Offset(0.08f, 0.92f)  // Bottom-Left
            )
        )
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    var isProcessing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("جاري إنشاء مستند PDF...") }

    var createdPdfFile by remember { mutableStateOf<File?>(null) }
    var extractedTextResult by remember { mutableStateOf<String?>(null) }
    var editableText by remember(extractedTextResult) { mutableStateOf(extractedTextResult ?: "") }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val tesseractManager = remember { TesseractManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraOcrScreen", "Error unbinding camera on dispose", e)
            }
            tesseractManager.release()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (loadedBitmap != null) {
                        capturedBitmap = loadedBitmap
                        cropPoints = listOf(
                            Offset(0.08f, 0.08f),
                            Offset(0.92f, 0.08f),
                            Offset(0.92f, 0.92f),
                            Offset(0.08f, 0.92f)
                        )
                        screenState = OcrScreenState.Crop
                    } else {
                        Toast.makeText(context, "تعذر فتح الصورة المحفوظة", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ أثناء قراءة الصورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val handleBackPress: () -> Unit = {
        when {
            createdPdfFile != null || screenState == OcrScreenState.Success -> {
                createdPdfFile = null
                extractedTextResult = null
                screenState = OcrScreenState.Crop
            }
            capturedBitmap != null || screenState == OcrScreenState.Crop -> {
                capturedBitmap = null
                screenState = OcrScreenState.Camera
            }
            else -> {
                onBack()
            }
        }
    }

    BackHandler(enabled = true) {
        handleBackPress()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF0D0E15))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Checkpoint 1: Top Bar Header
            Surface(
                color = Color(0xFF141622),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = handleBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "ماسح الكاميرا الضوئي",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Central Area depending on State Machine
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (screenState) {
                    OcrScreenState.Camera -> {
                        if (!hasCameraPermission) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "يلزم السماح بالوصول إلى الكاميرا لالتقاط المستندات",
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3))
                                    ) {
                                        Text("منح إذن الكاميرا", color = Color.Black)
                                    }
                                }
                            }
                        } else {
                            DisposableEffect(lifecycleOwner) {
                                onDispose {
                                    try {
                                        if (cameraProviderFuture.isDone) {
                                            cameraProviderFuture.get().unbindAll()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraOcrScreen", "Camera unbind on state exit failed", e)
                                    }
                                }
                            }

                            // CameraX Live Preview with 3x3 Grid Overlay
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx)
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build()
                                        val selector = CameraSelector.Builder()
                                            .requireLensFacing(lensFacing)
                                            .build()

                                        imageCapture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                            .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                                            .build()

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                imageCapture
                                            )
                                            preview.setSurfaceProvider(previewView.surfaceProvider)
                                        } catch (e: Exception) {
                                            Log.e("CameraOcrScreen", "Camera binding failed", e)
                                        }
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Transparent 3x3 Grid Overlay
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val gridColor = Color.White.copy(alpha = 0.25f)
                                    val stroke = 1.dp.toPx()

                                    drawLine(color = gridColor, start = Offset(w / 3f, 0f), end = Offset(w / 3f, h), strokeWidth = stroke)
                                    drawLine(color = gridColor, start = Offset(2 * w / 3f, 0f), end = Offset(2 * w / 3f, h), strokeWidth = stroke)

                                    drawLine(color = gridColor, start = Offset(0f, h / 3f), end = Offset(w, h / 3f), strokeWidth = stroke)
                                    drawLine(color = gridColor, start = Offset(0f, 2 * h / 3f), end = Offset(w, 2 * h / 3f), strokeWidth = stroke)
                                }
                            }
                        }
                    }

                    OcrScreenState.Crop -> {
                        capturedBitmap?.let { bmp ->
                            ScannerCropOverlay(
                                bitmap = bmp,
                                points = cropPoints,
                                onPointsChanged = { updated -> cropPoints = updated },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    OcrScreenState.Processing, OcrScreenState.Success -> {
                        // Display cropped image preview during processing
                        capturedBitmap?.let { bmp ->
                            ScannerCropOverlay(
                                bitmap = bmp,
                                points = cropPoints,
                                onPointsChanged = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Checkpoint 3: Processing Loading Overlay
                if (screenState == OcrScreenState.Processing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1F2232))
                                .padding(32.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5A3),
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = progressMessage,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom Bar Section
            when (screenState) {
                OcrScreenState.Camera -> {
                    // Checkpoint 1: Camera Dark Bottom Bar
                    Surface(
                        color = Color(0xFF141622),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Flash Toggle Icon (Left)
                            IconButton(
                                onClick = {
                                    flashEnabled = !flashEnabled
                                    imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF262B40), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "الفلاش",
                                    tint = if (flashEnabled) Color(0xFFFFD700) else Color.White
                                )
                            }

                            // Capture Button (Center) - Large 72.dp Circle in 0xFF00E5A3 with Scanner Icon
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5A3))
                                    .clickable {
                                        val capture = imageCapture ?: return@clickable
                                        val executor = ContextCompat.getMainExecutor(context)
                                        capture.takePicture(
                                            executor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val bmp = imageProxyToBitmap(image)
                                                    image.close()
                                                    if (bmp != null) {
                                                        capturedBitmap = bmp
                                                        cropPoints = listOf(
                                                            Offset(0.08f, 0.08f),
                                                            Offset(0.92f, 0.08f),
                                                            Offset(0.92f, 0.92f),
                                                            Offset(0.08f, 0.92f)
                                                        )
                                                        screenState = OcrScreenState.Crop
                                                    } else {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                "تعذر التقاط الصورة",
                                                                Toast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            "خطأ أثناء الالتقاط: ${exception.localizedMessage}",
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "التقاط مستند",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Gallery Button (Right)
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF262B40), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "معرض الصور",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                OcrScreenState.Crop -> {
                    // Checkpoint 2: Crop Bottom Bar with 3 Modern Cards
                    Surface(
                        color = Color(0xFF141622),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Card 1: "صورة جديدة" (Dark Card)
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        capturedBitmap = null
                                        screenState = OcrScreenState.Camera
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "صورة جديدة",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Card 2: "الصفحة كاملة" (Dark Card with Border)
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        cropPoints = listOf(
                                            Offset(0.0f, 0.0f),
                                            Offset(1.0f, 0.0f),
                                            Offset(1.0f, 1.0f),
                                            Offset(0.0f, 1.0f)
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "الصفحة كاملة",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Card 3: "مسح ضوئي" (Highlighted Card 0xFF00E5A3)
                            Surface(
                                color = Color(0xFF00E5A3),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clickable {
                                        val bmp = capturedBitmap ?: return@clickable
                                        screenState = OcrScreenState.Processing
                                        progressMessage = "جاري تنفيذ القص والتعرف الضوئي..."

                                        coroutineScope.launch {
                                            try {
                                                // Perspective Crop & Warp using user-selected crop points
                                                val croppedBmp = cropAndWarpBitmap(
                                                    bitmap = bmp,
                                                    tl = cropPoints[0],
                                                    tr = cropPoints[1],
                                                    bl = cropPoints[3],
                                                    br = cropPoints[2]
                                                )

                                                progressMessage = "جاري التعرف على النصوص Tesseract..."
                                                val combinedLanguages = withContext(Dispatchers.IO) {
                                                    tesseractManager.getAvailableLanguagesCombined()
                                                }
                                                val ocrData = withContext(Dispatchers.IO) {
                                                    tesseractManager.extractTextWithCoordinates(croppedBmp, combinedLanguages)
                                                }
                                                val fullText = withContext(Dispatchers.IO) {
                                                    tesseractManager.extractFullText(croppedBmp, combinedLanguages)
                                                }
                                                extractedTextResult = fullText

                                                progressMessage = "جاري إنشاء مستند PDF..."
                                                val pdfDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
                                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                val fileName = "Scanner_OCR_$timestamp.pdf"
                                                val outputFile = File(pdfDir, fileName)

                                                val createdFile = withContext(Dispatchers.IO) {
                                                    PdfBoxGenerator.createSearchablePdf(
                                                        context = context,
                                                        image = croppedBmp,
                                                        ocrData = ocrData,
                                                        outputPath = outputFile.absolutePath
                                                    )
                                                }

                                                createdPdfFile = createdFile
                                                screenState = OcrScreenState.Success
                                            } catch (e: Exception) {
                                                Log.e("CameraOcrScreen", "Error during scan processing", e)
                                                Toast.makeText(context, "حدث خطأ أثناء المعالجة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                screenState = OcrScreenState.Crop
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "مسح ضوئي",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {}
            }
        }

        // Checkpoint 1 & 2: Full-Screen Result Overlay for Success State
        AnimatedVisibility(
            visible = screenState == OcrScreenState.Success && createdPdfFile != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            createdPdfFile?.let { file ->
                Surface(
                    color = Color(0xFF141622),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 1. Top Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF00E5A3).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "تم إنشاء مستند PDF بنجاح!",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Copy All Button
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clickable {
                                        val textToCopy = editableText
                                        if (textToCopy.isNotEmpty()) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Extracted OCR Text", textToCopy)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ النص للحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "لا يوجد نص لنسخه", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "نسخ الكل",
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "نسخ الكل",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Text Area (Takes remaining vertical space weight(1f))
                        Surface(
                            color = Color(0xFF262B40),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                            ) {
                                BasicTextField(
                                    value = editableText,
                                    onValueChange = { editableText = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        textAlign = TextAlign.Start
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF00E5A3)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Bottom Fixed Actions
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary Wide Button: "فتح المستند PDF"
                            Button(
                                onClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(viewIntent, "فتح المستند بواسطة"))
                                    } catch (e: Exception) {
                                        Log.e("CameraOcrScreen", "Error opening external viewer", e)
                                        Toast.makeText(context, "تعذر الفتح في تطبيق خارجي، جاري الفتح في القارئ المدمج", Toast.LENGTH_SHORT).show()
                                        onOpenPdfViewer(file)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("فتح المستند PDF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Row of 2 Equal Buttons: "مشاركة المستند" and "القارئ المدمج"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة PDF القابل للبحث"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF262B40),
                                        contentColor = Color(0xFF00E5A3)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة المستند", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onOpenPdfViewer(file)
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF262B40),
                                        contentColor = Color(0xFF00E5A3)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("القارئ المدمج", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Final Wide Button: "مسح مستند جديد"
                            OutlinedButton(
                                onClick = {
                                    createdPdfFile = null
                                    capturedBitmap = null
                                    extractedTextResult = null
                                    screenState = OcrScreenState.Camera
                                },
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("مسح مستند جديد", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

/**
 * Checkpoint 1: Data class representing the 4 quadrilateral crop nodes
 */
data class CropPoints(
    var tl: Offset,
    var tr: Offset,
    var bl: Offset,
    var br: Offset
) {
    fun toList(): List<Offset> = listOf(tl, tr, br, bl)

    companion object {
        fun default(): CropPoints = CropPoints(
            tl = Offset(0.08f, 0.08f),
            tr = Offset(0.92f, 0.08f),
            bl = Offset(0.08f, 0.92f),
            br = Offset(0.92f, 0.92f)
        )

        fun fromList(list: List<Offset>): CropPoints {
            if (list.size >= 4) {
                return CropPoints(
                    tl = list[0],
                    tr = list[1],
                    br = list[2],
                    bl = list[3]
                )
            }
            return default()
        }
    }
}

/**
 * Checkpoints 1, 2, 3: Interactive Perspective Crop Overlay Composable
 * Applies pointerInput with detectDragGestures directly on Canvas.
 * Uses 4 distinct Compose state variables and rememberUpdatedState for zero-latency smooth drag response.
 */
@Composable
fun ScannerCropOverlay(
    bitmap: Bitmap,
    points: List<Offset>, // 4 points [TL, TR, BR, BL] normalized 0..1
    onPointsChanged: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeNode by remember { mutableStateOf<Int?>(null) } // 0=TL, 1=TR, 2=BR, 3=BL
    var magnifierSourceCenter by remember { mutableStateOf(Offset.Unspecified) }

    var tl by remember(bitmap) { mutableStateOf(points.getOrElse(0) { Offset(0.08f, 0.08f) }) }
    var tr by remember(bitmap) { mutableStateOf(points.getOrElse(1) { Offset(0.92f, 0.08f) }) }
    var br by remember(bitmap) { mutableStateOf(points.getOrElse(2) { Offset(0.92f, 0.92f) }) }
    var bl by remember(bitmap) { mutableStateOf(points.getOrElse(3) { Offset(0.08f, 0.92f) }) }

    LaunchedEffect(points) {
        if (activeNode == null && points.size >= 4) {
            tl = points[0]
            tr = points[1]
            br = points[2]
            bl = points[3]
        }
    }

    val srcWidth = bitmap.width.toFloat()
    val srcHeight = bitmap.height.toFloat()

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    val scale = if (canvasWidth > 0f && canvasHeight > 0f && srcWidth > 0f && srcHeight > 0f) {
        minOf(canvasWidth / srcWidth, canvasHeight / srcHeight)
    } else 1f
    val dispW = if (canvasWidth > 0f) srcWidth * scale else 1f
    val dispH = if (canvasHeight > 0f) srcHeight * scale else 1f
    val offsetX = (canvasWidth - dispW) / 2f
    val offsetY = (canvasHeight - dispH) / 2f

    val pxTL = Offset(offsetX + tl.x * dispW, offsetY + tl.y * dispH)
    val pxTR = Offset(offsetX + tr.x * dispW, offsetY + tr.y * dispH)
    val pxBR = Offset(offsetX + br.x * dispW, offsetY + br.y * dispH)
    val pxBL = Offset(offsetX + bl.x * dispW, offsetY + bl.y * dispH)

    val currentPxTL by rememberUpdatedState(pxTL)
    val currentPxTR by rememberUpdatedState(pxTR)
    val currentPxBR by rememberUpdatedState(pxBR)
    val currentPxBL by rememberUpdatedState(pxBL)
    val currentDispW by rememberUpdatedState(dispW)
    val currentDispH by rememberUpdatedState(dispH)

    val orangeColor = Color(0xFFFF7A00)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { layoutCoordinates ->
                canvasWidth = layoutCoordinates.size.width.toFloat()
                canvasHeight = layoutCoordinates.size.height.toFloat()
            }
            .magnifier(
                sourceCenter = { magnifierSourceCenter },
                magnifierCenter = {
                    if (magnifierSourceCenter.isSpecified) {
                        Offset(magnifierSourceCenter.x, magnifierSourceCenter.y - 250f)
                    } else {
                        Offset.Unspecified
                    }
                },
                zoom = 2.0f,
                size = DpSize(120.dp, 120.dp),
                cornerRadius = 60.dp
            )
            .pointerInput(bitmap) {
                val touchRadius = 150.dp.toPx() // Generous touch target threshold for easy finger capture

                detectDragGestures(
                    onDragStart = { touchPos ->
                        val distTl = (touchPos - currentPxTL).getDistance()
                        val distTr = (touchPos - currentPxTR).getDistance()
                        val distBr = (touchPos - currentPxBR).getDistance()
                        val distBl = (touchPos - currentPxBL).getDistance()

                        val minDist = minOf(distTl, distTr, distBr, distBl)
                        if (minDist <= touchRadius) {
                            activeNode = when (minDist) {
                                distTl -> 0
                                distTr -> 1
                                distBr -> 2
                                else -> 3
                            }
                            magnifierSourceCenter = touchPos
                        } else {
                            activeNode = null
                            magnifierSourceCenter = Offset.Unspecified
                        }
                    },
                    onDragEnd = {
                        activeNode = null
                        magnifierSourceCenter = Offset.Unspecified
                    },
                    onDragCancel = {
                        activeNode = null
                        magnifierSourceCenter = Offset.Unspecified
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeNode != null) {
                            magnifierSourceCenter = change.position
                        }
                        val dW = if (currentDispW > 0f) currentDispW else 1f
                        val dH = if (currentDispH > 0f) currentDispH else 1f

                        when (activeNode) {
                            0 -> tl = Offset((tl.x + dragAmount.x / dW).coerceIn(0f, 1f), (tl.y + dragAmount.y / dH).coerceIn(0f, 1f))
                            1 -> tr = Offset((tr.x + dragAmount.x / dW).coerceIn(0f, 1f), (tr.y + dragAmount.y / dH).coerceIn(0f, 1f))
                            2 -> br = Offset((br.x + dragAmount.x / dW).coerceIn(0f, 1f), (br.y + dragAmount.y / dH).coerceIn(0f, 1f))
                            3 -> bl = Offset((bl.x + dragAmount.x / dW).coerceIn(0f, 1f), (bl.y + dragAmount.y / dH).coerceIn(0f, 1f))
                        }
                        onPointsChanged(listOf(tl, tr, br, bl))
                    }
                )
            }
    ) {
        // 1. Draw Bitmap background
        val androidMatrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
        drawContext.canvas.nativeCanvas.drawBitmap(
            bitmap,
            androidMatrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        // 2. Crop Quadrilateral Path (TL -> TR -> BR -> BL -> Close)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pxTL.x, pxTL.y)
            lineTo(pxTR.x, pxTR.y)
            lineTo(pxBR.x, pxBR.y)
            lineTo(pxBL.x, pxBL.y)
            close()
        }

        // 3. Draw Semi-transparent Black Overlay (0.6f alpha) outside crop quadrilateral
        clipPath(path, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
        }

        // 4. Draw Thick Orange Border Line (0xFFFF7A00)
        drawPath(
            path = path,
            color = orangeColor,
            style = Stroke(width = 4.dp.toPx())
        )

        // 5. Draw 4 Large Orange Circles (25.dp radius) with Inner White Circles
        val nodesPx = listOf(pxTL, pxTR, pxBR, pxBL)
        val handleRadius = 25.dp.toPx()
        val innerDotRadius = 8.dp.toPx()

        nodesPx.forEachIndexed { idx, pxOffset ->
            val isActive = (idx == activeNode)
            val r = if (isActive) handleRadius * 1.25f else handleRadius

            // Outer shadow glow
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = r + 4.dp.toPx(),
                center = pxOffset
            )
            // Solid Orange Handle Circle
            drawCircle(
                color = orangeColor,
                radius = r,
                center = pxOffset
            )
            // Inner Precision White Circle
            drawCircle(
                color = Color.White,
                radius = innerDotRadius,
                center = pxOffset
            )
        }
    }
}

/**
 * Overloaded ScannerCropOverlay taking bitmap dimensions and callback returning individual Offset points
 */
@Composable
fun ScannerCropOverlay(
    modifier: Modifier = Modifier,
    bitmapWidth: Float,
    bitmapHeight: Float,
    onCropPointsChanged: (tl: Offset, tr: Offset, bl: Offset, br: Offset) -> Unit
) {
    var tl by remember { mutableStateOf(Offset(100f, 100f)) }
    var tr by remember { mutableStateOf(Offset(bitmapWidth - 100f, 100f)) }
    var bl by remember { mutableStateOf(Offset(100f, bitmapHeight - 100f)) }
    var br by remember { mutableStateOf(Offset(bitmapWidth - 100f, bitmapHeight - 100f)) }

    var activeNode by remember { mutableStateOf<Int?>(null) }
    var magnifierSourceCenter by remember { mutableStateOf(Offset.Unspecified) }
    val touchRadius = 150f

    val currentTL by rememberUpdatedState(tl)
    val currentTR by rememberUpdatedState(tr)
    val currentBL by rememberUpdatedState(bl)
    val currentBR by rememberUpdatedState(br)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .magnifier(
                sourceCenter = { magnifierSourceCenter },
                magnifierCenter = {
                    if (magnifierSourceCenter.isSpecified) {
                        Offset(magnifierSourceCenter.x, magnifierSourceCenter.y - 250f)
                    } else {
                        Offset.Unspecified
                    }
                },
                zoom = 2.0f,
                size = DpSize(120.dp, 120.dp),
                cornerRadius = 60.dp
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touchPos ->
                        val distTl = (touchPos - currentTL).getDistance()
                        val distTr = (touchPos - currentTR).getDistance()
                        val distBl = (touchPos - currentBL).getDistance()
                        val distBr = (touchPos - currentBR).getDistance()

                        val minDist = minOf(distTl, distTr, distBl, distBr)
                        if (minDist <= touchRadius) {
                            activeNode = when (minDist) {
                                distTl -> 0
                                distTr -> 1
                                distBl -> 2
                                else -> 3
                            }
                            magnifierSourceCenter = touchPos
                        } else {
                            activeNode = null
                            magnifierSourceCenter = Offset.Unspecified
                        }
                    },
                    onDragEnd = {
                        activeNode = null
                        magnifierSourceCenter = Offset.Unspecified
                    },
                    onDragCancel = {
                        activeNode = null
                        magnifierSourceCenter = Offset.Unspecified
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeNode != null) {
                            magnifierSourceCenter = change.position
                        }
                        when (activeNode) {
                            0 -> tl += dragAmount
                            1 -> tr += dragAmount
                            2 -> bl += dragAmount
                            3 -> br += dragAmount
                        }
                        onCropPointsChanged(tl, tr, bl, br)
                    }
                )
            }
    ) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }

        clipPath(path, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
        }

        drawPath(
            path = path,
            color = Color(0xFFFF7A00),
            style = Stroke(width = 8f)
        )

        val nodeRadius = 40f
        drawCircle(color = Color(0xFFFF7A00), radius = nodeRadius, center = tl)
        drawCircle(color = Color(0xFFFF7A00), radius = nodeRadius, center = tr)
        drawCircle(color = Color(0xFFFF7A00), radius = nodeRadius, center = bl)
        drawCircle(color = Color(0xFFFF7A00), radius = nodeRadius, center = br)
    }
}

/**
 * Checkpoint 4: Perspective Transform & Crop Helper
 * Crops image bounded by 4 points and applies perspective transform flattening the quadrilateral into a clean rectangle.
 */
/**
 * Checkpoint 1: Perspective Transform & Crop Function
 * Crops and warps the quadrilateral bounded by 4 points (tl, tr, bl, br) into a clean, flat rectangle Bitmap.
 * Supports both normalized points (0..1) and view pixel coordinates.
 */
fun cropAndWarpBitmap(
    bitmap: Bitmap,
    tl: Offset,
    tr: Offset,
    bl: Offset,
    br: Offset,
    viewWidth: Float = 0f,
    viewHeight: Float = 0f
): Bitmap {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    val rawTL: Offset
    val rawTR: Offset
    val rawBL: Offset
    val rawBR: Offset

    if (viewWidth > 0f && viewHeight > 0f && (tl.x > 1.0f || tl.y > 1.0f || tr.x > 1.0f || tr.y > 1.0f)) {
        val scale = minOf(viewWidth / srcW, viewHeight / srcH)
        val dispW = srcW * scale
        val dispH = srcH * scale
        val offsetX = (viewWidth - dispW) / 2f
        val offsetY = (viewHeight - dispH) / 2f

        fun mapToBitmap(pt: Offset): Offset {
            val normX = ((pt.x - offsetX) / dispW).coerceIn(0f, 1f)
            val normY = ((pt.y - offsetY) / dispH).coerceIn(0f, 1f)
            return Offset(normX * srcW, normY * srcH)
        }

        rawTL = mapToBitmap(tl)
        rawTR = mapToBitmap(tr)
        rawBL = mapToBitmap(bl)
        rawBR = mapToBitmap(br)
    } else {
        rawTL = Offset((tl.x * srcW).coerceIn(0f, srcW), (tl.y * srcH).coerceIn(0f, srcH))
        rawTR = Offset((tr.x * srcW).coerceIn(0f, srcW), (tr.y * srcH).coerceIn(0f, srcH))
        rawBL = Offset((bl.x * srcW).coerceIn(0f, srcW), (bl.y * srcH).coerceIn(0f, srcH))
        rawBR = Offset((br.x * srcW).coerceIn(0f, srcW), (br.y * srcH).coerceIn(0f, srcH))
    }

    // Expand points slightly outward (2% of image dimensions) to ensure white border around text
    val padX = (srcW * 0.02f).coerceAtLeast(12f)
    val padY = (srcH * 0.02f).coerceAtLeast(12f)

    val pTL = Offset((rawTL.x - padX).coerceIn(0f, srcW), (rawTL.y - padY).coerceIn(0f, srcH))
    val pTR = Offset((rawTR.x + padX).coerceIn(0f, srcW), (rawTR.y - padY).coerceIn(0f, srcH))
    val pBL = Offset((rawBL.x - padX).coerceIn(0f, srcW), (rawBL.y + padY).coerceIn(0f, srcH))
    val pBR = Offset((rawBR.x + padX).coerceIn(0f, srcW), (rawBR.y + padY).coerceIn(0f, srcH))

    val widthA = hypot((pBR.x - pBL.x).toDouble(), (pBR.y - pBL.y).toDouble()).toFloat()
    val widthB = hypot((pTR.x - pTL.x).toDouble(), (pTR.y - pTL.y).toDouble()).toFloat()
    val targetWidth = maxOf(widthA, widthB).coerceAtLeast(50f)

    val heightA = hypot((pTR.x - pBR.x).toDouble(), (pTR.y - pBR.y).toDouble()).toFloat()
    val heightB = hypot((pTL.x - pBL.x).toDouble(), (pTL.y - pBL.y).toDouble()).toFloat()
    val targetHeight = maxOf(heightA, heightB).coerceAtLeast(50f)

    val srcPoints = floatArrayOf(
        pTL.x, pTL.y,
        pTR.x, pTR.y,
        pBR.x, pBR.y,
        pBL.x, pBL.y
    )
    val dstPoints = floatArrayOf(
        0f, 0f,
        targetWidth, 0f,
        targetWidth, targetHeight,
        0f, targetHeight
    )

    val matrix = Matrix()
    matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

    val resultBmp = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, matrix, paint)

    return resultBmp
}

fun cropPerspective(
    bitmap: Bitmap,
    tlNorm: Offset,
    trNorm: Offset,
    brNorm: Offset,
    blNorm: Offset
): Bitmap {
    return cropAndWarpBitmap(bitmap, tl = tlNorm, tr = trNorm, bl = blNorm, br = brNorm)
}

fun cropPerspective(bitmap: Bitmap, cropPoints: CropPoints): Bitmap {
    return cropPerspective(bitmap, cropPoints.tl, cropPoints.tr, cropPoints.br, cropPoints.bl)
}

/**
 * Convert bitmap to high-contrast grayscale to optimize Tesseract OCR text reading precision.
 */
fun toGrayscaleAndContrast(bmp: Bitmap, contrastFactor: Float = 1.6f): Bitmap {
    val width = bmp.width
    val height = bmp.height
    val result = Bitmap.createBitmap(width, height, bmp.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    val grayMatrix = ColorMatrix().apply {
        setSaturation(0f)
    }

    val scale = contrastFactor
    val translate = (-0.5f * scale + 0.5f) * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )

    grayMatrix.postConcat(contrastMatrix)
    paint.colorFilter = ColorMatrixColorFilter(grayMatrix)
    canvas.drawBitmap(bmp, 0f, 0f, paint)

    return result
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotationDegrees = image.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}
