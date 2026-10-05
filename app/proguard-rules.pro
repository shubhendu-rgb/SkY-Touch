# Add project specific ProGuard rules here.

# Do safe dead-code shrinking without obfuscating class names or aggressive bytecode optimization
-dontoptimize
-dontobfuscate

# Keep all application code completely intact and un-obfuscated
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }

# Keep all Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep class androidx.core.content.FileProvider { *; }

# Keep Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration
-keep class androidx.room.paging.** { *; }
-dontwarn androidx.room.paging.**

# Keep WorkManager Worker reflection constructor
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }

# Keep Compose Runtime and Coroutines
-keep class androidx.compose.runtime.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Keep debug attributes
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*
