# Consumer ProGuard rules for NativeRuntimeSDK
# The published API lives in com.roni.library.os (the Gradle namespace matches it).
-keep public class com.roni.library.os.** { public *; }
-keep interface com.roni.library.os.** { *; }
-keep enum com.roni.library.os.** { *; }
-keep class com.roni.library.os.bootstrap.** { *; }
-keep class com.roni.library.os.activity.** { *; }
-keep class com.roni.library.os.NativeRuntimeResult { *; }
-keep class com.roni.library.os.RuntimeConfiguration { *; }
-keep class com.roni.library.os.RuntimeSession { *; }
-keepclassmembers class * implements com.roni.library.os.LifecycleObserver { *; }
