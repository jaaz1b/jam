package com.music.spotui.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    @Test fun comparesNumericallyNotAsText() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(UpdateChecker.isNewer("0.9.1", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
    }
    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(UpdateChecker.isNewer("0.9.0", "0.9.0"))
        assertFalse(UpdateChecker.isNewer("0.9", "0.9.0"))
        assertFalse(UpdateChecker.isNewer("0.8.9", "0.9.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0-beta", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "2.0.0-beta.3"))
        assertTrue(UpdateChecker.isNewer("2.0.0-beta.2", "2.0.0-beta.1"))
        assertFalse(UpdateChecker.isNewer("2.0.0-beta.1", "2.0.0-beta.1"))
        assertTrue(UpdateChecker.isNewer("2.0.1-beta.1", "2.0.0"))
    }
    @Test fun ordersBetasAndReleases() {
        assertTrue(UpdateChecker.isNewer("2.0.2-beta.7", "2.0.2-beta.6"))
        assertFalse(UpdateChecker.isNewer("2.0.2-beta.6", "2.0.2-beta.7"))
        assertTrue(UpdateChecker.isNewer("2.0.2-beta.10", "2.0.2-beta.9"))
        assertTrue(UpdateChecker.isNewer("2.0.2", "2.0.2-beta.7"))
        assertFalse(UpdateChecker.isNewer("2.0.2-beta.7", "2.0.2"))
        assertTrue(UpdateChecker.isNewer("2.0.3-beta.1", "2.0.2"))
        assertTrue(UpdateChecker.isNewer("2.1.0-beta.1", "2.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0.2-beta.1", "2.0.1-beta.9"))
    }
    @Test fun notesKeepTheChangelogSectionAsPlainLines() {
        val body = "- **Local Files**: a folder\n  - nested [link](https://x.y)\n\n**Full Changelog**: https://github.com/a/b/compare/v1...v2"
        assertEquals("• Local Files: a folder\n  • nested link", UpdateChecker.cleanNotes(body))
    }
    // Jam: one channel; releases are "android-v<version>" pre-releases carrying
    // Jam-Android-<version>.apk (arm64) and Jam-Android-<version>-x86_64.apk.
    @Test fun findsThisAppsApkForThisAbi() {
        val r = org.json.JSONObject("""{"tag_name":"android-v3.0.0-build.12","assets":[
            {"name":"Jam-Android-3.0.0-build.12-x86_64.apk","browser_download_url":"https://x/x86.apk"},
            {"name":"Jam-Android-3.0.0-build.12.apk","browser_download_url":"https://x/arm.apk"},
            {"name":"smoke-test.zip","browser_download_url":"https://x/smoke.zip"}]}""")
        assertEquals("https://x/arm.apk", UpdateChecker.apkAsset(r, beta = false, abis = arm64, name = "Jam-Android"))
        assertEquals("https://x/x86.apk", UpdateChecker.apkAsset(r, beta = false, abis = listOf("x86_64", "x86"), name = "Jam-Android"))
        // A phone whose ABIs the releases do not carry gets no link (the page opens).
        assertEquals("", UpdateChecker.apkAsset(r, beta = false, abis = listOf("armeabi-v7a", "armeabi"), name = "Jam-Android"))
        assertEquals("", UpdateChecker.apkAsset(org.json.JSONObject("{}"), beta = false, abis = arm64, name = "Jam-Android"))
    }
    @Test fun anotherAppsApkIsNeverTaken() {
        val other = org.json.JSONObject("""{"tag_name":"android-v3.0.0-build.12","assets":[
            {"name":"Orpheon-3.0.0-build.12-ARMv8.apk","browser_download_url":"https://x/o8.apk"},
            {"name":"Jam-Setup-3.2.0.exe","browser_download_url":"https://x/setup.exe"}]}""")
        assertEquals("", UpdateChecker.apkAsset(other, beta = false, abis = arm64, name = "Jam-Android"))
    }
    @Test fun onlyAnAndroidTagIsARelease() {
        assertTrue(UpdateChecker.isVersionTag("android-v3.0.0-build.12"))
        assertTrue(UpdateChecker.isVersionTag("android-v2.4.1-build.30"))
        assertFalse(UpdateChecker.isVersionTag("v3.2.0-build.5"))
        assertFalse(UpdateChecker.isVersionTag("linux-v3.2.0-build.1"))
        assertFalse(UpdateChecker.isVersionTag(""))
        assertEquals("3.0.0-build.12", UpdateChecker.versionOf("android-v3.0.0-build.12"))
    }

    /** The repository also holds the desktop's releases and Jam 2.x's: only Jam 3's Android builds are offered. */
    @Test fun onlyThisAppsReleasesAreOffered() {
        fun rel(tag: String, pre: Boolean, vararg apks: String) = org.json.JSONObject().put("tag_name", tag).put("prerelease", pre)
            .put("html_url", "https://x/$tag").put("body", "")
            .put("assets", org.json.JSONArray(apks.map { org.json.JSONObject().put("name", it).put("browser_download_url", "https://x/$it") }))
        val list = listOf(
            rel("v3.2.0-build.5", true, "Jam-Setup-3.2.0.exe"),
            rel("android-v2.4.1-build.30", true, "Jam-Android-2.4.1.apk"),
            rel("android-v3.0.0-build.12", true, "Jam-Android-3.0.0-build.12.apk"),
            rel("android-v3.0.0-build.11", true, "Jam-Android-3.0.0-build.11.apk"),
            rel("android-v3.0.0-build.13", true),
        )
        assertEquals("3.0.0-build.12", UpdateChecker.newest(list, beta = false, abis = arm64, name = "Jam-Android")?.version)
        assertTrue(UpdateChecker.isNewer("3.0.0-build.12", "3.0.0-build.11"))
        assertFalse(UpdateChecker.isNewer("2.4.1-build.30", "3.0.0-build.11"))
    }
}
