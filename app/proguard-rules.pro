# R8 rules for Otter.
#
# Almost everything this app depends on ships its own consumer rules — OkHttp, Coil, Media3 and
# AndroidX all do — so this file only covers what R8 cannot see from the code itself.

# The view model is never constructed by name in Kotlin: ViewModelProvider's default factory
# reflects for an (Application) constructor. Without this the constructor is unused code and
# R8 removes it, which fails at runtime rather than at build time.
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# The launcher entry point is an <activity-alias> pointing at the real activity. The alias name
# is not a class, so keep the target under its original name to be certain the manifest and the
# shipped shortcut still resolve to it after a rebrand that already moved the package once.
-keep class app.otter.client.MainActivity { *; }

# Media3 reads the available extractors and decoders reflectively when it builds a renderer or
# picks an extractor for a container it has not seen before.
-dontwarn androidx.media3.**

# okhttp/okio pull in optional platform integrations that are absent on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Keep source line numbers so a release stack trace can be read against the mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
