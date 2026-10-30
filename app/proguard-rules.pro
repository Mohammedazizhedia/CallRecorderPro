# Compose / Kotlin
-dontwarn org.jetbrains.kotlin.**
-keepclassmembers class * implements androidx.compose.ui.Modifier$Element { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep model classes (Room entities)
-keep class com.callrecorder.pro.data.db.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
