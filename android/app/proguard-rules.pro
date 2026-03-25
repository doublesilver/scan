-keep class com.scan.warehouse.model.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
