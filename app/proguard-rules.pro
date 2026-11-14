# Project-specific ProGuard rules.

# kotlinx.serialization: keep generated serializers and annotations used at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.freehtml.app.data.model.RecentFile { *; }
-keep class com.freehtml.app.data.model.DocumentType { *; }
-keepclassmembers enum com.freehtml.app.data.model.DocumentType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
