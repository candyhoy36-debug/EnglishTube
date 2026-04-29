# Add project-specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Keep Gson model classes
-keep class com.joy.englishtube.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# NewPipeExtractor — see README of TeamNewPipe/NewPipeExtractor for the
# canonical keep rules. The library uses reflection on its service +
# extractor classes plus jackson-style JSON via nanojson, and javax
# annotations are not on the Android runtime classpath.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.annotation.**
-dontwarn org.jsoup.**
-dontwarn com.grack.nanojson.**

# Bridge methods exposed to JS via @JavascriptInterface (must keep their names)
-keepclassmembers class com.joy.englishtube.ui.player.WebViewPlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
