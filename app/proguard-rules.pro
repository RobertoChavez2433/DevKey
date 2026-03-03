# Keep JNI native methods (called from C++)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the JNI bridge class (old package path)
-keep class org.pocketworkstation.pckeyboard.BinaryDictionary { *; }

# Keep IME service and activities referenced in AndroidManifest
-keep class dev.devkey.keyboard.LatinIME { *; }
-keep class dev.devkey.keyboard.LatinIMEBackupAgent { *; }
-keep class dev.devkey.keyboard.ui.settings.DevKeySettingsActivity { *; }
-keep class dev.devkey.keyboard.feature.voice.PermissionActivity { *; }
-keep class dev.devkey.keyboard.Main { *; }
-keep class dev.devkey.keyboard.InputLanguageSelection { *; }
-keep class dev.devkey.keyboard.NotificationReceiver { *; }

# Keep custom preference widgets referenced by name in XML
# TODO: Remove these once prefs.xml and prefs_*.xml files are deleted
-keep class dev.devkey.keyboard.SeekBarPreferenceString { *; }
-keep class dev.devkey.keyboard.SeekBarPreference { *; }
-keep class dev.devkey.keyboard.AutoSummaryListPreference { *; }
-keep class dev.devkey.keyboard.AutoSummaryEditTextPreference { *; }
-keep class dev.devkey.keyboard.VibratePreference { *; }

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.devkey.keyboard.data.export.**$$serializer { *; }
-keepclassmembers class dev.devkey.keyboard.data.export.** {
    *** Companion;
}

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep Keyboard and Key classes used reflectively
-keep class dev.devkey.keyboard.Keyboard { *; }
-keep class dev.devkey.keyboard.Keyboard$Key { *; }

# Compose
-dontwarn androidx.compose.**
