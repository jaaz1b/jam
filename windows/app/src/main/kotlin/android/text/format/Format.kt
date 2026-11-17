package android.text.format

object Formatter {
    @JvmStatic fun formatShortFileSize(c: android.content.Context?, bytes: Long): String = format(bytes, 1)
    @JvmStatic fun formatFileSize(c: android.content.Context?, bytes: Long): String = format(bytes, 2)
    private fun format(b: Long, digits: Int): String {
        val units = listOf("B", "kB", "MB", "GB", "TB"); var v = b.toDouble(); var u = 0
        while (v >= 900 && u < units.size - 1) { v /= 1000; u++ }
        val d = if (u == 0 || v >= 100) 0 else if (v >= 10) minOf(1, digits - 1).coerceAtLeast(0) else digits
        return String.format(app.jam.desktop.shim.Res.locale, "%.${d}f %s", v, units[u])
    }
}
object DateUtils {
    const val SECOND_IN_MILLIS = 1000L; const val MINUTE_IN_MILLIS = 60_000L; const val HOUR_IN_MILLIS = 3_600_000L; const val DAY_IN_MILLIS = 86_400_000L; const val WEEK_IN_MILLIS = 604_800_000L; const val YEAR_IN_MILLIS = 31_449_600_000L
    const val FORMAT_ABBREV_RELATIVE = 0x40000; const val FORMAT_SHOW_DATE = 16; const val FORMAT_SHOW_TIME = 1; const val FORMAT_ABBREV_MONTH = 0x10000; const val FORMAT_SHOW_YEAR = 4; const val FORMAT_NO_YEAR = 8
    @JvmStatic fun getRelativeTimeSpanString(time: Long, now: Long, minRes: Long, flags: Int): CharSequence {
        val diff = now - time; val past = diff >= 0; val d = kotlin.math.abs(diff)
        val l = app.jam.desktop.shim.Res.locale
        val (n, unit) = when {
            d < maxOf(minRes, MINUTE_IN_MILLIS) -> return if (minRes <= SECOND_IN_MILLIS) "${d / 1000} s" else java.text.MessageFormat.format("{0}", "0 min")
            d < HOUR_IN_MILLIS -> d / MINUTE_IN_MILLIS to java.time.temporal.ChronoUnit.MINUTES
            d < DAY_IN_MILLIS -> d / HOUR_IN_MILLIS to java.time.temporal.ChronoUnit.HOURS
            d < WEEK_IN_MILLIS -> d / DAY_IN_MILLIS to java.time.temporal.ChronoUnit.DAYS
            else -> return java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(l).format(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()))
        }
        val abbrev = flags and FORMAT_ABBREV_RELATIVE != 0
        val u = when (unit) { java.time.temporal.ChronoUnit.MINUTES -> if (abbrev) "min" else if (n == 1L) "minute" else "minutes"; java.time.temporal.ChronoUnit.HOURS -> if (abbrev) "hr" else if (n == 1L) "hour" else "hours"; else -> if (n == 1L) "day" else "days" }
        return if (past) "$n $u ago" else "in $n $u"
    }
    @JvmStatic fun getRelativeTimeSpanString(time: Long): CharSequence = getRelativeTimeSpanString(time, System.currentTimeMillis(), MINUTE_IN_MILLIS, 0)
    @JvmStatic fun formatDateTime(c: android.content.Context?, millis: Long, flags: Int): String =
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(app.jam.desktop.shim.Res.locale).format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
    @JvmStatic fun formatElapsedTime(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
    @JvmStatic fun isToday(t: Long): Boolean = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
}
object DateFormat { @JvmStatic fun is24HourFormat(c: android.content.Context?): Boolean = !java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT, app.jam.desktop.shim.Res.locale).let { it as? java.text.SimpleDateFormat }?.toPattern().orEmpty().contains('a') }
