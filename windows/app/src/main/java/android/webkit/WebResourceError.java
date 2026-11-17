package android.webkit;

public abstract class WebResourceError {
    public abstract int getErrorCode();
    public abstract CharSequence getDescription();
    public static WebResourceError of(int code, String description) {
        return new WebResourceError() {
            public int getErrorCode() { return code; }
            public CharSequence getDescription() { return description; }
        };
    }
}
