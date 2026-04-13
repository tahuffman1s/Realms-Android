# Keep Kotlin serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.realmsoffate.game.**$$serializer { *; }
-keepclassmembers class com.realmsoffate.game.** {
    *** Companion;
}
-keepclasseswithmembers class com.realmsoffate.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
