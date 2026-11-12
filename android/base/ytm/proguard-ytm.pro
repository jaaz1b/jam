# Jam: YouTube Music mode (ytm/src-tauri/gen/android/app). Its release build was not minified;
# in the Jam app it is, so everything the Rust engine reaches by JNI, Tauri reaches by reflection and
# the pages reach by @JavascriptInterface stays as it is.
-keep class app.jam.android.** { *; }
-keep class app.tauri.** { *; }
-keep class com.plugin.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn app.tauri.**
-dontwarn com.fasterxml.jackson.databind.ext.**
-keep class com.fasterxml.jackson.** { *; }
# Jackson (the Tauri runtime's JSON) refers to JDK classes Android doesn't have; it never loads them there.
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn com.fasterxml.jackson.databind.**
