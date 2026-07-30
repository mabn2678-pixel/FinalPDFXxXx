package com.example

import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.PdfDatabase
import com.example.ui.DashboardScreen
import com.example.ui.PdfViewModel
import com.example.ui.PdfViewModelFactory
import com.example.ui.Screen
import com.example.ui.ViewerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    PDFBoxResourceLoader.init(applicationContext)

    val database = PdfDatabase.getDatabase(applicationContext)
    val viewModel: PdfViewModel by viewModels {
      PdfViewModelFactory(database.recentPdfDao())
    }

    // Initialize state and check welcome screen status
    viewModel.initialize(applicationContext)

    // Handle incoming intent if the app is launched to view a PDF file
    handleIntent(intent, viewModel)

    setContent {
      val state by viewModel.uiState.collectAsState()
      val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
      val isDark = when (state.appTheme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
      }

      MyApplicationTheme(
        darkTheme = isDark,
        dynamicColor = false,
        colorPresetIndex = state.bottomBarColorIndex
      ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
          when (state.currentScreen) {
            Screen.Welcome -> {
              com.example.ui.WelcomeScreen(viewModel = viewModel)
            }
            Screen.Dashboard -> {
              DashboardScreen(viewModel = viewModel)
            }
            Screen.Viewer -> {
              ViewerScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val database = PdfDatabase.getDatabase(applicationContext)
    val viewModel: PdfViewModel by viewModels {
      PdfViewModelFactory(database.recentPdfDao())
    }
    handleIntent(intent, viewModel)
  }

  private fun handleIntent(intent: Intent?, viewModel: PdfViewModel) {
    if (intent == null) return
    val action = intent.action ?: return
    val isSupportedAction = action == Intent.ACTION_VIEW || 
                          action == Intent.ACTION_EDIT || 
                          action == Intent.ACTION_SEND || 
                          action == Intent.ACTION_SEND_MULTIPLE

    if (!isSupportedAction) return

    val targetUri: Uri? = intent.data 
      ?: @Suppress("DEPRECATION") (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
      ?: if (intent.clipData != null && intent.clipData!!.itemCount > 0) intent.clipData!!.getItemAt(0).uri else null

    if (targetUri != null) {
      lifecycleScope.launch(Dispatchers.IO) {
        try {
          val contentResolver = contentResolver
          var rawName = ""

          // Try to query display name
          contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
              val name = cursor.getString(nameIndex)
              if (!name.isNullOrEmpty()) {
                rawName = name
              }
            }
          }

          if (rawName.isEmpty()) {
            targetUri.lastPathSegment?.let { segment ->
              val clean = segment.substringAfterLast("/")
              if (clean.isNotEmpty()) {
                rawName = clean
              }
            }
          }

          if (rawName.isEmpty()) {
            rawName = "imported_document.pdf"
          }

          if (!rawName.endsWith(".pdf", ignoreCase = true)) {
            rawName = "$rawName.pdf"
          }

          val cleanDisplayName = rawName.replace(".pdf", "", ignoreCase = true).replace("_", " ")

          // 1. Check direct file URI scheme
          if (targetUri.scheme == "file") {
            val directPath = targetUri.path
            if (directPath != null && File(directPath).exists()) {
              withContext(Dispatchers.Main) {
                viewModel.selectPdf(directPath, cleanDisplayName)
              }
              return@launch
            }
          }

          // 2. Query Room DB to check if file was previously imported or scanned
          val database = PdfDatabase.getDatabase(applicationContext)
          val existingPdf = database.recentPdfDao().getPdfByName(cleanDisplayName)
            ?: database.recentPdfDao().getPdfByName(rawName)

          if (existingPdf != null && File(existingPdf.filePath).exists()) {
            withContext(Dispatchers.Main) {
              viewModel.selectPdf(existingPdf.filePath, existingPdf.fileName)
            }
            return@launch
          }

          // 3. Save to cache using clean filename without timestamp prefixes
          val sanitizedFileName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
          val cacheFile = File(cacheDir, sanitizedFileName)

          contentResolver.openInputStream(targetUri)?.use { inputStream ->
            FileOutputStream(cacheFile).use { outputStream ->
              inputStream.copyTo(outputStream)
            }
          }

          if (cacheFile.exists() && cacheFile.length() > 0) {
            withContext(Dispatchers.Main) {
              viewModel.selectPdf(cacheFile.absolutePath, cleanDisplayName)
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }
}
