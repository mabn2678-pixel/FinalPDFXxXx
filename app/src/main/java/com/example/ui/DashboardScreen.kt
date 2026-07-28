package com.example.ui

import com.example.ui.theme.BottomBarPresets
import com.example.ui.theme.BottomBarColorPreset
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentPdf
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recentPdfs by viewModel.recentPdfs.collectAsState(initial = emptyList())

    var selectedFilePaths by remember { mutableStateOf(emptySet<String>()) }
    var showFileActionsSheet by remember { mutableStateOf<LocalPdfFile?>(null) }
    var fileToRename by remember { mutableStateOf<LocalPdfFile?>(null) }
    var fileToDelete by remember { mutableStateOf<LocalPdfFile?>(null) }
    var fileToViewInfo by remember { mutableStateOf<LocalPdfFile?>(null) }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }

    val onToggleSelectFile = { filePath: String ->
        if (selectedFilePaths.contains(filePath)) {
            selectedFilePaths = selectedFilePaths - filePath
        } else {
            selectedFilePaths = selectedFilePaths + filePath
        }
    }

    var showExitConfirmSheet by remember { mutableStateOf(false) }

    // Back handler to return to the Home/Main tab, clear selection, or show exit confirmation sheet
    val isInSelectionMode = selectedFilePaths.isNotEmpty()
    BackHandler(enabled = true) {
        if (isInSelectionMode) {
            selectedFilePaths = emptySet()
        } else if (uiState.selectedTab != DashboardTab.Home) {
            viewModel.setTab(DashboardTab.Home)
        } else {
            showExitConfirmSheet = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, uri) ?: "ملف_غير_معروف.pdf"
            val cachedPath = viewModel.copyUriToCache(context, uri, fileName)
            if (cachedPath != null) {
                viewModel.selectPdf(cachedPath, fileName)
            }
        }
    }

    // Dynamic color scheme colors for custom items
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerBg = MaterialTheme.colorScheme.background

    var isBottomBarVisible by remember { mutableStateOf(true) }

    // Reset bottom bar visibility whenever active tab or selection state changes
    LaunchedEffect(uiState.selectedTab, selectedFilePaths.size) {
        isBottomBarVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = consumed.y
                if (delta < -15f && available.y == 0f) {
                    // Scrolling DOWN on a long list with actual consumed scroll -> Hide bottom bar
                    if (isBottomBarVisible) {
                        isBottomBarVisible = false
                    }
                } else if (delta > 15f || available.y > 0f) {
                    // Scrolling UP or overscrolling at top -> Show bottom bar
                    if (!isBottomBarVisible) {
                        isBottomBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280)) + fadeOut(animationSpec = tween(200))
            ) {
                CustomBottomBar(
                    selectedTab = uiState.selectedTab,
                    showTools = uiState.showToolsTab,
                    bottomBarColorIndex = uiState.bottomBarColorIndex,
                    appLanguage = uiState.appLanguage,
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.selectedTab == DashboardTab.Home && selectedFilePaths.isEmpty() && isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280)) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280)) + fadeOut(animationSpec = tween(200))
            ) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    containerColor = primaryColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("fab_open_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة ملف PDF",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(containerBg)
        ) {
            when (uiState.selectedTab) {
                DashboardTab.Home -> HomeTabScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    recentPdfs = recentPdfs,
                    onFilePickerLaunch = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    selectedFilePaths = selectedFilePaths,
                    onToggleSelectFile = onToggleSelectFile,
                    onShowFileActions = { showFileActionsSheet = it },
                    onShowFileDetails = { fileToViewInfo = it },
                    onClearSelection = { selectedFilePaths = emptySet() },
                    onShareMultiple = { shareMultiplePdfs(context, selectedFilePaths.toList()) },
                    onDeleteMultiple = { showMultiDeleteConfirm = true }
                )
                DashboardTab.Folders -> FoldersTabScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    selectedFilePaths = selectedFilePaths,
                    onToggleSelectFile = onToggleSelectFile,
                    onShowFileActions = { showFileActionsSheet = it },
                    onShowFileDetails = { fileToViewInfo = it }
                )
                DashboardTab.Tools -> ToolsTabScreen(
                    viewModel = viewModel
                )
                DashboardTab.Settings -> SettingsTabScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }

    // Modal Overlays & Actions Sheets
    if (showFileActionsSheet != null) {
        val file = showFileActionsSheet!!
        FileActionSheet(
            file = file,
            isFavorite = file.isFavorite,
            onToggleFav = { viewModel.toggleFavorite(context, file.filePath) },
            onSelect = { selectedFilePaths = selectedFilePaths + file.filePath },
            onShare = { sharePdf(context, file.filePath, file.fileName) },
            onRename = { fileToRename = file },
            onFileInfo = { fileToViewInfo = file },
            onDelete = { fileToDelete = file },
            onDismiss = { showFileActionsSheet = null },
            appLanguage = uiState.appLanguage
        )
    }

    // File Rename Dialog
    if (fileToRename != null) {
        var newName by remember { mutableStateOf(fileToRename!!.fileName.replace(".pdf", "", ignoreCase = true)) }
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("إعادة تسمية الملف", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("أدخل الاسم الجديد للملف:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = fileToRename!!
                        viewModel.renamePdfFile(
                            context = context,
                            filePath = file.filePath,
                            newName = newName,
                            onSuccess = {
                                Toast.makeText(context, "تم إعادة تسمية الملف بنجاح", Toast.LENGTH_SHORT).show()
                                fileToRename = null
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("حذف الملف نهائياً", fontWeight = FontWeight.Bold, color = Color(0xFFC62828)) },
            text = {
                Text("هل أنت متأكد من حذف الملف \"${fileToDelete!!.fileName}\" بشكل نهائي؟ لا يمكن التراجع عن هذه العملية.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    onClick = {
                        val file = fileToDelete!!
                        viewModel.deletePdfFile(
                            context = context,
                            filePath = file.filePath,
                            onSuccess = {
                                Toast.makeText(context, "تم حذف الملف بنجاح", Toast.LENGTH_SHORT).show()
                                fileToDelete = null
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("تأكيد الحذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Multi-File Delete Confirmation Dialog
    if (showMultiDeleteConfirm && selectedFilePaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirm = false },
            title = { Text("حذف الملفات المحددة", fontWeight = FontWeight.Bold, color = Color(0xFFC62828)) },
            text = {
                Text("هل أنت متأكد من حذف ${selectedFilePaths.size} ملف بشكل نهائي؟ لا يمكن التراجع عن هذه العملية.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    onClick = {
                        viewModel.deleteMultiplePdfs(
                            context = context,
                            filePaths = selectedFilePaths.toList(),
                            onSuccess = {
                                Toast.makeText(context, "تم حذف الملفات بنجاح", Toast.LENGTH_SHORT).show()
                                selectedFilePaths = emptySet()
                                showMultiDeleteConfirm = false
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("تأكيد الحذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showExitConfirmSheet) {
        ExitAppBottomSheet(
            onDismiss = { showExitConfirmSheet = false },
            appLanguage = uiState.appLanguage
        )
    }

    // File Detail Overlay Sheet
    if (fileToViewInfo != null) {
        val file = fileToViewInfo!!
        PdfDetailOverlaySheet(
            file = file,
            onDismiss = { fileToViewInfo = null },
            onOpenPdf = { pdfFile ->
                fileToViewInfo = null
                viewModel.selectPdf(pdfFile.filePath, pdfFile.fileName)
            },
            onSharePdf = { pdfFile ->
                sharePdf(context, pdfFile.filePath, pdfFile.fileName)
            },
            appLanguage = uiState.appLanguage
        )
    }
}

// ==========================================
// HOME TAB SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    viewModel: PdfViewModel,
    uiState: PdfUiState,
    recentPdfs: List<RecentPdf>,
    onFilePickerLaunch: () -> Unit,
    selectedFilePaths: Set<String>,
    onToggleSelectFile: (String) -> Unit,
    onShowFileActions: (LocalPdfFile) -> Unit,
    onShowFileDetails: (LocalPdfFile) -> Unit,
    onClearSelection: () -> Unit,
    onShareMultiple: () -> Unit,
    onDeleteMultiple: () -> Unit
) {
    val context = LocalContext.current
    val strings = remember(uiState.appLanguage) { AppStringsProvider.get(uiState.appLanguage) }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    
    val greetingText = if (currentHour < 12) strings.goodMorning else strings.goodEvening
    val greetingIcon = if (currentHour < 12) Icons.Default.WbSunny else Icons.Default.NightsStay
    val greetingColor = if (currentHour < 12) Color(0xFFFBC02D) else Color(0xFFB19DFF)

    var showSortSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }

    // Filter files based on dashboardSearchQuery, selectedFilter, and sortOption
    val filteredFiles = remember(uiState.allPdfFiles, uiState.dashboardSearchQuery, uiState.selectedFilter, uiState.sortOption, recentPdfs) {
        var list = uiState.allPdfFiles
        
        // Apply search query
        if (uiState.dashboardSearchQuery.isNotEmpty()) {
            list = list.filter { it.fileName.contains(uiState.dashboardSearchQuery, ignoreCase = true) }
        }
        
        // Apply category filter
        list = when (uiState.selectedFilter) {
            FileFilter.All -> list
            FileFilter.Favorites -> list.filter { it.isFavorite }
            FileFilter.Recent -> {
                val recentPaths = recentPdfs.map { it.filePath }.toSet()
                list.filter { recentPaths.contains(it.filePath) }
            }
        }

        // Helper to parse file size string to bytes for sorting
        fun getSizeBytes(sizeStr: String): Long {
            val trimmed = sizeStr.uppercase().trim()
            val numberPart = trimmed.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
            return when {
                trimmed.contains("GB") -> (numberPart * 1024 * 1024 * 1024).toLong()
                trimmed.contains("MB") -> (numberPart * 1024 * 1024).toLong()
                trimmed.contains("KB") -> (numberPart * 1024).toLong()
                else -> numberPart.toLong()
            }
        }

        // Apply sorting
        when (uiState.sortOption) {
            SortOption.ALPHA_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
            SortOption.ALPHA_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.fileName })
            SortOption.SIZE_ASC -> list.sortedBy { getSizeBytes(it.fileSize) }
            SortOption.SIZE_DESC -> list.sortedByDescending { getSizeBytes(it.fileSize) }
            SortOption.DATE_ASC -> list.sortedBy { it.lastModified }
            SortOption.DATE_DESC -> list.sortedByDescending { it.lastModified }
        }
    }

    val recentPdfsMap = remember(recentPdfs) {
        recentPdfs.associateBy { it.filePath }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (selectedFilePaths.isNotEmpty()) {
            // Selection Mode Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تم تحديد ${selectedFilePaths.size} ملف",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Selected Files
                    IconButton(onClick = onShareMultiple) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة المحددة", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Delete Selected Files
                    IconButton(onClick = onDeleteMultiple) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف المحددة", tint = Color.Red)
                    }
                }
            }
        } else {
            // Top Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = greetingIcon,
                        contentDescription = null,
                        tint = greetingColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = greetingText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strings.welcomeMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Two Premium Header Icons (Stats & Sort) - Separated Circular Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Statistics Icon Button
                IconButton(
                    onClick = { showStatsSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = strings.statsTitle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Sorting Icon Button (3 Vertical Dots)
                IconButton(
                    onClick = { showSortSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = strings.sortTitle,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

        // Compact Interactive Search Bar
        BasicTextField(
            value = uiState.dashboardSearchQuery,
            onValueChange = { viewModel.setDashboardSearchQuery(it) },
            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp)
                .testTag("dashboard_search_input"),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.dashboardSearchQuery.isEmpty()) {
                            Text(
                                text = strings.searchPlaceholder,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                    if (uiState.dashboardSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setDashboardSearchQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filters and View Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Pills Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                FilterPill(
                    label = strings.allDocs,
                    selected = uiState.selectedFilter == FileFilter.All,
                    onClick = { viewModel.setFileFilter(FileFilter.All) }
                )
                FilterPill(
                    label = strings.favorites,
                    selected = uiState.selectedFilter == FileFilter.Favorites,
                    onClick = { viewModel.setFileFilter(FileFilter.Favorites) }
                )
                FilterPill(
                    label = strings.recent,
                    selected = uiState.selectedFilter == FileFilter.Recent,
                    onClick = { viewModel.setFileFilter(FileFilter.Recent) }
                )
            }

            // Controls Row (Refresh scan + Grid/List Toggle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh/Scan Button (Same size as list toggle)
                IconButton(
                    onClick = {
                        viewModel.scanFiles(context)
                        Toast.makeText(context, "تم تحديث وفحص المجلدات لجلب أحدث ملفات الـ PDF!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث وفحص الملفات",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Grid / List Toggle Button
                IconButton(
                    onClick = { viewModel.toggleGridView(context) },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isGridView) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "تبديل المظهر",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val homeGridState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {
            androidx.compose.foundation.lazy.grid.LazyGridState()
        }
        val homeListState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        // Files List / Grid Renderer
        if (filteredFiles.isEmpty()) {
            EmptyDashboardView(queryEmpty = uiState.dashboardSearchQuery.isNotEmpty(), appLanguage = uiState.appLanguage)
        } else {
            if (uiState.isGridView) {
                // Grid Layout
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = homeGridState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredFiles, key = { it.filePath }) { file ->
                        val recentInfo = recentPdfsMap[file.filePath]
                        val progressPercent = if (recentInfo != null && recentInfo.totalPages > 0) {
                            (recentInfo.lastPage.toFloat() / recentInfo.totalPages.toFloat() * 100).toInt().coerceIn(0, 100)
                        } else null
                        val lastOpenedText = recentInfo?.let { formatLastOpened(it.lastOpened) }

                        val isSelected = selectedFilePaths.contains(file.filePath)
                        val isInSelectionMode = selectedFilePaths.isNotEmpty()

                        PdfGridItem(
                            file = file,
                            progressPercent = progressPercent,
                            lastOpenedText = lastOpenedText,
                            isSelected = isSelected,
                            isInSelectionMode = isInSelectionMode,
                            onClick = {
                                if (isInSelectionMode) {
                                    onToggleSelectFile(file.filePath)
                                } else {
                                    viewModel.selectPdf(file.filePath, file.fileName)
                                }
                            },
                            onLongClick = {
                                if (isInSelectionMode) {
                                    onToggleSelectFile(file.filePath)
                                } else {
                                    onShowFileDetails(file)
                                }
                            },
                            onMenuClick = {
                                onShowFileActions(file)
                            },
                            appLanguage = uiState.appLanguage,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                // List Layout
                LazyColumn(
                    state = homeListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredFiles, key = { it.filePath }) { file ->
                        val recentInfo = recentPdfsMap[file.filePath]
                        val progressPercent = if (recentInfo != null && recentInfo.totalPages > 0) {
                            (recentInfo.lastPage.toFloat() / recentInfo.totalPages.toFloat() * 100).toInt().coerceIn(0, 100)
                        } else null
                        val lastOpenedText = recentInfo?.let { formatLastOpened(it.lastOpened) }

                        val isSelected = selectedFilePaths.contains(file.filePath)
                        val isInSelectionMode = selectedFilePaths.isNotEmpty()

                        PdfListItem(
                            file = file,
                            progressPercent = progressPercent,
                            lastOpenedText = lastOpenedText,
                            isSelected = isSelected,
                            isInSelectionMode = isInSelectionMode,
                            onClick = {
                                if (isInSelectionMode) {
                                    onToggleSelectFile(file.filePath)
                                } else {
                                    viewModel.selectPdf(file.filePath, file.fileName)
                                }
                            },
                            onLongClick = {
                                if (isInSelectionMode) {
                                    onToggleSelectFile(file.filePath)
                                } else {
                                    onShowFileDetails(file)
                                }
                            },
                            onMenuClick = {
                                onShowFileActions(file)
                            },
                            appLanguage = uiState.appLanguage,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets Overlay
    if (showSortSheet) {
        SortFilesSheet(
            sortOption = uiState.sortOption,
            onSortSelected = { viewModel.setSortOption(context, it) },
            onDismiss = { showSortSheet = false },
            appLanguage = uiState.appLanguage
        )
    }

    if (showStatsSheet) {
        LibraryStatsSheet(
            uiState = uiState,
            recentPdfs = recentPdfs,
            onDismiss = { showStatsSheet = false }
        )
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        label = "pill_bg"
    )
    val textCol by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF5A5764),
        label = "pill_text"
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) Color.Transparent else Color.LightGray.copy(alpha = 0.4f),
        label = "pill_border"
    )

    Box(
        modifier = Modifier
            .shadow(if (selected) 2.dp else 0.dp, RoundedCornerShape(12.dp))
            .background(containerBg, RoundedCornerShape(12.dp))
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textCol
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfGridItem(
    file: LocalPdfFile,
    progressPercent: Int?,
    lastOpenedText: String?,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    appLanguage: String = "ar",
    modifier: Modifier = Modifier
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(215.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 3-dots Menu Button on Top Left
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات الملف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Check circle on top right if selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "محدد",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // PDF cover thumbnail with nested progress bar
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(88.dp)
                ) {
                    PdfThumbnail(
                        filePath = file.filePath,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Thin progress line at the bottom of the thumbnail
                    if (progressPercent != null && progressPercent > 0) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            color = Color(0xFF1E88E5),
                            trackColor = Color(0xFF1E88E5).copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Name of PDF
                val gridFileNameFontSize = when {
                    file.fileName.length > 35 -> 8.5.sp
                    file.fileName.length > 25 -> 9.sp
                    file.fileName.length > 15 -> 10.sp
                    else -> 11.sp
                }
                Text(
                    text = getLocalizedFileName(file.fileName, strings),
                    fontSize = gridFileNameFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = (gridFileNameFontSize.value + 2).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Small badges - Folder & File Size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        val badgeFontSize = when {
                            file.folderName.length > 25 -> 7.sp
                            file.folderName.length > 15 -> 7.5.sp
                            else -> 8.sp
                        }
                        Text(
                            text = getLocalizedFolderName(file.folderName, strings),
                            fontSize = badgeFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF3E0), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = file.fileSize,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            maxLines = 1
                        )
                    }
                }
                
                // Reading progress and last opened label
                if (progressPercent != null && progressPercent > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "قرأت $progressPercent% • $lastOpenedText",
                        fontSize = 7.5.sp,
                        color = Color(0xFF1E88E5),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfListItem(
    file: LocalPdfFile,
    progressPercent: Int?,
    lastOpenedText: String?,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    appLanguage: String = "ar",
    modifier: Modifier = Modifier
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF cover thumbnail with circular amber badge
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 54.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    PdfThumbnail(
                        filePath = file.filePath,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (progressPercent != null && progressPercent > 0) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            color = Color(0xFF1E88E5),
                            trackColor = Color(0xFF1E88E5).copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = strings.confirm,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Small circular PDF amber icon badge overlay
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-3).dp, y = (-3).dp)
                        .background(Color(0xFFFFF9C4), CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFFBC02D),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                val listFileNameFontSize = when {
                    file.fileName.length > 35 -> 10.sp
                    file.fileName.length > 25 -> 11.sp
                    file.fileName.length > 15 -> 12.sp
                    else -> 13.sp
                }
                Text(
                    text = getLocalizedFileName(file.fileName, strings),
                    fontSize = listFileNameFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (listFileNameFontSize.value + 2.5).sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Folder Badge
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        val badgeFontSize = when {
                            file.folderName.length > 25 -> 7.5.sp
                            file.folderName.length > 15 -> 8.sp
                            else -> 8.5.sp
                        }
                        Text(
                            text = getLocalizedFolderName(file.folderName, strings),
                            fontSize = badgeFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // File Size Badge
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF3E0), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = file.fileSize,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            maxLines = 1
                        )
                    }
                    
                    if (lastOpenedText != null) {
                        Text(
                            text = lastOpenedText,
                            fontSize = 8.5.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Progress Percentage
                if (progressPercent != null && progressPercent > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "تمت قراءة $progressPercent%",
                        fontSize = 8.5.sp,
                        color = Color(0xFF1E88E5),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 3-dots Menu Button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات الملف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// In-memory LruCache for instant, zero-latency thumbnail retrieval during scrolling Lookups
object ThumbnailMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 6
    private val cache = object : android.util.LruCache<String, Bitmap>(maxMemory.coerceAtLeast(32 * 1024)) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getBitmap(key: String): Bitmap? = cache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(key, bitmap)
        }
    }
}

private val SharedThumbnailIO = Dispatchers.IO.limitedParallelism(4)

@Composable
fun PdfThumbnail(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedBitmap = remember(filePath) { ThumbnailMemoryCache.getBitmap(filePath) }
    var imageBitmap by remember(filePath) { 
        mutableStateOf(cachedBitmap?.let { if (!it.isRecycled) it.asImageBitmap() else null }) 
    }

    LaunchedEffect(filePath) {
        if (imageBitmap == null) {
            withContext(SharedThumbnailIO) {
                val bitmap = getPdfThumbnailBitmap(context, filePath)
                if (bitmap != null && !bitmap.isRecycled) {
                    val img = bitmap.asImageBitmap()
                    withContext(Dispatchers.Main) {
                        imageBitmap = img
                    }
                }
            }
        }
    }
    
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val img = imageBitmap
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Sleek fallback cover
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PDF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

fun getPdfThumbnailBitmap(context: Context, filePath: String): Bitmap? {
    try {
        val cached = ThumbnailMemoryCache.getBitmap(filePath)
        if (cached != null && !cached.isRecycled) return cached

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        
        val cacheKey = "thumb_w_" + file.nameWithoutExtension.hashCode() + "_" + file.lastModified() + ".jpg"
        val cacheFile = File(context.cacheDir, cacheKey)
        
        if (cacheFile.exists()) {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath, options)
            if (bitmap != null) {
                ThumbnailMemoryCache.putBitmap(filePath, bitmap)
                return bitmap
            }
        }
        
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
        val renderer = PdfRenderer(pfd)
        if (renderer.pageCount > 0) {
            val page = renderer.openPage(0)
            val width = 160
            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(90)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            
            try {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            ThumbnailMemoryCache.putBitmap(filePath, bitmap)
            return bitmap
        } else {
            renderer.close()
            pfd.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun getPdfThumbnailPath(context: Context, filePath: String): String? {
    val bitmap = getPdfThumbnailBitmap(context, filePath)
    val file = File(filePath)
    if (!file.exists()) return null
    val cacheKey = "thumb_w_" + file.nameWithoutExtension.hashCode() + "_" + file.lastModified() + ".jpg"
    val cacheFile = File(context.cacheDir, cacheKey)
    return if (cacheFile.exists()) cacheFile.absolutePath else null
}

fun formatReadingTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 -> "$hours ساعة و $remainingMinutes دقيقة"
        minutes > 0 -> "$minutes دقيقة"
        else -> "$totalSeconds ثانية"
    }
}

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

fun formatLastOpened(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        hours < 24 -> "منذ $hours ساعة"
        days < 7 -> "منذ $days يوم"
        else -> synchronized(dateFormat) {
            dateFormat.format(Date(timestamp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilesSheet(
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "ar"
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }

    AppBottomSheet(
        onDismiss = onDismiss
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = strings.sortTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = strings.sortSubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Category 1: Alphabetical
            Text(
                text = strings.sortAlphabetical,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SortChip(
                    label = strings.sortAlphaAsc,
                    selected = sortOption == SortOption.ALPHA_ASC,
                    onClick = { onSortSelected(SortOption.ALPHA_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = strings.sortAlphaDesc,
                    selected = sortOption == SortOption.ALPHA_DESC,
                    onClick = { onSortSelected(SortOption.ALPHA_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category 2: File Size
            Text(
                text = strings.sortFileSizeLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SortChip(
                    label = strings.sortSizeAsc,
                    selected = sortOption == SortOption.SIZE_ASC,
                    onClick = { onSortSelected(SortOption.SIZE_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = strings.sortSizeDesc,
                    selected = sortOption == SortOption.SIZE_DESC,
                    onClick = { onSortSelected(SortOption.SIZE_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category 3: Date Modified
            Text(
                text = strings.sortDateAddedLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SortChip(
                    label = strings.sortDateAsc,
                    selected = sortOption == SortOption.DATE_ASC,
                    onClick = { onSortSelected(SortOption.DATE_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = strings.sortDateDesc,
                    selected = sortOption == SortOption.DATE_DESC,
                    onClick = { onSortSelected(SortOption.DATE_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }
    }
}

@Composable
fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = modifier.height(40.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryStatsSheet(
    uiState: PdfUiState,
    recentPdfs: List<RecentPdf>,
    onDismiss: () -> Unit
) {
    val strings = remember(uiState.appLanguage) { AppStringsProvider.get(uiState.appLanguage) }
    val totalFiles = uiState.allPdfFiles.size
    
    fun getSizeBytes(sizeStr: String): Long {
        val trimmed = sizeStr.uppercase().trim()
        val numberPart = trimmed.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        return when {
            trimmed.contains("GB") -> (numberPart * 1024 * 1024 * 1024).toLong()
            trimmed.contains("MB") -> (numberPart * 1024 * 1024).toLong()
            trimmed.contains("KB") -> (numberPart * 1024).toLong()
            else -> numberPart.toLong()
        }
    }
    
    val totalPdfBytes = uiState.allPdfFiles.sumOf { getSizeBytes(it.fileSize) }
    val totalPdfSizeFormatted = when {
        totalPdfBytes > 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", totalPdfBytes / (1024f * 1024f * 1024f))
        totalPdfBytes > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", totalPdfBytes / (1024f * 1024f))
        else -> "${totalPdfBytes / 1024} KB"
    }
    
    val favoriteCount = uiState.allPdfFiles.count { it.isFavorite }
    val openedCount = uiState.allPdfFiles.count { file -> recentPdfs.any { it.filePath == file.filePath } }
    val deletedCount = recentPdfs.count { !File(it.filePath).exists() }
    val formattedReadingTime = formatReadingTime(uiState.totalReadingTimeSeconds)

    AppBottomSheet(
        onDismiss = onDismiss
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = strings.statsTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = strings.libraryOverviewSubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Grid of stats cards
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        title = strings.totalFiles,
                        value = strings.fileCountFormat(totalFiles),
                        icon = Icons.Default.PictureAsPdf,
                        iconColor = Color(0xFFEF5350),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = strings.documentSize,
                        value = totalPdfSizeFormatted,
                        icon = Icons.Default.SdStorage,
                        iconColor = Color(0xFF42A5F5),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        title = strings.favoriteFiles,
                        value = strings.fileCountFormat(favoriteCount),
                        icon = Icons.Default.Star,
                        iconColor = Color(0xFFFFCA28),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = strings.openedFiles,
                        value = strings.fileCountFormat(openedCount),
                        icon = Icons.Default.History,
                        iconColor = Color(0xFF66BB6A),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        title = strings.readingHours,
                        value = formattedReadingTime,
                        icon = Icons.Default.AccessTime,
                        iconColor = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = strings.missingFiles,
                        value = strings.fileCountFormat(deletedCount),
                        icon = Icons.Default.FolderOff,
                        iconColor = Color(0xFF78909C),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun EmptyDashboardView(queryEmpty: Boolean, appLanguage: String = "ar") {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (queryEmpty) Icons.Default.SearchOff else Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = Color.LightGray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (queryEmpty) strings.noSearchResultsTitle else strings.emptyLibraryTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (queryEmpty) strings.noSearchResultsSubtitle else strings.emptyLibrarySubtitle,
            fontSize = 12.sp,
            color = Color.Gray.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ==========================================
// FOLDERS TAB SCREEN (Image 6)
// ==========================================
@Composable
fun FoldersTabScreen(
    viewModel: PdfViewModel,
    uiState: PdfUiState,
    selectedFilePaths: Set<String>,
    onToggleSelectFile: (String) -> Unit,
    onShowFileActions: (LocalPdfFile) -> Unit,
    onShowFileDetails: (LocalPdfFile) -> Unit
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentPdfs.collectAsState(initial = emptyList())
    val strings = remember(uiState.appLanguage) { AppStringsProvider.get(uiState.appLanguage) }

    val recentPdfsMap = remember(recentPdfs) {
        recentPdfs.associateBy { it.filePath }
    }
    
    // Group scanned files by folderName
    val folderGroups = remember(uiState.allPdfFiles) {
        uiState.allPdfFiles.groupBy { it.folderName }
    }
    
    // Tracks which folder is expanded to show its PDFs inside the folders tab
    var expandedFolder by remember { mutableStateOf<String?>(null) }

    // Intercept back button when inside a folder to return to the folder list
    BackHandler(enabled = expandedFolder != null) {
        expandedFolder = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Folders Header Stats
        FolderStatsRow(
            pdfCount = uiState.allPdfFiles.size,
            folderCount = folderGroups.keys.size,
            appLanguage = uiState.appLanguage,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (expandedFolder == null) {
            // Folders List View
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folderGroups.toList(), key = { it.first }) { (folderName, files) ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedFolder = folderName }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFFFF9C4), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFFFBC02D),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val folderNameFontSize = when {
                                        folderName.length > 30 -> 11.sp
                                        folderName.length > 20 -> 12.sp
                                        folderName.length > 12 -> 13.sp
                                        else -> 14.sp
                                    }
                                    Text(
                                        text = getLocalizedFolderName(folderName, strings),
                                        fontSize = folderNameFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = strings.fileCountFormat(files.size),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
            }
        } else {
            // Expanded Folder Content View
            var folderFilter by remember { mutableStateOf(FileFilter.All) }
            val folderFiles = folderGroups[expandedFolder] ?: emptyList()

            val filteredFolderFiles = remember(folderFiles, folderFilter, recentPdfsMap) {
                when (folderFilter) {
                    FileFilter.All -> folderFiles
                    FileFilter.Favorites -> folderFiles.filter { it.isFavorite }
                    FileFilter.Recent -> {
                        folderFiles.filter { recentPdfsMap.containsKey(it.filePath) }
                            .sortedByDescending { recentPdfsMap[it.filePath]?.lastOpened ?: 0L }
                    }
                }
            }

            // Folder Title Header Row with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { expandedFolder = null }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val expandedFolderTitleFontSize = when {
                    (expandedFolder ?: "").length > 30 -> 13.sp
                    (expandedFolder ?: "").length > 20 -> 15.sp
                    else -> 17.sp
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getLocalizedFolderName(expandedFolder ?: "", strings),
                        fontSize = expandedFolderTitleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.fileCountFormat(folderFiles.size),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Pills and Action Controls (Refresh + List/Grid Toggle) - Same as Home
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Pills Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FilterPill(
                        label = strings.allDocs,
                        selected = folderFilter == FileFilter.All,
                        onClick = { folderFilter = FileFilter.All }
                    )
                    FilterPill(
                        label = strings.favorites,
                        selected = folderFilter == FileFilter.Favorites,
                        onClick = { folderFilter = FileFilter.Favorites }
                    )
                    FilterPill(
                        label = strings.recent,
                        selected = folderFilter == FileFilter.Recent,
                        onClick = { folderFilter = FileFilter.Recent }
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Controls Row (Refresh scan + Grid/List Toggle)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Refresh Button
                    IconButton(
                        onClick = {
                            viewModel.scanFiles(context)
                            Toast.makeText(context, strings.processSuccess, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Grid / List Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleGridView(context) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "تبديل المظهر",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredFolderFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد ملفات في هذا التصنيف داخل المجلد",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                if (uiState.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFolderFiles, key = { it.filePath }) { file ->
                            val recentInfo = recentPdfsMap[file.filePath]
                            val progressPercent = if (recentInfo != null && recentInfo.totalPages > 0) {
                                (recentInfo.lastPage.toFloat() / recentInfo.totalPages.toFloat() * 100).toInt().coerceIn(0, 100)
                            } else null
                            val lastOpenedText = recentInfo?.let { formatLastOpened(it.lastOpened) }

                            val isSelected = selectedFilePaths.contains(file.filePath)
                            val isInSelectionMode = selectedFilePaths.isNotEmpty()

                            PdfGridItem(
                                file = file,
                                progressPercent = progressPercent,
                                lastOpenedText = lastOpenedText,
                                isSelected = isSelected,
                                isInSelectionMode = isInSelectionMode,
                                onClick = {
                                    if (isInSelectionMode) {
                                        onToggleSelectFile(file.filePath)
                                    } else {
                                        viewModel.selectPdf(file.filePath, file.fileName)
                                    }
                                },
                                onLongClick = {
                                    if (isInSelectionMode) {
                                        onToggleSelectFile(file.filePath)
                                    } else {
                                        onShowFileDetails(file)
                                    }
                                },
                                onMenuClick = {
                                    onShowFileActions(file)
                                },
                                appLanguage = uiState.appLanguage,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFolderFiles, key = { it.filePath }) { file ->
                            val recentInfo = recentPdfsMap[file.filePath]
                            val progressPercent = if (recentInfo != null && recentInfo.totalPages > 0) {
                                (recentInfo.lastPage.toFloat() / recentInfo.totalPages.toFloat() * 100).toInt().coerceIn(0, 100)
                            } else null
                            val lastOpenedText = recentInfo?.let { formatLastOpened(it.lastOpened) }

                            val isSelected = selectedFilePaths.contains(file.filePath)
                            val isInSelectionMode = selectedFilePaths.isNotEmpty()

                            PdfListItem(
                                file = file,
                                progressPercent = progressPercent,
                                lastOpenedText = lastOpenedText,
                                isSelected = isSelected,
                                isInSelectionMode = isInSelectionMode,
                                onClick = {
                                    if (isInSelectionMode) {
                                        onToggleSelectFile(file.filePath)
                                    } else {
                                        viewModel.selectPdf(file.filePath, file.fileName)
                                    }
                                },
                                onLongClick = {
                                    if (isInSelectionMode) {
                                        onToggleSelectFile(file.filePath)
                                    } else {
                                        onShowFileDetails(file)
                                    }
                                },
                                onMenuClick = {
                                    onShowFileActions(file)
                                },
                                appLanguage = uiState.appLanguage,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderStatsRow(
    pdfCount: Int,
    folderCount: Int,
    appLanguage: String = "ar",
    modifier: Modifier = Modifier
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // PDF Files Stat Card (Dark Navy Blue Translucent Box)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF111C2E))
                .border(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pdfCount.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.pdfFiles,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF1E3A8A).copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = strings.pdfFiles,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Folders Stat Card (Dark Amber/Orange Translucent Box)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF271A12))
                .border(1.dp, Color(0xFF7C2D12).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = folderCount.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.folders,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD1D5DB)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF7C2D12).copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color(0xFFF97316).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = strings.folders,
                        tint = Color(0xFFFB923C),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// TOOLS TAB SCREEN
// ==========================================
enum class ActiveTool {
    None,
    Merge,
    Split,
    Compress,
    Rotate,
    Reorder,
    DeletePages,
    ImageToPdf,
    PdfToImages,
    LockPdf,
    UnlockPdf,
    CameraOcr,
    CloudOcr
}

enum class SettingSheetType {
    APP_THEME,
    APP_LANGUAGE,
    SCROLL_MODE,
    DEFAULT_ZOOM,
    DOUBLE_TAP_ZOOM,
    READING_THEME,
    BRIGHTNESS,
    SCREEN_ORIENTATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsTabScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var activeTool by remember { mutableStateOf(ActiveTool.None) }
    
    // States for wizards
    var targetFileName by remember { mutableStateOf("") }
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSingleFilePath by remember { mutableStateOf("") }
    
    // Tool-specific inputs
    var splitFromPage by remember { mutableStateOf("1") }
    var splitToPage by remember { mutableStateOf("1") }
    var compressionLevel by remember { mutableStateOf("medium") } // "low", "medium", "high"
    var rotateDegrees by remember { mutableStateOf(90) }
    var rotateTargetPage by remember { mutableStateOf("-1") } // "-1" for all, or 1-based page index
    var reorderSequence by remember { mutableStateOf("") } // e.g. "3, 1, 2"
    var deletePagesSetInput by remember { mutableStateOf("") } // e.g. "2, 4"
    
    // New tools inputs
    var selectedImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pdfToImagesFormat by remember { mutableStateOf("PNG") }
    var pdfToImagesPages by remember { mutableStateOf("") }
    var lockPassword by remember { mutableStateOf("") }
    var lockAllowPrinting by remember { mutableStateOf(true) }
    var lockAllowCopying by remember { mutableStateOf(true) }
    var lockAllowModifying by remember { mutableStateOf(true) }
    var lockAllowAnnotations by remember { mutableStateOf(true) }
    var unlockPassword by remember { mutableStateOf("") }
    
    // Cloud OCR inputs
    var ocrLanguage by remember { mutableStateOf("ara") }
    var ocrStatusText by remember { mutableStateOf("جاري رفع الملف...") }
    
    var completedResultFilePath by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showLibraryPickerSheet by remember { mutableStateOf(false) }
    val strings = remember(uiState.appLanguage) { AppStringsProvider.get(uiState.appLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = strings.toolsTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = strings.toolsSubtitle,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // OCR SECTION
            item {
                ToolSectionHeader(title = "OCR")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = strings.cameraOcrTitle,
                        desc = strings.cameraOcrDesc,
                        icon = Icons.Default.DocumentScanner,
                        color = Color(0xFFE8E0F5),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.CameraOcr
                        }
                    )
                    ToolGridCard(
                        title = strings.cloudOcrTitle,
                        desc = strings.cloudOcrDesc,
                        icon = Icons.Default.CloudSync,
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.CloudOcr
                            targetFileName = "Scanned_Document"
                            selectedSingleFilePath = ""
                            ocrLanguage = "ara"
                        }
                    )
                }
            }

            // ORGANIZE SECTION
            item {
                ToolSectionHeader(title = "PDF Tools")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = strings.mergePdfTitle,
                        desc = strings.mergePdfDesc,
                        icon = Icons.Default.MergeType,
                        color = Color(0xFFE6E0FF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Merge
                            targetFileName = "Merged_Document"
                            selectedFilePaths = emptySet()
                        }
                    )
                    ToolGridCard(
                        title = strings.splitPdfTitle,
                        desc = strings.splitPdfDesc,
                        icon = Icons.Default.CallSplit,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Split
                            targetFileName = "Split_Document"
                            selectedSingleFilePath = ""
                            splitFromPage = "1"
                            splitToPage = "1"
                        }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = strings.compressPdfTitle,
                        desc = strings.compressPdfDesc,
                        icon = Icons.Default.Compress,
                        color = Color(0xFFF1EEFF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Compress
                            targetFileName = "Compressed_Document"
                            selectedSingleFilePath = ""
                            compressionLevel = "medium"
                        }
                    )
                    ToolGridCard(
                        title = strings.rotatePdfTitle,
                        desc = strings.rotatePdfDesc,
                        icon = Icons.Default.RotateRight,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Rotate
                            targetFileName = "Rotated_Document"
                            selectedSingleFilePath = ""
                            rotateDegrees = 90
                            rotateTargetPage = "-1"
                        }
                    )
                }
            }

            // ADVANCED EDIT SECTION
            item {
                ToolSectionHeader(title = "إعادة الترتيب والحذف")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = strings.reorderPdfTitle,
                        desc = strings.reorderPdfDesc,
                        icon = Icons.Default.List,
                        color = Color(0xFFE6E0FF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Reorder
                            targetFileName = "Reordered_Document"
                            selectedSingleFilePath = ""
                            reorderSequence = ""
                        }
                    )
                    ToolGridCard(
                        title = strings.deletePdfTitle,
                        desc = strings.deletePdfDesc,
                        icon = Icons.Default.Delete,
                        color = Color(0xFFFFD1D1),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.DeletePages
                            targetFileName = "Modified_Document"
                            selectedSingleFilePath = ""
                            deletePagesSetInput = ""
                        }
                    )
                }
            }

            // CONVERT AND SECURE SECTION
            item {
                ToolSectionHeader(title = "التحويل وتأمين الملفات")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = "صورة إلى PDF",
                        desc = "تحويل مجموعة صور إلى ملف PDF واحد مخصص",
                        icon = Icons.Default.Image,
                        color = Color(0xFFE1F5FE),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.ImageToPdf
                            targetFileName = "صور_محولة"
                            selectedImagePaths = emptyList()
                        }
                    )
                    ToolGridCard(
                        title = "PDF إلى صور",
                        desc = "تصدير صفحات ملف PDF كصور مستقلة",
                        icon = Icons.Default.Collections,
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.PdfToImages
                            selectedSingleFilePath = ""
                            pdfToImagesFormat = "PNG"
                            pdfToImagesPages = ""
                        }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = "قفل ملف PDF",
                        desc = "حماية وتشفير المستند بكلمة سر مخصصة",
                        icon = Icons.Default.Lock,
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.LockPdf
                            selectedSingleFilePath = ""
                            lockPassword = ""
                            lockAllowPrinting = true
                            lockAllowCopying = true
                            lockAllowModifying = true
                            lockAllowAnnotations = true
                            targetFileName = "ملف_محمي"
                        }
                    )
                    ToolGridCard(
                        title = "فتح ملف PDF",
                        desc = "إزالة كلمة السر والحماية من مستند مشفر",
                        icon = Icons.Default.LockOpen,
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.UnlockPdf
                            selectedSingleFilePath = ""
                            unlockPassword = ""
                            targetFileName = "ملف_مفتوح"
                        }
                    )
                }
            }
        }
    }

    // ==========================================
    // FULL-SCREEN PAGES FOR ADVANCED PDF TOOLS
    // ==========================================
    if (activeTool != ActiveTool.None) {
        BackHandler(enabled = true) {
            if (!isProcessing) {
                if (selectedSingleFilePath.isNotEmpty() || selectedFilePaths.isNotEmpty() || selectedImagePaths.isNotEmpty()) {
                    selectedSingleFilePath = ""
                    selectedFilePaths = emptySet()
                    selectedImagePaths = emptyList()
                } else {
                    activeTool = ActiveTool.None
                }
            }
        }

        val toolSinglePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                val fileName = getFileName(context, uri) ?: "ملف_من_النظام.pdf"
                val cachedPath = viewModel.copyUriToCache(context, uri, fileName)
                if (cachedPath != null) {
                    selectedSingleFilePath = cachedPath
                    val cleanName = fileName.replace(".pdf", "", ignoreCase = true).replace("_", " ")
                    targetFileName = when (activeTool) {
                        ActiveTool.Split -> "${cleanName}_مقسم"
                        ActiveTool.Compress -> "${cleanName}_مضغوط"
                        ActiveTool.Rotate -> "${cleanName}_مدور"
                        ActiveTool.Reorder -> "${cleanName}_مرتب"
                        ActiveTool.DeletePages -> "${cleanName}_بعد_الحذف"
                        ActiveTool.PdfToImages -> cleanName
                        ActiveTool.LockPdf -> "${cleanName}_محمي"
                        ActiveTool.UnlockPdf -> "${cleanName}_مفتوح"
                        ActiveTool.CloudOcr -> "${cleanName}_OCR"
                        else -> cleanName
                    }
                    viewModel.scanFiles(context)
                }
            }
        }

        val toolMultiPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val newPaths = mutableListOf<String>()
                uris.forEach { uri ->
                    val fileName = getFileName(context, uri) ?: "ملف_من_النظام.pdf"
                    val cachedPath = viewModel.copyUriToCache(context, uri, fileName)
                    if (cachedPath != null) {
                        newPaths.add(cachedPath)
                    }
                }
                selectedFilePaths = selectedFilePaths + newPaths
                viewModel.scanFiles(context)
            }
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            val paths = uris.mapNotNull { uri ->
                viewModel.copyUriToCache(context, uri, "img_${System.currentTimeMillis()}_${uri.lastPathSegment}.jpg")
            }
            selectedImagePaths = selectedImagePaths + paths
        }

        if (activeTool == ActiveTool.CameraOcr) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                CameraOcrSheet(
                    viewModel = viewModel,
                    state = uiState,
                    onDismiss = { activeTool = ActiveTool.None }
                )
            }
        } else {
            val hasSelectedFile = when (activeTool) {
                ActiveTool.Merge -> selectedFilePaths.isNotEmpty()
                ActiveTool.ImageToPdf -> selectedImagePaths.isNotEmpty()
                else -> selectedSingleFilePath.isNotEmpty()
            }

            if (!hasSelectedFile) {
                val info = when (activeTool) {
                    ActiveTool.Merge -> ToolInfo(strings.mergePdfTitle, strings.mergePdfDesc, Icons.Default.MergeType, Color(0xFFE6E0FF))
                    ActiveTool.Split -> ToolInfo(strings.splitPdfTitle, strings.splitPdfDesc, Icons.Default.CallSplit, Color(0xFFFFF9C4))
                    ActiveTool.Compress -> ToolInfo(strings.compressPdfTitle, strings.compressPdfDesc, Icons.Default.Compress, Color(0xFFF1EEFF))
                    ActiveTool.Rotate -> ToolInfo(strings.rotatePdfTitle, strings.rotatePdfDesc, Icons.Default.RotateRight, Color(0xFFFFF9C4))
                    ActiveTool.Reorder -> ToolInfo(strings.reorderPdfTitle, strings.reorderPdfDesc, Icons.Default.List, Color(0xFFE6E0FF))
                    ActiveTool.DeletePages -> ToolInfo(strings.deletePdfTitle, strings.deletePdfDesc, Icons.Default.Delete, Color(0xFFFFD1D1))
                    ActiveTool.ImageToPdf -> ToolInfo(strings.importImages, strings.toolsSubtitle, Icons.Default.Image, Color(0xFFE1F5FE))
                    ActiveTool.PdfToImages -> ToolInfo(strings.exportImage, strings.toolsSubtitle, Icons.Default.Collections, Color(0xFFE8F5E9))
                    ActiveTool.LockPdf -> ToolInfo(strings.lockPdfTitle, strings.lockPdfDesc, Icons.Default.Lock, Color(0xFFFFEBEE))
                    ActiveTool.UnlockPdf -> ToolInfo(strings.unlockPdfTitle, strings.unlockPdfDesc, Icons.Default.LockOpen, Color(0xFFFFF3E0))
                    ActiveTool.CloudOcr -> ToolInfo(strings.cloudOcrTitle, strings.cloudOcrDesc, Icons.Default.CloudSync, Color(0xFFE3F2FD))
                    else -> ToolInfo("", "", Icons.Default.Build, Color.Gray)
                }

                ToolIntroScreen(
                    title = info.title,
                    description = info.subtitle,
                    icon = info.icon,
                    accentColor = info.color,
                    buttonText = if (activeTool == ActiveTool.Merge) strings.importFiles else if (activeTool == ActiveTool.ImageToPdf) strings.importImages else strings.selectFile,
                    onSelectSaf = {
                        when (activeTool) {
                            ActiveTool.Merge -> toolMultiPickerLauncher.launch(arrayOf("application/pdf"))
                            ActiveTool.ImageToPdf -> imagePickerLauncher.launch("image/*")
                            else -> toolSinglePickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    },
                    onSelectFromLibrary = if (activeTool != ActiveTool.ImageToPdf) {
                        { showLibraryPickerSheet = true }
                    } else null,
                    onBack = { activeTool = ActiveTool.None },
                    appLanguage = uiState.appLanguage
                )
            } else {
                val toolInfo = when (activeTool) {
                    ActiveTool.Merge -> Pair(strings.mergePdfTitle, strings.mergePdfDesc)
                    ActiveTool.Split -> Pair(strings.splitPdfTitle, strings.splitPdfDesc)
                    ActiveTool.Compress -> Pair(strings.compressPdfTitle, strings.compressPdfDesc)
                    ActiveTool.Rotate -> Pair(strings.rotatePdfTitle, strings.rotatePdfDesc)
                    ActiveTool.Reorder -> Pair(strings.reorderPdfTitle, strings.reorderPdfDesc)
                    ActiveTool.DeletePages -> Pair(strings.deletePdfTitle, strings.deletePdfDesc)
                    ActiveTool.ImageToPdf -> Pair(strings.importImages, strings.toolsSubtitle)
                    ActiveTool.PdfToImages -> Pair(strings.exportImage, strings.toolsSubtitle)
                    ActiveTool.LockPdf -> Pair(strings.lockPdfTitle, strings.lockPdfDesc)
                    ActiveTool.UnlockPdf -> Pair(strings.unlockPdfTitle, strings.unlockPdfDesc)
                    ActiveTool.CloudOcr -> Pair(strings.cloudOcrTitle, strings.cloudOcrDesc)
                    else -> Pair("", "")
                }
                val toolTitle = toolInfo.first
                val toolSubtitle = toolInfo.second

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top App Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                IconButton(onClick = {
                                    if (!isProcessing) {
                                        selectedSingleFilePath = ""
                                        selectedFilePaths = emptySet()
                                        selectedImagePaths = emptyList()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = toolTitle,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = toolSubtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Content Area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (activeTool) {
                                ActiveTool.Merge -> {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("الملفات المحددة للدمج (${selectedFilePaths.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { toolMultiPickerLauncher.launch(arrayOf("application/pdf")) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("إضافة المزيد من الملفات")
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            selectedFilePaths.forEachIndexed { idx, path ->
                                                val file = File(path)
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(12.dp)
                                                    ) {
                                                        Text("${idx + 1}.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(file.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        IconButton(
                                                            onClick = { selectedFilePaths = selectedFilePaths - path },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات التصدير والاسم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.Split -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات التقسيم والنطاق", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedTextField(
                                                    value = splitFromPage,
                                                    onValueChange = { splitFromPage = it },
                                                    label = { Text("من صفحة") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = splitToPage,
                                                    onValueChange = { splitToPage = it },
                                                    label = { Text("إلى صفحة") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                ActiveTool.Compress -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات الضغط والاسم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("مستوى الضغط المطلوب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            val levels = listOf(
                                                "low" to "منخفض (جودة عالية)",
                                                "medium" to "متوسط (أفضل توازن - موصى به)",
                                                "high" to "عالي (أصغر حجم ملف)"
                                            )

                                            levels.forEach { (levelKey, levelLabel) ->
                                                Surface(
                                                    onClick = { compressionLevel = levelKey },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (compressionLevel == levelKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                                        RadioButton(selected = compressionLevel == levelKey, onClick = { compressionLevel = levelKey })
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(levelLabel, fontSize = 13.sp, fontWeight = if (compressionLevel == levelKey) FontWeight.Bold else FontWeight.Normal)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                ActiveTool.Rotate -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات التدوير", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("درجة التدوير المطلوبة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                listOf(90 to "90° مع العقارب", 180 to "180° رأساً", 270 to "270° عكس العقارب").forEach { (deg, label) ->
                                                    FilterChip(
                                                        selected = rotateDegrees == deg,
                                                        onClick = { rotateDegrees = deg },
                                                        label = { Text(label, fontSize = 11.sp) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = rotateTargetPage,
                                                onValueChange = { rotateTargetPage = it },
                                                label = { Text("الصفحة المستهدفة (-1 لجميع الصفحات)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.Reorder -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات الترتيب والاسم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("ترتيب الصفحات الجديد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("أدخل أرقام الصفحات بالترتيب الجديد مفصولة بفواصل:", fontSize = 11.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = reorderSequence,
                                                onValueChange = { reorderSequence = it },
                                                placeholder = { Text("مثال: 3, 1, 2, 4") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.DeletePages -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات الحذف والاسم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("أرقام الصفحات المراد حذفها", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("أدخل الصفحات المراد حذفها مفصولة بفواصل:", fontSize = 11.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = deletePagesSetInput,
                                                onValueChange = { deletePagesSetInput = it },
                                                placeholder = { Text("مثال: 2, 4") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.ImageToPdf -> {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("الصور المحددة (${selectedImagePaths.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = { imagePickerLauncher.launch("image/*") },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("إضافة صور من المعرض")
                                            }

                                            if (selectedImagePaths.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                selectedImagePaths.forEachIndexed { index, path ->
                                                    val file = File(path)
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(8.dp)
                                                        ) {
                                                            Text("${index + 1}. ${file.name}", fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                                            IconButton(
                                                                onClick = {
                                                                    selectedImagePaths = selectedImagePaths.toMutableList().apply { removeAt(index) }
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات الملف الناتج", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم ملف الـ PDF الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.PdfToImages -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("صيغة الصور الناتجة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pdfToImagesFormat = "PNG" }) {
                                                    RadioButton(selected = pdfToImagesFormat == "PNG", onClick = { pdfToImagesFormat = "PNG" })
                                                    Text("PNG (جودة عالية)", fontSize = 13.sp)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pdfToImagesFormat = "JPG" }) {
                                                    RadioButton(selected = pdfToImagesFormat == "JPG", onClick = { pdfToImagesFormat = "JPG" })
                                                    Text("JPG (حجم مدمج)", fontSize = 13.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = pdfToImagesPages,
                                                onValueChange = { pdfToImagesPages = it },
                                                label = { Text("تصدير صفحات معينة (اختياري)") },
                                                placeholder = { Text("مثال: 1, 2, 5-8 (اتركه فارغاً للكل)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.LockPdf -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات التشفير والكلمة السرية", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = lockPassword,
                                                onValueChange = { lockPassword = it },
                                                label = { Text("كلمة سر فتح الملف") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("خيارات الصلاحيات", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowPrinting = !lockAllowPrinting }) {
                                                Checkbox(checked = lockAllowPrinting, onCheckedChange = { lockAllowPrinting = it })
                                                Text("السماح بطباعة الملف", fontSize = 13.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowCopying = !lockAllowCopying }) {
                                                Checkbox(checked = lockAllowCopying, onCheckedChange = { lockAllowCopying = it })
                                                Text("السماح بنسخ النصوص والمحتوى", fontSize = 13.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowModifying = !lockAllowModifying }) {
                                                Checkbox(checked = lockAllowModifying, onCheckedChange = { lockAllowModifying = it })
                                                Text("السماح بتعديل صفحات الملف", fontSize = 13.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowAnnotations = !lockAllowAnnotations }) {
                                                Checkbox(checked = lockAllowAnnotations, onCheckedChange = { lockAllowAnnotations = it })
                                                Text("السماح بإضافة تعليقات وشروح", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                ActiveTool.UnlockPdf -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات فك التشفير", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = unlockPassword,
                                                onValueChange = { unlockPassword = it },
                                                label = { Text("كلمة السر الحالية لفتح الملف") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                ActiveTool.CloudOcr -> {
                                    ToolSelectedFileCard(
                                        filePath = selectedSingleFilePath,
                                        allPdfFiles = uiState.allPdfFiles,
                                        onChangeFile = { showLibraryPickerSheet = true },
                                        onPreviewFile = { path, name -> viewModel.selectPdf(path, name) }
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("إعدادات التصدير واللغة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = targetFileName,
                                                onValueChange = { targetFileName = it },
                                                label = { Text("اسم الملف الناتج") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text("لغة التعرف الضوئي (OCR):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val globalLangs = listOf(
                                                    "ara" to "العربية (الرئيسية)",
                                                    "auto" to "كشف تلقائي (كل اللغات)",
                                                    "eng" to "English (إنجليزية)",
                                                    "fra" to "Français (فرنسية)",
                                                    "deu" to "Deutsch (ألمانية)",
                                                    "spa" to "Español (إسبانية)",
                                                    "ita" to "Italiano (إيطالية)",
                                                    "tur" to "Türkçe (تركية)",
                                                    "rus" to "Русский (روسية)",
                                                    "zho" to "中文 (صينية)",
                                                    "jpn" to "日本語 (يابانية)",
                                                    "kor" to "한국어 (كورية)",
                                                    "hin" to "हिन्दी (هندية)",
                                                    "fas" to "فارسی (فارسية)",
                                                    "urd" to "اردو (أوردية)",
                                                    "por" to "Português (برتغالية)",
                                                    "ind" to "Bahasa (إندونيسية)",
                                                    "nld" to "Nederlands (هولندية)",
                                                    "pol" to "Polski (بولندية)",
                                                    "swe" to "Svenska (سويدية)",
                                                    "ell" to "Ελληνικά (يونانية)",
                                                    "heb" to "עברית (عبرية)"
                                                )
                                                items(globalLangs) { (code, name) ->
                                                    val isSelected = ocrLanguage == code
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { ocrLanguage = code },
                                                        label = { Text(name, fontSize = 12.sp) },
                                                        leadingIcon = if (isSelected) {
                                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                        } else null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {}
                            }

                        if (isProcessing) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        if (activeTool == ActiveTool.CloudOcr) ocrStatusText else "جاري معالجة وتطبيق العملية على المستند...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    when (activeTool) {
                                        ActiveTool.Merge -> {
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم الملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (selectedFilePaths.size < 2) {
                                                Toast.makeText(context, "الرجاء اختيار ملفين على الأقل للدمج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.mergePdfs(
                                                context = context,
                                                filePaths = selectedFilePaths.toList(),
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.Split -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val from = splitFromPage.toIntOrNull() ?: 1
                                            val to = splitToPage.toIntOrNull() ?: 1
                                            isProcessing = true
                                            viewModel.splitPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                fromPage = from,
                                                toPage = to,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.Compress -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.compressPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                qualityLevel = compressionLevel,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.Rotate -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val targetPage = rotateTargetPage.toIntOrNull() ?: -1
                                            isProcessing = true
                                            viewModel.rotatePdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                degrees = rotateDegrees,
                                                targetPage = targetPage,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.Reorder -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (reorderSequence.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال ترتيب الصفحات الجديد", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val orderList = reorderSequence.split(",").mapNotNull { it.trim().toIntOrNull() }
                                            if (orderList.isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال أرقام صفحات صحيحة (مثال: 3, 1, 2)", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.reorderPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                pageOrderList = orderList,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.DeletePages -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (deletePagesSetInput.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال أرقام الصفحات المراد حذفها", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val pagesSet = deletePagesSetInput.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                                            if (pagesSet.isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال أرقام صفحات صحيحة (مثال: 2, 4)", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.deletePagesPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                pagesToDelete = pagesSet,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.ImageToPdf -> {
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم الملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (selectedImagePaths.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار صورة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.imageToPdf(
                                                context = context,
                                                imagePaths = selectedImagePaths,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.PdfToImages -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.pdfToImages(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                format = pdfToImagesFormat,
                                                customPagesStr = pdfToImagesPages,
                                                onSuccess = { paths ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    Toast.makeText(context, "تم تصدير ${paths.size} صور بنجاح وحفظها في المعرض!", Toast.LENGTH_LONG).show()
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.LockPdf -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (lockPassword.isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال كلمة سر لقفل الملف", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.lockPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                userPassword = lockPassword,
                                                allowPrinting = lockAllowPrinting,
                                                allowCopying = lockAllowCopying,
                                                allowModifying = lockAllowModifying,
                                                allowAnnotations = lockAllowAnnotations,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.UnlockPdf -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (unlockPassword.isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال كلمة السر الحالية لإلغاء الحماية", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            viewModel.unlockPdf(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                password = unlockPassword,
                                                targetName = targetFileName,
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        ActiveTool.CloudOcr -> {
                                            if (selectedSingleFilePath.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اختيار ملف PDF أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (targetFileName.trim().isEmpty()) {
                                                Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isProcessing = true
                                            ocrStatusText = strings.extractingMetadata
                                            viewModel.convertPdfCloudOcr(
                                                context = context,
                                                filePath = selectedSingleFilePath,
                                                language = ocrLanguage,
                                                targetName = targetFileName,
                                                onStatusChange = { status ->
                                                    ocrStatusText = status
                                                },
                                                onSuccess = { path ->
                                                    isProcessing = false
                                                    activeTool = ActiveTool.None
                                                    completedResultFilePath = path
                                                    viewModel.scanFiles(context)
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }

                                        else -> {}
                                    }
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                val buttonText = when (activeTool) {
                                    ActiveTool.Merge -> strings.mergePdfTitle
                                    ActiveTool.Split -> strings.splitPdfTitle
                                    ActiveTool.Compress -> strings.compressPdfTitle
                                    ActiveTool.Rotate -> strings.rotatePdfTitle
                                    ActiveTool.Reorder -> strings.reorderPdfTitle
                                    ActiveTool.DeletePages -> strings.deletePdfTitle
                                    ActiveTool.ImageToPdf -> strings.importImages
                                    ActiveTool.PdfToImages -> strings.exportImage
                                    ActiveTool.LockPdf -> strings.lockPdfTitle
                                    ActiveTool.UnlockPdf -> strings.unlockPdfTitle
                                    ActiveTool.CloudOcr -> strings.cloudOcrTitle
                                    else -> strings.confirm
                                }
                                Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (completedResultFilePath != null) {
        ToolResultDialog(
            filePath = completedResultFilePath!!,
            onDismiss = { completedResultFilePath = null },
            onView = { path, name ->
                completedResultFilePath = null
                viewModel.selectPdf(path, name)
            },
            onShare = { path ->
                sharePdfFile(context, path)
            },
            appLanguage = uiState.appLanguage
        )
    }

    if (showLibraryPickerSheet) {
        LibraryPdfPickerSheet(
            allPdfFiles = uiState.allPdfFiles,
            appLanguage = uiState.appLanguage,
            onFileSelected = { path, name ->
                showLibraryPickerSheet = false
                val cleanName = name.replace(".pdf", "", ignoreCase = true).replace("_", " ")
                when (activeTool) {
                    ActiveTool.Merge -> {
                        selectedFilePaths = selectedFilePaths + path
                    }
                    ActiveTool.Split -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_split"
                    }
                    ActiveTool.Compress -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_compressed"
                    }
                    ActiveTool.Rotate -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_rotated"
                    }
                    ActiveTool.Reorder -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_reordered"
                    }
                    ActiveTool.DeletePages -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_modified"
                    }
                    ActiveTool.PdfToImages -> {
                        selectedSingleFilePath = path
                        targetFileName = cleanName
                    }
                    ActiveTool.LockPdf -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_protected"
                    }
                    ActiveTool.UnlockPdf -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_unlocked"
                    }
                    ActiveTool.CloudOcr -> {
                        selectedSingleFilePath = path
                        targetFileName = "${cleanName}_OCR"
                    }
                    else -> {
                        selectedSingleFilePath = path
                    }
                }
            },
            onDismiss = { showLibraryPickerSheet = false }
        )
    }
}

@Composable
fun ToolSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun ToolGridCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val finalBgColor = if (isDarkTheme) {
        when (color) {
            Color(0xFFE6E0FF) -> Color(0xFF231F3A)
            Color(0xFFFFF9C4) -> Color(0xFF332D15)
            Color(0xFFF1EEFF) -> Color(0xFF1E1A33)
            else -> Color(0xFF2A283E)
        }
    } else {
        color
    }
    
    val finalIconTint = if (isDarkTheme) {
        when (color) {
            Color(0xFFFFF9C4) -> Color(0xFFFFD54F) // YellowAccent
            else -> Color(0xFFB19DFF) // LavenderSecondary
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .height(135.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(finalBgColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = finalIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

// ==========================================
// SETTINGS TAB SCREEN (Image 2)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    viewModel: PdfViewModel,
    uiState: PdfUiState
) {
    val context = LocalContext.current
    var activeSheet by remember { mutableStateOf<SettingSheetType?>(null) }
    val strings = remember(uiState.appLanguage) { AppStringsProvider.get(uiState.appLanguage) }

    // Surface colors for PDF Reader Pro dark aesthetic
    val cardBg = MaterialTheme.colorScheme.surface
    val headerColor = Color(0xFF9E86FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header Branding Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FinalPDF Pro",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v2.2.0 - FinalPDF Engine",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // APPEARANCE SECTION
            item {
                SettingsSectionHeader(title = strings.appearanceSection, headerColor = headerColor)
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // App Theme Row
                        SettingsItemRow(
                            icon = Icons.Default.Brightness4,
                            iconBgColor = Color(0xFF9C27B0),
                            title = strings.appThemeTitle,
                            value = when (uiState.appTheme) {
                                "dark" -> "Dark"
                                "light" -> "Light"
                                else -> "Auto (System)"
                            },
                            onClick = { activeSheet = SettingSheetType.APP_THEME }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // App Language Row
                        SettingsItemRow(
                            icon = Icons.Default.Language,
                            iconBgColor = Color(0xFFE91E63),
                            title = strings.appLanguageTitle,
                            value = when (uiState.appLanguage) {
                                "en" -> "English"
                                "fr" -> "Français"
                                "de" -> "Deutsch"
                                "es" -> "Español"
                                "tr" -> "Türkçe"
                                "ru" -> "Русский"
                                "zh" -> "中文"
                                "ja" -> "日本語"
                                "ko" -> "한국어"
                                "auto" -> "Auto Multi-language"
                                else -> "العربية - Arabic"
                            },
                            onClick = { activeSheet = SettingSheetType.APP_LANGUAGE }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Show Tools Tab Switch
                        SettingsSwitchRow(
                            icon = Icons.Default.Grid3x3,
                            iconBgColor = Color(0xFF4CAF50),
                            title = strings.showToolsTabTitle,
                            subtitle = strings.showToolsTabSubtitle,
                            checked = uiState.showToolsTab,
                            onCheckedChange = { viewModel.setShowToolsTab(context, it) }
                        )
                    }
                }
            }

            // READER & DISPLAY SECTION
            item {
                SettingsSectionHeader(title = strings.readerDisplaySection, headerColor = headerColor)
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Scroll Mode Row
                        SettingsItemRow(
                            icon = Icons.Default.SwapVert,
                            iconBgColor = Color(0xFF2196F3),
                            title = strings.scrollDirectionTitle,
                            value = if (uiState.scrollMode == "horizontal") "Horizontal" else "Vertical",
                            onClick = { activeSheet = SettingSheetType.SCROLL_MODE }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Default Zoom Row
                        SettingsItemRow(
                            icon = Icons.Default.ZoomIn,
                            iconBgColor = Color(0xFF00BCD4),
                            title = strings.defaultZoomTitle,
                            value = when (uiState.defaultZoom) {
                                "page-fit" -> "Fit Page"
                                "1.0" -> "Actual Size (100%)"
                                else -> "Fit Width"
                            },
                            onClick = { activeSheet = SettingSheetType.DEFAULT_ZOOM }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Double Tap Zoom Row
                        SettingsItemRow(
                            icon = Icons.Default.TouchApp,
                            iconBgColor = Color(0xFF009688),
                            title = strings.doubleTapZoomTitle,
                            value = String.format("%.1fx", uiState.doubleTapZoomFactor),
                            onClick = { activeSheet = SettingSheetType.DOUBLE_TAP_ZOOM }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Reading Theme Row
                        SettingsItemRow(
                            icon = Icons.Default.ColorLens,
                            iconBgColor = Color(0xFF3F51B5),
                            title = strings.readingThemeTitle,
                            value = when (uiState.readingTheme) {
                                "dark" -> "Dark Gray"
                                "black" -> "AMOLED Black"
                                "sepia" -> "Sepia"
                                else -> "Light"
                            },
                            onClick = { activeSheet = SettingSheetType.READING_THEME }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Brightness Row
                        SettingsItemRow(
                            icon = Icons.Default.Brightness6,
                            iconBgColor = Color(0xFFFF9800),
                            title = strings.readerDisplaySection,
                            value = if (uiState.isSystemBrightness) strings.autoSystem else "${(uiState.customBrightness * 100).toInt()}%",
                            onClick = { activeSheet = SettingSheetType.BRIGHTNESS }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Screen Orientation Row
                        SettingsItemRow(
                            icon = Icons.Default.ScreenRotation,
                            iconBgColor = Color(0xFFE91E63),
                            title = strings.scrollDirectionTitle,
                            value = when (uiState.screenOrientation) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> strings.portraitMode
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> strings.landscapeMode
                                else -> strings.autoSystem
                            },
                            onClick = { activeSheet = SettingSheetType.SCREEN_ORIENTATION }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // Snap to Page Switch
                        SettingsSwitchRow(
                            icon = Icons.Default.LibraryAddCheck,
                            iconBgColor = Color(0xFFFF5722),
                            title = strings.scrollDirectionTitle,
                            subtitle = strings.scrollDirectionTitle,
                            checked = uiState.snapToPage,
                            onCheckedChange = { viewModel.setSnapToPage(it) }
                        )
                    }
                }
            }

            // APP COLOR THEME CUSTOMIZATION SECTION
            item {
                SettingsSectionHeader(title = strings.appearanceSection, headerColor = headerColor)
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.appThemeTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(BottomBarPresets) { index, preset ->
                                val isSelected = uiState.bottomBarColorIndex == index
                                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                val previewBg = if (index == 0) {
                                    if (isDarkTheme) Color(0xFF1C1B26) else Color.White
                                } else {
                                    if (isDarkTheme) preset.darkBg else preset.lightBg
                                }
                                val previewAccent = if (index == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    if (isDarkTheme) preset.darkOnSelected else preset.lightOnSelected
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(previewBg)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setBottomBarColorIndex(context, index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (index == 0) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = previewAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(previewAccent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // GENERAL UTILITIES
            item {
                SettingsSectionHeader(title = "SYSTEM", headerColor = headerColor)
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsItemRow(
                            icon = Icons.Default.DeleteSweep,
                            iconBgColor = Color(0xFF607D8B),
                            title = strings.deleteFile,
                            value = strings.deleteFileDesc,
                            onClick = {
                                viewModel.clearHistory()
                                Toast.makeText(context, strings.processSuccess, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Active Bottom Sheet Dialog Handler
    activeSheet?.let { sheet ->
        AppBottomSheet(
            onDismiss = { activeSheet = null },
            title = when (sheet) {
                SettingSheetType.APP_THEME -> strings.appThemeTitle
                SettingSheetType.APP_LANGUAGE -> strings.appLanguageTitle
                SettingSheetType.SCROLL_MODE -> strings.scrollDirectionTitle
                SettingSheetType.DEFAULT_ZOOM -> strings.defaultZoomTitle
                SettingSheetType.DOUBLE_TAP_ZOOM -> strings.doubleTapZoomTitle
                SettingSheetType.READING_THEME -> strings.readingThemeTitle
                SettingSheetType.BRIGHTNESS -> strings.readerDisplaySection
                SettingSheetType.SCREEN_ORIENTATION -> strings.scrollDirectionTitle
            }
        ) {
            when (sheet) {
                SettingSheetType.APP_THEME -> {
                    val themes = listOf(
                        "system" to strings.autoSystem,
                        "light" to strings.themeLight,
                        "dark" to strings.themeDark
                    )
                    themes.forEach { (key, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.appTheme == key,
                            onClick = {
                                viewModel.setAppTheme(context, key)
                                activeSheet = null
                            }
                        )
                    }
                }

                SettingSheetType.APP_LANGUAGE -> {
                    val languages = listOf(
                        "ar" to "العربية (Arabic)",
                        "auto" to strings.autoSystem,
                        "en" to "English",
                        "fr" to "Français (French)",
                        "de" to "Deutsch (German)",
                        "es" to "Español (Spanish)",
                        "tr" to "Türkçe (Turkish)",
                        "ru" to "Русский (Russian)",
                        "zh" to "中文 (Chinese)",
                        "ja" to "日本語 (Japanese)",
                        "ko" to "한국어 (Korean)"
                    )
                    languages.forEach { (key, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.appLanguage == key,
                            onClick = {
                                viewModel.setAppLanguage(context, key)
                                activeSheet = null
                            }
                        )
                    }
                }

                SettingSheetType.SCROLL_MODE -> {
                    val modes = listOf(
                        "vertical" to strings.verticalScroll,
                        "horizontal" to strings.horizontalScroll
                    )
                    modes.forEach { (key, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.scrollMode == key,
                            onClick = {
                                viewModel.setScrollMode(key)
                                activeSheet = null
                            }
                        )
                    }
                }

                SettingSheetType.DEFAULT_ZOOM -> {
                    val zooms = listOf(
                        "page-width" to strings.fitWidth,
                        "page-fit" to strings.fitPage,
                        "1.0" to strings.actualSize
                    )
                    zooms.forEach { (key, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.defaultZoom == key,
                            onClick = {
                                viewModel.setDefaultZoom(key)
                                activeSheet = null
                            }
                        )
                    }
                }

                SettingSheetType.DOUBLE_TAP_ZOOM -> {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "${strings.doubleTapZoomTitle}: ${String.format(Locale.US, "%.1fx", uiState.doubleTapZoomFactor)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        AppSlider(
                            value = uiState.doubleTapZoomFactor,
                            onValueChange = { viewModel.setDoubleTapZoomFactor(it) },
                            valueRange = 1.1f..5.0f,
                            steps = 38
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1.5f, 2.0f, 3.0f, 4.0f).forEach { factor ->
                                FilterChip(
                                    selected = (uiState.doubleTapZoomFactor - factor).let { Math.abs(it) < 0.05f },
                                    onClick = { viewModel.setDoubleTapZoomFactor(factor) },
                                    label = { Text(text = "${factor}x") }
                                )
                            }
                        }
                    }
                }

                SettingSheetType.READING_THEME -> {
                    val themes = listOf(
                        "light" to strings.themeLight,
                        "sepia" to strings.themeSepia,
                        "dark" to strings.themeDark,
                        "black" to strings.themeAmoled
                    )
                    themes.forEach { (key, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.readingTheme == key,
                            onClick = {
                                viewModel.setReadingTheme(key)
                                activeSheet = null
                            }
                        )
                    }
                }

                SettingSheetType.BRIGHTNESS -> {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSystemBrightness(!uiState.isSystemBrightness)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.useSystemBrightness,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = uiState.isSystemBrightness,
                                onCheckedChange = { viewModel.setSystemBrightness(it) }
                            )
                        }

                        if (!uiState.isSystemBrightness) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "${strings.readerDisplaySection}: ${(uiState.customBrightness * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AppSlider(
                                value = uiState.customBrightness,
                                onValueChange = { viewModel.setCustomBrightness(it) },
                                valueRange = 0.05f..1.0f
                            )
                        }
                    }
                }

                SettingSheetType.SCREEN_ORIENTATION -> {
                    val orientations = listOf(
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED to strings.autoSystem,
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to strings.portraitMode,
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to strings.landscapeMode
                    )
                    orientations.forEach { (orientationVal, label) ->
                        SettingRadioOptionRow(
                            label = label,
                            isSelected = uiState.screenOrientation == orientationVal,
                            onClick = {
                                viewModel.setScreenOrientation(orientationVal)
                                activeSheet = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, headerColor: Color = Color.Gray) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = headerColor,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconBgColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconBgColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconBgColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconBgColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingRadioOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "مُختار",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SettingsSelectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// CUSTOM BOTTOM BAR (Image 5 & 2 Style)
// ==========================================
@Composable
fun HomeSmileIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        
        // Define points for the house outline
        val left = strokeWidth / 2f + 1.dp.toPx()
        val right = w - strokeWidth / 2f - 1.dp.toPx()
        val top = strokeWidth / 2f + 2.dp.toPx()
        val bottom = h - strokeWidth / 2f - 1.dp.toPx()
        val midY = h * 0.45f
        val centerX = w / 2f
        
        val path = Path().apply {
            moveTo(left, bottom)
            lineTo(left, midY)
            lineTo(centerX, top)
            lineTo(right, midY)
            lineTo(right, bottom)
            close()
        }
        
        // Draw the house outline with rounded joints and caps
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
        
        // Draw the smile arc centered horizontally in the lower half of the house
        val smileWidth = w * 0.42f
        val smileHeight = h * 0.22f
        val smileLeft = centerX - smileWidth / 2f
        val smileTop = h * 0.52f
        
        drawArc(
            color = tint,
            startAngle = 10f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(smileLeft, smileTop),
            size = Size(smileWidth, smileHeight),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun CustomBottomBar(
    selectedTab: DashboardTab,
    showTools: Boolean,
    bottomBarColorIndex: Int,
    appLanguage: String = "ar",
    onTabSelected: (DashboardTab) -> Unit
) {
    DashboardAnimatedBottomNavBar(
        selectedTab = selectedTab,
        showTools = showTools,
        bottomBarColorIndex = bottomBarColorIndex,
        appLanguage = appLanguage,
        onTabSelected = onTabSelected
    )
}

@Composable
fun BottomTabItem(
    selected: Boolean,
    onSelectedColor: Color,
    unselectedColor: Color,
    selectedContainerColor: Color,
    onClick: () -> Unit,
    label: String,
    icon: @Composable (tint: Color) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("tab_${label}"),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (selected) onSelectedColor else unselectedColor
        // Smooth scaling bubble selection accent
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .background(
                    if (selected) selectedContainerColor else Color.Transparent,
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
    }
}

// Utility to get filename from Uri
fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

@Composable
fun StorageStatusCard(storageInfo: StorageInfo, appLanguage: String = "ar") {
    if (storageInfo.totalGb <= 0f) return // Only show if storage statistics could be fetched
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = strings.internalStorage,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.internalStorage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f GB / %.0f GB", storageInfo.usedGb, storageInfo.totalGb)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { storageInfo.usedPercentage / 100f },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${storageInfo.usedPercentage}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f GB", storageInfo.availableGb)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// FILE ACTION SHEET & DIALOGS
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    file: LocalPdfFile,
    isFavorite: Boolean,
    onToggleFav: () -> Unit,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onFileInfo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String = "ar"
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    val pageCount = remember(file.filePath) { getPdfPageCount(file.filePath) }
    
    AppBottomSheet(
        onDismiss = onDismiss
    ) {
                // Header of Bottom Sheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pageText = if (pageCount > 0) "${strings.fileCountFormat(pageCount)} • " else ""
                        Text(
                            text = "$pageText${file.fileSize}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // PDF Icon on the right
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section Title
                Text(
                    text = strings.quickActions,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                
                // Action: Add to Favorites / Save to your favorites for quick access
                ActionRowItem(
                    title = if (isFavorite) strings.removeFromFav else strings.addToFav,
                    desc = if (isFavorite) strings.removeFromFavDesc else strings.addToFavDesc,
                    icon = Icons.Default.Star,
                    iconTint = Color(0xFFFFB300),
                    bgTint = Color(0xFFFFB300).copy(alpha = 0.1f),
                    onClick = {
                        onToggleFav()
                        onDismiss()
                    }
                )
                
                // Action: Select (Enter multi-selection mode)
                ActionRowItem(
                    title = strings.selectFile,
                    desc = strings.selectFileDesc,
                    icon = Icons.Default.CheckCircle,
                    iconTint = Color(0xFF1E88E5),
                    bgTint = Color(0xFF1E88E5).copy(alpha = 0.1f),
                    onClick = {
                        onSelect()
                        onDismiss()
                    }
                )
                
                // Action: Share
                ActionRowItem(
                    title = strings.shareFile,
                    desc = strings.shareFileDesc,
                    icon = Icons.Default.Share,
                    iconTint = Color(0xFF43A047),
                    bgTint = Color(0xFF43A047).copy(alpha = 0.1f),
                    onClick = {
                        onShare()
                        onDismiss()
                    }
                )
                
                // Action: Rename
                ActionRowItem(
                    title = strings.renameFile,
                    desc = strings.renameFileDesc,
                    icon = Icons.Default.Edit,
                    iconTint = Color(0xFF8E24AA),
                    bgTint = Color(0xFF8E24AA).copy(alpha = 0.1f),
                    onClick = {
                        onRename()
                        onDismiss()
                    }
                )
                
                // Action: File Info
                ActionRowItem(
                    title = strings.fileInfo,
                    desc = strings.fileInfoDesc,
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF00ACC1),
                    bgTint = Color(0xFF00ACC1).copy(alpha = 0.1f),
                    onClick = {
                        onFileInfo()
                        onDismiss()
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action: Delete
                DeleteActionBanner(
                    title = strings.deleteFile,
                    desc = strings.deleteFileDesc,
                    icon = Icons.Default.Delete,
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
    }
}

@Composable
fun ActionRowItem(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    bgTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Icon container on the right
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(bgTint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DeleteActionBanner(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFEBEE), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828)
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color(0xFFE53935)
            )
        }
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFFFFCDD2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFC62828),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
        Text(text = value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun getPdfPageCount(filePath: String): Int {
    return try {
        val file = File(filePath)
        if (!file.exists()) return 0
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
        val count = renderer.pageCount
        renderer.close()
        parcelFileDescriptor.close()
        count
    } catch (e: Exception) {
        0
    }
}

fun shareMultiplePdfs(context: Context, filePaths: List<String>) {
    try {
        val uris = java.util.ArrayList<Uri>()
        for (filePath in filePaths) {
            val file = File(filePath)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }
        }
        if (uris.isEmpty()) return
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملفات عبر:"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun ToolFilePickerCard(
    title: String,
    selectedFilePath: String,
    allPdfFiles: List<LocalPdfFile>,
    onFileSelected: (filePath: String, fileName: String) -> Unit,
    onOpenSafPicker: () -> Unit,
    appLanguage: String = "ar"
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    var showLibraryPicker by remember { mutableStateOf(false) }
    val selectedFile = allPdfFiles.find { it.filePath == selectedFilePath }
    val displayName = if (selectedFile != null) selectedFile.fileName else if (selectedFilePath.isNotEmpty()) File(selectedFilePath).name else null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))

            if (displayName != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedFile != null) {
                                Text(
                                    text = "${selectedFile.fileSize} • ${selectedFile.folderName}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Text(
                                    text = strings.selectFile,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Text(
                    text = strings.noPdfSelected,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenSafPicker,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.selectFile, fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showLibraryPicker = !showLibraryPicker },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${strings.selectFromLibrary} (${allPdfFiles.size})", fontSize = 11.sp, maxLines = 1)
                }
            }

            if (showLibraryPicker) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.selectFromLibrary, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                
                if (allPdfFiles.isEmpty()) {
                    Text(strings.noPdfFound, fontSize = 12.sp, color = Color.Gray)
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        allPdfFiles.forEach { file ->
                            val isSelected = file.filePath == selectedFilePath
                            Surface(
                                onClick = {
                                    onFileSelected(file.filePath, file.fileName)
                                    showLibraryPicker = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = file.fileName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
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

fun sharePdfFile(context: Context, filePath: String) {
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
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملف عبر:"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun ToolResultDialog(
    filePath: String,
    onDismiss: () -> Unit,
    onView: (filePath: String, fileName: String) -> Unit,
    onShare: (filePath: String) -> Unit,
    appLanguage: String = "ar"
) {
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    val file = remember(filePath) { File(filePath) }
    val fileName = file.name
    val fileSizeStr = remember(file) {
        if (file.exists()) {
            val size = file.length()
            when {
                size > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
                size > 1024 -> String.format(Locale.US, "%.1f KB", size / 1024f)
                else -> "$size Bytes"
            }
        } else "0 KB"
    }
    val pageCount = remember(filePath) { getPdfPageCount(filePath) }

    var thumbnailBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(descriptor)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bmp = Bitmap.createBitmap(page.width / 2, page.height / 2, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        thumbnailBitmap = bmp
                    }
                    renderer.close()
                    descriptor.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = strings.processSuccess,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.fileSavedInFinalPDF,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp, 90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                                    contentDescription = "PDF Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF File",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FinalPDF",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "$fileSizeStr • $pageCount ${strings.pagesTab}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onView(filePath, fileName) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.openDocument, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onShare(filePath) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            strings.share,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.closeButton, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ==========================================
// PDF METADATA DETAIL OVERLAY SHEET
// ==========================================

data class PdfMetadata(
    val fileName: String,
    val filePath: String,
    val fileSize: String,
    val folderName: String,
    val title: String?,
    val author: String,
    val creator: String?,
    val producer: String?,
    val creationDate: String,
    val modificationDate: String,
    val subject: String?,
    val keywords: String?,
    val pageCount: Int,
    val isEncrypted: Boolean,
    val pdfVersion: String
)

fun extractPdfMetadata(context: Context, filePath: String): PdfMetadata {
    val file = File(filePath)
    if (!file.exists()) {
        return PdfMetadata(
            fileName = if (filePath.isNotBlank()) File(filePath).name else "مستند PDF",
            filePath = filePath,
            fileSize = "0 KB",
            folderName = "غير معروف",
            title = null,
            author = "غير محدد",
            creator = null,
            producer = null,
            creationDate = "غير معروف",
            modificationDate = "غير معروف",
            subject = null,
            keywords = null,
            pageCount = 0,
            isEncrypted = false,
            pdfVersion = "PDF"
        )
    }

    val fileSizeStr = when {
        file.length() > 1024 * 1024 -> String.format(Locale.US, "%.2f MB", file.length() / (1024f * 1024f))
        file.length() > 1024 -> String.format(Locale.US, "%.1f KB", file.length() / 1024f)
        else -> "${file.length()} Bytes"
    }

    val dateFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar"))
    val lastModStr = try {
        dateFormat.format(Date(file.lastModified()))
    } catch (e: Exception) {
        "غير معروف"
    }

    var title: String? = null
    var author: String? = null
    var creator: String? = null
    var producer: String? = null
    var subject: String? = null
    var keywords: String? = null
    var creationDateStr: String? = null
    var modDateStr: String? = lastModStr
    var totalPages = 0
    var isEncrypted = false
    var versionStr: String? = null

    try {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
        isEncrypted = doc.isEncrypted
        totalPages = doc.numberOfPages
        if (doc.version > 0f) {
            versionStr = "PDF ${doc.version}"
        }

        val info = doc.documentInformation
        if (info != null) {
            title = info.title?.trim()?.takeIf { it.isNotBlank() }
            author = info.author?.trim()?.takeIf { it.isNotBlank() }
            creator = info.creator?.trim()?.takeIf { it.isNotBlank() }
            producer = info.producer?.trim()?.takeIf { it.isNotBlank() }
            subject = info.subject?.trim()?.takeIf { it.isNotBlank() }
            keywords = info.keywords?.trim()?.takeIf { it.isNotBlank() }

            info.creationDate?.let { cal ->
                try {
                    creationDateStr = dateFormat.format(cal.time)
                } catch (_: Exception) { }
            }
            info.modificationDate?.let { cal ->
                try {
                    modDateStr = dateFormat.format(cal.time)
                } catch (_: Exception) { }
            }
        }
        doc.close()
    } catch (_: Exception) { }

    if (totalPages == 0) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            totalPages = renderer.pageCount
            renderer.close()
            pfd.close()
        } catch (_: Exception) { }
    }

    val folder = file.parentFile?.name ?: "المستندات"

    return PdfMetadata(
        fileName = file.name,
        filePath = file.absolutePath,
        fileSize = fileSizeStr,
        folderName = folder,
        title = title,
        author = author ?: "غير محدد",
        creator = creator ?: "غير محدد",
        producer = producer ?: "غير محدد",
        creationDate = creationDateStr ?: lastModStr,
        modificationDate = modDateStr ?: lastModStr,
        subject = subject,
        keywords = keywords,
        pageCount = totalPages,
        isEncrypted = isEncrypted,
        pdfVersion = versionStr ?: "PDF 1.7"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDetailOverlaySheet(
    file: LocalPdfFile,
    onDismiss: () -> Unit,
    onOpenPdf: (LocalPdfFile) -> Unit,
    onSharePdf: (LocalPdfFile) -> Unit,
    appLanguage: String = "ar"
) {
    val context = LocalContext.current
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }
    var metadata by remember { mutableStateOf<PdfMetadata?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file.filePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val meta = extractPdfMetadata(context, file.filePath)
            metadata = meta

            try {
                val f = File(file.filePath)
                if (f.exists()) {
                    val descriptor = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(descriptor)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bmp = Bitmap.createBitmap((page.width / 2).coerceAtLeast(100), (page.height / 2).coerceAtLeast(100), Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        thumbnailBitmap = bmp
                    }
                    renderer.close()
                    descriptor.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }

    AppBottomSheet(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card with Thumbnail & Main Metadata
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp, 88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pagesText = metadata?.let { if (it.pageCount > 0) strings.fileCountFormat(it.pageCount) else "PDF" } ?: "..."
                            DetailBadge(text = pagesText, icon = Icons.Default.MenuBook, color = MaterialTheme.colorScheme.primary)
                            metadata?.let {
                                DetailBadge(text = it.fileSize, icon = Icons.Default.Storage, color = Color(0xFF00B0FF))
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(strings.extractingMetadata, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            } else {
                val meta = metadata ?: extractPdfMetadata(context, file.filePath)

                // Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecChip(
                        title = strings.pdfVersion,
                        value = meta.pdfVersion,
                        icon = Icons.Outlined.Badge,
                        modifier = Modifier.weight(1f)
                    )
                    SpecChip(
                        title = strings.encryption,
                        value = if (meta.isEncrypted) strings.encrypted else strings.notEncrypted,
                        icon = if (meta.isEncrypted) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        color = if (meta.isEncrypted) Color(0xFFE53935) else Color(0xFF43A047),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Metadata Cards
                DetailCardSection(title = strings.authorSection, icon = Icons.Outlined.Person) {
                    DetailMetaRow(label = strings.authorLabel, value = meta.author, icon = Icons.Outlined.Person)
                    if (!meta.title.isNullOrBlank()) {
                        DetailMetaRow(label = strings.documentTitleLabel, value = meta.title, icon = Icons.Outlined.Title)
                    }
                    if (!meta.subject.isNullOrBlank()) {
                        DetailMetaRow(label = strings.subjectLabel, value = meta.subject, icon = Icons.Outlined.Subject)
                    }
                    if (!meta.keywords.isNullOrBlank()) {
                        DetailMetaRow(label = strings.keywordsLabel, value = meta.keywords, icon = Icons.Outlined.Tag)
                    }
                }

                DetailCardSection(title = strings.datesSection, icon = Icons.Outlined.CalendarToday) {
                    DetailMetaRow(label = strings.creationDate, value = meta.creationDate, icon = Icons.Outlined.CalendarMonth)
                    DetailMetaRow(label = strings.modificationDate, value = meta.modificationDate, icon = Icons.Outlined.Update)
                }

                if (!meta.creator.isNullOrBlank() && meta.creator != "غير محدد" || !meta.producer.isNullOrBlank() && meta.producer != "غير محدد") {
                    DetailCardSection(title = strings.creatorAppSection, icon = Icons.Outlined.Devices) {
                        if (!meta.creator.isNullOrBlank() && meta.creator != "غير محدد") {
                            DetailMetaRow(label = strings.creatorLabel, value = meta.creator!!, icon = Icons.Outlined.Laptop)
                        }
                        if (!meta.producer.isNullOrBlank() && meta.producer != "غير محدد") {
                            DetailMetaRow(label = strings.producerLabel, value = meta.producer!!, icon = Icons.Outlined.Build)
                        }
                    }
                }

                DetailCardSection(title = strings.storageLocationSection, icon = Icons.Outlined.Folder) {
                    DetailMetaRow(label = strings.folderName, value = meta.folderName, icon = Icons.Outlined.FolderOpen)

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(strings.fullPath, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = meta.filePath,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("PDF Path", meta.filePath)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, strings.pathCopied, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = strings.copyPath, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenPdf(file)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.openDocument, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onSharePdf(file)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.share)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailBadge(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun SpecChip(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color = Color(0xFF7C5CFF),
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = Color.Gray)
                Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun DetailCardSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF7C5CFF), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C5CFF))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 1.dp)
            content()
        }
    }
}

@Composable
fun DetailMetaRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}


