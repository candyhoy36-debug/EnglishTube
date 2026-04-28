# Add project-specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Keep Gson model classes
-keep class com.joy.englishtube.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# YouTube player
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
