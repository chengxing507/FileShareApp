# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard configuration.

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep our app classes
-keep class com.example.fileshare.** { *; }