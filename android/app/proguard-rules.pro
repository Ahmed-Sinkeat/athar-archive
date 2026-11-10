# Athar application-specific R8 rules.
#
# Keep this file even while empty: release and benchmark variants are minified
# and reference it from app/build.gradle.kts. Library consumer rules and the
# Android optimized defaults currently cover the implemented shell.


# 4.7: what the app reaches by name or reflection, stated here instead of leaning on library defaults.
# kotlinx.serialization: the catalog, frame and API models are decoded through generated serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.atharchive.** { *** Companion; }
-keepclasseswithmembers class com.atharchive.** { kotlinx.serialization.KSerializer serializer(...); }
# WorkManager creates workers by class name (adhkar reminders, downloads).
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# The launcher aliases name MainActivity from the manifest.
-keep class com.atharchive.MainActivity
