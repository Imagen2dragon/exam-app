# kotlinx.serialization keeps generated serializers via its own consumer rules.
-keepattributes *Annotation*, InnerClasses
# Strip verbose logs from release builds.
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }
