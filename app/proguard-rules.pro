-dontwarn android.media.**
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Đã sửa lại đúng chính tả chữ "Signature" và thêm Annotation
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep class com.whispercppdemo.data.dto.** { *; }
-keep class com.whispercppdemo.data.model.** { *; }
-keep class com.whispercppdemo.service.** { *; }

# Thêm dòng này để hỗ trợ Gson tốt hơn (phòng hờ)
-keep class com.google.gson.** { *; }