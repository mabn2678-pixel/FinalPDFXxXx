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

      MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
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
    val action = intent.action
    val data: Uri? = intent.data
    if (Intent.ACTION_VIEW == action && data != null) {
      try {
        val contentResolver = contentResolver
        var fileName = "opened_file"
        
        // Try to query display name
        contentResolver.query(data, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrEmpty()) {
              fileName = name
            }
          }
        }
        
        if (fileName == "opened_file") {
          data.lastPathSegment?.let { segment ->
            val clean = segment.substringAfterLast("/")
            if (clean.isNotEmpty()) {
              fileName = clean
            }
          }
        }
        
        if (!fileName.endsWith(".pdf", ignoreCase = true)) {
          fileName = "$fileName.pdf"
        }

        val cacheFile = File(cacheDir, fileName)
        contentResolver.openInputStream(data)?.use { inputStream ->
          FileOutputStream(cacheFile).use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }
        
        if (cacheFile.exists() && cacheFile.length() > 0) {
          val displayName = fileName.replace(".pdf", "", ignoreCase = true).replace("_", " ")
          viewModel.selectPdf(cacheFile.absolutePath, displayName)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}
