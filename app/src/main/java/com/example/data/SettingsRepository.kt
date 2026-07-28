package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo

data class AppSettings(
    val showToolsTab: Boolean = true,
    val appTheme: String = "system", // "system", "light", "dark"
    val isSystemBrightness: Boolean = true,
    val customBrightness: Float = 0.5f,
    val scrollMode: String = "vertical", // "vertical", "horizontal"
    val defaultZoom: String = "page-width", // "page-width", "page-fit", "1.0"
    val doubleTapZoomFactor: Float = 2.0f, // 1.1f to 5.0f
    val readingTheme: String = "light", // "light", "sepia", "dark", "black"
    val screenOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    val snapToPage: Boolean = false,
    val bottomBarColorIndex: Int = 0
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            showToolsTab = prefs.getBoolean("show_tools_tab", true),
            appTheme = prefs.getString("app_theme", "system") ?: "system",
            isSystemBrightness = prefs.getBoolean("is_system_brightness", true),
            customBrightness = prefs.getFloat("custom_brightness", 0.5f),
            scrollMode = prefs.getString("scroll_mode", "vertical") ?: "vertical",
            defaultZoom = prefs.getString("default_zoom", "page-width") ?: "page-width",
            doubleTapZoomFactor = prefs.getFloat("double_tap_zoom_factor", 2.0f),
            readingTheme = prefs.getString("reading_theme", "light") ?: "light",
            screenOrientation = prefs.getInt("screen_orientation", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
            snapToPage = prefs.getBoolean("snap_to_page", false),
            bottomBarColorIndex = prefs.getInt("bottom_bar_color_index", 0)
        )
    }

    fun setShowToolsTab(show: Boolean) {
        prefs.edit().putBoolean("show_tools_tab", show).apply()
    }

    fun setAppTheme(theme: String) {
        prefs.edit().putString("app_theme", theme).apply()
    }

    fun setSystemBrightness(isSystem: Boolean) {
        prefs.edit().putBoolean("is_system_brightness", isSystem).apply()
    }

    fun setCustomBrightness(brightness: Float) {
        prefs.edit().putFloat("custom_brightness", brightness).putBoolean("is_system_brightness", false).apply()
    }

    fun setScrollMode(mode: String) {
        prefs.edit().putString("scroll_mode", mode).apply()
    }

    fun setDefaultZoom(zoom: String) {
        prefs.edit().putString("default_zoom", zoom).apply()
    }

    fun setDoubleTapZoomFactor(factor: Float) {
        prefs.edit().putFloat("double_tap_zoom_factor", factor).apply()
    }

    fun setReadingTheme(theme: String) {
        prefs.edit().putString("reading_theme", theme).apply()
    }

    fun setScreenOrientation(orientation: Int) {
        prefs.edit().putInt("screen_orientation", orientation).apply()
    }

    fun setSnapToPage(snap: Boolean) {
        prefs.edit().putBoolean("snap_to_page", snap).apply()
    }

    fun setBottomBarColorIndex(index: Int) {
        prefs.edit().putInt("bottom_bar_color_index", index).apply()
    }
}
