package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PdfDatabase
import com.example.data.RecentPdf
import com.example.data.RecentPdfDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

enum class Screen {
    Welcome,
    Dashboard,
    Viewer
}

enum class DashboardTab {
    Settings,
    Tools,
    Folders,
    Home
}

enum class FileFilter {
    All,
    Favorites,
    Recent
}

data class StorageInfo(
    val totalGb: Float = 0f,
    val availableGb: Float = 0f,
    val usedGb: Float = 0f,
    val usedPercentage: Int = 0
)

enum class SortOption {
    ALPHA_ASC, // A -> Z
    ALPHA_DESC, // Z -> A
    SIZE_ASC, // Smallest -> Largest
    SIZE_DESC, // Largest -> Smallest
    DATE_ASC, // Oldest -> Newest
    DATE_DESC // Newest -> Oldest
}

data class LocalPdfFile(
    val filePath: String,
    val fileName: String,
    val fileSize: String,
    val folderName: String,
    val lastModified: Long,
    val isFavorite: Boolean = false
)

data class PdfUiState(
    val currentScreen: Screen = Screen.Welcome,
    val currentPdfPath: String? = null,
    val currentPdfName: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val searchQuery: String = "",
    val searchMatchesTotal: Int = 0,
    val searchMatchActive: Int = 0,
    val isSearchActive: Boolean = false,
    val currentScale: Float = 1.0f,
    val screenOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    
    // View Options
    val scrollMode: String = "vertical", // "vertical", "horizontal"
    val snapToPage: Boolean = false,
    val autoHideToolbar: Boolean = false,
    
    // Display Settings
    val readingTheme: String = "light", // "light", "dark", "black", "sepia"
    val isSystemBrightness: Boolean = true,
    val customBrightness: Float = 0.5f,
    val keepScreenOn: Boolean = false,
    
    // Bookmarks and AutoScroll
    val bookmarkedPages: Set<Int> = emptySet(),
    val isAutoScrolling: Boolean = false,
    val autoScrollSpeed: Int = 25, // px/s
    
    // Dashboard & Bottom Nav State
    val selectedTab: DashboardTab = DashboardTab.Home,
    val welcomeCompleted: Boolean = false,
    val dashboardSearchQuery: String = "",
    val isGridView: Boolean = true,
    val selectedFilter: FileFilter = FileFilter.All,
    val sortOption: SortOption = SortOption.ALPHA_ASC,
    val totalReadingTimeSeconds: Long = 0L,
    val starredPdfs: Set<String> = emptySet(),
    val allPdfFiles: List<LocalPdfFile> = emptyList(),
    val showToolsTab: Boolean = true,
    val storageInfo: StorageInfo = StorageInfo(),
    val appTheme: String = "system", // "system", "light", "dark"
    val bottomBarColorIndex: Int = 0, // 0 to 11

    // Edit / Annotation State
    val isEditMode: Boolean = false,
    val activeEditTool: String = "none", // "none", "pen", "text", "highlighter", "image"
    val annotationEditorMode: Int = 0, // 0: NONE, 3: FREETEXT, 9: HIGHLIGHT, 13: STAMP, 15: INK
    val editColor: String = "#FFFF00", // Hex color string (Yellow default)
    val editThickness: Float = 5f,
    val editOpacity: Float = 100f
)

class PdfViewModel(private val recentPdfDao: RecentPdfDao) : ViewModel() {

    private var appContext: Context? = null

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    val recentPdfs = recentPdfDao.getAllRecentPdfs()

    fun setSortOption(context: Context, option: SortOption) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sort_option", option.name).apply()
        _uiState.update { it.copy(sortOption = option) }
    }

    fun loadReadingTime(context: Context) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        val seconds = prefs.getLong("total_reading_time_seconds", 0L)
        _uiState.update { it.copy(totalReadingTimeSeconds = seconds) }
    }

    fun incrementReadingTime(context: Context, deltaSeconds: Long) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        val currentSecs = prefs.getLong("total_reading_time_seconds", _uiState.value.totalReadingTimeSeconds)
        val newTime = currentSecs + deltaSeconds
        prefs.edit().putLong("total_reading_time_seconds", newTime).apply()
        _uiState.update { it.copy(totalReadingTimeSeconds = newTime) }
    }

    fun incrementReadingTimeSilently(context: Context, deltaSeconds: Long) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        val currentSecs = prefs.getLong("total_reading_time_seconds", _uiState.value.totalReadingTimeSeconds)
        val newTime = currentSecs + deltaSeconds
        prefs.edit().putLong("total_reading_time_seconds", newTime).apply()
    }

    // SharedFlow to trigger JS commands in WebView
    private val _jsCommandFlow = MutableSharedFlow<String>()
    val jsCommandFlow: SharedFlow<String> = _jsCommandFlow.asSharedFlow()

    fun loadBookmarks(context: Context, filePath: String) {
        val prefs = context.getSharedPreferences("pdf_reader_bookmarks", Context.MODE_PRIVATE)
        val bookmarkStr = prefs.getString(filePath, "") ?: ""
        val bookmarks = if (bookmarkStr.isEmpty()) {
            emptySet()
        } else {
            bookmarkStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        }
        _uiState.update { it.copy(bookmarkedPages = bookmarks) }
    }

    fun toggleBookmark(context: Context, pageNumber: Int) {
        val path = _uiState.value.currentPdfPath ?: return
        val currentBookmarks = _uiState.value.bookmarkedPages.toMutableSet()
        if (currentBookmarks.contains(pageNumber)) {
            currentBookmarks.remove(pageNumber)
        } else {
            currentBookmarks.add(pageNumber)
        }
        _uiState.update { it.copy(bookmarkedPages = currentBookmarks) }
        
        val prefs = context.getSharedPreferences("pdf_reader_bookmarks", Context.MODE_PRIVATE)
        prefs.edit().putString(path, currentBookmarks.joinToString(",")).apply()
    }

    fun setScrollMode(mode: String) {
        _uiState.update { it.copy(scrollMode = mode) }
        val jsVal = if (_uiState.value.snapToPage) {
            3
        } else {
            if (mode == "horizontal") 1 else 0
        }
        sendJsCommand("if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) { PDFViewerApplication.pdfViewer.scrollMode = $jsVal; }")
    }

    fun setSnapToPage(snap: Boolean) {
        _uiState.update { it.copy(snapToPage = snap) }
        val mode = _uiState.value.scrollMode
        val jsVal = if (snap) {
            3
        } else {
            if (mode == "horizontal") 1 else 0
        }
        sendJsCommand("if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) { PDFViewerApplication.pdfViewer.scrollMode = $jsVal; }")
    }

    fun setAutoHideToolbar(autoHide: Boolean) {
        _uiState.update { it.copy(autoHideToolbar = autoHide) }
    }

    fun setReadingTheme(theme: String) {
        _uiState.update { it.copy(readingTheme = theme) }
        sendJsCommand("applyTheme('$theme')")
    }

    fun setSystemBrightness(isSystem: Boolean) {
        _uiState.update { it.copy(isSystemBrightness = isSystem) }
    }

    fun setCustomBrightness(brightness: Float) {
        _uiState.update { it.copy(customBrightness = brightness, isSystemBrightness = false) }
    }

    fun setKeepScreenOn(keep: Boolean) {
        _uiState.update { it.copy(keepScreenOn = keep) }
    }

    fun startAutoScroll(speedPxPerSecond: Int) {
        _uiState.update { it.copy(isAutoScrolling = true, autoScrollSpeed = speedPxPerSecond) }
        sendJsCommand("startAutoScroll($speedPxPerSecond)")
    }

    fun stopAutoScroll() {
        _uiState.update { it.copy(isAutoScrolling = false) }
        sendJsCommand("stopAutoScroll()")
    }

    fun selectPdf(filePath: String, fileName: String) {
        viewModelScope.launch {
            // Find if already exists, then insert or update
            val existing = recentPdfDao.getPdfByPath(filePath)
            val pdf = RecentPdf(
                id = existing?.id ?: 0,
                filePath = filePath,
                fileName = fileName,
                lastPage = existing?.lastPage ?: 1,
                totalPages = existing?.totalPages ?: 0,
                lastOpened = System.currentTimeMillis()
            )
            recentPdfDao.insertOrUpdate(pdf)

            appContext?.let { ctx ->
                val prefs = ctx.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_screen", "Viewer")
                    .putString("last_opened_pdf_path", filePath)
                    .putString("last_opened_pdf_name", fileName)
                    .putInt("last_opened_pdf_page", pdf.lastPage)
                    .apply()
            }

            _uiState.update {
                it.copy(
                    currentScreen = Screen.Viewer,
                    currentPdfPath = filePath,
                    currentPdfName = fileName,
                    currentPage = pdf.lastPage,
                    totalPages = pdf.totalPages,
                    searchQuery = "",
                    isSearchActive = false,
                    searchMatchesTotal = 0,
                    searchMatchActive = 0,
                    currentScale = 1.0f
                )
            }
        }
    }

    fun updatePage(page: Int, total: Int) {
        _uiState.update {
            it.copy(currentPage = page, totalPages = total)
        }
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("last_opened_pdf_page", page)
                .apply()
        }
        viewModelScope.launch {
            _uiState.value.currentPdfPath?.let { path ->
                recentPdfDao.updateLastPage(path, page)
                // Also update the total pages in the database if it was 0
                val existing = recentPdfDao.getPdfByPath(path)
                if (existing != null && existing.totalPages != total) {
                    recentPdfDao.insertOrUpdate(existing.copy(totalPages = total))
                }
            }
        }
    }

    fun updateSearchMatches(total: Int, current: Int) {
        _uiState.update {
            it.copy(searchMatchesTotal = total, searchMatchActive = current)
        }
    }

    fun goBackToDashboard() {
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_screen", "Dashboard")
                .apply()
        }
        _uiState.update {
            it.copy(currentScreen = Screen.Dashboard)
        }
    }

    fun sendJsCommand(command: String) {
        viewModelScope.launch {
            _jsCommandFlow.emit(command)
        }
    }

    // Edit / Annotation controls
    fun toggleEditMode(enabled: Boolean) {
        _uiState.update { it.copy(isEditMode = enabled) }
        if (!enabled) {
            setEditTool("none")
        }
    }

    fun setEditTool(tool: String) {
        _uiState.update { it.copy(activeEditTool = tool) }
        val modeVal = when (tool) {
            "pen" -> 15       // INK
            "text" -> 3       // FREETEXT
            "highlighter" -> 9 // HIGHLIGHT
            "image" -> 13     // STAMP
            else -> 0         // NONE
        }
        sendJsCommand("PDFViewerApplication.eventBus.dispatch('switchannotationeditormode', { mode: $modeVal });")
        if (tool != "none") {
            sendJsCommand("document.body.classList.add('edit-mode-active');")
        } else {
            sendJsCommand("document.body.classList.remove('edit-mode-active');")
        }
    }

    fun setEditColor(hexColor: String) {
        _uiState.update { it.copy(editColor = hexColor) }
        sendJsCommand("PDFViewerApplication.eventBus.dispatch('switchannotationeditorparams', { type: 1, value: '$hexColor' });")
    }

    fun setEditThickness(thickness: Float) {
        _uiState.update { it.copy(editThickness = thickness) }
        sendJsCommand("PDFViewerApplication.eventBus.dispatch('switchannotationeditorparams', { type: 3, value: ${thickness.toInt()} });")
    }

    fun setEditOpacity(opacity: Float) {
        _uiState.update { it.copy(editOpacity = opacity) }
        sendJsCommand("PDFViewerApplication.eventBus.dispatch('switchannotationeditorparams', { type: 2, value: ${opacity.toInt()} });")
    }

    fun triggerUndo() {
        sendJsCommand("if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.annotationEditorUIManager) { PDFViewerApplication.pdfViewer.annotationEditorUIManager.undo(); }")
    }

    fun triggerRedo() {
        sendJsCommand("if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer && PDFViewerApplication.pdfViewer.annotationEditorUIManager) { PDFViewerApplication.pdfViewer.annotationEditorUIManager.redo(); }")
    }

    fun setAnnotationEditorMode(mode: Int) {
        _uiState.update { 
            it.copy(
                annotationEditorMode = mode,
                isEditMode = (mode != 0)
            ) 
        }
        sendJsCommand("""
            if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) {
                PDFViewerApplication.pdfViewer.annotationEditorMode = { mode: $mode };
            }
        """.trimIndent())
    }

    fun requestSaveAnnotatedPdf() {
        sendJsCommand("""
            (async () => {
                try {
                    if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfDocument) {
                        const data = await PDFViewerApplication.pdfDocument.saveDocument();
                        let binary = '';
                        const bytes = new Uint8Array(data);
                        const chunkSize = 8192;
                        for (let i = 0; i < bytes.length; i += chunkSize) {
                            binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
                        }
                        const base64 = btoa(binary);
                        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onDocumentSaved) {
                            AndroidBridge.onDocumentSaved(base64);
                        } else if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onPdfSaved) {
                            AndroidBridge.onPdfSaved(base64);
                        }
                    } else {
                        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onSaveError) {
                            AndroidBridge.onSaveError("PDFViewerApplication is not ready or has no pdfDocument");
                        }
                    }
                } catch (e) {
                    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onSaveError) {
                        AndroidBridge.onSaveError(e.message || e.toString());
                    }
                }
            })();
        """.trimIndent())
    }

    fun saveAnnotatedPdf(context: Context, base64Data: String) {
        val currentPath = _uiState.value.currentPdfPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                val file = File(currentPath)
                file.writeBytes(bytes)
                
                // Scan files to refresh size/metadata
                scanFiles(context)
                
                _uiState.update { 
                    it.copy(
                        isEditMode = false,
                        activeEditTool = "none"
                    ) 
                }
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "تم حفظ التعديلات بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "فشل حفظ التعديلات: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onPdfSaveFailed(context: Context, error: String) {
        viewModelScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(context, "خطأ في الحفظ: $error", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun triggerFitWidth() {
        sendJsCommand("(function() { try { if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) { PDFViewerApplication.pdfViewer.currentScaleValue = 'page-width'; setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 100); } } catch(e) {} })()")
    }

    fun triggerFitPage() {
        sendJsCommand("(function() { try { if (typeof PDFViewerApplication !== 'undefined' && PDFViewerApplication.pdfViewer) { PDFViewerApplication.pdfViewer.currentScaleValue = 'page-fit'; setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 100); } } catch(e) {} })()")
    }

    // PDF Navigation commands
    fun triggerNextPage() {
        sendJsCommand("PDFViewerApplication.pdfViewer.nextPage()")
    }

    fun triggerPreviousPage() {
        sendJsCommand("PDFViewerApplication.pdfViewer.previousPage()")
    }

    fun triggerZoomIn() {
        sendJsCommand("PDFViewerApplication.pdfViewer.currentScale += 0.25")
        _uiState.update { it.copy(currentScale = it.currentScale + 0.25f) }
    }

    fun triggerZoomOut() {
        sendJsCommand("PDFViewerApplication.pdfViewer.currentScale -= 0.25")
        _uiState.update { it.copy(currentScale = (it.currentScale - 0.25f).coerceAtLeast(0.25f)) }
    }

    fun triggerSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = true) }
        val jsQuery = query.replace("'", "\\'")
        sendJsCommand(
            """
            PDFViewerApplication.eventBus.dispatch('find', {
                query: '$jsQuery',
                caseSensitive: false,
                entireWord: false,
                highlightAll: true,
                findPrevious: false
            })
            """.trimIndent()
        )
    }

    fun navigateSearchNext() {
        val jsQuery = _uiState.value.searchQuery.replace("'", "\\'")
        sendJsCommand(
            """
            PDFViewerApplication.eventBus.dispatch('find', {
                query: '$jsQuery',
                caseSensitive: false,
                entireWord: false,
                highlightAll: true,
                findPrevious: false,
                findAgain: true
            })
            """.trimIndent()
        )
    }

    fun navigateSearchPrev() {
        val jsQuery = _uiState.value.searchQuery.replace("'", "\\'")
        sendJsCommand(
            """
            PDFViewerApplication.eventBus.dispatch('find', {
                query: '$jsQuery',
                caseSensitive: false,
                entireWord: false,
                highlightAll: true,
                findPrevious: true,
                findAgain: true
            })
            """.trimIndent()
        )
    }

    fun closeSearch() {
        _uiState.update {
            it.copy(isSearchActive = false, searchQuery = "", searchMatchesTotal = 0, searchMatchActive = 0)
        }
        // Dispatch find command with empty string to clear highlights
        sendJsCommand(
            """
            PDFViewerApplication.eventBus.dispatch('find', {
                query: '',
                caseSensitive: false,
                entireWord: false,
                highlightAll: true,
                findPrevious: false
            })
            """.trimIndent()
        )
    }

    fun openSearch() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun setScale(scale: Float) {
        val coerced = scale.coerceIn(1.0f, 4.0f)
        _uiState.update { it.copy(currentScale = coerced) }
        sendJsCommand("window.setScale($coerced)")
    }

    fun updateScaleFromJs(scale: Float) {
        val coerced = scale.coerceIn(1.0f, 4.0f)
        _uiState.update { it.copy(currentScale = coerced) }
    }

    fun setScreenOrientation(context: Context, orientation: Int) {
        var currentContext = context
        var activity: android.app.Activity? = null
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) {
                activity = currentContext
                break
            }
            currentContext = currentContext.baseContext
        }
        activity?.requestedOrientation = orientation
        _uiState.update { it.copy(screenOrientation = orientation) }
    }

    fun deleteRecentPdf(filePath: String) {
        viewModelScope.launch {
            recentPdfDao.deletePdf(filePath)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            recentPdfDao.clearHistory()
        }
    }

    // Helper functions for file management
    fun openSamplePdf(context: Context) {
        viewModelScope.launch {
            val sampleFile = File(context.cacheDir, "sample_test.pdf")
            try {
                // Copy the pre-bundled pdf.js test PDF to the cache directory as a sample!
                if (!sampleFile.exists()) {
                    context.assets.open("pdfjs/web/compressed.tracemonkey-pldi-09.pdf").use { input ->
                        sampleFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                selectPdf(sampleFile.absolutePath, "الملف التجريبي (TraceMonkey)")
            } catch (e: Exception) {
                e.printStackTrace()
                // In case it fails because pdf.js is still unzipping, try another fallback or notify
            }
        }
    }

    // New navigation and state management methods
    fun createFourteenPagePdf(context: Context): File {
        val file = File(context.cacheDir, "book_14_pages.pdf")
        if (file.exists() && file.length() > 0) return file

        val pdfDocument = android.graphics.pdf.PdfDocument()
        
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }
        
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(124, 92, 255)
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val subTitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(118, 116, 130)
            textSize = 16f
            isAntiAlias = true
        }

        for (i in 1..14) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, i).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(250, 249, 254)
            }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(230, 224, 255)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(24f, 24f, 571f, 818f, borderPaint)

            canvas.drawText("مستند الـ 14 صفحة التجريبي لقراءة الـ PDF", 50f, 75f, titlePaint)
            canvas.drawText("التحقق من حل مشكلة الارتداد والأداء في WebView", 50f, 105f, subTitlePaint)
            
            val linePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(124, 92, 255)
                strokeWidth = 3f
            }
            canvas.drawLine(50f, 125f, 545f, 125f, linePaint)

            canvas.drawText("الصفحة الحالية: $i من 14 صفحة", 60f, 200f, textPaint)
            canvas.drawText("هذا الملف تم توليده ديناميكياً لاختبار ثبات واجهة العرض أثناء القراءة النشطة.", 60f, 250f, textPaint)
            canvas.drawText("مع التعديل الجديد، يتم تحديث زمن القراءة صامتاً كل ثانية في SharedPreferences", 60f, 300f, textPaint)
            canvas.drawText("دون إعادة بناء (recomposition) لكامل الشاشة أو إعادة قياس WebView.", 60f, 350f, textPaint)
            canvas.drawText("بذلك، لن يواجه المستخدم أي مشكلة 'قفز' أو 'ارتداد' في التمرير نهائياً.", 60f, 400f, textPaint)
            canvas.drawText("أنت تقرأ الآن الصفحة رقم $i بكل سلاسة واستقرار.", 60f, 450f, textPaint)

            val footerLinePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(220, 220, 230)
                strokeWidth = 1f
            }
            canvas.drawLine(50f, 750f, 545f, 750f, footerLinePaint)
            canvas.drawText("تطبيق FinalPDF © 2026", 50f, 780f, subTitlePaint)
            canvas.drawText("صفحة $i / 14", 450f, 780f, subTitlePaint)

            pdfDocument.finishPage(page)
        }

        try {
            file.outputStream().use { out ->
                pdfDocument.writeTo(out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
        return file
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        val completed = prefs.getBoolean("welcome_completed", false)
        val showTools = prefs.getBoolean("show_tools_tab", true)
        val theme = prefs.getString("app_theme", "system") ?: "system"
        val bottomBarColorIdx = prefs.getInt("bottom_bar_color_index", 0)
        val sortOptName = prefs.getString("sort_option", SortOption.ALPHA_ASC.name) ?: SortOption.ALPHA_ASC.name
        val sortOpt = try { SortOption.valueOf(sortOptName) } catch(e: Exception) { SortOption.ALPHA_ASC }
        
        val starredSet = prefs.getStringSet("starred_pdfs", emptySet()) ?: emptySet()
        
        // Load persistent viewer state
        val lastScreen = prefs.getString("last_screen", "Dashboard")
        val savedPath = prefs.getString("last_opened_pdf_path", null)
        val savedName = prefs.getString("last_opened_pdf_name", null)
        val savedPage = prefs.getInt("last_opened_pdf_page", 1)
        val savedIsGrid = prefs.getBoolean("is_grid_view", true)

        // Generate the 14-page PDF on startup
        val sample14File = createFourteenPagePdf(context)

        _uiState.update {
            val nextScreen = if (!completed) {
                Screen.Welcome
            } else if (lastScreen == "Viewer" && savedPath != null && File(savedPath).exists()) {
                Screen.Viewer
            } else if (it.currentScreen == Screen.Viewer) {
                Screen.Viewer
            } else {
                Screen.Dashboard
            }
            
            val path = if (nextScreen == Screen.Viewer) {
                savedPath
            } else {
                it.currentPdfPath ?: if (completed) sample14File.absolutePath else null
            }

            val name = if (nextScreen == Screen.Viewer) {
                savedName
            } else {
                it.currentPdfName ?: if (completed) "الملف التجريبي (14 صفحة)" else null
            }

            val page = if (nextScreen == Screen.Viewer) {
                savedPage
            } else {
                if (it.currentPage > 0) it.currentPage else if (completed) 1 else 0
            }

            val total = if (nextScreen == Screen.Viewer) {
                0 // Will be updated asynchronously or when WebView loads
            } else {
                if (it.totalPages > 0) it.totalPages else if (completed) 14 else 0
            }

            it.copy(
                welcomeCompleted = completed,
                showToolsTab = showTools,
                isGridView = savedIsGrid,
                starredPdfs = starredSet,
                appTheme = theme,
                bottomBarColorIndex = bottomBarColorIdx,
                sortOption = sortOpt,
                currentScreen = nextScreen,
                currentPdfPath = path,
                currentPdfName = name,
                currentPage = page,
                totalPages = total
            )
        }
        
        // Fetch total pages asynchronously from the database if restoring Viewer Screen
        if (completed && lastScreen == "Viewer" && savedPath != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val dbPdf = recentPdfDao.getPdfByPath(savedPath)
                if (dbPdf != null && dbPdf.totalPages > 0) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            if (it.currentPdfPath == savedPath) {
                                it.copy(totalPages = dbPdf.totalPages)
                            } else {
                                it
                            }
                        }
                    }
                }
            }
        }

        loadReadingTime(context)
        
        // Scan for local PDF files and populate cache
        scanFiles(context)
    }

    fun setAppTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme", theme).apply()
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun setBottomBarColorIndex(context: Context, index: Int) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("bottom_bar_color_index", index).apply()
        _uiState.update { it.copy(bottomBarColorIndex = index) }
    }
    
    fun completeWelcome(context: Context) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("welcome_completed", true).apply()
        
        val sample14File = createFourteenPagePdf(context)

        _uiState.update {
            it.copy(
                welcomeCompleted = true,
                currentScreen = Screen.Viewer,
                currentPdfPath = sample14File.absolutePath,
                currentPdfName = "الملف التجريبي (14 صفحة)",
                currentPage = 1,
                totalPages = 14
            )
        }
        scanFiles(context)
    }
    
    fun setTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
    
    fun setDashboardSearchQuery(query: String) {
        _uiState.update { it.copy(dashboardSearchQuery = query) }
    }
    
    fun toggleGridView(context: Context? = null) {
        val newIsGrid = !_uiState.value.isGridView
        val ctx = context ?: appContext
        ctx?.let {
            val prefs = it.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_grid_view", newIsGrid).apply()
        }
        _uiState.update { it.copy(isGridView = newIsGrid) }
    }
    
    fun setFileFilter(filter: FileFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }
    
    fun toggleFavorite(context: Context, filePath: String) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        val currentStarred = _uiState.value.starredPdfs.toMutableSet()
        if (currentStarred.contains(filePath)) {
            currentStarred.remove(filePath)
        } else {
            currentStarred.add(filePath)
        }
        prefs.edit().putStringSet("starred_pdfs", currentStarred).apply()
        _uiState.update { it.copy(starredPdfs = currentStarred) }
        
        // Rescan files to update favorites status
        scanFiles(context)
    }
    
    fun setShowToolsTab(context: Context, show: Boolean) {
        val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_tools_tab", show).apply()
        _uiState.update { it.copy(showToolsTab = show) }
    }
    
    private fun getStorageInfo(): StorageInfo {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            
            val totalGb = totalBytes / (1024f * 1024f * 1024f)
            val availableGb = availableBytes / (1024f * 1024f * 1024f)
            val usedGb = usedBytes / (1024f * 1024f * 1024f)
            val usedPercentage = if (totalBytes > 0) ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt() else 0
            
            StorageInfo(totalGb, availableGb, usedGb, usedPercentage)
        } catch (e: Exception) {
            StorageInfo(0f, 0f, 0f, 0)
        }
    }

    private fun scanDirRecursively(dir: File, result: MutableList<LocalPdfFile>, maxDepth: Int = 3, currentDepth: Int = 0) {
        if (currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirRecursively(file, result, maxDepth, currentDepth + 1)
                }
            } else if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                val isAlreadyAdded = result.any { it.filePath == file.absolutePath }
                if (!isAlreadyAdded) {
                    val size = file.length()
                    val sizeStr = when {
                        size > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
                        size > 1024 -> "${size / 1024} KB"
                        else -> "$size B"
                    }
                    
                    val folderName = dir.name.ifEmpty { "Documents" }
                    result.add(
                        LocalPdfFile(
                            filePath = file.absolutePath,
                            fileName = file.name.replace(".pdf", "", ignoreCase = true).replace("_", " "),
                            fileSize = sizeStr,
                            folderName = folderName,
                            lastModified = file.lastModified(),
                            isFavorite = false
                        )
                    )
                }
            }
        }
    }

    fun scanFiles(context: Context) {
        viewModelScope.launch {
            val storage = getStorageInfo()
            val filesList = mutableListOf<LocalPdfFile>()
            
            // Check storage permission
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            
            if (hasPermission) {
                // 1. Scan via MediaStore
                val contentResolver = context.contentResolver
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DATA} LIKE ?"
                val selectionArgs = arrayOf("application/pdf", "%.pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                
                try {
                    contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                        val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                        val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                        val dateIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                        
                        while (cursor.moveToNext()) {
                            if (dataIndex != -1) {
                                val path = cursor.getString(dataIndex)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists()) {
                                        val name = if (nameIndex != -1) cursor.getString(nameIndex) else file.name
                                        val rawSize = if (sizeIndex != -1) cursor.getLong(sizeIndex) else file.length()
                                        val lastMod = if (dateIndex != -1) cursor.getLong(dateIndex) * 1000 else file.lastModified()
                                        
                                        val sizeStr = when {
                                            rawSize > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", rawSize / (1024f * 1024f))
                                            rawSize > 1024 -> "${rawSize / 1024} KB"
                                            else -> "$rawSize B"
                                        }
                                        
                                        val parentName = file.parentFile?.name ?: "Documents"
                                        val isFav = _uiState.value.starredPdfs.contains(path)
                                        
                                        val isAlreadyAdded = filesList.any { it.filePath == path }
                                        if (!isAlreadyAdded) {
                                            filesList.add(
                                                LocalPdfFile(
                                                    filePath = path,
                                                    fileName = name.replace(".pdf", "", ignoreCase = true).replace("_", " "),
                                                    fileSize = sizeStr,
                                                    folderName = parentName,
                                                    lastModified = lastMod,
                                                    isFavorite = isFav
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 2. Scan standard directories recursively
                val finalPdfDir = getFinalPdfOutputDir(context)
                val publicDirs = listOf(
                    finalPdfDir,
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FinalPDF"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FinalPDF"),
                    File(context.getExternalFilesDir(null), "FinalPDF"),
                    File(context.cacheDir, "processed_pdfs"),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    File("/sdcard/Download"),
                    File("/sdcard/Documents"),
                    File("/sdcard/WhatsApp/Media/WhatsApp Documents"),
                    File("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
                )
                
                for (dir in publicDirs) {
                    try {
                        if (dir.exists() && dir.isDirectory) {
                            scanDirRecursively(dir, filesList, maxDepth = 3)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            // 3. Scan user's cache directory (for imported files or fallback sample files)
            try {
                val cacheFiles = context.cacheDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                cacheFiles?.forEach { file ->
                    val isAlreadyAdded = filesList.any { it.filePath == file.absolutePath }
                    if (!isAlreadyAdded) {
                        val isFav = _uiState.value.starredPdfs.contains(file.absolutePath)
                        val size = file.length()
                        val sizeStr = when {
                            size > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
                            size > 1024 -> "${size / 1024} KB"
                            else -> "$size B"
                        }
                        
                        val isPrebuilt = file.name in listOf(
                            "Hören_&_Sprechen_A2.pdf",
                            "Netzwerk_Neu_A2_Übungsbuch.pdf",
                            "NWn_A2_Glossar_Arabisch.pdf",
                            "B1_Wortschatz.pdf",
                            "CamScanner_2025-11-09.pdf"
                        )
                        if (isPrebuilt) {
                            try {
                                file.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return@forEach
                        }
                        
                        filesList.add(
                            LocalPdfFile(
                                filePath = file.absolutePath,
                                fileName = if (file.name == "book_14_pages.pdf") "الملف التجريبي (14 صفحة)" else file.name.replace(".pdf", "", ignoreCase = true).replace("_", " "),
                                fileSize = sizeStr,
                                folderName = if (file.name == "book_14_pages.pdf") "ملفات تجريبية" else "مستندات مستوردة",
                                lastModified = file.lastModified(),
                                isFavorite = isFav
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 5. Update state and filter out any remaining mock/sample/test/assets files
            val finalFiles = filesList.map {
                it.copy(isFavorite = _uiState.value.starredPdfs.contains(it.filePath))
            }.filter { file ->
                val lowerName = file.fileName.lowercase()
                val lowerFolder = file.folderName.lowercase()
                val isSampleOrTest = (lowerName.contains("sample") || lowerName.contains("test") ||
                        lowerName.contains("تجريبي") || lowerName.contains("تجريبية")) && !file.filePath.contains("14")
                val isAssetsFolder = (lowerFolder == "assets" || lowerFolder == "ملفات تجريبية") && !file.filePath.contains("14")
                val isPrebuilt = file.filePath.contains("Hören_&_Sprechen_A2") ||
                        file.filePath.contains("Netzwerk_Neu_A2") ||
                        file.filePath.contains("NWn_A2_Glossar") ||
                        file.filePath.contains("B1_Wortschatz") ||
                        file.filePath.contains("CamScanner_2025")
                
                !isSampleOrTest && !isAssetsFolder && !isPrebuilt
            }.distinctBy { it.filePath }
            
            _uiState.update { 
                it.copy(
                    allPdfFiles = finalFiles,
                    storageInfo = storage
                )
            }
        }
    }

    fun copyUriToCache(context: Context, uri: Uri, originalName: String): String? {
        return try {
            val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tempFile = File(context.cacheDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFinalPdfOutputDir(context: Context): File {
        val dirName = "FinalPDF"
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val finalPdfDir = File(docsDir, dirName)
            if (!finalPdfDir.exists()) {
                finalPdfDir.mkdirs()
            }
            if (finalPdfDir.exists()) {
                return finalPdfDir
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val finalPdfDownloadDir = File(downloadDir, dirName)
            if (!finalPdfDownloadDir.exists()) {
                finalPdfDownloadDir.mkdirs()
            }
            if (finalPdfDownloadDir.exists()) {
                return finalPdfDownloadDir
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val extDir = File(context.getExternalFilesDir(null), dirName)
        if (!extDir.exists()) {
            extDir.mkdirs()
        }
        return extDir
    }

    fun mergePdfs(context: Context, filePaths: List<String>, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                var pageIndex = 0
                for (path in filePaths) {
                    val file = File(path)
                    if (!file.exists()) continue
                    val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fileDescriptor)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val width = page.width
                        val height = page.height
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex++).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDocument.finishPage(pdfPage)
                        page.close()
                        bitmap.recycle()
                    }
                    renderer.close()
                    fileDescriptor.close()
                }
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ غير معروف أثناء دمج الملفات")
                }
            }
        }
    }

    fun splitPdf(context: Context, filePath: String, fromPage: Int, toPage: Int, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                val total = renderer.pageCount
                val start = fromPage.coerceIn(1, total)
                val end = toPage.coerceIn(start, total)
                
                val pdfDocument = PdfDocument()
                var pageIndex = 0
                for (i in (start - 1)..(end - 1)) {
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    page.close()
                    bitmap.recycle()
                }
                renderer.close()
                fileDescriptor.close()
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء تقسيم الملف")
                }
            }
        }
    }

    fun compressPdf(context: Context, filePath: String, qualityLevel: String, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                val pdfDocument = PdfDocument()
                
                val scaleFactor = when (qualityLevel) {
                    "high" -> 0.5f
                    "medium" -> 0.7f
                    else -> 0.85f
                }
                val compressQuality = when (qualityLevel) {
                    "high" -> 40
                    "medium" -> 65
                    else -> 80
                }
                
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val origWidth = page.width
                    val origHeight = page.height
                    
                    val newWidth = (origWidth * scaleFactor).toInt().coerceAtLeast(100)
                    val newHeight = (origHeight * scaleFactor).toInt().coerceAtLeast(100)
                    
                    val renderBmp = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
                    page.render(renderBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val bytesOut = ByteArrayOutputStream()
                    renderBmp.compress(Bitmap.CompressFormat.JPEG, compressQuality, bytesOut)
                    val compressedBytes = bytesOut.toByteArray()
                    val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(origWidth, origHeight, i).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    
                    val destRect = Rect(0, 0, origWidth, origHeight)
                    pdfPage.canvas.drawBitmap(compressedBmp, null, destRect, null)
                    
                    pdfDocument.finishPage(pdfPage)
                    page.close()
                    renderBmp.recycle()
                    compressedBmp.recycle()
                }
                renderer.close()
                fileDescriptor.close()
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء ضغط الملف")
                }
            }
        }
    }

    fun rotatePdf(context: Context, filePath: String, degrees: Int, targetPage: Int, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                val pdfDocument = PdfDocument()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val shouldRotate = (targetPage == -1) || (targetPage == (i + 1))
                    
                    val (finalWidth, finalHeight) = if (shouldRotate && (degrees == 90 || degrees == 270)) {
                        Pair(height, width)
                    } else {
                        Pair(width, height)
                    }
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(finalWidth, finalHeight, i).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    
                    if (shouldRotate) {
                        val matrix = Matrix()
                        matrix.postRotate(degrees.toFloat())
                        
                        when (degrees) {
                            90 -> matrix.postTranslate(finalWidth.toFloat(), 0f)
                            180 -> matrix.postTranslate(finalWidth.toFloat(), finalHeight.toFloat())
                            270 -> matrix.postTranslate(0f, finalHeight.toFloat())
                        }
                        pdfPage.canvas.drawBitmap(bitmap, matrix, null)
                    } else {
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    }
                    
                    pdfDocument.finishPage(pdfPage)
                    page.close()
                    bitmap.recycle()
                }
                renderer.close()
                fileDescriptor.close()
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء تدوير صفحات الملف")
                }
            }
        }
    }

    fun reorderPdf(context: Context, filePath: String, pageOrderList: List<Int>, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                val pdfDocument = PdfDocument()
                var pageIndex = 0
                for (pageOneBased in pageOrderList) {
                    val i = pageOneBased - 1
                    if (i < 0 || i >= renderer.pageCount) continue
                    
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    page.close()
                    bitmap.recycle()
                }
                renderer.close()
                fileDescriptor.close()
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء إعادة ترتيب الصفحات")
                }
            }
        }
    }

    fun deletePagesPdf(context: Context, filePath: String, pagesToDelete: Set<Int>, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                
                val pdfDocument = PdfDocument()
                var pageIndex = 0
                var addedAny = false
                for (i in 0 until renderer.pageCount) {
                    val pageNumOneBased = i + 1
                    if (pagesToDelete.contains(pageNumOneBased)) continue
                    
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex++).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    page.close()
                    bitmap.recycle()
                    addedAny = true
                }
                renderer.close()
                fileDescriptor.close()
                
                if (!addedAny) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("لا يمكن حذف جميع الصفحات! يجب إبقاء صفحة واحدة على الأقل.")
                    }
                    pdfDocument.close()
                    return@launch
                }
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء حذف الصفحات")
                }
            }
        }
    }

    fun imageToPdf(context: Context, imagePaths: List<String>, targetName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (imagePaths.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("لم يتم اختيار أي صور")
                    }
                    return@launch
                }
                val pdfDocument = PdfDocument()
                for ((index, path) in imagePaths.withIndex()) {
                    val file = File(path)
                    if (!file.exists()) continue
                    val bitmap = BitmapFactory.decodeFile(path) ?: continue
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                outputFile.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء تحويل الصور إلى ملف PDF")
                }
            }
        }
    }

    fun pdfToImages(
        context: Context,
        filePath: String,
        format: String, // "jpg" or "png"
        customPagesStr: String, // empty for all, or comma-separated/ranges
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                val totalPages = renderer.pageCount
                
                val pagesToExport = mutableSetOf<Int>()
                if (customPagesStr.trim().isEmpty()) {
                    for (i in 1..totalPages) {
                        pagesToExport.add(i)
                    }
                } else {
                    val parts = customPagesStr.split(",")
                    for (part in parts) {
                        val trimmed = part.trim()
                        if (trimmed.contains("-")) {
                            val rangeParts = trimmed.split("-")
                            if (rangeParts.size == 2) {
                                val start = rangeParts[0].trim().toIntOrNull()
                                val end = rangeParts[1].trim().toIntOrNull()
                                if (start != null && end != null) {
                                    val startCoerced = start.coerceIn(1, totalPages)
                                    val endCoerced = end.coerceIn(startCoerced, totalPages)
                                    for (p in startCoerced..endCoerced) {
                                        pagesToExport.add(p)
                                    }
                                }
                            }
                        } else {
                            val pageNum = trimmed.toIntOrNull()
                            if (pageNum != null && pageNum in 1..totalPages) {
                                pagesToExport.add(pageNum)
                            }
                        }
                    }
                }
                
                if (pagesToExport.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("لم يتم تحديد صفحات صالحة للتصدير")
                    }
                    renderer.close()
                    fileDescriptor.close()
                    return@launch
                }
                
                val outputDirName = "Exported_Images_${file.nameWithoutExtension}_${System.currentTimeMillis()}"
                val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), outputDirName)
                if (!outputDir.exists()) outputDir.mkdirs()
                
                val exportedPaths = mutableListOf<String>()
                
                for (pageNum in pagesToExport) {
                    val i = pageNum - 1
                    val page = renderer.openPage(i)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val ext = format.lowercase()
                    val imgFile = File(outputDir, "page_${pageNum}.$ext")
                    imgFile.outputStream().use { out ->
                        if (ext == "png") {
                           bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else {
                           bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                    
                    exportedPaths.add(imgFile.absolutePath)
                    page.close()
                    bitmap.recycle()
                }
                
                renderer.close()
                fileDescriptor.close()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(exportedPaths)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء تصدير الصفحات لصور")
                }
            }
        }
    }

    fun lockPdf(
        context: Context,
        filePath: String,
        userPassword: String,
        allowPrinting: Boolean,
        allowCopying: Boolean,
        allowModifying: Boolean,
        allowAnnotations: Boolean,
        targetName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
                
                val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
                ap.setCanPrint(allowPrinting)
                ap.setCanExtractContent(allowCopying)
                ap.setCanModify(allowModifying)
                ap.setCanModifyAnnotations(allowAnnotations)
                
                val ownerPassword = "owner_default_secret_key"
                val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                    ownerPassword,
                    userPassword,
                    ap
                )
                spp.setEncryptionKeyLength(128)
                document.protect(spp)
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                
                document.save(outputFile)
                document.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء حماية وقفل الملف بكلمة سر")
                }
            }
        }
    }

    fun unlockPdf(
        context: Context,
        filePath: String,
        password: String,
        targetName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("الملف غير موجود")
                    }
                    return@launch
                }
                
                val document = try {
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(file, password)
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("كلمة المرور غير صحيحة أو الملف تالف")
                    }
                    return@launch
                }
                
                if (document.isEncrypted) {
                    document.setAllSecurityToBeRemoved(true)
                }
                
                val finalName = if (targetName.lowercase().endsWith(".pdf")) targetName else "$targetName.pdf"
                val outputDir = getFinalPdfOutputDir(context)
                val outputFile = File(outputDir, finalName)
                
                document.save(outputFile)
                document.close()
                
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("application/pdf"), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                scanFiles(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "حدث خطأ أثناء فك قفل الملف")
                }
            }
        }
    }

    fun renamePdfFile(context: Context, filePath: String, newName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val oldFile = File(filePath)
                if (!oldFile.exists()) {
                    onError("الملف غير موجود")
                    return@launch
                }
                
                var formattedNewName = newName.trim()
                if (!formattedNewName.endsWith(".pdf", ignoreCase = true)) {
                    formattedNewName += ".pdf"
                }
                
                val parentDir = oldFile.parentFile
                val newFile = File(parentDir, formattedNewName)
                
                if (newFile.exists()) {
                    onError("يوجد ملف آخر بنفس الاسم")
                    return@launch
                }
                
                if (oldFile.renameTo(newFile)) {
                    val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                    val currentStarred = _uiState.value.starredPdfs.toMutableSet()
                    if (currentStarred.contains(filePath)) {
                        currentStarred.remove(filePath)
                        currentStarred.add(newFile.absolutePath)
                        prefs.edit().putStringSet("starred_pdfs", currentStarred).apply()
                        _uiState.update { it.copy(starredPdfs = currentStarred) }
                    }
                    
                    val recent = recentPdfDao.getPdfByPath(filePath)
                    if (recent != null) {
                        recentPdfDao.deletePdf(filePath)
                        recentPdfDao.insertOrUpdate(recent.copy(filePath = newFile.absolutePath, fileName = formattedNewName.replace(".pdf", "", ignoreCase = true).replace("_", " ")))
                    }
                    
                    scanFiles(context)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    onError("فشل إعادة تسمية الملف")
                }
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء إعادة التسمية")
            }
        }
    }

    fun deletePdfFile(context: Context, filePath: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                    val currentStarred = _uiState.value.starredPdfs.toMutableSet()
                    if (currentStarred.contains(filePath)) {
                        currentStarred.remove(filePath)
                        prefs.edit().putStringSet("starred_pdfs", currentStarred).apply()
                        _uiState.update { it.copy(starredPdfs = currentStarred) }
                    }
                    
                    recentPdfDao.deletePdf(filePath)
                    
                    scanFiles(context)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    onError("فشل حذف الملف من الذاكرة")
                }
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء حذف الملف")
            }
        }
    }

    fun deleteMultiplePdfs(context: Context, filePaths: List<String>, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var deleteCount = 0
                val prefs = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                val currentStarred = _uiState.value.starredPdfs.toMutableSet()
                
                for (filePath in filePaths) {
                    val file = File(filePath)
                    if (file.exists() && file.delete()) {
                        deleteCount++
                        currentStarred.remove(filePath)
                        recentPdfDao.deletePdf(filePath)
                    }
                }
                
                prefs.edit().putStringSet("starred_pdfs", currentStarred).apply()
                _uiState.update { it.copy(starredPdfs = currentStarred) }
                
                scanFiles(context)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (deleteCount > 0) {
                        onSuccess()
                    } else {
                        onError("فشل حذف الملفات المحددة")
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء حذف الملفات")
            }
        }
    }
}

class PdfViewModelFactory(private val recentPdfDao: RecentPdfDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PdfViewModel(recentPdfDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
