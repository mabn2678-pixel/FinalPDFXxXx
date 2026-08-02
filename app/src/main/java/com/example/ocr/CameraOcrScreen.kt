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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.CropControlBottomBar
import com.example.ui.InteractiveCropOverlay
import com.example.ui.cropBitmapNormalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CameraOcrScreen(
    onBack: () -> Unit = {},
    onOpenPdfViewer: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

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

    var selectedLanguage by remember { mutableStateOf("ara+eng") } // "ara+eng", "eng", "deu"
    var dropdownExpanded by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }

    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    var createdPdfFile by remember { mutableStateOf<File?>(null) }
    var extractedTextResult by remember { mutableStateOf<String?>(null) }
    var extractedBoxesCount by remember { mutableIntStateOf(0) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

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
                        cropLeft = 0.05f
                        cropTop = 0.05f
                        cropRight = 0.95f
                        cropBottom = 0.95f
                        createdPdfFile = null
                        extractedTextResult = null
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
            // Top Bar Header
            Surface(
                color = Color(0xFF141622),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ماسح الكاميرا الضوئي (Searchable PDF)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إنشاء مستندات PDF قابلة للبحث والنسخ بالكامل بدون إنترنت",
                            color = Color(0xFF00E5A3),
                            fontSize = 11.sp
                        )
                    }

                    // Flash Toggle
                    IconButton(
                        onClick = {
                            flashEnabled = !flashEnabled
                            imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        }
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "الفلاش",
                            tint = if (flashEnabled) Color(0xFFFFD700) else Color.White
                        )
                    }
                }
            }

            // Language Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1D2D))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color(0xFF00E5A3),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "لغة التعرف (Tesseract):",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box {
                    val langOptions = listOf(
                        "ara+eng" to "عربي + إنجليزي",
                        "eng" to "إنجليزي فقط",
                        "deu" to "ألماني"
                    )
                    val currentLabel = langOptions.find { it.first == selectedLanguage }?.second ?: "عربي + إنجليزي"

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF262B40))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentLabel,
                            color = Color(0xFF00E5A3),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "قائمة اللغات",
                            tint = Color(0xFF00E5A3),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1F2232))
                    ) {
                        langOptions.forEach { (langKey, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (selectedLanguage == langKey) Color(0xFF00E5A3) else Color.White,
                                        fontWeight = if (selectedLanguage == langKey) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedLanguage = langKey
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Main Preview / Crop Box Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!hasCameraPermission && capturedBitmap == null) {
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
                } else if (capturedBitmap != null) {
                    // Image Captured & Crop Mode
                    Box(modifier = Modifier.fillMaxSize()) {
                        InteractiveCropOverlay(
                            bitmap = capturedBitmap,
                            cropLeft = cropLeft,
                            cropTop = cropTop,
                            cropRight = cropRight,
                            cropBottom = cropBottom,
                            isScanning = isProcessing,
                            onCropChanged = { l, t, r, b ->
                                cropLeft = l
                                cropTop = t
                                cropRight = r
                                cropBottom = b
                            }
                        )

                        // Instruction Hint Banner
                        if (createdPdfFile == null && !isProcessing) {
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
                                        text = "اسحب الزوايا الخضراء لتحديد النص المراد إنشاؤه في الـ PDF",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Live Camera Stream
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
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
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            capture
                                        )
                                    } catch (exc: Exception) {
                                        Log.e("CameraOcrScreen", "Camera binding failed", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Live Crop overlay over camera preview
                        InteractiveCropOverlay(
                            bitmap = null,
                            cropLeft = cropLeft,
                            cropTop = cropTop,
                            cropRight = cropRight,
                            cropBottom = cropBottom,
                            isScanning = isProcessing,
                            onCropChanged = { l, t, r, b ->
                                cropLeft = l
                                cropTop = t
                                cropRight = r
                                cropBottom = b
                            }
                        )

                        // Instruction Hint Banner
                        if (createdPdfFile == null && !isProcessing) {
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
                                        text = "تحديد أبعاد الصفحة قبل التصوير لإنشاء الـ PDF القابل للبحث",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Loading Overlay Indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5A3),
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = progressMessage.ifEmpty { "جاري التعرف على النصوص وإنشاء المستند..." },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "يعمل محرك Tesseract & PdfBox أوفلاين بالكامل",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Bottom Actions Control Bar
            if (capturedBitmap != null && createdPdfFile == null) {
                CropControlBottomBar(
                    isScanning = isProcessing,
                    preferOnlineAi = false,
                    onScanCrop = {
                        val originalBmp = capturedBitmap ?: return@CropControlBottomBar
                        coroutineScope.launch {
                            try {
                                isProcessing = true
                                progressMessage = "جاري تطبيق فلتر التباين العالي (أبيض وأسود)..."

                                val croppedBmp = cropBitmapNormalized(
                                    originalBmp,
                                    cropLeft,
                                    cropTop,
                                    cropRight,
                                    cropBottom
                                )

                                // Apply grayscale and contrast enhancement to sharpen text for Tesseract OCR
                                val enhancedOcrBmp = toGrayscaleAndContrast(croppedBmp, 1.6f)

                                progressMessage = "جاري التعرف الضوئي Tesseract على النصوص..."

                                val ocrData = tesseractManager.extractTextWithCoordinates(
                                    enhancedOcrBmp,
                                    selectedLanguage
                                )
                                extractedBoxesCount = ocrData.size

                                val fullText = tesseractManager.extractFullText(
                                    enhancedOcrBmp,
                                    selectedLanguage
                                )
                                extractedTextResult = fullText

                                progressMessage = "جاري كتابة طبقة النصوص المخفية وتوليد Searchable PDF..."

                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val fileName = "Searchable_OCR_$timeStamp.pdf"
                                val pdfDir = File(context.filesDir, "SearchablePDFs")
                                if (!pdfDir.exists()) pdfDir.mkdirs()

                                val outputFile = File(pdfDir, fileName)

                                // Pass original colored cropped image to PDF generator, high contrast image was used ONLY for Tesseract OCR reading!
                                val createdFile = PdfBoxGenerator.createSearchablePdf(
                                    context = context,
                                    image = croppedBmp,
                                    ocrData = ocrData,
                                    outputPath = outputFile.absolutePath
                                )

                                createdPdfFile = createdFile
                                isProcessing = false
                                Toast.makeText(context, "تم إنشاء ملف PDF القابل للبحث بنجاح!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                isProcessing = false
                                Log.e("CameraOcrScreen", "Error during OCR & PDF generation", e)
                                Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onSelectFull = {
                        cropLeft = 0f
                        cropTop = 0f
                        cropRight = 1f
                        cropBottom = 1f
                        Toast.makeText(context, "تم تحديد كامل الكادر", Toast.LENGTH_SHORT).show()
                    },
                    onRetake = {
                        capturedBitmap = null
                        createdPdfFile = null
                        extractedTextResult = null
                    }
                )
            } else if (capturedBitmap == null) {
                // Live Capture Bar
                Surface(
                    color = Color(0xFF141622),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Button
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0xFF262B40), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "استيراد من المعرض",
                                tint = Color.White
                            )
                        }

                        // Capture Shutter Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5A3))
                                .clickable {
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

                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        if (rotatedBmp != null) {
                                                            capturedBitmap = rotatedBmp
                                                            cropLeft = 0.05f
                                                            cropTop = 0.05f
                                                            cropRight = 0.95f
                                                            cropBottom = 0.95f
                                                            createdPdfFile = null
                                                            extractedTextResult = null
                                                        } else {
                                                            Toast.makeText(context, "فشل التقاط الصورة", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("CameraOcrScreen", "Capture error: ${exception.message}", exception)
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        val previewBmp = previewViewRef?.bitmap
                                                        if (previewBmp != null) {
                                                            capturedBitmap = previewBmp
                                                            cropLeft = 0.05f
                                                            cropTop = 0.05f
                                                            cropRight = 0.95f
                                                            cropBottom = 0.95f
                                                            createdPdfFile = null
                                                            extractedTextResult = null
                                                        } else {
                                                            Toast.makeText(context, "تعذر التقاط الصورة", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, Color.Black, CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "التقاط",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Switch Camera Lens
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(0xFF262B40), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تبديل الكاميرا",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Result Dialog Card when PDF is generated
        if (createdPdfFile != null) {
            val file = createdPdfFile!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1E2235),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5A3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E5A3),
                            modifier = Modifier.size(54.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "تم إنشاء مستند PDF القابل للبحث!",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "تم دمج $extractedBoxesCount كلمة قابلة للتحديد والنسخ فوق المستند عبر Tesseract & PdfBox",
                            color = Color(0xFF00E5A3),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Extracted Text Preview Card
                        if (!extractedTextResult.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFF141622),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "معاينة النص المستخرج:",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Extracted OCR", extractedTextResult)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "تم نسخ النص إلى الحافظة!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "نسخ النص",
                                                tint = Color(0xFF00E5A3),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = extractedTextResult!!,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Action Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Button "فتح المستند": External ACTION_VIEW intent with FileProvider
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فتح المستند", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 2. Button "مشاركة": External ACTION_SEND intent with FileProvider
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
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف PDF"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "خطأ أثناء المشاركة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاركة", fontSize = 12.sp)
                                }

                                // 3. Button "القارئ المدمج"
                                OutlinedButton(
                                    onClick = {
                                        onOpenPdfViewer(file)
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3).copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5A3)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("القارئ المدمج", fontSize = 12.sp)
                                }
                            }

                            // 4. Button "مسح جديد"
                            OutlinedButton(
                                onClick = {
                                    createdPdfFile = null
                                    capturedBitmap = null
                                    extractedTextResult = null
                                },
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مسح مستند جديد", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Checkpoint 1: Image Enhancement for OCR
 * Convert bitmap to grayscale and boost contrast to clarify dark text on light background.
 * Used exclusively for Tesseract text extraction to increase OCR precision.
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
