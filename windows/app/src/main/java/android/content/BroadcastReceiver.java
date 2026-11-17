package android.content;

public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);
    public final PendingResult goAsync() { return new PendingResult(); }
    public final boolean isOrderedBroadcast() { return false; }
    public final void abortBroadcast() {}
    public final void setResultCode(int c) {}
    public final void setResultData(String d) {}
    public static class PendingResult {
        private int resultCode; private String resultData;
        public final void finish() {}
        public final void setResultCode(int c) { resultCode = c; }
        public final int getResultCode() { return resultCode; }
        public final void setResultData(String d) { resultData = d; }
        public final String getResultData() { return resultData; }
    }
}
