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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.math.hypot

enum class UnifiedCameraState {
    Camera,
    Crop,
    Processing,
    Success
}

@Composable
fun UnifiedCameraOcrScreen(
    onBack: () -> Unit = {},
    onOpenPdfViewer: (File) -> Unit = {},
    onTextExtracted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf(UnifiedCameraState.Camera) }

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
                Offset(0.08f, 0.08f), // TL
                Offset(0.92f, 0.08f), // TR
                Offset(0.92f, 0.92f), // BR
                Offset(0.08f, 0.92f)  // BL
            )
        )
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    var progressMessage by remember { mutableStateOf("جاري المسح الضوئي...") }
    var extractedTextResult by remember { mutableStateOf("") }
    var editableText by remember(extractedTextResult) { mutableStateOf(extractedTextResult) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val ocrEngine = remember { UnifiedOcrEngine(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (e: Exception) {
                Log.e("UnifiedCameraOcr", "Error unbinding camera on dispose", e)
            }
            ocrEngine.release()
        }
    }

    val runOcrScan: () -> Unit = {
        val bmp = capturedBitmap
        if (bmp != null) {
            screenState = UnifiedCameraState.Processing
            progressMessage = "جاري المسح الضوئي..."

            coroutineScope.launch {
                try {
                    val croppedBmp = perspectiveCropBitmap(
                        bitmap = bmp,
                        tl = cropPoints[0],
                        tr = cropPoints[1],
                        br = cropPoints[2],
                        bl = cropPoints[3]
                    )

                    val text = withContext(Dispatchers.IO) {
                        ocrEngine.scan(croppedBmp)
                    }

                    extractedTextResult = text
                    editableText = text
                    screenState = UnifiedCameraState.Success
                } catch (e: Exception) {
                    Log.e("UnifiedCameraOcr", "Error during scan processing", e)
                    extractedTextResult = ""
                    editableText = ""
                    screenState = UnifiedCameraState.Success
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            coroutineScope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
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
                        screenState = UnifiedCameraState.Crop
                    } else {
                        Toast.makeText(context, "تعذر فتح الصورة", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ أثناء قراءة الصورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val handleBackPress: () -> Unit = {
        when (screenState) {
            UnifiedCameraState.Success -> {
                screenState = UnifiedCameraState.Crop
            }
            UnifiedCameraState.Crop -> {
                capturedBitmap = null
                screenState = UnifiedCameraState.Camera
            }
            UnifiedCameraState.Processing -> {
                screenState = UnifiedCameraState.Crop
            }
            UnifiedCameraState.Camera -> {
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
            // Top Bar Header
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
                        text = when (screenState) {
                            UnifiedCameraState.Camera -> "التقاط مستند"
                            UnifiedCameraState.Crop -> "تحديد وقص المستند"
                            UnifiedCameraState.Processing -> "جاري المسح الضوئي..."
                            UnifiedCameraState.Success -> "نتيجة المسح الضوئي"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (screenState) {
                    UnifiedCameraState.Camera -> {
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
                                        text = "يلزم السماح بالوصول إلى الكاميرا لمسح المستندات",
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
                            DisposableEffect(screenState) {
                                onDispose {
                                    try {
                                        if (cameraProviderFuture.isDone) {
                                            cameraProviderFuture.get().unbindAll()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UnifiedCameraOcr", "Camera unbind failed", e)
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx).apply {
                                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        }
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
                                            Log.e("UnifiedCameraOcr", "Camera binding failed", e)
                                        }
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Transparent Grid Guide
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

                    UnifiedCameraState.Crop, UnifiedCameraState.Processing -> {
                        capturedBitmap?.let { bmp ->
                            UnifiedCropView(
                                bitmap = bmp,
                                points = cropPoints,
                                onPointsChanged = { updated -> cropPoints = updated },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    UnifiedCameraState.Success -> {
                        capturedBitmap?.let { bmp ->
                            UnifiedCropView(
                                bitmap = bmp,
                                points = cropPoints,
                                onPointsChanged = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Laser Scanning Overlay for Processing State
                if (screenState == UnifiedCameraState.Processing) {
                    LaserScanOverlay(modifier = Modifier.fillMaxSize())

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(Color(0xFF141622), RoundedCornerShape(16.dp))
                                .padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5A3),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = progressMessage,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom Actions Bar (for Camera & Crop states)
            when (screenState) {
                UnifiedCameraState.Camera -> {
                    Surface(
                        color = Color(0xFF141622),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Gallery Button
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF262B40), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "المعرض",
                                    tint = Color.White
                                )
                            }

                            // Shutter / Capture Button
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF00E5A3).copy(alpha = 0.2f), CircleShape)
                                    .padding(6.dp)
                                    .background(Color(0xFF00E5A3), CircleShape)
                                    .clickable {
                                        val capture = imageCapture
                                        if (capture != null) {
                                            val executor = Executors.newSingleThreadExecutor()
                                            capture.takePicture(
                                                executor,
                                                object : ImageCapture.OnImageCapturedCallback() {
                                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                        val bitmap = imageProxyToBitmap(imageProxy)
                                                        imageProxy.close()
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            if (bitmap != null) {
                                                                capturedBitmap = bitmap
                                                                cropPoints = listOf(
                                                                    Offset(0.08f, 0.08f),
                                                                    Offset(0.92f, 0.08f),
                                                                    Offset(0.92f, 0.92f),
                                                                    Offset(0.08f, 0.92f)
                                                                )
                                                                screenState = UnifiedCameraState.Crop
                                                            } else {
                                                                Toast.makeText(context, "تعذر التقاط الصورة", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            Toast.makeText(context, "خطأ أثناء الالتقاط: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "التقاط",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Flash & Camera Flip Controls
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { flashEnabled = !flashEnabled },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF262B40), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "الفلاش",
                                        tint = if (flashEnabled) Color(0xFF00E5A3) else Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                            CameraSelector.LENS_FACING_FRONT
                                        } else {
                                            CameraSelector.LENS_FACING_BACK
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF262B40), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "تبديل الكاميرا",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                UnifiedCameraState.Crop -> {
                    Surface(
                        color = Color(0xFF141622),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotate Button
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        capturedBitmap?.let { bmp ->
                                            val matrix = Matrix().apply { postRotate(90f) }
                                            capturedBitmap = Bitmap.createBitmap(
                                                bmp, 0, 0, bmp.width, bmp.height, matrix, true
                                            )
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تدوير",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Reset Crop Button
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        cropPoints = listOf(
                                            Offset(0.04f, 0.04f),
                                            Offset(0.96f, 0.04f),
                                            Offset(0.96f, 0.96f),
                                            Offset(0.04f, 0.96f)
                                        )
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
                                        text = "إعادة ضبط",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Start Scan Button
                            Surface(
                                color = Color(0xFF00E5A3),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clickable {
                                        runOcrScan()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
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

        // Full Screen Result Modal for Success State
        AnimatedVisibility(
            visible = screenState == UnifiedCameraState.Success,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            val isFailed = editableText.isBlank()

            Surface(
                color = Color(0xFF141622),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
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
                                    .background(
                                        if (isFailed) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF00E5A3).copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFailed) Icons.Default.SearchOff else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isFailed) Color(0xFFFF5252) else Color(0xFF00E5A3),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = if (isFailed) "تعذر استخراج النص" else "تم المسح بنجاح",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isFailed) {
                            // Copy Text Action
                            Surface(
                                color = Color(0xFF262B40),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable {
                                    if (editableText.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Extracted Text", editableText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
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
                                        contentDescription = "نسخ النص",
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "نسخ النص",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Extracted Text Field or Failure Message
                    Surface(
                        color = Color(0xFF262B40),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isFailed) Color(0xFFFF5252).copy(alpha = 0.3f) else Color(0xFF00E5A3).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (isFailed) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = Color(0xFFFF7A00),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "لم يتم العثور على نص واضح، حاول تحسين الإضاءة أو التقريب من النص",
                                        color = Color(0xFFE0E0E0),
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                }
                            } else {
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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isFailed) {
                            // Retry Button on Failure
                            Button(
                                onClick = {
                                    runOcrScan()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إعادة المحاولة",
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Re-crop / Re-adjust button
                            OutlinedButton(
                                onClick = {
                                    screenState = UnifiedCameraState.Crop
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تعديل التحديد والقص",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            // Confirm / Save Action
                            Button(
                                onClick = {
                                    onTextExtracted(editableText)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A3)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = "اعتماد النص المستخرج",
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Share Button
                                OutlinedButton(
                                    onClick = {
                                        if (editableText.isNotBlank()) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, editableText)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة النص"))
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5A3)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5A3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مشاركة",
                                        color = Color(0xFF00E5A3),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Retake / Rescan Button
                                OutlinedButton(
                                    onClick = {
                                        screenState = UnifiedCameraState.Crop
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Crop,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إعادة القص",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LaserScanOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "LaserScan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val yPos = size.height * scanProgress
        val laserHeight = 3.dp.toPx()
        val glowHeight = 36.dp.toPx()

        // Soft green laser glow
        val glowBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0x4400E5A3),
                Color(0x9900E5A3),
                Color.Transparent
            ),
            startY = (yPos - glowHeight).coerceAtLeast(0f),
            endY = (yPos + glowHeight).coerceAtMost(size.height)
        )
        drawRect(
            brush = glowBrush,
            topLeft = Offset(0f, (yPos - glowHeight).coerceAtLeast(0f)),
            size = Size(size.width, glowHeight * 2)
        )

        // Core bright laser line
        drawLine(
            color = Color(0xFF00E5A3),
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = laserHeight
        )
    }
}

@Composable
fun UnifiedCropView(
    bitmap: Bitmap,
    points: List<Offset>,
    onPointsChanged: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var activeNode by remember { mutableStateOf<Int?>(null) }
    var magnifierSourceCenter by remember { mutableStateOf(Offset.Unspecified) }

    var tl by remember(points) { mutableStateOf(points.getOrElse(0) { Offset(0.08f, 0.08f) }) }
    var tr by remember(points) { mutableStateOf(points.getOrElse(1) { Offset(0.92f, 0.08f) }) }
    var br by remember(points) { mutableStateOf(points.getOrElse(2) { Offset(0.92f, 0.92f) }) }
    var bl by remember(points) { mutableStateOf(points.getOrElse(3) { Offset(0.08f, 0.92f) }) }

    val bW = bitmap.width.toFloat()
    val bH = bitmap.height.toFloat()

    val scale = remember(size, bW, bH) {
        if (size.width > 0 && size.height > 0 && bW > 0 && bH > 0) {
            minOf(size.width.toFloat() / bW, size.height.toFloat() / bH)
        } else 1f
    }
    val dW = bW * scale
    val dH = bH * scale
    val offsetX = (size.width - dW) / 2f
    val offsetY = (size.height - dH) / 2f

    fun toPx(norm: Offset): Offset = Offset(offsetX + norm.x * dW, offsetY + norm.y * dH)

    val pxTL = toPx(tl)
    val pxTR = toPx(tr)
    val pxBR = toPx(br)
    val pxBL = toPx(bl)

    val orangeColor = Color(0xFFFF7A00)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                size = coordinates.size
            }
            .magnifier(
                sourceCenter = { magnifierSourceCenter },
                magnifierCenter = {
                    if (magnifierSourceCenter.isSpecified) {
                        Offset(magnifierSourceCenter.x, (magnifierSourceCenter.y - 200f).coerceAtLeast(100f))
                    } else {
                        Offset.Unspecified
                    }
                },
                zoom = 2.2f,
                size = DpSize(130.dp, 130.dp),
                cornerRadius = 65.dp
            )
            .pointerInput(dW, dH, offsetX, offsetY) {
                if (dW <= 0 || dH <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { touchPos ->
                        val touchRadius = 60.dp.toPx()
                        val dists = listOf(
                            (touchPos - pxTL).getDistance(),
                            (touchPos - pxTR).getDistance(),
                            (touchPos - pxBR).getDistance(),
                            (touchPos - pxBL).getDistance()
                        )
                        val minVal = dists.minOrNull() ?: Float.MAX_VALUE
                        if (minVal <= touchRadius) {
                            activeNode = dists.indexOf(minVal)
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
        // Draw Bitmap
        val androidMatrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
        drawContext.canvas.nativeCanvas.drawBitmap(
            bitmap,
            androidMatrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        // Crop Path
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pxTL.x, pxTL.y)
            lineTo(pxTR.x, pxTR.y)
            lineTo(pxBR.x, pxBR.y)
            lineTo(pxBL.x, pxBL.y)
            close()
        }

        // Semi-transparent Overlay outside crop box
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
        }

        // Orange Border
        drawPath(
            path = path,
            color = orangeColor,
            style = Stroke(width = 4.dp.toPx())
        )

        // Corner Handles
        val nodesPx = listOf(pxTL, pxTR, pxBR, pxBL)
        val handleRadius = 24.dp.toPx()
        val innerDotRadius = 7.dp.toPx()

        nodesPx.forEachIndexed { idx, pxOffset ->
            val isActive = (idx == activeNode)
            val r = if (isActive) handleRadius * 1.25f else handleRadius

            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = r + 4.dp.toPx(),
                center = pxOffset
            )
            drawCircle(
                color = orangeColor,
                radius = r,
                center = pxOffset
            )
            drawCircle(
                color = Color.White,
                radius = innerDotRadius,
                center = pxOffset
            )
        }
    }
}

fun perspectiveCropBitmap(
    bitmap: Bitmap,
    tl: Offset,
    tr: Offset,
    br: Offset,
    bl: Offset
): Bitmap {
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    val rawTL = Offset((tl.x * srcW).coerceIn(0f, srcW), (tl.y * srcH).coerceIn(0f, srcH))
    val rawTR = Offset((tr.x * srcW).coerceIn(0f, srcW), (tr.y * srcH).coerceIn(0f, srcH))
    val rawBR = Offset((br.x * srcW).coerceIn(0f, srcW), (br.y * srcH).coerceIn(0f, srcH))
    val rawBL = Offset((bl.x * srcW).coerceIn(0f, srcW), (bl.y * srcH).coerceIn(0f, srcH))

    val padX = (srcW * 0.02f).coerceAtLeast(8f)
    val padY = (srcH * 0.02f).coerceAtLeast(8f)

    val pTL = Offset((rawTL.x - padX).coerceIn(0f, srcW), (rawTL.y - padY).coerceIn(0f, srcH))
    val pTR = Offset((rawTR.x + padX).coerceIn(0f, srcW), (rawTR.y - padY).coerceIn(0f, srcH))
    val pBR = Offset((rawBR.x + padX).coerceIn(0f, srcW), (rawBR.y + padY).coerceIn(0f, srcH))
    val pBL = Offset((rawBL.x - padX).coerceIn(0f, srcW), (rawBL.y - padY).coerceIn(0f, srcH))

    val widthA = hypot((pBR.x - pBL.x).toDouble(), (pBR.y - pBL.y).toDouble()).toFloat()
    val widthB = hypot((pTR.x - pTL.x).toDouble(), (pTR.y - pTL.y).toDouble()).toFloat()
    val targetWidth = maxOf(widthA, widthB).coerceAtLeast(50f)

    val heightA = hypot((pTR.x - pBR.x).toDouble(), (pTR.y - pBR.y).toDouble()).toFloat()
    val heightB = hypot((pTL.x - pBL.x).toDouble(), (pTL.y - pBL.y).toDouble()).toFloat()
    val targetHeight = maxOf(heightA, heightB).coerceAtLeast(50f)

    val resultBitmap = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)

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

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, matrix, paint)

    return resultBitmap
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val planeProxy = imageProxy.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0 && bitmap != null) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e("UnifiedCameraOcr", "Error converting ImageProxy to Bitmap", e)
        null
    }
}
