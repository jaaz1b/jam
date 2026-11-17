package androidx.media3.datasource;

public final class HttpUtil {
    public static String buildRangeRequestHeader(long position, long length) {
        if (position == 0 && length == -1) return null;
        StringBuilder s = new StringBuilder("bytes=").append(position).append('-');
        if (length != -1) s.append(position + length - 1);
        return s.toString();
    }
    public static long getDocumentSize(String contentRange) { if (contentRange == null) return -1; int i = contentRange.lastIndexOf('/'); if (i < 0) return -1; try { return Long.parseLong(contentRange.substring(i + 1).trim()); } catch (Exception e) { return -1; } }
    public static long getContentLength(String contentLength, String contentRange) {
        long len = -1;
        if (contentLength != null && !contentLength.isEmpty()) try { len = Long.parseLong(contentLength); } catch (Exception ignored) {}
        if (contentRange != null && !contentRange.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("bytes (\\d+)-(\\d+)/(?:\\d+|\\*)").matcher(contentRange);
            if (m.find()) { long r = Long.parseLong(m.group(2)) - Long.parseLong(m.group(1)) + 1; if (len < 0) len = r; }
        }
        return len;
    }
}
