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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
                    isScanning = true
                    scanStatusMessage = if (preferOnlineAi) {
                        "جاري الاتصال عبر السحابة لتمحيص وقراءة نصوص صورة المعرض..."
                    } else {
                        "جاري تحليل نصوص صورة المعرض بالمحرك المحلي..."
                    }
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (loadedBitmap != null) {
                        capturedBitmap = loadedBitmap
                        val res = processMultiLanguageCameraOcr(context, loadedBitmap, preferOnlineAi)
                        ocrResult = res
                        if (res.isApiKeyError) {
                            showApiKeyDialog = true
                        }
                    } else {
                        Toast.makeText(context, "تعذر فتح الصورة المحفوطة", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ أثناء قراءة الصورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isScanning = false
                }
            }
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ماسح الكاميرا الضوئي (Camera OCR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "استخراج النصوص العربية والألمانية والإنجليزية بثوانٍ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Language Pills & Engine Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageBadge("العربية")
            LanguageBadge("الألمانية")
            LanguageBadge("الإنجليزية")

            Spacer(modifier = Modifier.weight(1f))

            FilterChip(
                selected = preferOnlineAi,
                onClick = {
                    preferOnlineAi = !preferOnlineAi
                    if (preferOnlineAi && !SecureKeyManager.hasSavedKey(context)) {
                        showApiKeyDialog = true
                    }
                },
                label = {
                    Text(
                        text = if (preferOnlineAi) "ذكاء أونلاين" else "أوفلاين محلي",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (preferOnlineAi) Icons.Default.CloudSync else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )

            IconButton(
                onClick = { showApiKeyDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "مفتاح Gemini API",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!hasCameraPermission) {
            // Permission request UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "إذن الكاميرا مطلوب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يتطلب الماسح الضوئي الوصول إلى كاميرا الجهاز لتصوير المستندات والأوراق وقراءتها.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("منح إذن الكاميرا", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (ocrResult != null) {
            // Display Results Screen
            val res = ocrResult!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
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
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (res.isOnline) Icons.Default.Language else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (res.isOnline) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = res.engineName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (res.isOnline) Color(0xFF1565C0) else Color(0xFF2E7D32)
                            )
                        }

                        Text(
                            text = "${res.text.length} حرف",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = res.text.ifBlank { "(لم يتم اكتشاف نصوص في الصورة المصورة)" },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                copyTextToClipboardLocal(context, res.text)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ النص", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                shareTextLocal(context, res.text, "النص المصور بالكاميرا")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                ocrResult = null
                                capturedBitmap = null
                                isScanning = false
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "تصوير آخر",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            // Camera Live Feed or Captured Image Scanning View
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                if (capturedBitmap != null) {
                    // Show captured image
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "الصورة المصورة",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // CameraX View
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
                }

                // Document Frame Guidelines & Corner Highlights
                DocumentFrameOverlay(
                    isScanning = isScanning
                )

                // Flash & Switch Controls Overlay (Top)
                if (capturedBitmap == null && !isScanning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                flashEnabled = !flashEnabled
                                camera?.cameraControl?.enableTorch(flashEnabled)
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "الكشاف",
                                tint = if (flashEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.size(20.dp)
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
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "تبديل الكاميرا",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Scanning Progress / Shutter Button (Bottom Overlay)
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xDD1C182B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF00FFCC),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = scanStatusMessage.ifEmpty { "جاري قراءة الحروف العربية والألمانية والإنجليزية..." },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (preferOnlineAi) "الاتصال بالسحابة الذكية (Gemini Vision AI)" else "محرك ML Kit المحلي (أوفلاين)",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                } else if (capturedBitmap == null) {
                    // Control Bar with Gallery Picker, Shutter Button, and Camera Flip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Picker Button
                        Surface(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = "معرض الصور",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Main Shutter Button
                        Surface(
                            onClick = {
                                isScanning = true
                                scanStatusMessage = if (preferOnlineAi) {
                                    "جاري الاتصال عبر الإنترنت لتحليل النص العربي والألماني والإنجليزي..."
                                } else {
                                    "جاري تحليل الصورة بالمحرك المحلي..."
                                }

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
                                                    capturedBitmap = rotatedBmp
                                                    if (rotatedBmp != null) {
                                                        val res = processMultiLanguageCameraOcr(context, rotatedBmp, preferOnlineAi)
                                                        ocrResult = res
                                                        if (res.isApiKeyError) {
                                                            showApiKeyDialog = true
                                                        }
                                                        isScanning = false
                                                    } else {
                                                        isScanning = false
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
                                                        val res = processMultiLanguageCameraOcr(context, previewBmp, preferOnlineAi)
                                                        ocrResult = res
                                                        if (res.isApiKeyError) {
                                                            showApiKeyDialog = true
                                                        }
                                                        isScanning = false
                                                    } else {
                                                        isScanning = false
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
                                        coroutineScope.launch {
                                            val res = processMultiLanguageCameraOcr(context, bmp, preferOnlineAi)
                                            ocrResult = res
                                            isScanning = false
                                        }
                                    } else {
                                        isScanning = false
                                        Toast.makeText(context, "الكاميرا غير جاهزة بعد، جرب التقاط صورة من المعرض", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp),
                            border = BorderStroke(3.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "مسح ضوئي",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Switch Lens Button
                        Surface(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "تبديل الكاميرا",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
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
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
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

            val client = OkHttpClient.Builder()
                .connectTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val candidateModels = listOf(
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-pro"
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
