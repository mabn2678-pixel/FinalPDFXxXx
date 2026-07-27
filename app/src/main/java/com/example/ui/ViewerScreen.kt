package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.print.PrintManager
import android.print.PrintDocumentAdapter
import android.print.PrintAttributes
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import java.io.ByteArrayOutputStream
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.example.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class BottomSheetType {
    None,
    MoreOptions,
    ViewOptions,
    DisplaySettings,
    ZoomSettings,
    JumpToPage,
    DocumentInfo,
    Bookmarks,
    AutoScroll,
    DocumentNavigation,
    OcrText,
    CameraOcr,
    NotesAndHighlights
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    
    var activeSheet by remember { mutableStateOf(BottomSheetType.None) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isBarsVisible by remember { mutableStateOf(true) }
    var isBrowsing by remember { mutableStateOf(false) }
    var browsingUrl by remember { mutableStateOf<String?>(null) }
    var overlayWebViewRef by remember { mutableStateOf<WebView?>(null) }

    var isPageIndicatorVisible by remember { mutableStateOf(false) }
    var pageIndicatorTrigger by remember { mutableStateOf(0L) }

    // Show page indicator on page change
    LaunchedEffect(state.currentPage) {
        if (state.currentPage > 0) {
            pageIndicatorTrigger = System.currentTimeMillis()
        }
    }

    // Debounced page indicator visibility (remains visible for 2 seconds after scroll stops)
    LaunchedEffect(pageIndicatorTrigger) {
        if (pageIndicatorTrigger > 0L) {
            isPageIndicatorVisible = true
            delay(2000)
            isPageIndicatorVisible = false
        }
    }

    var activeAudioUrl by remember { mutableStateOf<String?>(null) }
    var audioWordName by remember { mutableStateOf("") }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var isAudioLoading by remember { mutableStateOf(false) }

    val mediaPlayer = remember { android.media.MediaPlayer() }

    fun restoreGermanUmlauts(text: String): String {
        try {
            var result = text.replace(Regex("[-_\\s]*\\d+$"), "")
            
            result = result.replace("ae", "ä")
            result = result.replace("oe", "ö")
            result = result.replace("Ae", "Ä")
            result = result.replace("Oe", "Ö")
            
            val sb = java.lang.StringBuilder()
            var i = 0
            val len = result.length
            while (i < len) {
                if (i < len - 1 && result[i] == 'u' && result[i+1] == 'e') {
                    val prevChar = if (i > 0) result[i-1].lowercaseChar() else ' '
                    val isExceptionPredecessor = prevChar == 'q' || prevChar == 'e' || prevChar == 'a' || prevChar == 'o'
                    
                    var isExceptionUell = false
                    if (i < len - 3 && result[i+2] == 'l' && result[i+3] == 'l') {
                        val p = prevChar
                        if (p == 't' || p == 's' || p == 'd' || p == 'r' || p == 'n' || p == 'v' || p == 'x') {
                            isExceptionUell = true
                        }
                    }
                    
                    var isZuerst = false
                    if (prevChar == 'z' && i < len - 3 && result.substring(i, i+4).lowercase(Locale.ROOT) == "uerst") {
                        isZuerst = true
                    }

                    if (isExceptionPredecessor || isExceptionUell || isZuerst) {
                        sb.append("ue")
                    } else {
                        sb.append("ü")
                    }
                    i += 2
                } else if (i < len - 1 && result[i] == 'U' && result[i+1] == 'e') {
                    sb.append("Ü")
                    i += 2
                } else {
                    sb.append(result[i])
                    i++
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            return text
        }
    }

    fun extractWordFromUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            val queryText = uri.getQueryParameter("q") ?: uri.getQueryParameter("text")
            val baseWord = if (queryText != null) {
                Uri.decode(queryText).trim()
            } else {
                val lastSegment = uri.lastPathSegment ?: return "نطق الكلمة"
                val cleanName = if (lastSegment.contains(".")) {
                    lastSegment.substringBeforeLast(".")
                } else {
                    lastSegment
                }
                val decoded = Uri.decode(cleanName)
                decoded.replace("_", " ").replace("-", " ").trim()
            }
            return restoreGermanUmlauts(baseWord)
        } catch (e: Exception) {
            return "نطق الكلمة"
        }
    }

    fun stopAndDismissAudio() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeAudioUrl = null
        isAudioPlaying = false
        isAudioLoading = false
    }

    fun playAudio(url: String) {
        try {
            activeAudioUrl = url
            audioWordName = extractWordFromUrl(url)
            isAudioLoading = true
            isAudioPlaying = false

            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.parse(url))
            
            mediaPlayer.setOnPreparedListener { mp ->
                isAudioLoading = false
                isAudioPlaying = true
                mp.start()
            }
            
            mediaPlayer.setOnCompletionListener {
                isAudioPlaying = false
                // Auto-dismiss the mini player after 1.5 seconds to keep the screen clear for the PDF
                coroutineScope.launch {
                    delay(1500)
                    if (!mediaPlayer.isPlaying && activeAudioUrl == url) {
                        stopAndDismissAudio()
                    }
                }
            }
            
            mediaPlayer.setOnErrorListener { mp, what, extra ->
                isAudioLoading = false
                isAudioPlaying = false
                Toast.makeText(context, "تعذر تشغيل الصوت. قد يكون الرابط غير صالح أو لا يوجد اتصال بالإنترنت.", Toast.LENGTH_LONG).show()
                true
            }
            
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            isAudioLoading = false
            isAudioPlaying = false
            Toast.makeText(context, "خطأ أثناء تشغيل الصوت: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun replayAudio() {
        try {
            if (activeAudioUrl != null) {
                if (!mediaPlayer.isPlaying) {
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                    isAudioPlaying = true
                } else {
                    mediaPlayer.seekTo(0)
                }
            }
        } catch (e: Exception) {
            activeAudioUrl?.let { playAudio(it) }
        }
    }



    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.incrementReadingTime(context, 0L)
        }
    }

    // Dynamic scale initialization on load
    LaunchedEffect(state.currentPdfPath) {
        state.currentPdfPath?.let { path ->
            viewModel.loadBookmarks(context, path)
        }
    }

    // Active reading duration tracker
    LaunchedEffect(state.currentPdfPath) {
        if (state.currentPdfPath != null) {
            while (true) {
                delay(1000L)
                viewModel.incrementReadingTimeSilently(context, 1L)
            }
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val attemptExit = {
        val overlayWebView = overlayWebViewRef
        val webView = webViewRef
        if (isBrowsing && overlayWebView != null) {
            if (overlayWebView.canGoBack()) {
                overlayWebView.goBack()
            } else {
                browsingUrl = null
                isBrowsing = false
                overlayWebViewRef = null
            }
        } else if (state.hasUnsavedChanges) {
            showExitConfirmDialog = true
        } else if (state.isEditMode) {
            viewModel.toggleEditMode(false)
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            viewModel.goBackToDashboard()
        }
    }

    // Back button handler
    BackHandler {
        attemptExit()
    }

    // Programmatic screen keep-awake
    DisposableEffect(state.keepScreenOn) {
        if (state.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Programmatic screen brightness adjustments
    LaunchedEffect(state.isSystemBrightness, state.customBrightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (state.isSystemBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                state.customBrightness.coerceIn(0.01f, 1.0f)
            }
            window.attributes = layoutParams
        }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (state.appTheme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    // Programmatic status bar icon color setup & visibility toggling
    DisposableEffect(isBarsVisible, isBrowsing, isDark) {
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isBrowsing) {
                // In-app web browser mode: status bar is ALWAYS visible
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                // Light mode -> dark status bar icons (!isDark = true), Dark mode -> light icons (!isDark = false)
                insetsController.isAppearanceLightStatusBars = !isDark
            } else {
                if (isBarsVisible) {
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    insetsController.isAppearanceLightStatusBars = !isDark
                } else {
                    // Tap to hide controls: hide phone notification/status bar
                    insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        onDispose {
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // Auto-hide control bars after 5 seconds of inactivity
    LaunchedEffect(isBarsVisible, state.autoHideToolbar) {
        if (state.autoHideToolbar && isBarsVisible) {
            kotlinx.coroutines.delay(5000L)
            isBarsVisible = false
        }
    }

    // Collect JS commands and execute them on WebView
    LaunchedEffect(webViewRef, state.currentPdfPath) {
        webViewRef?.let { webView ->
            viewModel.jsCommandFlow.collect { command ->
                webView.evaluateJavascript(command, null)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main WebView rendering PDF.js
            if (state.currentPdfPath != null) {
                key(state.currentPdfPath) {
                    var zoomScale by remember { mutableStateOf(state.currentScale) }

                    LaunchedEffect(state.currentScale) {
                        zoomScale = state.currentScale
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                                    viewModel.setScale(zoomScale)
                                }
                            }
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                    ) {
                        PdfWebView(
                            pdfPath = state.currentPdfPath!!,
                            viewModel = viewModel,
                            onWebViewCreated = { webViewRef = it },
                            onSingleTap = { 
                                if (!isBrowsing) {
                                    isBarsVisible = !isBarsVisible 
                                }
                            },
                            onScrollEvent = {
                                pageIndicatorTrigger = System.currentTimeMillis()
                            },
                            onAudioLinkClicked = { url -> playAudio(url) },
                            onBrowsingStateChanged = { browsing ->
                                if (!browsing) {
                                    if (browsingUrl == null) {
                                        isBrowsing = false
                                    }
                                } else {
                                    isBrowsing = true
                                }
                            },
                            onExternalLinkClicked = { url ->
                                browsingUrl = url
                                isBrowsing = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (isBrowsing) Modifier.statusBarsPadding() else Modifier)
                        )
                    }

                    // ADD STICKY NOTE DIALOG
                    if (state.showAddStickyNoteDialog) {
                        AddStickyNoteDialog(
                            viewModel = viewModel,
                            state = state,
                            onDismiss = { viewModel.closeAddStickyNoteDialog() }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // IN-APP BROWSER OVERLAY FOR EXTERNAL LINKS (FIX FOR SCROLL & PDF RESET)
            if (isBrowsing && browsingUrl != null) {
                var overlayLoadingProgress by remember { mutableStateOf(0f) }
                var overlayIsLoading by remember { mutableStateOf(true) }
                var overlayTitle by remember { mutableStateOf("جاري التحميل...") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    // Header Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val overlayWebView = overlayWebViewRef
                                        if (overlayWebView != null && overlayWebView.canGoBack()) {
                                            overlayWebView.goBack()
                                        } else {
                                            browsingUrl = null
                                            isBrowsing = false
                                            overlayWebViewRef = null
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع"
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = overlayTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = browsingUrl ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        overlayWebViewRef?.reload()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "إعادة تحميل"
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        browsingUrl = null
                                        isBrowsing = false
                                        overlayWebViewRef = null
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق"
                                    )
                                }
                            }

                            if (overlayIsLoading) {
                                LinearProgressIndicator(
                                    progress = { overlayLoadingProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                        }
                    }

                    // Separate WebView
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        overlayLoadingProgress = newProgress / 100f
                                        overlayIsLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        super.onReceivedTitle(view, title)
                                        if (!title.isNullOrEmpty()) {
                                            overlayTitle = title
                                        }
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        overlayIsLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        overlayIsLoading = false
                                    }
                                }
                                overlayWebViewRef = this
                                loadUrl(browsingUrl!!)
                            }
                        },
                        update = { webView ->
                            if (browsingUrl != null && webView.url != browsingUrl) {
                                webView.loadUrl(browsingUrl!!)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // STATUS BAR DARK TRANSLUCENT BACKDROP OVERLAY
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            if (statusBarHeight > 0.dp && !isBrowsing && isBarsVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.85f))
                )
            }

            // FLOATING TOP BAR WITH STATUS BAR BACKDROP (CAPSULE/DYNAMIC ISLAND STYLE)
            AnimatedVisibility(
                visible = isBarsVisible && !isBrowsing,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .widthIn(max = 480.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xF2ECE6F8))
                        .border(BorderStroke(1.dp, Color(0x407C5CFF)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isSearchActive) {
                            NativeSearchBar(
                                viewModel = viewModel,
                                state = state,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            )
                        } else {
                            val fileName = state.currentPdfName ?: "عرض ملف PDF"
                            val fileNameFontSize = when {
                                fileName.length > 30 -> 10.5.sp
                                fileName.length > 18 -> 11.5.sp
                                else -> 12.sp
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Section: Compact Actions
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    GlowingIconButton(
                                        icon = Icons.Default.Share,
                                        contentDescription = "مشاركة",
                                        onClick = {
                                            state.currentPdfPath?.let { path ->
                                                sharePdf(context, path, fileName)
                                            }
                                        },
                                        tint = Color(0xFF3F51B5),
                                        iconSize = 16.dp,
                                        haloSize = 30.dp
                                    )

                                    if (state.annotationEditorMode != 0) {
                                        GlowingIconButton(
                                            icon = Icons.Default.Check,
                                            contentDescription = "حفظ والتطبيق",
                                            onClick = { viewModel.requestSaveAnnotatedPdf() },
                                            tint = Color(0xFF4CAF50),
                                            iconSize = 18.dp,
                                            haloSize = 30.dp
                                        )
                                    }
                                    GlowingIconButton(
                                        icon = Icons.Default.Search,
                                        contentDescription = "البحث",
                                        onClick = { viewModel.openSearch() },
                                        tint = Color(0xFF009688),
                                        iconSize = 16.dp,
                                        haloSize = 30.dp
                                    )
                                }

                                // Centered Title text - Compact single line filename
                                Text(
                                    text = fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = fileNameFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                    color = Color(0xFF1C182B),
                                    textAlign = TextAlign.Center
                                )

                                // Right Section: Back Arrow
                                GlowingIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    onClick = { attemptExit() },
                                    tint = Color(0xFF9C27B0),
                                    iconSize = 18.dp,
                                    haloSize = 30.dp,
                                    modifier = Modifier.testTag("viewer_back_btn")
                                )
                            }
                        }
                    }
                }
            }

            // FLOATING TRIGGER BUBBLE (When toolbars are hidden)
            AnimatedVisibility(
                visible = !isBarsVisible && !isBrowsing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                FloatingActionButton(
                    onClick = { isBarsVisible = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuOpen,
                        contentDescription = "إظهار شريط التحكم",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // CONTROLS & BOTTOM DOCK LAYOUT
            AnimatedVisibility(
                visible = isBarsVisible && !isBrowsing,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                        // Sleek circular-dock style Bottom bar with 6 beautifully labeled items
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("native_bottom_bar")
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xF2ECE6F8))
                                .border(BorderStroke(1.dp, Color(0x407C5CFF)), RoundedCornerShape(22.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val isBookmarked = state.bookmarkedPages.contains(state.currentPage)

                                BottomBarItem(
                                    icon = Icons.Default.MenuBook,
                                    label = "الصفحات",
                                    onClick = { activeSheet = BottomSheetType.DocumentNavigation },
                                    tint = Color(0xFF538A58), // Matte Green (أخضر مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                                BottomBarItem(
                                    icon = if (state.scrollMode == "horizontal") Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                                    label = "العرض",
                                    onClick = { activeSheet = BottomSheetType.ViewOptions },
                                    tint = Color(0xFF427B9B), // Matte Blue (أزرق مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                                BottomBarItem(
                                    icon = Icons.Default.ZoomIn,
                                    label = "الزووم",
                                    onClick = { activeSheet = BottomSheetType.ZoomSettings },
                                    tint = Color(0xFFB8783B), // Matte Amber/Orange (برتقالي مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                                BottomBarItem(
                                    icon = Icons.Default.Palette,
                                    label = "السمات",
                                    onClick = { activeSheet = BottomSheetType.DisplaySettings },
                                    tint = Color(0xFF7B639B), // Matte Purple (بنفسجي مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                                BottomBarItem(
                                    icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    label = "إشارة",
                                    onClick = { viewModel.toggleBookmark(context, state.currentPage) },
                                    tint = if (isBookmarked) Color(0xFFC24B66) else Color(0xFFC24B66).copy(alpha = 0.55f), // Matte Red (أحمر مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                                BottomBarItem(
                                    icon = Icons.Default.MoreHoriz,
                                    label = "أدوات",
                                    onClick = { activeSheet = BottomSheetType.MoreOptions },
                                    tint = Color(0xFF4B8B85), // Matte Teal (تيال مطفي)
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                }
            }

            // ADAPTIVE SHEETS MANAGER (SIDE SHEET FOR LANDSCAPE, BOTTOM SHEET FOR PORTRAIT)
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (activeSheet != BottomSheetType.None) {
                if (isLandscape) {
                    // Dimmed background backdrop to dismiss on click outside
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { activeSheet = BottomSheetType.None }
                    )

                    // Left Side sheet panel
                    AnimatedVisibility(
                        visible = activeSheet != BottomSheetType.None,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(360.dp)
                            .background(
                                color = Color(0xF2ECE6F8),
                                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                            )
                            .border(
                                BorderStroke(1.dp, Color(0x407C5CFF)),
                                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                            )
                            .clickable(enabled = false) { } // Prevent clicks through to background
                    ) {
                        MaterialTheme(colorScheme = glassLavenderColorScheme) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                // Top close bar for landscape side sheet
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { activeSheet = BottomSheetType.None }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Content area
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    when (activeSheet) {
                                        BottomSheetType.MoreOptions -> MoreOptionsSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onNavigate = { activeSheet = it },
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.ViewOptions -> ViewOptionsSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.DisplaySettings -> DisplaySettingsSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.ZoomSettings -> ZoomSettingsSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.JumpToPage -> JumpToPageSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.DocumentInfo -> DocumentInfoSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.Bookmarks -> BookmarksSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.DocumentNavigation -> DocumentNavigationSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.AutoScroll -> AutoScrollSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.OcrText -> OcrTextSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.CameraOcr -> CameraOcrSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        BottomSheetType.NotesAndHighlights -> NotesAndHighlightsSheet(
                                            viewModel = viewModel,
                                            state = state,
                                            onDismiss = { activeSheet = BottomSheetType.None }
                                        )
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Portrait bottom sheet
                    AppBottomSheet(
                        onDismiss = { activeSheet = BottomSheetType.None }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            when (activeSheet) {
                                BottomSheetType.MoreOptions -> MoreOptionsSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onNavigate = { activeSheet = it },
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.ViewOptions -> ViewOptionsSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.DisplaySettings -> DisplaySettingsSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.ZoomSettings -> ZoomSettingsSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.JumpToPage -> JumpToPageSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.DocumentInfo -> DocumentInfoSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.Bookmarks -> BookmarksSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.DocumentNavigation -> DocumentNavigationSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.AutoScroll -> AutoScrollSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.OcrText -> OcrTextSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.CameraOcr -> CameraOcrSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                BottomSheetType.NotesAndHighlights -> NotesAndHighlightsSheet(
                                    viewModel = viewModel,
                                    state = state,
                                    onDismiss = { activeSheet = BottomSheetType.None }
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }

            // FLOATING MINI PLAYER OVERLAY
            if (activeAudioUrl != null) {
                MiniPlayerOverlay(
                    wordName = audioWordName,
                    isPlaying = isAudioPlaying,
                    isLoading = isAudioLoading,
                    onReplay = { replayAudio() },
                    onDismiss = { stopAndDismissAudio() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = if (isBarsVisible) {
                                WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp
                            } else {
                                WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp
                            }
                        )
                )
            }

            if (showExitConfirmDialog) {
                UnsavedChangesDialog(
                    onSaveAndExit = {
                        showExitConfirmDialog = false
                        viewModel.requestSaveAndExit()
                    },
                    onDiscardAndExit = {
                        showExitConfirmDialog = false
                        viewModel.discardAndExit()
                    },
                    onCancel = {
                        showExitConfirmDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFECE6F8),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF7C5CFF).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color(0xFF7C5CFF),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "هل تريد تطبيق التعديلات؟",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1C182B),
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "هناك تعديلات غير محفوظة على هذا المستند. يمكنك حفظ التعديلات الآن أو الخروج بدون حفظ.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFF4C4566),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onSaveAndExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_save_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C5CFF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حفظ والتطبيق",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                OutlinedButton(
                    onClick = onDiscardAndExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_discard_btn"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF9C8EB9)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF4C4566)
                    )
                ) {
                    Text(
                        text = "خروج بدون حفظ",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("dialog_cancel_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "إلغاء",
                        color = Color(0xFF625B71),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PdfWebView(
    pdfPath: String,
    viewModel: PdfViewModel,
    onWebViewCreated: (WebView) -> Unit,
    onSingleTap: () -> Unit,
    onScrollEvent: () -> Unit,
    onAudioLinkClicked: (String) -> Unit,
    onBrowsingStateChanged: (Boolean) -> Unit,
    onExternalLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filePathCallbackRef = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                (0 until count).map { data.clipData!!.getItemAt(it).uri }.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            filePathCallbackRef.value?.onReceiveValue(uris)
        } else {
            filePathCallbackRef.value?.onReceiveValue(null)
        }
        filePathCallbackRef.value = null
    }

    AndroidView(
        factory = { ctx ->
            object : WebView(ctx) {
                override fun requestChildRectangleOnScreen(
                    child: android.view.View,
                    rect: android.graphics.Rect,
                    immediate: Boolean
                ): Boolean {
                    // Prevent WebView from automatically scrolling when child elements (like PDF text layer) get focus or update layout bounds
                    return false
                }

                override fun requestChildRectangleOnScreen(
                    child: android.view.View,
                    rect: android.graphics.Rect,
                    immediate: Boolean,
                    focusedChildId: Int
                ): Boolean {
                    // Prevent WebView from automatically scrolling when child elements (like PDF text layer) get focus or update layout bounds
                    return false
                }

                override fun scrollTo(x: Int, y: Int) {
                    // Prevent outer WebView from scrolling itself
                }

                override fun scrollBy(x: Int, y: Int) {
                    // Prevent outer WebView from scrolling itself
                }

                override fun overScrollBy(
                    deltaX: Int, deltaY: Int,
                    scrollX: Int, scrollY: Int,
                    scrollRangeX: Int, scrollRangeY: Int,
                    maxOverScrollX: Int, maxOverScrollY: Int,
                    isTouchEvent: Boolean
                ): Boolean {
                    return false
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                overScrollMode = android.view.View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        filePathCallbackRef.value?.onReceiveValue(null)
                        filePathCallbackRef.value = filePathCallback
                        try {
                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            fileChooserLauncher.launch(intent)
                        } catch (e: Exception) {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallbackRef.value = null
                            return false
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        val isBrowsing = url != null && (url.startsWith("http://") || url.startsWith("https://"))
                        onBrowsingStateChanged(isBrowsing)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url == null) return false
                        val isAudio = url.contains(".mp3", ignoreCase = true) ||
                                      url.contains(".wav", ignoreCase = true) ||
                                      url.contains(".ogg", ignoreCase = true) ||
                                      url.contains(".m4a", ignoreCase = true) ||
                                      url.contains("/audio/", ignoreCase = true) ||
                                      url.contains("/sounds/", ignoreCase = true) ||
                                      url.contains("/pronunciation/", ignoreCase = true) ||
                                      url.contains("audio_url=", ignoreCase = true) ||
                                      url.contains("translate_tts", ignoreCase = true) ||
                                      url.contains("translate.google", ignoreCase = true) ||
                                      url.contains("google.com/speech", ignoreCase = true)
                        if (isAudio) {
                            onAudioLinkClicked(url)
                            return true
                        }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            onBrowsingStateChanged(true)
                            onExternalLinkClicked(url)
                            return true
                        }
                        return false
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        val isAudio = url.contains(".mp3", ignoreCase = true) ||
                                      url.contains(".wav", ignoreCase = true) ||
                                      url.contains(".ogg", ignoreCase = true) ||
                                      url.contains(".m4a", ignoreCase = true) ||
                                      url.contains("/audio/", ignoreCase = true) ||
                                      url.contains("/sounds/", ignoreCase = true) ||
                                      url.contains("/pronunciation/", ignoreCase = true) ||
                                      url.contains("audio_url=", ignoreCase = true) ||
                                      url.contains("translate_tts", ignoreCase = true) ||
                                      url.contains("translate.google", ignoreCase = true) ||
                                      url.contains("google.com/speech", ignoreCase = true)
                        if (isAudio) {
                            onAudioLinkClicked(url)
                            return true
                        }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            onBrowsingStateChanged(true)
                            onExternalLinkClicked(url)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val isBrowsing = url != null && (url.startsWith("http://") || url.startsWith("https://"))
                        onBrowsingStateChanged(isBrowsing)
                        if (isBrowsing) return

                        // Script to establish bridge listeners, remove PDF.js toolbar, and sync state
                        val setupScript = """
                            (function() {
                                function checkPDFjs() {
                                    if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.initializedPromise) {
                                        PDFViewerApplication.initializedPromise.then(() => {
                                            function initFastScrubber() {
                                                if (document.getElementById('fastScrubberTrack')) return;

                                                var track = document.createElement('div');
                                                track.id = 'fastScrubberTrack';
                                                track.className = 'fast-scrubber-track';

                                                var thumb = document.createElement('div');
                                                thumb.id = 'fastScrubberThumb';
                                                thumb.className = 'fast-scrubber-thumb';

                                                var handle = document.createElement('div');
                                                handle.className = 'wps-scroll-handle';
                                                handle.innerHTML = '<div class="wps-handle-line"></div><div class="wps-handle-line"></div><div class="wps-handle-line"></div>';

                                                thumb.appendChild(handle);
                                                track.appendChild(thumb);
                                                document.body.appendChild(track);

                                                var isScrubbing = false;
                                                var startTouchOffsetY = 0;

                                                function updateScrubber() {
                                                    var container = document.getElementById('viewerContainer');
                                                    if (!container || !track || !thumb) return;
                                                    var maxScroll = container.scrollHeight - container.clientHeight;
                                                    if (maxScroll <= 0) return;
                                                    var trackHeight = track.clientHeight - thumb.clientHeight;
                                                    if (trackHeight <= 0) return;

                                                    var scrollRatio = container.scrollTop / maxScroll;
                                                     if (scrollRatio < 0) scrollRatio = 0;
                                                     if (scrollRatio > 1) scrollRatio = 1;

                                                    var thumbTop = scrollRatio * trackHeight;
                                                    thumb.style.transform = 'translateY(' + thumbTop + 'px)';
                                                }

                                                function doScrub(e) {
                                                    if (!isScrubbing) return;
                                                    var container = document.getElementById('viewerContainer');
                                                    if (!container || !track || !thumb) return;
                                                    var rect = track.getBoundingClientRect();
                                                    var clientY = (e.touches && e.touches.length > 0) ? e.touches[0].clientY : e.clientY;
                                                    var trackHeight = rect.height - thumb.clientHeight;
                                                    if (trackHeight <= 0) return;

                                                    var offsetY = clientY - rect.top - startTouchOffsetY;
                                                    var ratio = offsetY / trackHeight;
                                                    if (ratio < 0) ratio = 0;
                                                    if (ratio > 1) ratio = 1;

                                                    var maxScroll = container.scrollHeight - container.clientHeight;
                                                    container.scrollTop = ratio * maxScroll;

                                                    document.body.classList.add('scrolling');
                                                    document.body.classList.add('scrubbing');
                                                    updateScrubber();
                                                    if (window.AndroidBridge && window.AndroidBridge.onScroll) {
                                                        window.AndroidBridge.onScroll();
                                                     }
                                                }

                                                function onThumbStart(e) {
                                                    isScrubbing = true;
                                                    var clientY = (e.touches && e.touches.length > 0) ? e.touches[0].clientY : e.clientY;
                                                    var thumbRect = thumb.getBoundingClientRect();
                                                    startTouchOffsetY = clientY - thumbRect.top;
                                                    if (startTouchOffsetY < 0 || startTouchOffsetY > thumb.clientHeight) {
                                                        startTouchOffsetY = thumb.clientHeight / 2;
                                                    }

                                                    document.body.classList.add('scrolling');
                                                    document.body.classList.add('scrubbing');

                                                    doScrub(e);
                                                    if (e.cancelable) e.preventDefault();
                                                    e.stopPropagation();
                                                }

                                                thumb.addEventListener('touchstart', onThumbStart, {passive: false});

                                                window.addEventListener('touchmove', function(e) {
                                                    if (isScrubbing) {
                                                        doScrub(e);
                                                        if (e.cancelable) e.preventDefault();
                                                        e.stopPropagation();
                                                    }
                                                }, {passive: false});

                                                window.addEventListener('touchend', function(e) {
                                                    if (isScrubbing) {
                                                        isScrubbing = false;
                                                        document.body.classList.remove('scrubbing');
                                                    }
                                                });

                                                window.addEventListener('touchcancel', function(e) {
                                                    if (isScrubbing) {
                                                        isScrubbing = false;
                                                        document.body.classList.remove('scrubbing');
                                                    }
                                                });

                                                thumb.addEventListener('mousedown', onThumbStart);

                                                window.addEventListener('mousemove', function(e) {
                                                    if (isScrubbing) {
                                                        doScrub(e);
                                                    }
                                                });

                                                window.addEventListener('mouseup', function() {
                                                    if (isScrubbing) {
                                                        isScrubbing = false;
                                                        document.body.classList.remove('scrubbing');
                                                    }
                                                });

                                                window.updateFastScrubber = updateScrubber;
                                            }

                                            // Function to safely report current and total pages
                                            function reportPageStatus() {
                                                try {
                                                    initFastScrubber();
                                                    if (window.updateFastScrubber) {
                                                        window.updateFastScrubber();
                                                    }
                                                    var total = PDFViewerApplication.pagesCount || (PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.pagesCount) || 0;
                                                    var current = PDFViewerApplication.page || (PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentPageNumber) || 1;
                                                    if (total > 0) {
                                                        AndroidBridge.onPageChanged(current, total);
                                                    }

                                                    // Inject custom page number indicators to each page
                                                    var pages = document.querySelectorAll('.page');
                                                    pages.forEach(function(page) {
                                                        var pageNum = page.getAttribute('data-page-number');
                                                        if (!pageNum && page.id && page.id.startsWith('pageContainer')) {
                                                            pageNum = page.id.replace('pageContainer', '');
                                                        }
                                                        if (pageNum) {
                                                            var indicator = page.querySelector('.custom-page-number-indicator');
                                                            if (!indicator) {
                                                                indicator = document.createElement('div');
                                                                indicator.className = 'custom-page-number-indicator';
                                                                indicator.innerText = pageNum;
                                                                page.appendChild(indicator);
                                                            } else if (indicator.innerText !== pageNum) {
                                                                indicator.innerText = pageNum;
                                                            }
                                                        }
                                                    });
                                                } catch (err) {
                                                    console.error("Error reporting page status: " + err);
                                                }
                                            }

                                            // 1. Setup pagechanging, pagesinit, pagesloaded event to call back to Android
                                            PDFViewerApplication.eventBus.on('pagechanging', (e) => {
                                                AndroidBridge.onPageChanged(e.pageNumber, e.pagesCount);
                                            });

                                            PDFViewerApplication.eventBus.on('pagesinit', (e) => {
                                                reportPageStatus();
                                                try {
                                                    PDFViewerApplication.pdfViewer.scrollMode = ${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0};
                                                } catch (err) {}
                                            });

                                            PDFViewerApplication.eventBus.on('pagesloaded', (e) => {
                                                reportPageStatus();
                                                try {
                                                    PDFViewerApplication.pdfViewer.scrollMode = ${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0};
                                                } catch (err) {}
                                            });

                                            // Report immediately
                                            reportPageStatus();

                                            // Setup a solid fallback interval to poll page changes every 400ms
                                            setInterval(reportPageStatus, 400);

                                            // Setup scroll and touchmove listener to trigger page indicator visibility
                                            var container = document.getElementById('viewerContainer');
                                            if (container) {
                                                var scrollTimeout;
                                                var handleScroll = () => {
                                                    document.body.classList.add('scrolling');
                                                    if (window.updateFastScrubber) {
                                                        window.updateFastScrubber();
                                                    }
                                                    clearTimeout(scrollTimeout);
                                                    scrollTimeout = setTimeout(() => {
                                                        if (!document.body.classList.contains('scrubbing')) {
                                                            document.body.classList.remove('scrolling');
                                                        }
                                                    }, 2000);
                                                    AndroidBridge.onScroll();
                                                };
                                                container.addEventListener('scroll', handleScroll, {passive: true});
                                                container.addEventListener('touchmove', handleScroll, {passive: true});
                                            }

                                            // 2. Setup find event listeners to call back search counts
                                            function reportSearchMatches(e) {
                                                if (e && e.matchesCount) {
                                                    AndroidBridge.onSearchCountUpdated(e.matchesCount.total, e.matchesCount.current);
                                                } else {
                                                    // Fallback check on findController
                                                    try {
                                                        var fc = PDFViewerApplication.findController;
                                                        if (fc && fc._matchesCount) {
                                                            AndroidBridge.onSearchCountUpdated(fc._matchesCount.total, fc._matchesCount.current);
                                                        } else {
                                                            AndroidBridge.onSearchCountUpdated(0, 0);
                                                        }
                                                    } catch(err) {
                                                        AndroidBridge.onSearchCountUpdated(0, 0);
                                                    }
                                                }
                                            }

                                            PDFViewerApplication.eventBus.on('updatefindcontrolstate', (e) => {
                                                reportSearchMatches(e);
                                            });

                                            PDFViewerApplication.eventBus.on('updatefindmatchescount', (e) => {
                                                reportSearchMatches(e);
                                            });

                                            PDFViewerApplication.eventBus.on('annotationeditormodechanged', (e) => {
                                                if (e && typeof e.mode !== 'undefined' && window.AndroidBridge && window.AndroidBridge.onEditorModeChanged) {
                                                    window.AndroidBridge.onEditorModeChanged(e.mode);
                                                }
                                            });

                                            PDFViewerApplication.eventBus.on('annotationeditorparamschanged', (e) => {
                                                if (e && e.details && window.AndroidBridge && window.AndroidBridge.onEditorParamsChanged) {
                                                    var paramsObj = {};
                                                    try {
                                                        for (var entry of e.details) {
                                                            if (Array.isArray(entry) && entry.length >= 2) {
                                                                paramsObj[entry[0]] = entry[1];
                                                            }
                                                        }
                                                    } catch(err) {}
                                                    window.AndroidBridge.onEditorParamsChanged(JSON.stringify(paramsObj));
                                                }
                                            });

                                            PDFViewerApplication.eventBus.on('annotationeditorstateschanged', () => {
                                                if (window.AndroidBridge && window.AndroidBridge.onAnnotationChanged) {
                                                    window.AndroidBridge.onAnnotationChanged();
                                                }
                                            });

                                            // Setup FreeText Control Header Observer
                                            function attachFreeTextHeader(freeTextEditor) {
                                                if (!freeTextEditor || freeTextEditor.querySelector('.freetext-control-header')) return;
                                                
                                                var header = document.createElement('div');
                                                header.className = 'freetext-control-header';
                                                header.setAttribute('contenteditable', 'false');
                                                
                                                var internal = freeTextEditor.querySelector('.internal');
                                                var currentFontSize = 16;
                                                if (internal) {
                                                    var computedSize = parseInt(window.getComputedStyle(internal).fontSize);
                                                    if (computedSize && !isNaN(computedSize) && computedSize > 0) {
                                                        currentFontSize = computedSize;
                                                    }
                                                }
                                                
                                                header.innerHTML = `
                                                    <button type="button" class="ft-btn ft-down" title="تصغير الخط">A-</button>
                                                    <span class="ft-label">${'$'}{currentFontSize}px</span>
                                                    <button type="button" class="ft-btn ft-up" title="تكبير الخط">A+</button>
                                                    <div class="ft-divider"></div>
                                                    <button type="button" class="ft-btn ft-del" title="حذف النص">
                                                        <svg viewBox="0 0 24 24" width="14" height="14" fill="#FF5252">
                                                            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                                                        </svg>
                                                    </button>
                                                `;

                                                header.addEventListener('mousedown', function(e) { e.stopPropagation(); });
                                                header.addEventListener('touchstart', function(e) { e.stopPropagation(); }, {passive: true});
                                                header.addEventListener('click', function(e) { e.stopPropagation(); });

                                                var label = header.querySelector('.ft-label');
                                                var btnDown = header.querySelector('.ft-down');
                                                var btnUp = header.querySelector('.ft-up');
                                                var btnDel = header.querySelector('.ft-del');

                                                function updateSize(newSize) {
                                                    if (newSize < 10) newSize = 10;
                                                    if (newSize > 72) newSize = 72;
                                                    currentFontSize = newSize;
                                                    if (label) label.innerText = currentFontSize + 'px';
                                                    if (internal) {
                                                        internal.style.fontSize = currentFontSize + 'px';
                                                    }
                                                    if (freeTextEditor.annotationEditor) {
                                                        freeTextEditor.annotationEditor.fontSize = currentFontSize;
                                                    }
                                                    try {
                                                        if (window.PDFViewerApplication && window.PDFViewerApplication.eventBus) {
                                                            window.PDFViewerApplication.eventBus.dispatch('switchannotationeditorparams', {
                                                                type: 1,
                                                                value: currentFontSize
                                                            });
                                                        }
                                                    } catch(err) {}
                                                }

                                                if (btnDown) {
                                                    btnDown.addEventListener('click', function(e) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        updateSize(currentFontSize - 2);
                                                    });
                                                }

                                                if (btnUp) {
                                                    btnUp.addEventListener('click', function(e) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        updateSize(currentFontSize + 2);
                                                    });
                                                }

                                                if (btnDel) {
                                                    btnDel.addEventListener('click', function(e) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        if (freeTextEditor.annotationEditor && typeof freeTextEditor.annotationEditor.remove === 'function') {
                                                            freeTextEditor.annotationEditor.remove();
                                                        } else if (typeof freeTextEditor.remove === 'function') {
                                                            freeTextEditor.remove();
                                                        }
                                                    });
                                                }

                                                freeTextEditor.appendChild(header);
                                            }

                                            var freeTextObserver = new MutationObserver(function(mutations) {
                                                var editors = document.querySelectorAll('.annotationEditorLayer .freeTextEditor');
                                                editors.forEach(function(editor) {
                                                    attachFreeTextHeader(editor);
                                                });
                                            });
                                            freeTextObserver.observe(document.body, { childList: true, subtree: true });

                                            // 3. Complete hide/vanish of PDF.js official viewer toolbar elements
                                            var style = document.createElement('style');
                                            style.type = 'text/css';
                                            style.innerHTML = `
                                                #sidebarContainer, #secondaryToolbar, .findbar, #toolbarViewerLeft, #toolbarViewerMiddle { 
                                                    display: none !important; 
                                                } 
                                                #toolbarContainer, .toolbar {
                                                    background: transparent !important;
                                                    border: none !important;
                                                    box-shadow: none !important;
                                                    height: 0 !important;
                                                    min-height: 0 !important;
                                                    overflow: visible !important;
                                                    position: absolute !important;
                                                    top: 0 !important;
                                                    left: 0 !important;
                                                    right: 0 !important;
                                                    z-index: 99999 !important;
                                                    pointer-events: none !important;
                                                }
                                                #toolbarViewerRight {
                                                    pointer-events: auto !important;
                                                    width: 100% !important;
                                                    display: flex !important;
                                                    justify-content: center !important;
                                                }
                                                #editorModeButtons {
                                                    display: none !important;
                                                } 
                                                #outerContainer {
                                                    position: fixed !important;
                                                    top: 0 !important;
                                                    left: 0 !important;
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    overflow: hidden !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    overscroll-behavior: none !important;
                                                    overscroll-behavior-y: none !important;
                                                }
                                                #viewerContainer { 
                                                    position: absolute !important;
                                                    top: 0 !important; 
                                                    left: 0 !important;
                                                    right: 0 !important;
                                                    bottom: 0 !important; 
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    overflow: auto !important;
                                                    scroll-snap-type: none !important;
                                                    overscroll-behavior: none !important;
                                                    overscroll-behavior-y: none !important;
                                                    -webkit-overflow-scrolling: touch !important;
                                                }
                                                html, body {
                                                    position: fixed !important;
                                                    top: 0 !important;
                                                    left: 0 !important;
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    overflow: hidden !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    overscroll-behavior: none !important;
                                                    overscroll-behavior-y: none !important;
                                                }
                                                .page, .spread, .dummyPage {
                                                    scroll-snap-align: none !important;
                                                    scroll-snap-stop: normal !important;
                                                    contain: layout !important;
                                                }
                                                #outerContainer, #viewerContainer, #viewer, .page, .spread, .dummyPage, .canvasWrapper, .textLayer, .annotationLayer {
                                                    overflow-anchor: none !important;
                                                    scroll-snap-type: none !important;
                                                    scroll-snap-align: none !important;
                                                }
                                                * {
                                                    overflow-anchor: none !important;
                                                    scroll-behavior: auto !important;
                                                    scroll-snap-type: none !important;
                                                    scroll-snap-align: none !important;
                                                }
                                                body.edit-mode-active #viewerContainer,
                                                body.edit-mode-active .page,
                                                body.edit-mode-active .spread,
                                                body.edit-mode-active #outerContainer,
                                                body.edit-mode-active html,
                                                body.edit-mode-active body {
                                                    touch-action: none !important;
                                                    user-select: none !important;
                                                    -webkit-user-select: none !important;
                                                }
                                                .page {
                                                    position: relative !important;
                                                }
                                                .custom-page-number-indicator {
                                                    position: absolute !important;
                                                    bottom: 8px !important;
                                                    right: 8px !important;
                                                    background-color: rgba(32, 26, 36, 0.85) !important;
                                                    color: #ffffff !important;
                                                    font-size: 10px !important;
                                                    font-weight: bold !important;
                                                    padding: 3px 6px !important;
                                                    border-radius: 4px !important;
                                                    z-index: 99999 !important;
                                                    opacity: 0 !important;
                                                    transition: opacity 0.3s ease-in-out !important;
                                                    pointer-events: none !important;
                                                    direction: ltr !important;
                                                    font-family: system-ui, -apple-system, sans-serif !important;
                                                    box-shadow: 0 2px 5px rgba(0,0,0,0.3) !important;
                                                    border: 1px solid rgba(255,255,255,0.15) !important;
                                                    line-height: 1 !important;
                                                }
                                                body.scrolling .custom-page-number-indicator {
                                                    opacity: 1 !important;
                                                }
                                                .annotationEditorLayer .freeTextEditor {
                                                    position: absolute !important;
                                                    padding: 6px 12px !important;
                                                    border: 1.5px dashed #7C5CFF !important;
                                                    border-radius: 8px !important;
                                                    background-color: rgba(236, 230, 248, 0.45) !important;
                                                    box-sizing: border-box !important;
                                                    min-width: 60px !important;
                                                    min-height: 36px !important;
                                                    width: auto !important;
                                                    height: auto !important;
                                                    touch-action: none !important;
                                                    margin: 0 !important;
                                                    transform-origin: 0 0 !important;
                                                    overflow: visible !important;
                                                    transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease !important;
                                                }
                                                .freetext-control-header {
                                                    position: absolute !important;
                                                    top: -40px !important;
                                                    left: 0 !important;
                                                    display: flex !important;
                                                    align-items: center !important;
                                                    gap: 4px !important;
                                                    background: #1F1B2C !important;
                                                    color: #FFFFFF !important;
                                                    padding: 3px 8px !important;
                                                    border-radius: 18px !important;
                                                    box-shadow: 0 4px 12px rgba(0,0,0,0.35) !important;
                                                    border: 1px solid rgba(255,255,255,0.2) !important;
                                                    z-index: 999999 !important;
                                                    direction: ltr !important;
                                                    user-select: none !important;
                                                    -webkit-user-select: none !important;
                                                    pointer-events: auto !important;
                                                    white-space: nowrap !important;
                                                    font-family: system-ui, -apple-system, sans-serif !important;
                                                }
                                                .freetext-control-header .ft-btn {
                                                    background: rgba(255,255,255,0.12) !important;
                                                    border: none !important;
                                                    color: #FFFFFF !important;
                                                    font-size: 11px !important;
                                                    font-weight: bold !important;
                                                    width: 24px !important;
                                                    height: 24px !important;
                                                    border-radius: 12px !important;
                                                    display: inline-flex !important;
                                                    align-items: center !important;
                                                    justify-content: center !important;
                                                    cursor: pointer !important;
                                                    padding: 0 !important;
                                                    margin: 0 !important;
                                                    outline: none !important;
                                                }
                                                .freetext-control-header .ft-btn:active {
                                                    background: rgba(255,255,255,0.3) !important;
                                                }
                                                .freetext-control-header .ft-label {
                                                    font-size: 11px !important;
                                                    font-weight: 600 !important;
                                                    color: #D1C4E9 !important;
                                                    padding: 0 4px !important;
                                                }
                                                .freetext-control-header .ft-del {
                                                    background: rgba(244, 67, 54, 0.25) !important;
                                                    color: #FF5252 !important;
                                                }
                                                .freetext-control-header .ft-del:active {
                                                    background: rgba(244, 67, 54, 0.5) !important;
                                                }
                                                .freetext-control-header .ft-divider {
                                                    width: 1px !important;
                                                    height: 14px !important;
                                                    background-color: rgba(255,255,255,0.2) !important;
                                                    margin: 0 2px !important;
                                                }
                                                .annotationEditorLayer .freeTextEditor.selectedEditor,
                                                .annotationEditorLayer .freeTextEditor:focus-within {
                                                    border: 2px solid #7C5CFF !important;
                                                    box-shadow: 0 0 0 3px rgba(124, 92, 255, 0.25) !important;
                                                    background-color: #FFFFFF !important;
                                                }
                                                .annotationEditorLayer .freeTextEditor .internal {
                                                    background: transparent !important;
                                                    border: none !important;
                                                    outline: none !important;
                                                    padding: 0 !important;
                                                    margin: 0 !important;
                                                    font-family: system-ui, -apple-system, sans-serif !important;
                                                    font-size: 16px !important;
                                                    line-height: 1.4 !important;
                                                    color: #1C182B !important;
                                                    white-space: pre-wrap !important;
                                                    word-break: break-word !important;
                                                    overflow: visible !important;
                                                    min-width: 40px !important;
                                                    width: auto !important;
                                                    height: auto !important;
                                                    display: inline-block !important;
                                                }
                                                .fast-scrubber-track {
                                                    position: fixed !important;
                                                    top: 70px !important;
                                                    bottom: 90px !important;
                                                    left: 0px !important;
                                                    width: 36px !important;
                                                    z-index: 99999 !important;
                                                    pointer-events: none !important;
                                                    opacity: 0 !important;
                                                    transition: opacity 0.3s ease-in-out !important;
                                                    touch-action: none !important;
                                                }
                                                body.scrolling .fast-scrubber-track,
                                                body.scrubbing .fast-scrubber-track {
                                                    opacity: 1 !important;
                                                }
                                                .fast-scrubber-thumb {
                                                    position: absolute !important;
                                                    left: 0px !important;
                                                    top: 0 !important;
                                                    width: 36px !important;
                                                    height: 54px !important;
                                                    display: flex !important;
                                                    align-items: center !important;
                                                    justify-content: center !important;
                                                    cursor: pointer !important;
                                                    touch-action: none !important;
                                                    will-change: transform !important;
                                                    pointer-events: auto !important;
                                                }
                                                .wps-scroll-handle {
                                                    width: 18px !important;
                                                    height: 46px !important;
                                                    background: linear-gradient(180deg, #8E6CFF 0%, #673AB7 100%) !important;
                                                    border-radius: 12px !important;
                                                    box-shadow: 0 4px 12px rgba(103, 58, 183, 0.5) !important;
                                                    border: 1.5px solid rgba(255, 255, 255, 0.95) !important;
                                                    display: flex !important;
                                                    flex-direction: column !important;
                                                    align-items: center !important;
                                                    justify-content: center !important;
                                                    gap: 3.5px !important;
                                                    transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease !important;
                                                }
                                                .wps-handle-line {
                                                    width: 8px !important;
                                                    height: 2px !important;
                                                    background-color: rgba(255, 255, 255, 0.92) !important;
                                                    border-radius: 2px !important;
                                                }
                                                body.scrubbing .wps-scroll-handle {
                                                    transform: scale(1.15) !important;
                                                    background: linear-gradient(180deg, #9E7CFF 0%, #7E57C2 100%) !important;
                                                    box-shadow: 0 6px 18px rgba(103, 58, 183, 0.75) !important;
                                                }
                                            `;
                                            document.head.appendChild(style);

                                            // Define custom helper functions globally
                                            window.applyTheme = function(themeName) {
                                                var container = document.getElementById('viewerContainer');
                                                if (!container) return;
                                                container.style.filter = '';
                                                container.style.backgroundColor = '';
                                                document.body.style.backgroundColor = '';
                                                if (themeName === 'dark') {
                                                    container.style.filter = 'invert(0.9) hue-rotate(180deg)';
                                                    container.style.backgroundColor = '#121212';
                                                    document.body.style.backgroundColor = '#121212';
                                                } else if (themeName === 'black') {
                                                    container.style.filter = 'invert(1) contrast(1.1)';
                                                    container.style.backgroundColor = '#000000';
                                                    document.body.style.backgroundColor = '#000000';
                                                } else if (themeName === 'sepia') {
                                                    container.style.filter = 'sepia(0.55) contrast(0.95) brightness(0.95)';
                                                    container.style.backgroundColor = '#F4ECD8';
                                                    document.body.style.backgroundColor = '#F4ECD8';
                                                } else {
                                                    container.style.backgroundColor = '#F4F4F9';
                                                    document.body.style.backgroundColor = '#F4F4F9';
                                                }
                                            };

                                            window.autoScrollInterval = null;
                                            window.startAutoScroll = function(speed) {
                                                if (window.autoScrollInterval) clearInterval(window.autoScrollInterval);
                                                var container = document.getElementById('viewerContainer');
                                                if (!container) return;
                                                var lastTime = performance.now();
                                                window.autoScrollInterval = setInterval(function() {
                                                    var now = performance.now();
                                                    var delta = (now - lastTime) / 1000;
                                                    lastTime = now;
                                                    container.scrollTop += speed * delta;
                                                }, 16);
                                            };

                                            window.stopAutoScroll = function() {
                                                if (window.autoScrollInterval) {
                                                    clearInterval(window.autoScrollInterval);
                                                    window.autoScrollInterval = null;
                                                }
                                            };

                                            window.doubleTapZoomFactor = ${state.doubleTapZoomFactor};
                                            window.defaultScale = null;
                                            window.setScale = function(scale) {
                                                try {
                                                    if (!window.defaultScale && typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentScale) {
                                                        window.defaultScale = PDFViewerApplication.pdfViewer.currentScale;
                                                    }
                                                    var minScale = 0.1;
                                                    var maxScale = 5.0;
                                                    if (scale < minScale) scale = minScale;
                                                    if (scale > maxScale) scale = maxScale;
                                                    if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                        PDFViewerApplication.pdfViewer.currentScale = scale;
                                                    }
                                                } catch (e) {
                                                    console.error("Error in setScale JS: " + e);
                                                }
                                            };

                                            window.addEventListener('resize', function() {
                                                if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                    PDFViewerApplication.pdfViewer.update();
                                                }
                                            });

                                            var initialTouchDist = 0;
                                            var initialScale = 1.0;

                                            document.addEventListener('touchstart', function(e) {
                                                if (e.touches.length === 2) {
                                                    var t1 = e.touches[0];
                                                    var t2 = e.touches[1];
                                                    initialTouchDist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
                                                    initialScale = PDFViewerApplication.pdfViewer.currentScale || 1.0;
                                                }
                                            }, { passive: true });

                                            document.addEventListener('touchmove', function(e) {
                                                if (e.touches.length === 2 && initialTouchDist > 0) {
                                                    e.preventDefault();
                                                    var t1 = e.touches[0];
                                                    var t2 = e.touches[1];
                                                    var dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
                                                    var factor = dist / initialTouchDist;
                                                    var newScale = initialScale * factor;
                                                    if (!window.defaultScale && typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentScale) {
                                                        window.defaultScale = PDFViewerApplication.pdfViewer.currentScale;
                                                    }
                                                    var minScale = 0.1;
                                                    newScale = Math.min(Math.max(newScale, minScale), 5.0);
                                                    window.setScale(newScale);
                                                    AndroidBridge.onScaleChanged(newScale);
                                                }
                                            }, { passive: false });

                                            document.addEventListener('touchend', function(e) {
                                                if (e.touches.length < 2) {
                                                    initialTouchDist = 0;
                                                }
                                            }, { passive: true });

                                            // 5. Setup click listener for single tap bar toggling and double tap zoom
                                            var lastTapTime = 0;
                                            var lastTapX = 0;
                                            var lastTapY = 0;
                                            var singleTapTimer = null;

                                            document.addEventListener('click', function(e) {
                                                var interactive = e.target.closest('a, button, input, select, textarea, .internalLink, #fastScrubberTrack, #fastScrubberThumb, .wps-scroll-handle, .annotationEditorLayer, .freeTextEditor, .internal, [contenteditable="true"]');
                                                if (interactive || document.body.classList.contains('edit-mode-active')) {
                                                    return;
                                                }
                                                if (window.getSelection && window.getSelection().toString().trim() !== "") {
                                                    return;
                                                }

                                                var now = Date.now();
                                                var x = e.clientX;
                                                var y = e.clientY;

                                                var timeDiff = now - lastTapTime;
                                                var dist = Math.hypot(x - lastTapX, y - lastTapY);

                                                if (timeDiff > 0 && timeDiff < 300 && dist < 35) {
                                                    // Double tap detected: Toggle zoom between normal fit and 2x
                                                    if (singleTapTimer) {
                                                        clearTimeout(singleTapTimer);
                                                        singleTapTimer = null;
                                                    }
                                                    lastTapTime = 0;

                                                    if (!window.defaultScale && typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentScale) {
                                                        window.defaultScale = PDFViewerApplication.pdfViewer.currentScale;
                                                    }

                                                    var baseScale = window.defaultScale || (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer ? PDFViewerApplication.pdfViewer.currentScale : 1.0);
                                                    var curScale = (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentScale) ? PDFViewerApplication.pdfViewer.currentScale : baseScale;

                                                    var factor = window.doubleTapZoomFactor || ${state.doubleTapZoomFactor};
                                                    var targetScale = (curScale < baseScale * 1.25) ? (baseScale * factor) : baseScale;

                                                    window.setScale(targetScale);
                                                    if (window.AndroidBridge && window.AndroidBridge.onScaleChanged) {
                                                        window.AndroidBridge.onScaleChanged(targetScale);
                                                    }

                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                } else {
                                                    // Candidate for single tap
                                                    lastTapTime = now;
                                                    lastTapX = x;
                                                    lastTapY = y;

                                                    if (singleTapTimer) {
                                                        clearTimeout(singleTapTimer);
                                                    }
                                                    singleTapTimer = setTimeout(function() {
                                                        singleTapTimer = null;
                                                        if (window.AndroidBridge && window.AndroidBridge.onSingleTap) {
                                                            window.AndroidBridge.onSingleTap();
                                                        }
                                                    }, 250);
                                                }
                                            }, true);

                                            // Capture-phase link interceptor to prevent WebView navigation for audio URLs
                                            document.addEventListener('click', function(e) {
                                                var anchor = e.target.closest('a');
                                                if (anchor && anchor.href) {
                                                    var url = anchor.href;
                                                    var isAudio = url.toLowerCase().indexOf('.mp3') !== -1 || 
                                                                  url.toLowerCase().indexOf('.wav') !== -1 || 
                                                                  url.toLowerCase().indexOf('.ogg') !== -1 || 
                                                                  url.toLowerCase().indexOf('.m4a') !== -1 || 
                                                                  url.toLowerCase().indexOf('/audio/') !== -1 || 
                                                                  url.toLowerCase().indexOf('/sounds/') !== -1 || 
                                                                  url.toLowerCase().indexOf('/pronunciation/') !== -1 || 
                                                                  url.toLowerCase().indexOf('audio_url=') !== -1 || url.toLowerCase().indexOf('translate_tts') !== -1 || url.toLowerCase().indexOf('translate.google') !== -1 || url.toLowerCase().indexOf('google.com/speech') !== -1;
                                                    if (isAudio) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        AndroidBridge.onAudioLinkClicked(url);
                                                        return false;
                                                    }
                                                }
                                            }, true);

                                            // Pause auto-scroll on manual user interaction (touch, drag, mouse click, mouse wheel)
                                            var handleManualInteraction = function() {
                                                if (window.autoScrollInterval) {
                                                     AndroidBridge.onManualScroll();
                                                }
                                            };
                                            var viewerContainer = document.getElementById('viewerContainer');
                                            if (viewerContainer) {
                                                viewerContainer.addEventListener('touchstart', handleManualInteraction, { passive: true });
                                                viewerContainer.addEventListener('mousedown', handleManualInteraction, { passive: true });
                                                viewerContainer.addEventListener('wheel', handleManualInteraction, { passive: true });
                                            } else {
                                                document.addEventListener('touchstart', handleManualInteraction, { passive: true });
                                                document.addEventListener('mousedown', handleManualInteraction, { passive: true });
                                            }

                                            // Apply current UI states
                                            window.applyTheme('${state.readingTheme}');
                                            PDFViewerApplication.pdfViewer.scrollMode = ${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0};
                                            if ('${state.activeEditTool}' !== 'none') {
                                                document.body.classList.add('edit-mode-active');
                                            } else {
                                                document.body.classList.remove('edit-mode-active');
                                            }

                                            // 4. Initialise page to saved state
                                            var initialPage = ${state.currentPage};
                                            if (initialPage > 1) {
                                                PDFViewerApplication.pdfViewer.currentPageNumber = initialPage;
                                            }

                                            // Report exact scale from PDF.js to Android bridge
                                            if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.eventBus) {
                                                var reportCurrentScale = function() {
                                                    try {
                                                        if (PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.currentScale) {
                                                            var cs = PDFViewerApplication.pdfViewer.currentScale;
                                                            if (!window.defaultScale) { window.defaultScale = cs; }
                                                            if (window.AndroidBridge && window.AndroidBridge.onScaleChanged) {
                                                                window.AndroidBridge.onScaleChanged(cs);
                                                            }
                                                        }
                                                    } catch(e) {}
                                                };

                                                PDFViewerApplication.eventBus.on('scalechanging', function(evt) {
                                                    if (evt && evt.scale && window.AndroidBridge && window.AndroidBridge.onScaleChanged) {
                                                        window.AndroidBridge.onScaleChanged(evt.scale);
                                                    } else {
                                                        reportCurrentScale();
                                                    }
                                                });
                                                PDFViewerApplication.eventBus.on('pagesinit', reportCurrentScale);
                                                PDFViewerApplication.eventBus.on('pagerendered', reportCurrentScale);
                                                PDFViewerApplication.eventBus.on('scalechanged', reportCurrentScale);

                                                setTimeout(reportCurrentScale, 300);
                                                setTimeout(reportCurrentScale, 800);
                                            }

                                            // 4b. Completely disable PDF.js History Manager and Hash Updates to prevent scroll jumps
                                            try {
                                                PDFViewerApplication.preferences.set('disableHistory', true);
                                            } catch (e) {}
                                            try {
                                                if (window.PDFViewerApplicationOptions) {
                                                    window.PDFViewerApplicationOptions.set('disableHistory', true);
                                                    window.PDFViewerApplicationOptions.set('historyUpdateUrl', false);
                                                }
                                            } catch (e) {}

                                            try {
                                                window.history.pushState = function() {};
                                                window.history.replaceState = function() {};
                                            } catch (e) {}

                                            try {
                                                var _pdfHistory = null;
                                                Object.defineProperty(PDFViewerApplication, 'pdfHistory', {
                                                    get: function() {
                                                        return _pdfHistory;
                                                    },
                                                    set: function(val) {
                                                        _pdfHistory = val;
                                                        if (_pdfHistory) {
                                                            _pdfHistory.push = function() {};
                                                            _pdfHistory.pushPage = function() {};
                                                            _pdfHistory.pushCurrentPosition = function() {};
                                                            _pdfHistory.updateCurrentPage = function() {};
                                                            _pdfHistory.writeQueue = function() {};
                                                            _pdfHistory.initialize = function() {};
                                                            _pdfHistory._updateViewarea = function() {};
                                                        }
                                                    },
                                                    configurable: true,
                                                    enumerable: true
                                                });
                                            } catch (e) {
                                                if (PDFViewerApplication.pdfHistory) {
                                                    PDFViewerApplication.pdfHistory.push = function() {};
                                                    PDFViewerApplication.pdfHistory.pushPage = function() {};
                                                    PDFViewerApplication.pdfHistory.pushCurrentPosition = function() {};
                                                    PDFViewerApplication.pdfHistory.updateCurrentPage = function() {};
                                                    PDFViewerApplication.pdfHistory.writeQueue = function() {};
                                                    PDFViewerApplication.pdfHistory.initialize = function() {};
                                                    PDFViewerApplication.pdfHistory._updateViewarea = function() {};
                                                }
                                            }

                                            // Diagnostics script for scroll jump and resize events
                                            (function() {
                                                function log(msg) {
                                                    console.log('JUMPDEBUG: ' + msg);
                                                }
                                                var vc = document.getElementById('viewerContainer');
                                                if (vc) {
                                                    var lastTop = vc.scrollTop;
                                                    setInterval(function() {
                                                        var diff = vc.scrollTop - lastTop;
                                                        if (Math.abs(diff) > 3) {
                                                            log('SCROLL JUMP ' + Math.round(lastTop) + ' -> ' + Math.round(vc.scrollTop) + ' delta=' + Math.round(diff));
                                                        }
                                                        lastTop = vc.scrollTop;
                                                    }, 150);

                                                    if (window.ResizeObserver) {
                                                        var ro = new ResizeObserver(function(entries) {
                                                            entries.forEach(function(entry) {
                                                                log('RESIZE ' + (entry.target.className || entry.target.id) + ' -> ' + Math.round(entry.contentRect.height) + 'px');
                                                            });
                                                        });
                                                        document.querySelectorAll('.page').forEach(function(p) { ro.observe(p); });
                                                    }

                                                    window.addEventListener('resize', function() {
                                                        log('WINDOW RESIZE EVENT FIRED');
                                                    });

                                                    if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                        var origUpdate = PDFViewerApplication.pdfViewer.update.bind(PDFViewerApplication.pdfViewer);
                                                        PDFViewerApplication.pdfViewer.update = function() {
                                                            log('pdfViewer.update() CALLED');
                                                            return origUpdate();
                                                        };
                                                    }
                                                }
                                            })();
                                        });
                                    } else {
                                        setTimeout(checkPDFjs, 150);
                                    }
                                }
                                checkPDFjs();
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(setupScript, null)
                        
                        val selectionScript = """
                            (function() {
                                window.lastSelectionRange = null;
                                window.lastSelectionText = "";

                                window.applyHighlightToSelection = function(colorHex) {
                                    try {
                                        var sel = window.getSelection();
                                        var range = null;
                                        if (sel && !sel.isCollapsed && sel.rangeCount > 0) {
                                            range = sel.getRangeAt(0);
                                        } else if (window.lastSelectionRange) {
                                            range = window.lastSelectionRange;
                                        }

                                        if (!range) return false;

                                        var container = range.commonAncestorContainer;
                                        var pageElem = (container.nodeType === 1 ? container : container.parentElement).closest('.page');
                                        if (!pageElem) {
                                            var activePageNum = 1;
                                            try {
                                                if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                    activePageNum = PDFViewerApplication.pdfViewer.currentPageNumber || 1;
                                                }
                                            } catch(e) {}
                                            pageElem = document.querySelector('.page[data-page-number="' + activePageNum + '"]') || document.querySelector('.page');
                                        }

                                        if (!pageElem) return false;

                                        var rects = range.getClientRects();
                                        var pageRect = pageElem.getBoundingClientRect();

                                        var highlightLayer = pageElem.querySelector('.custom-pdf-highlights-layer');
                                        if (!highlightLayer) {
                                            highlightLayer = document.createElement('div');
                                            highlightLayer.className = 'custom-pdf-highlights-layer';
                                            highlightLayer.style.cssText = 'position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 8;';
                                            pageElem.appendChild(highlightLayer);
                                        }

                                        var addedAny = false;
                                        if (rects && rects.length > 0) {
                                            for (var i = 0; i < rects.length; i++) {
                                                var rect = rects[i];
                                                if (rect.width > 0.5 && rect.height > 0.5) {
                                                    var leftPct = ((rect.left - pageRect.left) / pageRect.width) * 100;
                                                    var topPct = ((rect.top - pageRect.top) / pageRect.height) * 100;
                                                    var widthPct = (rect.width / pageRect.width) * 100;
                                                    var heightPct = (rect.height / pageRect.height) * 100;

                                                    var highlightSpan = document.createElement('div');
                                                    highlightSpan.className = 'custom-highlight-rect';
                                                    highlightSpan.setAttribute('data-color', colorHex);
                                                    highlightSpan.style.cssText = 'position: absolute;' +
                                                        'left: ' + leftPct + '%;' +
                                                        'top: ' + topPct + '%;' +
                                                        'width: ' + widthPct + '%;' +
                                                        'height: ' + heightPct + '%;' +
                                                        'background-color: ' + colorHex + ';' +
                                                        'opacity: 0.42;' +
                                                        'mix-blend-mode: multiply;' +
                                                        'border-radius: 2px;' +
                                                        'pointer-events: auto;' +
                                                        'box-shadow: 0 0 2px ' + colorHex + ';';

                                                    highlightSpan.addEventListener('click', function(ev) {
                                                        ev.stopPropagation();
                                                        if (confirm('هل تريد حذف هذا التظليل؟')) {
                                                            this.remove();
                                                        }
                                                    });

                                                    highlightLayer.appendChild(highlightSpan);
                                                    addedAny = true;
                                                }
                                            }
                                        }

                                        if (!addedAny && window.lastSelectionText) {
                                            var targetText = window.lastSelectionText.trim();
                                            var textSpans = pageElem.querySelectorAll('.textLayer span');
                                            textSpans.forEach(function(s) {
                                                if (s.textContent && targetText.length > 0 && (s.textContent.includes(targetText) || targetText.includes(s.textContent.trim()))) {
                                                    s.style.backgroundColor = colorHex;
                                                    s.style.opacity = '0.5';
                                                    s.style.borderRadius = '2px';
                                                    addedAny = true;
                                                }
                                            });
                                        }

                                        try {
                                            if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                PDFViewerApplication.pdfViewer.annotationEditorMode = { mode: 9 };
                                                if (PDFViewerApplication.eventBus) {
                                                    PDFViewerApplication.eventBus.dispatch('switchannotationeditorparams', {
                                                        source: window,
                                                        type: 7,
                                                        value: colorHex
                                                    });
                                                }
                                            }
                                        } catch(e) {}

                                        if (window.getSelection) {
                                            window.getSelection().removeAllRanges();
                                        }
                                        window.lastSelectionRange = null;

                                        return addedAny;
                                    } catch(err) {
                                        console.error("applyHighlightToSelection error:", err);
                                        return false;
                                    }
                                };

                                var selDebounce = null;
                                function notifySelection() {
                                    if (selDebounce) clearTimeout(selDebounce);
                                    selDebounce = setTimeout(function() {
                                        var sel = window.getSelection();
                                        if (sel && !sel.isCollapsed && sel.rangeCount > 0) {
                                            var text = sel.toString().trim();
                                            if (text.length > 0) {
                                                try {
                                                    window.lastSelectionRange = sel.getRangeAt(0).cloneRange();
                                                    window.lastSelectionText = text;
                                                } catch(e) {}

                                                var pageNum = 1;
                                                try {
                                                    if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                                                        pageNum = PDFViewerApplication.pdfViewer.currentPageNumber || 1;
                                                    }
                                                } catch(e) {}
                                                if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onTextSelected) {
                                                    AndroidBridge.onTextSelected(text, pageNum);
                                                }
                                            }
                                        }
                                    }, 150);
                                }
                                document.addEventListener('selectionchange', notifySelection);
                                document.addEventListener('mouseup', notifySelection);
                                document.addEventListener('touchend', notifySelection);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(selectionScript, null)
                    }
                }

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onSingleTap() {
                        coroutineScope.launch {
                            onSingleTap()
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onScroll() {
                        coroutineScope.launch {
                            onScrollEvent()
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onAudioLinkClicked(url: String) {
                        coroutineScope.launch {
                            onAudioLinkClicked(url)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onManualScroll() {
                        coroutineScope.launch {
                            viewModel.stopAutoScroll()
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onPageChanged(pageNumber: Int, pagesCount: Int) {
                        coroutineScope.launch {
                            viewModel.updatePage(pageNumber, pagesCount)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onSearchCountUpdated(total: Int, current: Int) {
                        coroutineScope.launch {
                            viewModel.updateSearchMatches(total, current)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onScaleChanged(scale: Float) {
                        coroutineScope.launch {
                            viewModel.updateScaleFromJs(scale)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onAnnotationChanged() {
                        coroutineScope.launch {
                            viewModel.markHasUnsavedChanges(true)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onEditorModeChanged(mode: Int) {
                        coroutineScope.launch {
                            viewModel.onEditorModeChanged(mode)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onEditorParamsChanged(paramsJson: String) {
                        coroutineScope.launch {
                            viewModel.onEditorParamsChanged(paramsJson)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onDocumentSaved(base64Data: String) {
                        coroutineScope.launch {
                            viewModel.saveAnnotatedPdf(context, base64Data)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onSaveError(error: String) {
                        coroutineScope.launch {
                            viewModel.onPdfSaveFailed(context, error)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onPdfSaved(base64Data: String) {
                        coroutineScope.launch {
                            viewModel.saveAnnotatedPdf(context, base64Data)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onPdfSaveFailed(error: String) {
                        coroutineScope.launch {
                            viewModel.onPdfSaveFailed(context, error)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onTextSelected(text: String, pageNumber: Int) {
                        coroutineScope.launch {
                            viewModel.onTextSelected(text, pageNumber)
                        }
                    }
                }, "AndroidBridge")

                // Encode file URL properly
                val encodedFileUrl = Uri.encode("file://$pdfPath")
                val currentPage = state.currentPage
                val viewerUrl = "file:///android_asset/pdfjs/web/viewer.html?file=$encodedFileUrl#page=$currentPage&zoom=${state.defaultZoom}&scrollMode=${if (state.snapToPage) 3 else if (state.scrollMode == "horizontal") 1 else 0}"
                loadUrl(viewerUrl)
                onWebViewCreated(this)
            }
        },
        modifier = modifier
    )
}

@Composable
fun NativeSearchBar(
    viewModel: PdfViewModel,
    state: PdfUiState,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var textState by remember { mutableStateOf(state.searchQuery) }

    // Keep the TextField in sync with the state when state resets (e.g. search closed)
    LaunchedEffect(state.searchQuery) {
        if (state.searchQuery != textState) {
            textState = state.searchQuery
        }
    }

    MaterialTheme(colorScheme = glassLavenderColorScheme) {
        Row(
            modifier = modifier
                .testTag("native_search_bar")
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.closeSearch() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق البحث",
                    tint = Color(0xFF1C182B)
                )
            }

            BasicTextField(
                value = textState,
                onValueChange = {
                    textState = it
                    viewModel.triggerSearch(it)
                },
                textStyle = TextStyle(
                    color = Color(0xFF1C182B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textState.isEmpty()) {
                            Text(
                                text = "ابحث عن كلمة...",
                                color = Color(0x991C182B),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (state.searchQuery.isNotEmpty()) {
                Text(
                    text = if (state.searchMatchesTotal > 0) "${state.searchMatchActive} من ${state.searchMatchesTotal}" else "0 من 0",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C182B)
                )

                IconButton(
                    onClick = { viewModel.navigateSearchPrev() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "المطابقة السابقة",
                        tint = Color(0xFF1C182B)
                    )
                }

                IconButton(
                    onClick = { viewModel.navigateSearchNext() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "المطابقة التالية",
                        tint = Color(0xFF1C182B)
                    )
                }
            }
        }
    }
}

// ----------------- SUB-SHEETS FOR OPTIONS -----------------

@Composable
fun MoreOptionsSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onNavigate: (BottomSheetType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "خيارات إضافية",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Grid columns
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoreOptionGridItem(
                    icon = Icons.Outlined.StickyNote2,
                    label = "الملاحظات والتظليلات",
                    onClick = {
                        onNavigate(BottomSheetType.NotesAndHighlights)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.PhotoCamera,
                    label = "ماسح الكاميرا الضوئي",
                    onClick = {
                        onNavigate(BottomSheetType.CameraOcr)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.DocumentScanner,
                    label = "استخراج النص (OCR)",
                    onClick = {
                        onNavigate(BottomSheetType.OcrText)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.Pin,
                    label = "الإشارات المرجعية",
                    onClick = {
                        onNavigate(BottomSheetType.Bookmarks)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.DirectionsRun,
                    label = "التمرير التلقائي",
                    onClick = {
                        onNavigate(BottomSheetType.AutoScroll)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.Print,
                    label = "طباعة المستند",
                    onClick = {
                        onDismiss()
                        state.currentPdfPath?.let { path ->
                            printPdf(context, path, state.currentPdfName ?: "doc.pdf")
                        }
                    }
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoreOptionGridItem(
                    icon = Icons.Outlined.Navigation,
                    label = "الانتقال إلى صفحة",
                    onClick = {
                        onNavigate(BottomSheetType.JumpToPage)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.Info,
                    label = "معلومات المستند",
                    onClick = {
                        onNavigate(BottomSheetType.DocumentInfo)
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.Share,
                    label = "مشاركة الملف",
                    onClick = {
                        onDismiss()
                        state.currentPdfPath?.let { path ->
                            sharePdf(context, path, state.currentPdfName ?: "doc.pdf")
                        }
                    }
                )
                MoreOptionGridItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "تصدير الصفحة (PNG)",
                    onClick = {
                        onDismiss()
                        state.currentPdfPath?.let { path ->
                            exportPageAsPngToGallery(
                                context = context,
                                filePath = path,
                                pageNumber = state.currentPage,
                                pdfName = state.currentPdfName ?: "doc.pdf"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MoreOptionGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ViewOptionsSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "خيارات العرض",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp),
            textAlign = TextAlign.Start
        )

        Text(
            text = "وضع التمرير",
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isHorizontal = state.scrollMode == "horizontal"
            
            // Horizontal scroll option
            Surface(
                onClick = { viewModel.setScrollMode("horizontal") },
                shape = RoundedCornerShape(16.dp),
                color = if (isHorizontal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isHorizontal) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "أفقي",
                        tint = if (isHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "أفقي",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Vertical scroll option
            Surface(
                onClick = { viewModel.setScrollMode("vertical") },
                shape = RoundedCornerShape(16.dp),
                color = if (!isHorizontal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (!isHorizontal) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewStream,
                        contentDescription = "عمودي",
                        tint = if (!isHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "عمودي",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // Snap to page
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "محاذاة تلقائية إلى الصفحة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "تثبيت عرض الصفحة على حواف الشاشة",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.snapToPage,
                onCheckedChange = { viewModel.setSnapToPage(it) }
            )
        }

        // Auto-hide controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "إخفاء شريط الأدوات تلقائيًا",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "إخفاء عناصر التحكم كليًا بعد ٥ ثوانٍ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.autoHideToolbar,
                onCheckedChange = { viewModel.setAutoHideToolbar(it) }
            )
        }
    }
}

@Composable
fun ZoomSettingsSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "الزووم والتحكم في العرض",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
            textAlign = TextAlign.Start
        )

        Text(
            text = "تعديل مقياس الصفحات واتجاه الشاشة للقراءة المريحة",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Start
        )

        // Card displaying Current Zoom Percentage with - and +
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "مقياس الزووم الحالي",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.triggerZoomOut() },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "تصغير",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(state.currentScale * 100).roundToInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "الحجم الفعلي للملف",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.triggerZoomIn() },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "تكبير",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                SleekSlider(
                    value = state.currentScale,
                    onValueChange = { viewModel.setScale(it) },
                    valueRange = 0.1f..5.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Quick zoom preset buttons
        Text(
            text = "خيارات ملاءمة الصفحة السريعة",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.setScale(1.0f) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (Math.abs(state.currentScale - 1.0f) < 0.05f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (Math.abs(state.currentScale - 1.0f) < 0.05f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(text = "الحجم الأصلي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.triggerFitWidth() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(text = "ملائمة العرض", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.triggerFitPage() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(text = "ملائمة الصفحة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Double Tap Zoom section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "معامل التكبير عند النقر المزدوج (Double Tap Zoom)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "×${String.format("%.1f", state.doubleTapZoomFactor)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1.1f, 1.5f, 2.0f, 2.3f, 3.0f, 5.0f).forEach { factor ->
                val isSelected = Math.abs(state.doubleTapZoomFactor - factor) < 0.1f
                Surface(
                    onClick = { viewModel.setDoubleTapZoomFactor(factor) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×${String.format("%.1f", factor)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SleekSlider(
            value = state.doubleTapZoomFactor,
            onValueChange = { viewModel.setDoubleTapZoomFactor(it) },
            valueRange = 1.1f..5.0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Screen orientation options
        Text(
            text = "اتجاه الشاشة المفضل",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val orientations = listOf(
                Triple(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "عمودي (طولي)", Icons.Default.StayCurrentPortrait),
                Triple(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, "أفقي (عرضي)", Icons.Default.StayCurrentLandscape),
                Triple(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, "تلقائي (حسب النظام)", Icons.Default.ScreenRotation)
            )

            orientations.forEach { (orientationVal, label, icon) ->
                val isSelected = state.screenOrientation == orientationVal
                Button(
                    onClick = { viewModel.setScreenOrientation(context, orientationVal) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun DisplaySettingsSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "إعدادات العرض ومظهر القراءة",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp),
            textAlign = TextAlign.Start
        )

        // Reading Theme Boxes
        Text(
            text = "مظهر القراءة",
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = listOf(
                Triple("light", "فاتح", Color.White),
                Triple("dark", "داكن", Color(0xFF1E1E2E)),
                Triple("black", "أسود", Color.Black),
                Triple("sepia", "ورق دافئ", Color(0xFFF4ECD8))
            )

            themes.forEach { (themeName, label, bgColor) ->
                val isSelected = state.readingTheme == themeName
                val outlineColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val textColor = if (themeName == "light" || themeName == "sepia") Color.Black else Color.White

                Surface(
                    onClick = { viewModel.setReadingTheme(themeName) },
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, outlineColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(55.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "محدد",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // Brightness Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "سطوع الشاشة مخصص",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "تلقائي للنظام",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Checkbox(
                    checked = state.isSystemBrightness,
                    onCheckedChange = { viewModel.setSystemBrightness(it) }
                )
            }
        }

        if (!state.isSystemBrightness) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness5,
                    contentDescription = "منخفض",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                SleekSlider(
                    value = state.customBrightness,
                    onValueChange = { viewModel.setCustomBrightness(it) },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Brightness7,
                    contentDescription = "مرتفع",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Keep Screen On
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "إبقاء الشاشة مفعّلة دائمًا",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "منع إيقاف تشغيل الشاشة تلقائيًا أثناء القراءة",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.keepScreenOn,
                onCheckedChange = { viewModel.setKeepScreenOn(it) }
            )
        }
    }
}

@Composable
fun JumpToPageSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val maxPage = if (state.totalPages > 0) state.totalPages else 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "الانتقال السريع إلى صفحة",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Text(
            text = "الصفحة الحالية: ${state.currentPage} من $maxPage",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                errorMessage = null
            },
            label = { Text("أدخل رقم الصفحة") },
            placeholder = { Text("مثال: 5") },
            isError = errorMessage != null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    val target = textInput.toIntOrNull()
                    if (target == null || target < 1 || target > maxPage) {
                        errorMessage = "يرجى إدخال رقم صحيح بين ١ و $maxPage"
                    } else {
                        viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = $target")
                        onDismiss()
                    }
                }
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("إلغاء")
            }

            Button(
                onClick = {
                    val target = textInput.toIntOrNull()
                    if (target == null || target < 1 || target > maxPage) {
                        errorMessage = "يرجى إدخال رقم صحيح بين ١ و $maxPage"
                    } else {
                        viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = $target")
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("انطلق")
            }
        }
    }
}

@Composable
fun DocumentInfoSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileSizeStr = getReadableFileSize(state.currentPdfPath)
    
    val formatVersion = "PDF 1.7"
    val securityStr = "مؤمن تلقائياً (غير مشفر)"
    val lastOpenedDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "معلومات المستند",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp),
            textAlign = TextAlign.Start
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DocInfoRow(label = "اسم الملف", value = state.currentPdfName ?: "غير معروف")
            }
            item {
                DocInfoRow(label = "حجم الملف", value = fileSizeStr)
            }
            item {
                DocInfoRow(label = "العدد الفعلي للصفحات", value = "${state.totalPages} صفحات")
            }
            item {
                DocInfoRow(label = "موقع التخزين", value = state.currentPdfPath ?: "مجلد التطبيق")
            }
            item {
                DocInfoRow(label = "إصدار التنسيق", value = formatVersion)
            }
            item {
                DocInfoRow(label = "الأمان والخصوصية", value = securityStr)
            }
            item {
                DocInfoRow(label = "تاريخ آخر قراءة", value = lastOpenedDateStr)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إغلاق")
        }
    }
}

@Composable
fun DocInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookmarksSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "الإشارات المرجعية المضافة",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Start
        )

        val bookmarks = state.bookmarkedPages.toList().sorted()

        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "فارغ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "لم يتم إضافة أي إشارات مرجعية بعد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اضغط على أيقونة الشريط في الأسفل لحفظ الصفحة الحالية للرجوع إليها لاحقاً.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bookmarks) { page ->
                    val context = LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = $page")
                                        onDismiss()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "علامة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "الصفحة $page",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            IconButton(
                                onClick = { viewModel.toggleBookmark(context, page) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف الإشارة",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
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
fun AutoScrollSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    var speedState by remember { mutableFloatStateOf(state.autoScrollSpeed.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "التمرير التلقائي الذكي",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
            textAlign = TextAlign.Start
        )
        
        Text(
            text = "قراءة هادئة خالية من اليدين بمعدل سرعة مريح لتمرير الصفحات تلقائياً.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Presets speed selection
        Text(
            text = "تحديد سرعة مسبقة",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                Pair("بطيء", 12f),
                Pair("متوسط", 25f),
                Pair("سريع", 55f),
                Pair("سريع جداً", 90f)
            )

            presets.forEach { (label, speed) ->
                val isSelected = speedState == speed
                Surface(
                    onClick = {
                        speedState = speed
                        if (state.isAutoScrolling) {
                            viewModel.startAutoScroll(speed.roundToInt())
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Custom speed slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ضبط دقيق للسرعة",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${speedState.roundToInt()} بكسل/ث",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SleekSlider(
            value = speedState,
            onValueChange = {
                speedState = it
                if (state.isAutoScrolling) {
                    viewModel.startAutoScroll(it.roundToInt())
                }
            },
            valueRange = 5f..150f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Large Start/Stop toggle button
        Button(
            onClick = {
                if (state.isAutoScrolling) {
                    viewModel.stopAutoScroll()
                } else {
                    viewModel.startAutoScroll(speedState.roundToInt())
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isAutoScrolling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (state.isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isAutoScrolling) "إيقاف" else "بدء"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isAutoScrolling) "إيقاف التمرير التلقائي" else "بدء التمرير التلقائي",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ----------------- PRINT & SHARE UTILITY INTEGRATIONS -----------------

fun getReadableFileSize(filePath: String?): String {
    if (filePath == null) return "0 KB"
    val file = File(filePath)
    if (!file.exists()) return "0 KB"
    val bytes = file.length()
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + ""
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun sharePdf(context: Context, filePath: String, fileName: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, fileName)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملف عبر:"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun printPdf(context: Context, filePath: String, fileName: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${context.packageName} - $fileName"
        val file = File(filePath)
        if (file.exists()) {
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(fileName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        file.inputStream().use { input ->
                            FileOutputStream(destination?.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(jobName, printAdapter, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun exportPageAsPngToGallery(
    context: Context,
    filePath: String,
    pageNumber: Int,
    pdfName: String
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "الملف غير موجود!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            if (pfd == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل فتح المستند!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "المستند فارغ!", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val pageIndex = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(pageIndex)

            // High resolution scale (~2400px width) for crystal-clear PNG image quality
            val targetWidthPx = 2400f
            val scale = (targetWidthPx / page.width.toFloat()).coerceAtLeast(2.5f)
            val bitmapWidth = (page.width * scale).toInt()
            val bitmapHeight = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()

            val cleanName = pdfName.substringBeforeLast(".pdf").replace(" ", "_")
            val fileName = "${cleanName}_صفحة_${pageIndex + 1}_${System.currentTimeMillis()}.png"

            var isSaved = false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/FinalPDF")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    isSaved = true
                }
            } else {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val appFolder = File(picturesDir, "FinalPDF")
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                val imageFile = File(appFolder, fileName)
                FileOutputStream(imageFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                isSaved = true
            }

            withContext(Dispatchers.Main) {
                if (isSaved) {
                    Toast.makeText(
                        context,
                        "تم حفظ الصفحة ${pageIndex + 1} كصورة PNG عالية الجودة في المعرض! 🖼️",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "تعذر حفظ الصورة في المعرض", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "خطأ أثناء تصدير الصفحة: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun GlowingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    haloSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(haloSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .drawBehind {
                val radius = size.minDimension / 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.28f),
                            tint.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun BottomBarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF6B6578),
    isSelected: Boolean = false
) {
    val activeColor = if (isSelected) MaterialTheme.colorScheme.primary else tint
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.35f),
                                activeColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PdfPageThumbnail(
    pdfPath: String,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pdfPath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(pdfPath, pageIndex) {
        kotlinx.coroutines.Dispatchers.IO.run {
            try {
                val file = File(pdfPath)
                if (file.exists()) {
                    val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fileDescriptor)
                    if (pageIndex >= 0 && pageIndex < renderer.pageCount) {
                        val page = renderer.openPage(pageIndex)
                        
                        val width = 180
                        val height = 260
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = bmp
                        page.close()
                    }
                    renderer.close()
                    fileDescriptor.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "الصفحة ${pageIndex + 1}",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentNavigationSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(2) } // default to Pages tab
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 550.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "تصفح المستند",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )
        
        val tabTitles = listOf(
            "التفاصيل",
            "الإشارات (${state.bookmarkedPages.size})",
            "الصفحات (${state.totalPages})"
        )
        
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Details tab
                    val fileSizeStr = getReadableFileSize(state.currentPdfPath)
                    val formatVersion = "PDF 1.7"
                    val securityStr = "مؤمن تلقائياً (غير مشفر)"
                    val lastOpenedDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item { DocInfoRow(label = "اسم الملف", value = state.currentPdfName ?: "غير معروف") }
                        item { DocInfoRow(label = "حجم الملف", value = fileSizeStr) }
                        item { DocInfoRow(label = "العدد الفعلي للصفحات", value = "${state.totalPages} صفحات") }
                        item { DocInfoRow(label = "موقع التخزين", value = state.currentPdfPath ?: "مجلد التطبيق") }
                        item { DocInfoRow(label = "إصدار التنسيق", value = formatVersion) }
                        item { DocInfoRow(label = "الأمان والخصوصية", value = securityStr) }
                        item { DocInfoRow(label = "تاريخ آخر قراءة", value = lastOpenedDateStr) }
                    }
                }
                1 -> {
                    // Bookmarks tab
                    val bookmarks = state.bookmarkedPages.toList().sorted()
                    if (bookmarks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = "فارغ",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لم يتم إضافة أي إشارات مرجعية بعد",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(bookmarks) { page ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = $page")
                                                onDismiss()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmark,
                                                contentDescription = "إشارة",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "الصفحة $page",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { viewModel.toggleBookmark(context, page) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Pages thumbnails grid tab
                    if (state.totalPages <= 0) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "جاري تحميل الصفحات...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val lazyGridState = rememberLazyGridState()
                        
                        LaunchedEffect(state.currentPage) {
                            if (state.currentPage in 1..state.totalPages) {
                                try {
                                    lazyGridState.animateScrollToItem(state.currentPage - 1)
                                } catch (e: Exception) {
                                    // Ignore scroll errors
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = lazyGridState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items((1..state.totalPages).toList()) { page ->
                                val isCurrent = page == state.currentPage
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = $page")
                                            onDismiss()
                                        }
                                        .padding(6.dp)
                                ) {
                                    state.currentPdfPath?.let { path ->
                                        PdfPageThumbnail(
                                            pdfPath = path,
                                            pageIndex = page - 1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.7f)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = "$page",
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
fun WifiSoundWaveIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Headset,
            contentDescription = "سماعة",
            tint = Color(0xFFB19DFF),
            modifier = Modifier.size(15.dp)
        )
        WaveArcs(
            isPlaying = isPlaying,
            modifier = Modifier.size(width = 12.dp, height = 15.dp)
        )
    }
}

@Composable
fun WaveArcs(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_arcs")
    val pulseProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arc1"
    )
    val pulseProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arc2"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = 1.25.dp.toPx()
        val waveColor = Color(0xFFB19DFF)

        // Draw arcs emerging from the left side (where the headset is) towards the right
        val center = Offset(0f, height / 2f)
        val maxRadius = width

        if (isPlaying) {
            listOf(pulseProgress1, pulseProgress2).forEach { progress ->
                val radius = 2.dp.toPx() + (maxRadius - 2.dp.toPx()) * progress
                val alpha = (1f - progress).coerceIn(0f, 1f)
                drawArc(
                    color = waveColor.copy(alpha = alpha),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        } else {
            // Static small arc when paused
            val radius = 4.dp.toPx()
            drawArc(
                color = waveColor.copy(alpha = 0.5f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun SpokenWordText(
    text: String,
    modifier: Modifier = Modifier
) {
    val trimmed = text.trim()
    val isSentence = trimmed.contains(" ") || trimmed.length > 20
    val baseFontSize = when {
        isSentence -> 10.sp
        trimmed.length > 15 -> 9.sp
        trimmed.length > 10 -> 10.sp
        else -> 12.sp
    }

    if (isSentence) {
        val scrollState = rememberScrollState()
        LaunchedEffect(trimmed) {
            scrollState.scrollTo(0)
            while (true) {
                delay(1200)
                if (scrollState.maxValue > 0) {
                    scrollState.animateScrollTo(
                        value = scrollState.maxValue,
                        animationSpec = tween(
                            durationMillis = (scrollState.maxValue * 15).coerceIn(2000, 12000),
                            easing = LinearEasing
                        )
                    )
                    delay(1500)
                    scrollState.animateScrollTo(
                        value = 0,
                        animationSpec = tween(1200, easing = LinearEasing)
                    )
                }
            }
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState, enabled = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trimmed,
                fontSize = baseFontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Text(
            text = trimmed,
            fontSize = baseFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
fun MiniPlayerOverlay(
    wordName: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    onReplay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 360.dp)
            .testTag("audio_mini_player"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE01C182B), // Harmonized dark glassmorphism background
        border = BorderStroke(1.dp, Color(0xFF7C5CFF).copy(alpha = 0.5f)), // Glowing violet outline
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Wi-Fi Wave Icon & Spoken Text Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Dynamic pulsing headset soundwave indicator
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(26.dp)
                        .background(Color(0xFF222031), RoundedCornerShape(13.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WifiSoundWaveIndicator(
                        isPlaying = isPlaying
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "جاري النطق...",
                        fontSize = 9.sp,
                        color = Color(0xFFB19DFF),
                        fontWeight = FontWeight.Medium
                    )
                    SpokenWordText(
                        text = wordName.ifEmpty { "كلمة غير معروفة" }
                    )
                }
            }

            // Right side: Clean actions with separated small circular halos (backgrounds)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Replay Button with separate custom circular background (smaller but separated)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222031))
                        .clickable(enabled = !isLoading, onClick = onReplay),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFB19DFF),
                            strokeWidth = 1.2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "إعادة النطق",
                            tint = Color(0xFFB19DFF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Delete/Close Button with separate custom circular background (smaller but separated)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B1C1C))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق المشغل",
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditBottomBar(
    state: PdfUiState,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    MaterialTheme(colorScheme = glassLavenderColorScheme) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xF2ECE6F8), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, Color(0x407C5CFF)), RoundedCornerShape(24.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Settings panels above the tool tabs
        if (state.activeEditTool == "pen") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pencil Thickness Slider (1.5 to 18.5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "السماكة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(60.dp)
                    )
                    SleekSlider(
                        value = state.editThickness.coerceIn(1.5f, 18.5f),
                        onValueChange = { viewModel.setEditThickness(it) },
                        valueRange = 1.5f..18.5f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f px", state.editThickness),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(45.dp)
                    )
                }

                // Pencil Colors (Red, Green, Yellow, Blue, Black)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اللون",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val colors = listOf(
                        "#F44336" to Color(0xFFF44336), // Red
                        "#4CAF50" to Color(0xFF4CAF50), // Green
                        "#FFEB3B" to Color(0xFFFFEB3B), // Yellow
                        "#2196F3" to Color(0xFF2196F3), // Blue
                        "#000000" to Color(0xFF000000)  // Black
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { (hex, composeColor) ->
                            val isSelected = state.editColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setEditColor(hex) }
                            )
                        }
                    }
                }
            }
        } else if (state.activeEditTool == "highlighter") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Highlighter Thickness Slider (1.5 to 18.5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "السماكة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(60.dp)
                    )
                    SleekSlider(
                        value = state.editThickness.coerceIn(1.5f, 18.5f),
                        onValueChange = { viewModel.setEditThickness(it) },
                        valueRange = 1.5f..18.5f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f px", state.editThickness),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(45.dp)
                    )
                }

                // Highlighter Opacity/Transparency Slider (10% to 100%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الشفافية",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(60.dp)
                    )
                    SleekSlider(
                        value = state.editOpacity.coerceIn(10f, 100f),
                        onValueChange = { viewModel.setEditOpacity(it) },
                        valueRange = 10f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${state.editOpacity.toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(45.dp)
                    )
                }

                // Highlighter Colors (Red, Green, Yellow, Blue, Black)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اللون",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val colors = listOf(
                        "#F44336" to Color(0xFFF44336), // Red
                        "#4CAF50" to Color(0xFF4CAF50), // Green
                        "#FFEB3B" to Color(0xFFFFEB3B), // Yellow
                        "#2196F3" to Color(0xFF2196F3), // Blue
                        "#000000" to Color(0xFF000000)  // Black
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { (hex, composeColor) ->
                            val isSelected = state.editColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setEditColor(hex) }
                            )
                        }
                    }
                }
            }
        }

        if (state.activeEditTool != "none") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // 2. Main Edit Bottom Bar Tabs (3 items)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Pencil Tool Button (قلم رصاص)
            val isPenActive = state.activeEditTool == "pen"
            val penColor = if (isPenActive) Color(0xFF7C5CFF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        viewModel.setEditTool("pen")
                        // Enforce the thickness limits
                        if (state.editThickness < 1.5f || state.editThickness > 18.5f) {
                            viewModel.setEditThickness(5.0f)
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (isPenActive) {
                                val radius = size.minDimension / 2
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF7C5CFF).copy(alpha = 0.28f),
                                            Color(0xFF7C5CFF).copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "قلم رصاص",
                        tint = penColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "قلم رصاص",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = penColor
                )
            }

            // Highlighter Tool Button (قلم تحديد)
            val isHighlighterActive = state.activeEditTool == "highlighter"
            val highlighterColor = if (isHighlighterActive) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        viewModel.setEditTool("highlighter")
                        // Enforce the thickness limits
                        if (state.editThickness < 1.5f || state.editThickness > 18.5f) {
                            viewModel.setEditThickness(10.0f)
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (isHighlighterActive) {
                                val radius = size.minDimension / 2
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFB74D).copy(alpha = 0.28f),
                                            Color(0xFFFFB74D).copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "قلم تحديد",
                        tint = highlighterColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "قلم تحديد",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = highlighterColor
                )
            }

            // Disable/Move Tool Button (إيقاف الأدوات / التنقل بالصفحات)
            val isNoneActive = state.activeEditTool == "none"
            val noneColor = if (isNoneActive) Color(0xFF4DB6AC) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.setEditTool("none") }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (isNoneActive) {
                                val radius = size.minDimension / 2
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF4DB6AC).copy(alpha = 0.28f),
                                            Color(0xFF4DB6AC).copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "تصفح وحركة",
                        tint = noneColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "تصفح وحركة",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = noneColor
                )
            }
        }
    }
    }
}

@Composable
fun EditBottomBar_Old(
    state: PdfUiState,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Controls Drawer (Colors & Sliders) - only shown when a valid tool is active
        if (state.activeEditTool != "none" && state.activeEditTool != "image") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Palette Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اللون",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val colors = listOf(
                        "#FFFF00" to Color(0xFFFFFF00), // Yellow
                        "#4CAF50" to Color(0xFF4CAF50), // Green
                        "#2196F3" to Color(0xFF2196F3), // Blue
                        "#F44336" to Color(0xFFF44336), // Red
                        "#E91E63" to Color(0xFFE91E63), // Pink
                        "#000000" to Color(0xFF000000), // Black
                        "#FFFFFF" to Color(0xFFFFFFFF)  // White
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { (hex, composeColor) ->
                            val isSelected = state.editColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setEditColor(hex) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Thickness Slider (not applicable for Text tool, but highly relevant for Pen/Highlighter)
                if (state.activeEditTool == "pen" || state.activeEditTool == "highlighter") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "السماكة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(50.dp)
                        )
                        SleekSlider(
                            value = state.editThickness,
                            onValueChange = { viewModel.setEditThickness(it) },
                            valueRange = 1f..30f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${state.editThickness.toInt()}px",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "العتامة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(50.dp)
                    )
                    SleekSlider(
                        value = state.editOpacity,
                        onValueChange = { viewModel.setEditOpacity(it) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${state.editOpacity.toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (state.activeEditTool == "text") {
            // Text tool specific options: just color selection for quick text colors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "لون الخط",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val colors = listOf(
                    "#000000" to Color(0xFF000000), // Black
                    "#F44336" to Color(0xFFF44336), // Red
                    "#2196F3" to Color(0xFF2196F3), // Blue
                    "#4CAF50" to Color(0xFF4CAF50), // Green
                    "#FFFF00" to Color(0xFFFFFF00)  // Yellow
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { (hex, composeColor) ->
                        val isSelected = state.editColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setEditColor(hex) }
                        )
                    }
                }
            }
        }

        if (state.activeEditTool != "none") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // Active tools bar row (The core edit action bottom bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Free draw (Pen)
            EditToolItem(
                icon = Icons.Default.Create,
                label = "الرسم الحر",
                isSelected = state.activeEditTool == "pen",
                onClick = { viewModel.setEditTool("pen") }
            )

            // Text T
            EditToolItem(
                icon = Icons.Default.TextFields,
                label = "نص",
                isSelected = state.activeEditTool == "text",
                onClick = { viewModel.setEditTool("text") }
            )

            // Highlighter
            EditToolItem(
                icon = Icons.Default.Highlight,
                label = "تحديد",
                isSelected = state.activeEditTool == "highlighter",
                onClick = { viewModel.setEditTool("highlighter") }
            )

            // Image
            EditToolItem(
                icon = Icons.Default.Image,
                label = "إدراج صورة",
                isSelected = state.activeEditTool == "image",
                onClick = { viewModel.setEditTool("image") }
            )

            // Clear active tool button
            if (state.activeEditTool != "none") {
                EditToolItem(
                    icon = Icons.Default.Block,
                    label = "تعطيل الأداة",
                    isSelected = false,
                    onClick = { viewModel.setEditTool("none") },
                    tint = Color.Red.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Save/Done button
            Button(
                onClick = { viewModel.toggleEditMode(false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(text = "تم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EditToolItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Extracted OCR Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String, title: String = "النص المستخرج") {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة النص عبر:"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun extractTextFromPdfPage(
    context: Context,
    pdfPath: String,
    pageIndex: Int
): String = withContext(Dispatchers.IO) {
    var pdfBoxText = ""
    var mlKitText = ""
    
    // 1. First attempt: PDFBox native text extraction
    try {
        val file = File(pdfPath)
        if (file.exists()) {
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val raw = stripper.getText(doc)
            doc.close()
            if (raw.isNotBlank()) {
                pdfBoxText = raw.trim()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Second attempt: ML Kit OCR on high-resolution rendered bitmap image of the page
    // Crucial for scanned books where PDFBox only sees a tiny link/watermark footer
    try {
        val file = File(pdfPath)
        if (file.exists()) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= 0 && pageIndex < renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val width = (page.width * 3.0f).toInt().coerceAtLeast(900)
                val height = (page.height * 3.0f).toInt().coerceAtLeast(900)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pfd.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val task = recognizer.process(image)
                val visionText = Tasks.await(task)
                if (visionText.text.isNotBlank()) {
                    mlKitText = visionText.text.trim()
                }
            } else {
                pfd.close()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Smart merge: If PDFBox text is short (< 180 chars, e.g. watermark/header) and ML Kit text is present, prefer ML Kit or combine
    val result = when {
        pdfBoxText.length < 180 && mlKitText.isNotBlank() -> {
            if (pdfBoxText.isNotBlank() && !mlKitText.contains(pdfBoxText, ignoreCase = true)) {
                "$mlKitText\n\n--- [معلومات الهامش] ---\n$pdfBoxText"
            } else {
                mlKitText
            }
        }
        pdfBoxText.isNotBlank() -> pdfBoxText
        mlKitText.isNotBlank() -> mlKitText
        else -> ""
    }

    return@withContext result
}

data class OcrExtractionResult(
    val text: String,
    val isOnlineSuccess: Boolean,
    val engineName: String
)

suspend fun extractTextFromPdfPageOnline(
    context: Context,
    pdfPath: String,
    pageIndex: Int
): OcrExtractionResult = withContext(Dispatchers.IO) {
    var base64Image = ""
    try {
        val file = File(pdfPath)
        if (file.exists()) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= 0 && pageIndex < renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val width = (page.width * 3.5f).toInt().coerceAtLeast(1000)
                val height = (page.height * 3.5f).toInt().coerceAtLeast(1000)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pfd.close()

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            } else {
                pfd.close()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (base64Image.isBlank()) {
        val localText = extractTextFromPdfPage(context, pdfPath, pageIndex)
        return@withContext OcrExtractionResult(localText, false, "القارئ المحلي أوفلاين")
    }

    val apiKey = com.example.data.SecureKeyManager.getGeminiApiKey(context)

    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
        val promptText = "أنت نظام استخراج النصوص الضوئية (OCR) الأكثر دقة للكتب والمستندات المصورة. هذه صفحة كتاب تحتوي على صور أو أوراق مصورة. يرجى قراءة هذه الصورة بالكامل واستخراج كافة النصوص العربية والألمانية والإنجليزية والرموز المكتوبة داخل أي صورة أو فقرة بالصفحة بدقة متناهية. حافظ على نفس ترتيب السطور والمحتوى بدون حذف أي كلمة. اكتب النص المستخرج فقط بدون أي تعليق أو مقدمات."
        
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

            android.util.Log.d("GeminiApiDebug", "ViewerScreen Gemini Request Payload (first 300 chars): ${jsonPayload.toString().take(300)}...")

            val client = OkHttpClient.Builder()
                .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // Try valid Gemini model endpoints
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
                                    return@withContext OcrExtractionResult(
                                        text = extractedText.trim(),
                                        isOnlineSuccess = true,
                                        engineName = "Google Gemini AI (سحب نصوص الصور والكتب)"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fallback to local PDFBox / ML Kit if online fails or key is missing
    val localText = extractTextFromPdfPage(context, pdfPath, pageIndex)
    return@withContext OcrExtractionResult(localText, false, "القارئ المحلي (PDFBox / ML Kit أوفلاين)")
}

suspend fun extractTextFromAllPages(
    context: Context,
    pdfPath: String,
    useOnlineAi: Boolean = true,
    maxPages: Int = 30,
    onProgress: (Int, Int) -> Unit
): OcrExtractionResult = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    var onlineSuccessCount = 0
    var totalProcessed = 0
    try {
        val file = File(pdfPath)
        if (file.exists()) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount.coerceAtMost(maxPages)
            renderer.close()
            pfd.close()

            for (p in 0 until totalPages) {
                onProgress(p + 1, totalPages)
                totalProcessed++
                val res = if (useOnlineAi) {
                    extractTextFromPdfPageOnline(context, pdfPath, p)
                } else {
                    OcrExtractionResult(extractTextFromPdfPage(context, pdfPath, p), false, "القارئ المحلي أوفلاين")
                }
                if (res.isOnlineSuccess) onlineSuccessCount++
                sb.append("📄 --- [الصفحة ").append(p + 1).append(" من ").append(totalPages).append("] ---\n\n")
                if (res.text.isNotBlank()) {
                    sb.append(res.text).append("\n\n")
                } else {
                    sb.append("(لم يتم العثور على نص في هذه الصفحة)\n\n")
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    val isOnlineSuccess = useOnlineAi && onlineSuccessCount > 0
    val engineName = if (isOnlineSuccess) "Google Gemini AI أونلاين ($onlineSuccessCount/$totalProcessed صفحة)" else "القارئ المحلي أوفلاين"
    return@withContext OcrExtractionResult(sb.toString().trim(), isOnlineSuccess, engineName)
}

@Composable
fun OcrTextSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scanMode by remember { mutableIntStateOf(0) } // 0: currentPage, 1: allPages
    var isOnlineAi by remember { mutableStateOf(true) } // true: Gemini Online, false: ML Kit Offline
    var isProcessing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var extractionResult by remember { mutableStateOf<OcrExtractionResult?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val currentPage = state.currentPage
    val totalPages = state.totalPages
    val pdfPath = state.currentPdfPath

    LaunchedEffect(pdfPath, currentPage, scanMode, isOnlineAi) {
        if (pdfPath != null) {
            isProcessing = true
            extractionResult = null
            val engineName = if (isOnlineAi) "الذكاء الاصطناعي أونلاين (Gemini 3.5)" else "القارئ المحلي أوفلاين"
            if (scanMode == 0) {
                progressText = "جاري الاتصال وسحب النص عبر $engineName..."
                extractionResult = if (isOnlineAi) {
                    extractTextFromPdfPageOnline(context, pdfPath, (currentPage - 1).coerceAtLeast(0))
                } else {
                    OcrExtractionResult(
                        text = extractTextFromPdfPage(context, pdfPath, (currentPage - 1).coerceAtLeast(0)),
                        isOnlineSuccess = false,
                        engineName = "القارئ المحلي أوفلاين"
                    )
                }
            } else {
                extractionResult = extractTextFromAllPages(context, pdfPath, useOnlineAi = isOnlineAi, maxPages = 30) { current, total ->
                    progressText = "جاري معالجة الصفحة $current من $total عبر $engineName..."
                }
            }
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "استخراج النص الضوئي (OCR)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = if (isOnlineAi) Color(0xFFE3F2FD) else Color(0xFFE8F5E9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isOnlineAi) Color(0xFF90CAF9) else Color(0xFFA5D6A7))
            ) {
                Row(
                    modifier = Modifier
                        .clickable { isOnlineAi = !isOnlineAi }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOnlineAi) Icons.Outlined.Cloud else Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = if (isOnlineAi) Color(0xFF1565C0) else Color(0xFF2E7D32),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnlineAi) "أونلاين (Gemini AI)" else "أوفلاين 100%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnlineAi) Color(0xFF1565C0) else Color(0xFF2E7D32)
                    )
                }
            }
        }

        // Engine Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isOnlineAi = true },
                color = if (isOnlineAi) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = if (isOnlineAi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الذكاء الاصطناعي (أونلاين)",
                        textAlign = TextAlign.Center,
                        color = if (isOnlineAi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isOnlineAi = false },
                color = if (!isOnlineAi) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = if (!isOnlineAi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "القارئ المحلي (أوفلاين)",
                        textAlign = TextAlign.Center,
                        color = if (!isOnlineAi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Scope Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { scanMode = 0 },
                color = if (scanMode == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "الصفحة الحالية ($currentPage)",
                    textAlign = TextAlign.Center,
                    color = if (scanMode == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { scanMode = 1 },
                color = if (scanMode == 1) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "المستند بالكامل ($totalPages صفحة)",
                    textAlign = TextAlign.Center,
                    color = if (scanMode == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = progressText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (isOnlineAi) Color(0xFF2196F3) else Color(0xFF4CAF50),
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnlineAi) "نشاط الإنترنت: جاري الاتصال بخوادم Google Gemini AI..." else "بدون إنترنت: جاري التحليل المحلي أوفلاين...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            val res = extractionResult
            val extractedText = res?.text ?: ""

            if (extractedText.isNotBlank()) {
                // Status indicator banner
                Surface(
                    color = if (res?.isOnlineSuccess == true) Color(0xFFE3F2FD) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (res?.isOnlineSuccess == true) Color(0xFF90CAF9) else Color(0xFFFFCC80)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (res?.isOnlineSuccess == true) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (res?.isOnlineSuccess == true) {
                                "الإنترنت شغال: تم الاستخراج أونلاين بنجاح بواسطة ${res.engineName}"
                            } else {
                                "${res?.engineName ?: "تم الاستخراج بواسطة القارئ المحلي"}"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (res?.isOnlineSuccess == true) Color(0xFF0D47A1) else Color(0xFFE65100)
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في النص المستخرج...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "مسح")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                val displayText = if (searchQuery.isBlank()) {
                    extractedText
                } else {
                    extractedText.lines()
                        .filter { it.contains(searchQuery, ignoreCase = true) }
                        .joinToString("\n")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = if (displayText.isBlank() && searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لـ \"$searchQuery\"" else displayText,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الحروف: ${extractedText.length} | الكلمات: ${extractedText.split("\\s+".toRegex()).size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                copyTextToClipboard(context, extractedText)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                shareText(context, extractedText, state.currentPdfName ?: "النص المستخرج")
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FindInPage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لم يتم العثور على أي نصوص في هذه الصفحة",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddStickyNoteDialog(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#FFF59D") }

    val colorOptions = listOf(
        "#FFF59D" to Color(0xFFFFF59D), // Yellow
        "#C8E6C9" to Color(0xFFC8E6C9), // Green
        "#BBDEFB" to Color(0xFFBBDEFB), // Blue
        "#E1BEE7" to Color(0xFFE1BEE7), // Purple
        "#F8BBD0" to Color(0xFFF8BBD0)  // Pink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        viewModel.addStickyNote(context, noteText, selectedColorHex)
                    } else {
                        Toast.makeText(context, "الرجاء كتابة الملاحظة أولاً", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ الملاحظة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إلغاء")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column {
                    Text(
                        text = "إضافة ملاحظة لاصقة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "صفحة ${if (state.selectionPageNumber > 0) state.selectionPageNumber else state.currentPage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.selectedPdfText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "النص المرجعي المباشر:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${state.selectedPdfText}\"",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("اكتب ملاحظتك الخاصة هنا...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5
                )

                Text(
                    text = "لون الملاحظة اللاصقة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorOptions.forEach { (hex, color) ->
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun NotesAndHighlightsSheet(
    viewModel: PdfViewModel,
    state: PdfUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val stickyNotes = state.stickyNotesList

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.StickyNote2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "الملاحظات والتظليلات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${stickyNotes.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق"
                )
            }
        }

        if (stickyNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.StickyNote2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد ملاحظات أو تظليلات محفوظة بعد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "حدد أي نص داخل المستند لإظهار شريط التظليل المباشر وإضافة ملاحظات لاصقة!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stickyNotes, key = { it.id }) { note ->
                    val noteBgColor = try {
                        Color(android.graphics.Color.parseColor(note.colorHex))
                    } catch (e: Exception) {
                        Color(0xFFFFF59D)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = noteBgColor.copy(alpha = 0.85f)),
                        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (note.isHighlightOnly) "تظليل نص" else "ملاحظة لاصقة",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "صفحة ${note.pageNumber}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (note.selectedText.isNotBlank()) {
                                Text(
                                    text = "\"${note.selectedText}\"",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black.copy(alpha = 0.75f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .background(
                                            color = Color.White.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(6.dp)
                                        .fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!note.isHighlightOnly && note.noteText.isNotBlank()) {
                                Text(
                                    text = note.noteText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.sendJsCommand("PDFViewerApplication.pdfViewer.currentPageNumber = ${note.pageNumber}")
                                        onDismiss()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "الانتقال للصفحة",
                                        fontSize = 11.sp,
                                        color = Color.Black.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            copyTextToClipboard(context, note.noteText.ifBlank { note.selectedText })
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "نسخ",
                                            tint = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteStickyNote(note.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(16.dp)
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
}

