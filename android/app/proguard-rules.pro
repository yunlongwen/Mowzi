# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Retrofit
-keepattributes Signature
-keepattributes *Annotation*

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data classes for serialization
-keepclassmembers class com.mowzi.app.data.remote.dto.** {
    *;
}