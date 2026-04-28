# Add project-specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Keep Gson model classes
-keep class com.joy.englishtube.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Bridge methods exposed to JS via @JavascriptInterface (must keep their names)
-keepclassmembers class com.joy.englishtube.ui.player.WebViewPlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
