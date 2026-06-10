# LSPosed API - keep all hook classes
-keep class game.miplus.handwriting.hook.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep all classes that might be used by Xposed
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends de.robv.android.xposed.IXposedHookLoadPackage

# Keep JavaScript injection string
-keepclassmembers class game.miplus.handwriting.hook.MainHook {
    private static final java.lang.String INJECT_JS;
}
