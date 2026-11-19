# Preserve annotations, generics, and enclosing metadata needed by Room & Kotlin Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Room entities, DAOs, and generated Database_Impl
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# WorkManager Workers & AppWidgetProviders / Receivers / Services
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.widgets.** { *; }
-keep class com.example.notifications.** { *; }
-keep class com.example.timer.TimerForegroundService { *; }
-keep class com.example.timer.TimerAlarmReceiver { *; }

# ViewModels & Application
-keep class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.example.SyllabusApplication { *; }

# Kotlin Serialization (Navigation routes + Backup JSON models)
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keep class com.example.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.example.data.backup.**$$serializer { *; }
-keep class com.example.data.backup.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.instrument.**

# Strip verbose/debug logging calls in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
