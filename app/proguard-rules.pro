# Preserve JavascriptInterface methods for WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# PDFBox Android rules
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# MLKit
-keep class com.google.mlkit.** { *; }

# Moshi & Room
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}

