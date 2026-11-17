package androidx.webkit;
public class WebMessageCompat {
    private final String data;
    public WebMessageCompat(String data) { this.data = data; }
    public String getData() { return data; }
    public int getType() { return 0; }
}
