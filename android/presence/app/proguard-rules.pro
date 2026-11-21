# The settings page calls these through addJavascriptInterface.
-keepclassmembers class app.jam.presence.SettingsActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
