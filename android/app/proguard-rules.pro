-keep class com.scan.warehouse.model.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
