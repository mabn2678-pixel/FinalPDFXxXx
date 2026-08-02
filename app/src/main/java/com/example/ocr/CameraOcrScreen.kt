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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val tesseractManager = remember { TesseractManager(context) }

    DisposableEffect(Unit) {
        onDispose {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    IconButton(onClick = onBack) {
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Auto-Language Badge (Compact pill shape)
                    Surface(
                        color = Color(0xFF262B40),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF00E5A3),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto",
                                color = Color(0xFF00E5A3),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "صورة جديدة",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
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
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الصفحة كاملة",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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
                                                // Perspective Crop
                                                val croppedBmp = cropPerspective(
                                                    bmp,
                                                    cropPoints[0],
                                                    cropPoints[1],
                                                    cropPoints[2],
                                                    cropPoints[3]
                                                )

                                                // B&W Contrast Enhancement for Tesseract
                                                val enhancedBmp = toGrayscaleAndContrast(croppedBmp, 1.6f)

                                                progressMessage = "جاري التعرف على النصوص Tesseract..."
                                                val combinedLanguages = withContext(Dispatchers.IO) {
                                                    tesseractManager.getAvailableLanguagesCombined()
                                                }
                                                val ocrData = withContext(Dispatchers.IO) {
                                                    tesseractManager.extractTextWithCoordinates(enhancedBmp, combinedLanguages)
                                                }
                                                val fullText = withContext(Dispatchers.IO) {
                                                    tesseractManager.extractFullText(enhancedBmp, combinedLanguages)
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
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مسح ضوئي",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
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
                                        extractedTextResult?.let { textToCopy ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Extracted OCR Text", textToCopy)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ النص للحافظة بنجاح", Toast.LENGTH_SHORT).show()
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
                                    .padding(16.dp)
                            ) {
                                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = extractedTextResult.takeIf { !it.isNull_or_empty() }
                                            ?: "لم يتم العثور على نص واضح في المستند",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
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
 * Checkpoints 1, 2, 3: Interactive Perspective Crop Composable
 * Draws the bitmap, semi-transparent black overlay mask, orange boundary path (0xFFFF7A00),
 * and 4 orange touch node handles (radius 40f) responsive to touch drag gestures.
 */
@Composable
fun InteractivePerspectiveCrop(
    bitmap: Bitmap,
    cropPoints: CropPoints,
    onCropPointsChanged: (CropPoints) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeNodeName by remember { mutableStateOf<String?>(null) } // "TL", "TR", "BL", "BR"

    val srcWidth = bitmap.width.toFloat()
    val srcHeight = bitmap.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasSize = it.size }
    ) {
        val cWidth = canvasSize.width.toFloat()
        val cHeight = canvasSize.height.toFloat()

        if (cWidth > 0 && cHeight > 0 && srcWidth > 0 && srcHeight > 0) {
            val scale = minOf(cWidth / srcWidth, cHeight / srcHeight)
            val dispW = srcWidth * scale
            val dispH = srcHeight * scale
            val offsetX = (cWidth - dispW) / 2f
            val offsetY = (cHeight - dispH) / 2f

            // Screen pixel coordinates for 4 nodes
            val pxTL = Offset(offsetX + cropPoints.tl.x * dispW, offsetY + cropPoints.tl.y * dispH)
            val pxTR = Offset(offsetX + cropPoints.tr.x * dispW, offsetY + cropPoints.tr.y * dispH)
            val pxBR = Offset(offsetX + cropPoints.br.x * dispW, offsetY + cropPoints.br.y * dispH)
            val pxBL = Offset(offsetX + cropPoints.bl.x * dispW, offsetY + cropPoints.bl.y * dispH)

            val orangeColor = Color(0xFFFF7A00)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cropPoints, canvasSize) {
                        detectDragGestures(
                            onDragStart = { touchOffset ->
                                val distTL = hypot((pxTL.x - touchOffset.x).toDouble(), (pxTL.y - touchOffset.y).toDouble()).toFloat()
                                val distTR = hypot((pxTR.x - touchOffset.x).toDouble(), (pxTR.y - touchOffset.y).toDouble()).toFloat()
                                val distBR = hypot((pxBR.x - touchOffset.x).toDouble(), (pxBR.y - touchOffset.y).toDouble()).toFloat()
                                val distBL = hypot((pxBL.x - touchOffset.x).toDouble(), (pxBL.y - touchOffset.y).toDouble()).toFloat()

                                val nodeDists = listOf(
                                    "TL" to distTL,
                                    "TR" to distTR,
                                    "BR" to distBR,
                                    "BL" to distBL
                                )
                                val closest = nodeDists.minByOrNull { it.second }
                                if (closest != null && closest.second <= 100f) {
                                    activeNodeName = closest.first
                                } else {
                                    activeNodeName = null
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                activeNodeName?.let { node ->
                                    val currentNorm = when (node) {
                                        "TL" -> cropPoints.tl
                                        "TR" -> cropPoints.tr
                                        "BR" -> cropPoints.br
                                        "BL" -> cropPoints.bl
                                        else -> Offset.Zero
                                    }
                                    val currentPx = Offset(offsetX + currentNorm.x * dispW, offsetY + currentNorm.y * dispH)
                                    val newPx = currentPx + dragAmount
                                    val newNormX = ((newPx.x - offsetX) / dispW).coerceIn(0f, 1f)
                                    val newNormY = ((newPx.y - offsetY) / dispH).coerceIn(0f, 1f)
                                    val updatedNorm = Offset(newNormX, newNormY)

                                    val newCropPoints = when (node) {
                                        "TL" -> cropPoints.copy(tl = updatedNorm)
                                        "TR" -> cropPoints.copy(tr = updatedNorm)
                                        "BR" -> cropPoints.copy(br = updatedNorm)
                                        "BL" -> cropPoints.copy(bl = updatedNorm)
                                        else -> cropPoints
                                    }
                                    onCropPointsChanged(newCropPoints)
                                }
                            },
                            onDragEnd = { activeNodeName = null },
                            onDragCancel = { activeNodeName = null }
                        )
                    }
            ) {
                // 1. Draw Bitmap
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
                val quadPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pxTL.x, pxTL.y)
                    lineTo(pxTR.x, pxTR.y)
                    lineTo(pxBR.x, pxBR.y)
                    lineTo(pxBL.x, pxBL.y)
                    close()
                }

                // 3. Draw Semi-transparent Black Overlay outside selection
                val fullScreenPath = androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, cWidth, cHeight))
                }
                val dimOutsidePath = androidx.compose.ui.graphics.Path.combine(
                    androidx.compose.ui.graphics.PathOperation.Difference,
                    fullScreenPath,
                    quadPath
                )
                drawPath(
                    path = dimOutsidePath,
                    color = Color.Black.copy(alpha = 0.55f)
                )

                // 4. Draw Thick Orange Border Line (0xFFFF7A00)
                drawPath(
                    path = quadPath,
                    color = orangeColor,
                    style = Stroke(width = 3.5.dp.toPx())
                )

                // 5. Draw 4 Orange Node Circles (radius 40f) for clear touch handle interaction
                val nodesList = listOf(
                    "TL" to pxTL,
                    "TR" to pxTR,
                    "BR" to pxBR,
                    "BL" to pxBL
                )

                nodesList.forEach { (name, pxOffset) ->
                    val isActive = (name == activeNodeName)
                    val baseRadius = 40f
                    val outerRadius = if (isActive) baseRadius + 12f else baseRadius

                    // Outer soft shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = outerRadius + 4f,
                        center = pxOffset
                    )
                    // Orange Circle (0xFFFF7A00)
                    drawCircle(
                        color = orangeColor,
                        radius = outerRadius,
                        center = pxOffset
                    )
                    // Inner White Precision Dot
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = pxOffset
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerCropOverlay(
    bitmap: Bitmap,
    points: List<Offset>, // 4 points [TL, TR, BR, BL] normalized 0..1
    onPointsChanged: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    val cropPoints = remember(points) {
        if (points.size >= 4) {
            CropPoints(tl = points[0], tr = points[1], br = points[2], bl = points[3])
        } else {
            CropPoints.default()
        }
    }

    InteractivePerspectiveCrop(
        bitmap = bitmap,
        cropPoints = cropPoints,
        onCropPointsChanged = { updated ->
            onPointsChanged(listOf(updated.tl, updated.tr, updated.br, updated.bl))
        },
        modifier = modifier
    )
}

/**
 * Checkpoint 4: Perspective Transform & Crop Helper
 * Crops image bounded by 4 points and applies perspective transform flattening the quadrilateral into a clean rectangle.
 */
fun cropPerspective(
    bitmap: Bitmap,
    tlNorm: Offset,
    trNorm: Offset,
    brNorm: Offset,
    blNorm: Offset
): Bitmap {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    val pTL = Offset(tlNorm.x * srcW, tlNorm.y * srcH)
    val pTR = Offset(trNorm.x * srcW, trNorm.y * srcH)
    val pBR = Offset(brNorm.x * srcW, brNorm.y * srcH)
    val pBL = Offset(blNorm.x * srcW, blNorm.y * srcH)

    val widthA = hypot((pBR.x - pBL.x).toDouble(), (pBR.y - pBL.y).toDouble()).toFloat()
    val widthB = hypot((pTR.x - pTL.x).toDouble(), (pTR.y - pTL.y).toDouble()).toFloat()
    val targetWidth = maxOf(widthA, widthB).coerceAtLeast(100f)

    val heightA = hypot((pTR.x - pBR.x).toDouble(), (pTR.y - pBR.y).toDouble()).toFloat()
    val heightB = hypot((pTL.x - pBL.x).toDouble(), (pTL.y - pBL.y).toDouble()).toFloat()
    val targetHeight = maxOf(heightA, heightB).coerceAtLeast(100f)

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
