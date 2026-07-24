package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val glassLavenderColorScheme = lightColorScheme(
    surface = Color(0xFFECE6F8),
    onSurface = Color(0xFF1C182B),
    surfaceVariant = Color(0xFFE2DBF0),
    onSurfaceVariant = Color(0xFF4C4566),
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DBF0),
    onPrimaryContainer = Color(0xFF2C1480),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0F5),
    onSecondaryContainer = Color(0xFF1E192B),
    background = Color(0xFFECE6F8),
    onBackground = Color(0xFF1C182B),
    outline = Color(0xFF8C869C),
    outlineVariant = Color(0xFFC7BFE6)
)

data class BottomBarColorPreset(
    val name: String,
    val lightBg: Color,
    val darkBg: Color,
    val lightOnSelected: Color,
    val darkOnSelected: Color,
    val lightUnselected: Color,
    val darkUnselected: Color,
    val lightSelectedContainer: Color,
    val darkSelectedContainer: Color
)

val BottomBarPresets = listOf(
    BottomBarColorPreset(
        name = "الافتراضي",
        lightBg = Color.White,
        darkBg = Color(0xFF1C1B26),
        lightOnSelected = Color(0xFF7C5CFF), // LavenderPrimary
        darkOnSelected = Color(0xFFB19DFF), // LavenderSecondary
        lightUnselected = Color(0xFF767482),
        darkUnselected = Color(0xFFBBB8CF),
        lightSelectedContainer = Color(0xFFF1EEFF),
        darkSelectedContainer = Color(0xFF2A283E)
    ),
    BottomBarColorPreset(
        name = "الأزرق الملكي",
        lightBg = Color(0xFFE3F2FD),
        darkBg = Color(0xFF0D47A1),
        lightOnSelected = Color(0xFF1565C0),
        darkOnSelected = Color(0xFF90CAF9),
        lightUnselected = Color(0xFF546E7A),
        darkUnselected = Color(0xFFB0BEC5),
        lightSelectedContainer = Color(0xFFBBDEFB),
        darkSelectedContainer = Color(0xFF1565C0)
    ),
    BottomBarColorPreset(
        name = "الأخضر الزمردي",
        lightBg = Color(0xFFE8F5E9),
        darkBg = Color(0xFF1B5E20),
        lightOnSelected = Color(0xFF2E7D32),
        darkOnSelected = Color(0xFFA5D6A7),
        lightUnselected = Color(0xFF4F5B66),
        darkUnselected = Color(0xFFC8E6C9),
        lightSelectedContainer = Color(0xFFC8E6C9),
        darkSelectedContainer = Color(0xFF2E7D32)
    ),
    BottomBarColorPreset(
        name = "البرتقالي الدافئ",
        lightBg = Color(0xFFFFF3E0),
        darkBg = Color(0xFFE65100),
        lightOnSelected = Color(0xFFD84315),
        darkOnSelected = Color(0xFFFFB74D),
        lightUnselected = Color(0xFF5D4037),
        darkUnselected = Color(0xFFFFE0B2),
        lightSelectedContainer = Color(0xFFFFE0B2),
        darkSelectedContainer = Color(0xFFD84315)
    ),
    BottomBarColorPreset(
        name = "الأحمر القرمزي",
        lightBg = Color(0xFFFFEBEE),
        darkBg = Color(0xFFB71C1C),
        lightOnSelected = Color(0xFFC62828),
        darkOnSelected = Color(0xFFFFCDD2),
        lightUnselected = Color(0xFF5D4037),
        darkUnselected = Color(0xFFFFCDD2),
        lightSelectedContainer = Color(0xFFFFCDD2),
        darkSelectedContainer = Color(0xFFC62828)
    ),
    BottomBarColorPreset(
        name = "خيال اللافندر",
        lightBg = Color(0xFFF3E5F5),
        darkBg = Color(0xFF4A148C),
        lightOnSelected = Color(0xFF6A1B9A),
        darkOnSelected = Color(0xFFE1BEE7),
        lightUnselected = Color(0xFF4A148C),
        darkUnselected = Color(0xFFE1BEE7),
        lightSelectedContainer = Color(0xFFE1BEE7),
        darkSelectedContainer = Color(0xFF6A1B9A)
    ),
    BottomBarColorPreset(
        name = "السيبي دافئ",
        lightBg = Color(0xFFEFEBE9),
        darkBg = Color(0xFF3E2723),
        lightOnSelected = Color(0xFF4E342E),
        darkOnSelected = Color(0xFFD7CCC8),
        lightUnselected = Color(0xFF705751),
        darkUnselected = Color(0xFFD7CCC8),
        lightSelectedContainer = Color(0xFFD7CCC8),
        darkSelectedContainer = Color(0xFF4E342E)
    ),
    BottomBarColorPreset(
        name = "نسيم التيل",
        lightBg = Color(0xFFE0F2F1),
        darkBg = Color(0xFF004D40),
        lightOnSelected = Color(0xFF00695C),
        darkOnSelected = Color(0xFF80CBC4),
        lightUnselected = Color(0xFF37474F),
        darkUnselected = Color(0xFFB2DFDB),
        lightSelectedContainer = Color(0xFFB2DFDB),
        darkSelectedContainer = Color(0xFF00695C)
    ),
    BottomBarColorPreset(
        name = "الوردي المرجاني",
        lightBg = Color(0xFFFCE4EC),
        darkBg = Color(0xFF880E4F),
        lightOnSelected = Color(0xFFAD1457),
        darkOnSelected = Color(0xFFF8BBD0),
        lightUnselected = Color(0xFF4A148C),
        darkUnselected = Color(0xFFF8BBD0),
        lightSelectedContainer = Color(0xFFF8BBD0),
        darkSelectedContainer = Color(0xFFAD1457)
    ),
    BottomBarColorPreset(
        name = "إنديغو السيبراني",
        lightBg = Color(0xFFE8EAF6),
        darkBg = Color(0xFF1A237E),
        lightOnSelected = Color(0xFF283593),
        darkOnSelected = Color(0xFFC5CAE9),
        lightUnselected = Color(0xFF3F51B5),
        darkUnselected = Color(0xFFC5CAE9),
        lightSelectedContainer = Color(0xFFC5CAE9),
        darkSelectedContainer = Color(0xFF283593)
    ),
    BottomBarColorPreset(
        name = "الفحم الحجري",
        lightBg = Color(0xFFECEFF1),
        darkBg = Color(0xFF263238),
        lightOnSelected = Color(0xFF37474F),
        darkOnSelected = Color(0xFFCFD8DC),
        lightUnselected = Color(0xFF455A64),
        darkUnselected = Color(0xFFCFD8DC),
        lightSelectedContainer = Color(0xFFCFD8DC),
        darkSelectedContainer = Color(0xFF37474F)
    )
)

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

    // Back handler to return to the Home/Main tab from other tabs or clear selection mode
    val isInSelectionMode = selectedFilePaths.isNotEmpty()
    BackHandler(enabled = isInSelectionMode || uiState.selectedTab != DashboardTab.Home) {
        if (isInSelectionMode) {
            selectedFilePaths = emptySet()
        } else {
            viewModel.setTab(DashboardTab.Home)
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

    Scaffold(
        bottomBar = {
            CustomBottomBar(
                selectedTab = uiState.selectedTab,
                showTools = uiState.showToolsTab,
                bottomBarColorIndex = uiState.bottomBarColorIndex,
                onTabSelected = { viewModel.setTab(it) }
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == DashboardTab.Home && selectedFilePaths.isEmpty()) {
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
        modifier = modifier.fillMaxSize()
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
                    onClearSelection = { selectedFilePaths = emptySet() },
                    onShareMultiple = { shareMultiplePdfs(context, selectedFilePaths.toList()) },
                    onDeleteMultiple = { showMultiDeleteConfirm = true }
                )
                DashboardTab.Folders -> FoldersTabScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    selectedFilePaths = selectedFilePaths,
                    onToggleSelectFile = onToggleSelectFile,
                    onShowFileActions = { showFileActionsSheet = it }
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
            onDismiss = { showFileActionsSheet = null }
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

    // File Info Dialog
    if (fileToViewInfo != null) {
        val file = fileToViewInfo!!
        val dateStr = remember(file.lastModified) {
            try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified))
            } catch (e: Exception) {
                "غير معروف"
            }
        }
        val pageCount = remember(file.filePath) { getPdfPageCount(file.filePath) }
        
        AlertDialog(
            onDismissRequest = { fileToViewInfo = null },
            title = { Text("معلومات الملف", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(label = "اسم الملف:", value = file.fileName)
                    InfoRow(label = "مسار الملف:", value = file.filePath)
                    InfoRow(label = "حجم الملف:", value = file.fileSize)
                    InfoRow(label = "تاريخ التعديل:", value = dateStr)
                    InfoRow(label = "عدد الصفحات:", value = if (pageCount > 0) "$pageCount صفحة" else "غير معروف")
                    InfoRow(label = "مجلد الملف:", value = file.folderName)
                }
            },
            confirmButton = {
                Button(onClick = { fileToViewInfo = null }) {
                    Text("حسناً")
                }
            }
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
    onClearSelection: () -> Unit,
    onShareMultiple: () -> Unit,
    onDeleteMultiple: () -> Unit
) {
    val context = LocalContext.current
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    
    val greetingText = if (currentHour < 12) "صباح الخير!" else "مساء الخير!"
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
                    text = "مرحباً بك في تطبيق FinalPDF",
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
                        contentDescription = "إحصائيات المكتبة",
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
                        contentDescription = "ترتيب الملفات",
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
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.dashboardSearchQuery.isEmpty()) {
                            Text(
                                text = "ابحث في ملفات الـ PDF...",
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
                                contentDescription = "مسح",
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
                    label = "كل الملفات",
                    selected = uiState.selectedFilter == FileFilter.All,
                    onClick = { viewModel.setFileFilter(FileFilter.All) }
                )
                FilterPill(
                    label = "المفضلة",
                    selected = uiState.selectedFilter == FileFilter.Favorites,
                    onClick = { viewModel.setFileFilter(FileFilter.Favorites) }
                )
                FilterPill(
                    label = "الأخيرة",
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
                    onClick = { viewModel.toggleGridView() },
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

        // Files List / Grid Renderer
        if (filteredFiles.isEmpty()) {
            EmptyDashboardView(queryEmpty = uiState.dashboardSearchQuery.isNotEmpty())
        } else {
            if (uiState.isGridView) {
                // Grid Layout
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
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
                                    onShowFileActions(file)
                                }
                            },
                            onMenuClick = {
                                onShowFileActions(file)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                // List Layout
                LazyColumn(
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
                                    onShowFileActions(file)
                                }
                            },
                            onMenuClick = {
                                onShowFileActions(file)
                            },
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
            onDismiss = { showSortSheet = false }
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
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
                    .padding(4.dp)
                    .size(32.dp)
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
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // PDF cover thumbnail with nested progress bar
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(115.dp)
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
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Name of PDF
                val gridFileNameFontSize = when {
                    file.fileName.length > 35 -> 9.sp
                    file.fileName.length > 25 -> 10.sp
                    file.fileName.length > 15 -> 11.sp
                    else -> 12.sp
                }
                Text(
                    text = file.fileName,
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

                // Small badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        val badgeFontSize = when {
                            file.folderName.length > 25 -> 6.sp
                            file.folderName.length > 15 -> 7.sp
                            else -> 8.sp
                        }
                        Text(
                            text = file.folderName,
                            fontSize = badgeFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = file.fileSize,
                        fontSize = 8.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
                
                // Reading progress and last opened label
                if (progressPercent != null && progressPercent > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قرأت $progressPercent% • $lastOpenedText",
                        fontSize = 8.sp,
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF cover thumbnail
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
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
                            .height(4.dp)
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
                            contentDescription = "محدد",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                    text = file.fileName,
                    fontSize = listFileNameFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (listFileNameFontSize.value + 2).sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        val badgeFontSize = when {
                            file.folderName.length > 25 -> 6.sp
                            file.folderName.length > 15 -> 7.sp
                            else -> 8.sp
                        }
                        Text(
                            text = file.folderName,
                            fontSize = badgeFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = file.fileSize,
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    
                    if (lastOpenedText != null) {
                        Text(
                            text = lastOpenedText,
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Progress Percentage
                if (progressPercent != null && progressPercent > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تمت قراءة $progressPercent%",
                        fontSize = 9.sp,
                        color = Color(0xFF1E88E5),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 3-dots Menu Button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات الملف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// In-memory cache for fast, instant thumbnail retrieval during scroll lookups
object ThumbnailMemoryCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun get(key: String): String? = cache[key]
    fun put(key: String, value: String) {
        cache[key] = value
    }
}

@Composable
fun PdfThumbnail(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedPath = remember(filePath) { ThumbnailMemoryCache.get(filePath) }
    var thumbnailPath by remember(filePath) { mutableStateOf<String?>(cachedPath) }
    
    LaunchedEffect(filePath) {
        if (thumbnailPath == null) {
            withContext(Dispatchers.IO) {
                val path = getPdfThumbnailPath(context, filePath)
                if (path != null) {
                    ThumbnailMemoryCache.put(filePath, path)
                    withContext(Dispatchers.Main) {
                        thumbnailPath = path
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
            if (thumbnailPath != null) {
                AsyncImage(
                    model = thumbnailPath,
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

fun getPdfThumbnailPath(context: Context, filePath: String): String? {
    try {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        
        val cacheKey = "thumb_w_" + file.nameWithoutExtension.hashCode() + "_" + file.lastModified() + ".png"
        val cacheFile = File(context.cacheDir, cacheKey)
        if (cacheFile.exists()) {
            return cacheFile.absolutePath
        }
        
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
        val renderer = PdfRenderer(pfd)
        if (renderer.pageCount > 0) {
            val page = renderer.openPage(0)
            val width = 150
            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            return cacheFile.absolutePath
        } else {
            renderer.close()
            pfd.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
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
        else -> {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilesSheet(
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2ECE6F8),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        MaterialTheme(colorScheme = glassLavenderColorScheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
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
                    text = "ترتيب الملفات",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "اختر طريقة تنظيم واستعراض ملفات الـ PDF الخاصة بك",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Category 1: Alphabetical
            Text(
                text = "أبجدي",
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
                    label = "أ -> ي  ↑",
                    selected = sortOption == SortOption.ALPHA_ASC,
                    onClick = { onSortSelected(SortOption.ALPHA_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = "ي -> أ  ↓",
                    selected = sortOption == SortOption.ALPHA_DESC,
                    onClick = { onSortSelected(SortOption.ALPHA_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category 2: File Size
            Text(
                text = "حجم الملف",
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
                    label = "الأصغر أولاً  ↑",
                    selected = sortOption == SortOption.SIZE_ASC,
                    onClick = { onSortSelected(SortOption.SIZE_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = "الأكبر أولاً  ↓",
                    selected = sortOption == SortOption.SIZE_DESC,
                    onClick = { onSortSelected(SortOption.SIZE_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category 3: Date Modified
            Text(
                text = "تاريخ الإضافة",
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
                    label = "الأقدم أولاً  ↑",
                    selected = sortOption == SortOption.DATE_ASC,
                    onClick = { onSortSelected(SortOption.DATE_ASC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                SortChip(
                    label = "الأحدث أولاً  ↓",
                    selected = sortOption == SortOption.DATE_DESC,
                    onClick = { onSortSelected(SortOption.DATE_DESC); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2ECE6F8),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        MaterialTheme(colorScheme = glassLavenderColorScheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
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
                    text = "إحصائيات المكتبة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "نظرة عامة على ملفات ومؤشرات القراءة في مكتبتك",
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
                        title = "إجمالي الملفات",
                        value = "$totalFiles ملف",
                        icon = Icons.Default.PictureAsPdf,
                        iconColor = Color(0xFFEF5350),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "حجم المستندات",
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
                        title = "الملفات المفضلة",
                        value = "$favoriteCount ملف",
                        icon = Icons.Default.Star,
                        iconColor = Color(0xFFFFCA28),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "الملفات المفتوحة",
                        value = "$openedCount ملف",
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
                        title = "ساعات القراءة",
                        value = formattedReadingTime,
                        icon = Icons.Default.AccessTime,
                        iconColor = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "مفقودة من الهاتف",
                        value = "$deletedCount ملف",
                        icon = Icons.Default.FolderOff,
                        iconColor = Color(0xFF78909C),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
fun EmptyDashboardView(queryEmpty: Boolean) {
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
            text = if (queryEmpty) "لم نجد نتائج بحث مطابقة" else "مكتبتك فارغة حالياً",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (queryEmpty) "تأكد من كتابة اسم الملف بشكل صحيح وجرب مجدداً." 
            else "اضغط على زر الإضافة (+) لفتح وقراءة أول ملف PDF لك.",
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
    onShowFileActions: (LocalPdfFile) -> Unit
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentPdfs.collectAsState(initial = emptyList())

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
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EEFF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مستعرض المجلدات",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تصنيف منظم لسهولة العثور على الكتب",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FolderStatColumn(num = folderGroups.keys.size, label = "مجلدات")
                    FolderStatColumn(num = uiState.allPdfFiles.size, label = "مستندات")
                }
            }
        }

        if (expandedFolder == null) {
            // Folders List View
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                folderGroups.forEach { (folderName, files) ->
                    item {
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
                                        text = folderName,
                                        fontSize = folderNameFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${files.size} ملفات PDF",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "استعراض",
                                    tint = Color.Gray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Expanded Folder Content View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { expandedFolder = null }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val expandedFolderTitleFontSize = when {
                    (expandedFolder ?: "").length > 30 -> 12.sp
                    (expandedFolder ?: "").length > 20 -> 14.sp
                    else -> 16.sp
                }
                Text(
                    text = expandedFolder ?: "",
                    fontSize = expandedFolderTitleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val folderFiles = folderGroups[expandedFolder] ?: emptyList()

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folderFiles, key = { it.filePath }) { file ->
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
                                onShowFileActions(file)
                            }
                        },
                        onMenuClick = {
                            onShowFileActions(file)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun FolderStatColumn(num: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = num.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
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
    CameraOcr
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
    
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "أدوات الـ PDF المتقدمة",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "أدوات ذكية حقيقية لتعديل وتخصيص مستنداتك وحفظها فوراً",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // OCR CAMERA SECTION
            item {
                ToolSectionHeader(title = "الماسح الضوئي والتعرف على النصوص (OCR)")
            }
            item {
                ToolGridCard(
                    title = "ماسح الكاميرا الضوئي (Camera OCR)",
                    desc = "تصوير أي مستند أو ورقة واستخراج النصوص فوراً بتقنية ML Kit مع تأثير مسح ليزري",
                    icon = Icons.Default.DocumentScanner,
                    color = Color(0xFFE8E0F5),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        activeTool = ActiveTool.CameraOcr
                    }
                )
            }

            // ORGANIZE SECTION
            item {
                ToolSectionHeader(title = "تنظيم وترتيب الملفات")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = "دمج ملفات PDF",
                        desc = "دمج عدة ملفات في ملف واحد مخصص",
                        icon = Icons.Default.MergeType,
                        color = Color(0xFFE6E0FF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Merge
                            targetFileName = "دمج_المستندات"
                            selectedFilePaths = emptySet()
                        }
                    )
                    ToolGridCard(
                        title = "تقسيم ملف PDF",
                        desc = "استخراج صفحات محددة لملف جديد",
                        icon = Icons.Default.CallSplit,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Split
                            targetFileName = "تقسيم_الملف"
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
                            splitFromPage = "1"
                            splitToPage = "1"
                        }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolGridCard(
                        title = "ضغط ملف PDF",
                        desc = "تصغير الحجم بكفاءة عالية للحفظ والمشاركة",
                        icon = Icons.Default.Compress,
                        color = Color(0xFFF1EEFF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Compress
                            targetFileName = "الملف_المضغوط"
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
                            compressionLevel = "medium"
                        }
                    )
                    ToolGridCard(
                        title = "تدوير الصفحات",
                        desc = "تغيير اتجاه لصفحة معينة أو كل الصفحات",
                        icon = Icons.Default.RotateRight,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Rotate
                            targetFileName = "الملف_المعدل"
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
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
                        title = "إعادة ترتيب الصفحات",
                        desc = "تنظيم تسلسل الصفحات حسب رغبتك",
                        icon = Icons.Default.List,
                        color = Color(0xFFE6E0FF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.Reorder
                            targetFileName = "الملف_المرتب"
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
                            reorderSequence = ""
                        }
                    )
                    ToolGridCard(
                        title = "حذف الصفحات",
                        desc = "إزالة صفحة واحدة أو أكثر من المستند",
                        icon = Icons.Default.Delete,
                        color = Color(0xFFFFD1D1),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTool = ActiveTool.DeletePages
                            targetFileName = "الملف_بعد_الحذف"
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
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
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
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
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
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
                            selectedSingleFilePath = uiState.allPdfFiles.firstOrNull()?.filePath ?: ""
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
                activeTool = ActiveTool.None
            }
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
            val (toolTitle, toolSubtitle) = when (activeTool) {
                ActiveTool.Merge -> "دمج ملفات PDF" to "اختر ملفات الـ PDF التي ترغب في دمجها بالترتيب المناسب لتصديرها في مستند واحد"
                ActiveTool.Split -> "تقسيم ملف PDF" to "اختر الملف والصفحات المراد استخراجها وفصلها في ملف جديد منفصل"
                ActiveTool.Compress -> "ضغط ملف PDF" to "تقليل حجم الملف مع الحفاظ على أعلى جودة قراءة ووضوح"
                ActiveTool.Rotate -> "تدوير صفحات PDF" to "تعديل اتجاه وزاوية دوران صفحات مستند الـ PDF"
                ActiveTool.Reorder -> "إعادة ترتيب الصفحات" to "إعادة تنظيم وترتيب تسلسل صفحات ملف الـ PDF"
                ActiveTool.DeletePages -> "حذف صفحات من PDF" to "إزالة صفحة واحدة أو أكثر غير مرغوبة من مستندك"
                ActiveTool.ImageToPdf -> "تحويل صور إلى PDF" to "اختر صورة أو أكثر من المعرض وتحويلها إلى ملف PDF مرتب"
                ActiveTool.PdfToImages -> "تحويل PDF إلى صور" to "استخراج صفحات مستند الـ PDF وحفظها كصور عالية الجودة"
                ActiveTool.LockPdf -> "تشفير وقفل ملف PDF" to "حماية مستند الـ PDF بكلمة سر وتعيين صلاحيات القراءة والطباعة"
                ActiveTool.UnlockPdf -> "فك قفل وإزالة حماية PDF" to "إزالة كلمة السر والتشفير من مستند مشفر"
                else -> "" to ""
            }

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
                            IconButton(onClick = { if (!isProcessing) activeTool = ActiveTool.None }) {
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
                                        Text("إعدادات التصدير", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("حدد الملفات المراد دمجها (${selectedFilePaths.size} محددة)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (uiState.allPdfFiles.isEmpty()) {
                                            Text("لا يوجد ملفات PDF متوفرة في مكتبتك.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        } else {
                                            uiState.allPdfFiles.forEach { file ->
                                                val isSelected = selectedFilePaths.contains(file.filePath)
                                                Surface(
                                                    onClick = {
                                                        selectedFilePaths = if (isSelected) selectedFilePaths - file.filePath else selectedFilePaths + file.filePath
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(12.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = isSelected,
                                                            onCheckedChange = {
                                                                selectedFilePaths = if (it) selectedFilePaths + file.filePath else selectedFilePaths - file.filePath
                                                            }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(file.fileName, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(file.fileSize, fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            ActiveTool.Split -> {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF المراد تقسيمه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_مقسم"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = targetFileName,
                                            onValueChange = { targetFileName = it },
                                            label = { Text("اسم الملف الناتج") },
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
                                        Text("حدد نطاق الصفحات المراد فصلها", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر الملف المراد ضغطه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_مضغوط"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = targetFileName,
                                            onValueChange = { targetFileName = it },
                                            label = { Text("اسم الملف الناتج") },
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
                                        Text("مستوى الضغط المطلوبة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_مدور"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = targetFileName,
                                            onValueChange = { targetFileName = it },
                                            label = { Text("اسم الملف الناتج") },
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
                                        Text("درجة التدوير المطلوبة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(90 to "90° مع العقارب", 180 to "180° رأساً", 270 to "270° عكس العقارب").forEach { (deg, label) ->
                                                FilterChip(
                                                    selected = rotateDegrees == deg,
                                                    onClick = { rotateDegrees = deg },
                                                    label = { Text(label, fontSize = 12.sp) },
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF المراد إعادة ترتيبه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_مرتب"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = targetFileName,
                                            onValueChange = { targetFileName = it },
                                            label = { Text("اسم الملف الناتج") },
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
                                        Text("ترتيب الصفحات الجديد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("أدخل أرقام الصفحات بالترتيب الجديد مفصولة بفواصل:", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF لحذف صفحات منه", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_معدل"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = targetFileName,
                                            onValueChange = { targetFileName = it },
                                            label = { Text("اسم الملف الناتج") },
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
                                        Text("أرقام الصفحات المراد حذفها", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("أدخل الصفحات المراد حذفها مفصولة بفواصل:", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                val imagePickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.GetMultipleContents()
                                ) { uris ->
                                    val paths = uris.mapNotNull { uri ->
                                        viewModel.copyUriToCache(context, uri, "img_${System.currentTimeMillis()}_${uri.lastPathSegment}.jpg")
                                    }
                                    selectedImagePaths = selectedImagePaths + paths
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
                            }

                            ActiveTool.PdfToImages -> {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF لتحويله لصور", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF المراد تشفيره", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_محمي"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
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
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("اختر ملف الـ PDF المشفر", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(selectedFile?.fileName ?: "اضغط لاختيار ملف...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                uiState.allPdfFiles.forEach { file ->
                                                    DropdownMenuItem(
                                                        text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            selectedSingleFilePath = file.filePath
                                                            targetFileName = "${file.fileName}_مفتوح"
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
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
                                    Text("جاري معالجة وتطبيق العملية على المستند...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                                    Toast.makeText(context, "تم دمج المستندات بنجاح وحفظها!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم تقسيم الملف بنجاح وحفظه!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم ضغط الملف بنجاح وحفظه!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم تدوير وحفظ الملف بنجاح!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم إعادة ترتيب الصفحات بنجاح!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم حذف الصفحات وحفظ الملف بنجاح!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم تحويل الصور إلى PDF وحفظ الملف بنجاح!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم قفل وحماية الملف بنجاح!", Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, "تم إزالة الحماية وحفظ الملف بنجاح!", Toast.LENGTH_LONG).show()
                                                },
                                                onError = { err ->
                                                    isProcessing = false
                                                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
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
                                    ActiveTool.Merge -> "دمج المستندات"
                                    ActiveTool.Split -> "تقسيم وحفظ"
                                    ActiveTool.Compress -> "ابدأ الضغط الآن"
                                    ActiveTool.Rotate -> "تدوير وحفظ"
                                    ActiveTool.Reorder -> "إعادة الترتيب وحفظ"
                                    ActiveTool.DeletePages -> "تأكيد الحذف وحفظ"
                                    ActiveTool.ImageToPdf -> "إنشاء ملف PDF"
                                    ActiveTool.PdfToImages -> "تصدير الصور"
                                    ActiveTool.LockPdf -> "قفل وتشفير الملف"
                                    ActiveTool.UnlockPdf -> "فك حماية وقفل الملف"
                                    else -> "تأكيد"
                                }
                                Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog / Sheet for Merge Tool
    if (activeTool == ActiveTool.Merge) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("دمج ملفات PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text("اختر ملفات الـ PDF التي ترغب في دمجها بالترتيب المناسب:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    
                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    if (uiState.allPdfFiles.isEmpty()) {
                        Text("لا يوجد ملفات PDF متوفرة في مكتبتك حالياً للدمج.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uiState.allPdfFiles) { file ->
                                val isSelected = selectedFilePaths.contains(file.filePath)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFilePaths = if (isSelected) {
                                                selectedFilePaths - file.filePath
                                            } else {
                                                selectedFilePaths + file.filePath
                                            }
                                        }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedFilePaths = if (isSelected) {
                                                selectedFilePaths - file.filePath
                                            } else {
                                                selectedFilePaths + file.filePath
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${file.fileSize} - ${file.folderName}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري دمج وحفظ الملف...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetFileName.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "تم دمج الملفات بنجاح وحفظها في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedFilePaths.size >= 2
                ) {
                    Text("دمج المستندات")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for Split Tool
    if (activeTool == ActiveTool.Split) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }
        
        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("تقسيم ملف PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("اختر الملف والصفحات المراد استخراجها في ملف جديد منفصل:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    
                    // Single File Picker Dropdown
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف المراد تقسيمه...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_مقتطع"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = splitFromPage,
                            onValueChange = { splitFromPage = it },
                            label = { Text("من صفحة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = splitToPage,
                            onValueChange = { splitToPage = it },
                            label = { Text("إلى صفحة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري معالجة وتصدير الملف...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val from = splitFromPage.toIntOrNull()
                        val to = splitToPage.toIntOrNull()
                        if (selectedSingleFilePath.isEmpty()) {
                            Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (targetFileName.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (from == null || to == null || from < 1 || to < from) {
                            Toast.makeText(context, "الرجاء إدخال أرقام صفحات صحيحة (من صفحة يجب أن تكون أصغر من أو تساوي إلى صفحة)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
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
                                Toast.makeText(context, "تم تقسيم الملف وحفظ الصفحات في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("تقسيم وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for Compress Tool
    if (activeTool == ActiveTool.Compress) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("ضغط ملف PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("قم بتقليل حجم الملف مع الحفاظ على أعلى جودة قراءة:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف المراد ضغطه...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_مضغوط"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Text("قوة الضغط المطلوبة:", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("low", "منخفضة (جودة عالية)", Color(0xFFD4EDDA)),
                            Triple("medium", "متوسطة (أفضل توازن)", Color(0xFFFFF3CD)),
                            Triple("high", "عالية (أصغر حجم)", Color(0xFFF8D7DA))
                        ).forEach { (level, name, color) ->
                            val isSelected = compressionLevel == level
                            Surface(
                                onClick = { compressionLevel = level },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                    Text(name, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري الضغط وتقليل حجم المستند...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                                Toast.makeText(context, "تم ضغط الملف بنجاح وحفظه في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("ابدأ الضغط الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for Rotate Tool
    if (activeTool == ActiveTool.Rotate) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("تدوير صفحات PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("تعديل وتدوير اتجاه صفحات مستند الـ PDF:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف المراد تعديله...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_مدور"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Text("زاوية التدوير:", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(90, 180, 270).forEach { deg ->
                            val isSelected = rotateDegrees == deg
                            Surface(
                                onClick = { rotateDegrees = deg },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("$deg°", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = rotateTargetPage,
                        onValueChange = { rotateTargetPage = it },
                        label = { Text("الصفحة المستهدفة (-1 لجميع الصفحات)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري تدوير صفحات المستند...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedSingleFilePath.isEmpty()) {
                            Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (targetFileName.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val pageVal = rotateTargetPage.toIntOrNull() ?: -1
                        isProcessing = true
                        viewModel.rotatePdf(
                            context = context,
                            filePath = selectedSingleFilePath,
                            degrees = rotateDegrees,
                            targetPage = pageVal,
                            targetName = targetFileName,
                            onSuccess = { path ->
                                isProcessing = false
                                activeTool = ActiveTool.None
                                Toast.makeText(context, "تم تدوير وحفظ الملف بنجاح في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("تدوير وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for Reorder Tool
    if (activeTool == ActiveTool.Reorder) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("إعادة ترتيب الصفحات", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("أدخل أرقام الصفحات بالترتيب الجديد المطلوب (مفصولة بفواصل):", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف المراد إعادة ترتيبه...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_مرتب"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = reorderSequence,
                        onValueChange = { reorderSequence = it },
                        label = { Text("ترتيب الصفحات (مثال: 3, 1, 2, 4)") },
                        placeholder = { Text("مثال: 3, 1, 2, 4") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري إعادة ترتيب وحفظ الصفحات...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedSingleFilePath.isEmpty()) {
                            Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (targetFileName.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val orderList = reorderSequence.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (orderList.isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال ترتيب صفحات صحيح (مثال: 3,1,2)", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "تم إعادة ترتيب الصفحات وحفظ الملف في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("إعادة الترتيب وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for DeletePages Tool
    if (activeTool == ActiveTool.DeletePages) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("حذف صفحات من PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("أدخل أرقام الصفحات التي ترغب بحذفها (مفصولة بفواصل):", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف لحذف صفحات منه...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_معدل"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = deletePagesSetInput,
                        onValueChange = { deletePagesSetInput = it },
                        label = { Text("رقم الصفحات للحذف (مثال: 2, 4)") },
                        placeholder = { Text("مثال: 2, 4") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري حذف الصفحات المحددة وحفظ المستند...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedSingleFilePath.isEmpty()) {
                            Toast.makeText(context, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (targetFileName.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال اسم للملف الناتج", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val pagesToDelete = deletePagesSetInput.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                        if (pagesToDelete.isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال أرقام صفحات صحيحة لحذفها", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isProcessing = true
                        viewModel.deletePagesPdf(
                            context = context,
                            filePath = selectedSingleFilePath,
                            pagesToDelete = pagesToDelete,
                            targetName = targetFileName,
                            onSuccess = { path ->
                                isProcessing = false
                                activeTool = ActiveTool.None
                                Toast.makeText(context, "تم حذف الصفحات بنجاح وحفظ الملف في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("تأكيد الحذف وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for ImageToPdf Tool
    if (activeTool == ActiveTool.ImageToPdf) {
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            val paths = uris.mapNotNull { uri ->
                viewModel.copyUriToCache(context, uri, "img_${System.currentTimeMillis()}_${uri.lastPathSegment}.jpg")
            }
            selectedImagePaths = selectedImagePaths + paths
        }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("تحويل صور إلى PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text("اختر صورة أو أكثر لتحويلها إلى ملف PDF بالترتيب المناسب:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    
                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم ملف الـ PDF الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إضافة صور من المعرض (${selectedImagePaths.size})")
                    }

                    if (selectedImagePaths.isEmpty()) {
                        Text("لم يتم اختيار أي صور حتى الآن.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("الصور المحددة (اسحب للحذف):", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(selectedImagePaths) { index, path ->
                                val file = File(path)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("${index + 1}. ${file.name}", fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    IconButton(
                                        onClick = {
                                            selectedImagePaths = selectedImagePaths.toMutableList().apply { removeAt(index) }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري تحويل الصور وتوليد الـ PDF...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                                Toast.makeText(context, "تم حفظ الملف بنجاح في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedImagePaths.isNotEmpty()
                ) {
                    Text("إنشاء ملف PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for PdfToImages Tool
    if (activeTool == ActiveTool.PdfToImages) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("تحويل PDF إلى صور", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("اختر ملف الـ PDF وصيغة التصدير:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر الملف لتحويله...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text("صيغة الصور الناتجة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pdfToImagesFormat = "PNG" }) {
                            RadioButton(selected = pdfToImagesFormat == "PNG", onClick = { pdfToImagesFormat = "PNG" })
                            Text("PNG (جودة عالية)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { pdfToImagesFormat = "JPG" }) {
                            RadioButton(selected = pdfToImagesFormat == "JPG", onClick = { pdfToImagesFormat = "JPG" })
                            Text("JPG (حجم مدمج)")
                        }
                    }

                    OutlinedTextField(
                        value = pdfToImagesPages,
                        onValueChange = { pdfToImagesPages = it },
                        label = { Text("تصدير صفحات معينة (اختياري)") },
                        placeholder = { Text("مثال: 1, 2, 5-8 (اتركه فارغاً للكل)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري تصدير صفحات الـ PDF لصور...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                                Toast.makeText(context, "تم تصدير ${paths.size} صور بنجاح وحفظها في الصور التطبيقية!", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("تصدير الصور")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for LockPdf Tool
    if (activeTool == ActiveTool.LockPdf) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("تشفير وقفل ملف PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text("اختر ملف الـ PDF وأدخل كلمة سر لحمايته:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر ملف الـ PDF...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_محمي"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = lockPassword,
                        onValueChange = { lockPassword = it },
                        label = { Text("كلمة سر فتح الملف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Text("خيارات الصلاحيات:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowPrinting = !lockAllowPrinting }) {
                                Checkbox(checked = lockAllowPrinting, onCheckedChange = { lockAllowPrinting = it })
                                Text("السماح بطباعة الملف", fontSize = 12.sp)
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowCopying = !lockAllowCopying }) {
                                Checkbox(checked = lockAllowCopying, onCheckedChange = { lockAllowCopying = it })
                                Text("السماح بنسخ النصوص والمحتوى", fontSize = 12.sp)
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowModifying = !lockAllowModifying }) {
                                Checkbox(checked = lockAllowModifying, onCheckedChange = { lockAllowModifying = it })
                                Text("السماح بتعديل صفحات الملف", fontSize = 12.sp)
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { lockAllowAnnotations = !lockAllowAnnotations }) {
                                Checkbox(checked = lockAllowAnnotations, onCheckedChange = { lockAllowAnnotations = it })
                                Text("السماح بإضافة تعليقات وشروح", fontSize = 12.sp)
                            }
                        }
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري تشفير وحماية الملف بكلمة سر...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                                Toast.makeText(context, "تم قفل وحماية الملف بنجاح وحفظه في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("قفل وتشفير الملف")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog / Sheet for UnlockPdf Tool
    if (activeTool == ActiveTool.UnlockPdf) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        val selectedFile = uiState.allPdfFiles.find { it.filePath == selectedSingleFilePath }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) activeTool = ActiveTool.None },
            title = { Text("فك قفل وإزالة حماية PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("اختر ملف الـ PDF المشفر وأدخل كلمة السر لإزالة الحماية:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedFile?.fileName ?: "اختر ملف الـ PDF المشفر...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            uiState.allPdfFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedSingleFilePath = file.filePath
                                        targetFileName = "${file.fileName}_مفتوح"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetFileName,
                        onValueChange = { targetFileName = it },
                        label = { Text("اسم الملف الناتج") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label = { Text("كلمة السر الحالية لفتح الملف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("جاري معالجة وفك تشفير الملف...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                                Toast.makeText(context, "تم إزالة الحماية وحفظ الملف بنجاح في: $path", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, "خطأ: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isProcessing && selectedSingleFilePath.isNotEmpty()
                ) {
                    Text("فك حماية وقفل الملف")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActiveTool.None }, enabled = !isProcessing) {
                    Text("إلغاء")
                }
            }
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
@Composable
fun SettingsTabScreen(
    viewModel: PdfViewModel,
    uiState: PdfUiState
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header Branding Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFFF1EEFF), RoundedCornerShape(14.dp)),
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
                            text = "تطبيق FinalPDF",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFF9C4), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "GPL 3.0",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "الإصدار 2.2.0 - تصفح بلا حدود",
                        fontSize = 12.sp,
                        color = Color.Gray
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
                SettingsSectionHeader(title = "تخصيص المظهر")
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // App Theme Row
                        SettingsSelectionRow(
                            icon = Icons.Default.Brightness4,
                            title = "مظهر التطبيق",
                            value = when (uiState.appTheme) {
                                "dark" -> "الوضع الداكن"
                                "light" -> "الوضع الفاتح"
                                else -> "تلقائي (حسب النظام)"
                            },
                            onClick = {
                                val themes = listOf("system", "light", "dark")
                                val currentIdx = themes.indexOf(uiState.appTheme)
                                val nextTheme = themes[(currentIdx + 1) % themes.size]
                                viewModel.setAppTheme(context, nextTheme)
                            }
                        )
                        
                        Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)

                        // Show Tools Tab Toggle Option
                        SettingsSwitchRow(
                            icon = Icons.Default.Grid3x3,
                            title = "إظهار تبويب الأدوات",
                            subtitle = "إظهار أو إخفاء تبويب التعديل المتقدم في الشريط السفلي",
                            checked = uiState.showToolsTab,
                            onCheckedChange = { viewModel.setShowToolsTab(context, it) }
                        )
                    }
                }
            }

            // BOTTOM BAR CUSTOMIZATION SECTION
            item {
                SettingsSectionHeader(title = "تخصيص لون الشريط السفلي")
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "اختر لوناً لشريط التنقل السفلي للتطبيق (11 خياراً):",
                            fontSize = 12.sp,
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
                                    Color(0xFF7C5CFF) // LavenderPrimary
                                } else {
                                    if (isDarkTheme) preset.darkOnSelected else preset.lightOnSelected
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(previewBg)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
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

            // READER SETTINGS SECTION
            item {
                SettingsSectionHeader(title = "إعدادات القارئ والتمرير")
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSelectionRow(
                            icon = Icons.Default.SwapVert,
                            title = "اتجاه التمرير الافتراضي",
                            value = if (uiState.scrollMode == "horizontal") "أفقي" else "عمودي",
                            onClick = {
                                val nextMode = if (uiState.scrollMode == "horizontal") "vertical" else "horizontal"
                                viewModel.setScrollMode(nextMode)
                                Toast.makeText(context, "تم تغيير اتجاه القراءة!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)
                        SettingsSelectionRow(
                            icon = Icons.Default.Palette,
                            title = "مظهر صفحات القراءة",
                            value = when (uiState.readingTheme) {
                                "dark" -> "داكن"
                                "black" -> "أسود بالكامل"
                                "sepia" -> "دافئ (Sepia)"
                                else -> "فاتح"
                            },
                            onClick = {
                                val themes = listOf("light", "dark", "sepia", "black")
                                val currentIdx = themes.indexOf(uiState.readingTheme)
                                val nextTheme = themes[(currentIdx + 1) % themes.size]
                                viewModel.setReadingTheme(nextTheme)
                            }
                        )
                    }
                }
            }

            // GENERAL UTILITIES
            item {
                SettingsSectionHeader(title = "الدعم والنظام")
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSelectionRow(
                            icon = Icons.Default.DeleteSweep,
                            title = "مسح ذاكرة التخزين المؤقت",
                            value = "تنظيف الذاكرة",
                            onClick = {
                                viewModel.clearHistory()
                                Toast.makeText(context, "تم تنظيف السجل وذاكرة الكاش بنجاح!", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 10.sp, color = Color.Gray, lineHeight = 13.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
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
    onTabSelected: (DashboardTab) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val preset = BottomBarPresets.getOrElse(bottomBarColorIndex) { BottomBarPresets[0] }
    
    val barColor = if (bottomBarColorIndex == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        if (isDark) preset.darkBg else preset.lightBg
    }
    
    val onSelectedColor = if (bottomBarColorIndex == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) preset.darkOnSelected else preset.lightOnSelected
    }
    
    val unselectedColor = if (bottomBarColorIndex == 0) {
        Color(0xFF767482)
    } else {
        if (isDark) preset.darkUnselected else preset.lightUnselected
    }
    
    val selectedContainerColor = if (bottomBarColorIndex == 0) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        if (isDark) preset.darkSelectedContainer else preset.lightSelectedContainer
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = barColor,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Note: Order from Left to Right is: Settings, Tools, Folders, Home
            
            // Settings Tab
            BottomTabItem(
                selected = selectedTab == DashboardTab.Settings,
                onSelectedColor = onSelectedColor,
                unselectedColor = unselectedColor,
                selectedContainerColor = selectedContainerColor,
                onClick = { onTabSelected(DashboardTab.Settings) },
                label = "الإعدادات",
                icon = { tint ->
                    Icon(
                        imageVector = if (selectedTab == DashboardTab.Settings) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = "الإعدادات",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )

            // Tools Tab (only display if showTools is active)
            if (showTools) {
                BottomTabItem(
                    selected = selectedTab == DashboardTab.Tools,
                    onSelectedColor = onSelectedColor,
                    unselectedColor = unselectedColor,
                    selectedContainerColor = selectedContainerColor,
                    onClick = { onTabSelected(DashboardTab.Tools) },
                    label = "الأدوات",
                    icon = { tint ->
                        Icon(
                            imageVector = if (selectedTab == DashboardTab.Tools) Icons.Filled.Construction else Icons.Outlined.Construction,
                            contentDescription = "الأدوات",
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }

            // Folders Tab
            BottomTabItem(
                selected = selectedTab == DashboardTab.Folders,
                onSelectedColor = onSelectedColor,
                unselectedColor = unselectedColor,
                selectedContainerColor = selectedContainerColor,
                onClick = { onTabSelected(DashboardTab.Folders) },
                label = "المجلدات",
                icon = { tint ->
                    Icon(
                        imageVector = if (selectedTab == DashboardTab.Folders) Icons.Filled.Folder else Icons.Outlined.Folder,
                        contentDescription = "المجلدات",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )

            // Home Tab
            BottomTabItem(
                selected = selectedTab == DashboardTab.Home,
                onSelectedColor = onSelectedColor,
                unselectedColor = unselectedColor,
                selectedContainerColor = selectedContainerColor,
                onClick = { onTabSelected(DashboardTab.Home) },
                label = "الرئيسية",
                icon = { tint ->
                    Icon(
                        imageVector = if (selectedTab == DashboardTab.Home) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "الرئيسية",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        }
    }
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
fun StorageStatusCard(storageInfo: StorageInfo) {
    if (storageInfo.totalGb <= 0f) return // Only show if storage statistics could be fetched
    
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
                        contentDescription = "مساحة التخزين",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مساحة تخزين الهاتف",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f", storageInfo.usedGb)} جيجابايت مستخدمة من ${String.format(Locale.US, "%.0f", storageInfo.totalGb)} جيجابايت",
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
                    text = "مستعملة: ${storageInfo.usedPercentage}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "المساحة الحرة: ${String.format(Locale.US, "%.1f", storageInfo.availableGb)} جيجابايت",
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
    onDismiss: () -> Unit
) {
    val pageCount = remember(file.filePath) { getPdfPageCount(file.filePath) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2ECE6F8),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        MaterialTheme(colorScheme = glassLavenderColorScheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                        val pageText = if (pageCount > 0) "pages $pageCount • " else ""
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
                            .background(Color(0xFF7C5CFF).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFF7C5CFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Section Title
                Text(
                    text = "Quick Actions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                
                // Action: Add to Favorites / Save to your favorites for quick access
                ActionRowItem(
                    title = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    desc = if (isFavorite) "Remove from your favorite files" else "Save to your favorites for quick access",
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
                    title = "تحديد الملف",
                    desc = "Enter multi-selection mode for this file",
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
                    title = "مشاركة الملف",
                    desc = "Send this PDF to other apps",
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
                    title = "إعادة تسمية",
                    desc = "Change the file name",
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
                    title = "معلومات الملف",
                    desc = "View file details and properties",
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF00ACC1),
                    bgTint = Color(0xFF00ACC1).copy(alpha = 0.1f),
                    onClick = {
                        onFileInfo()
                        onDismiss()
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action: Delete (Dangerous Action styled beautifully as a red banner button just like the screenshot!)
                DeleteActionBanner(
                    title = "حذف الملف",
                    desc = "Permanently remove this file",
                    icon = Icons.Default.Delete,
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
            }
        }
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

