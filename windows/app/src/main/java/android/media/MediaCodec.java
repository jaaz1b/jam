package android.media;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * MediaCodec on the PC: the decoder for audio/raw (what MediaExtractor gives here, already decoded by
 * ffmpeg) passes the PCM through; synchronous and callback use work as on Android.
 */
public final class MediaCodec {
    public static final int BUFFER_FLAG_KEY_FRAME = 1, BUFFER_FLAG_CODEC_CONFIG = 2, BUFFER_FLAG_END_OF_STREAM = 4, BUFFER_FLAG_PARTIAL_FRAME = 8;
    public static final int INFO_TRY_AGAIN_LATER = -1, INFO_OUTPUT_FORMAT_CHANGED = -2, INFO_OUTPUT_BUFFERS_CHANGED = -3;
    public static final int CONFIGURE_FLAG_ENCODE = 1;
    public static final class BufferInfo { public int offset, size, flags; public long presentationTimeUs; public void set(int o, int s, long t, int f) { offset = o; size = s; presentationTimeUs = t; flags = f; } }
    public static final class CodecException extends IllegalStateException { public CodecException(String m) { super(m); } public boolean isTransient() { return false; } public boolean isRecoverable() { return false; } public String getDiagnosticInfo() { return getMessage(); } public int getErrorCode() { return 0; } }
    public static final class CryptoException extends RuntimeException { public CryptoException(String m) { super(m); } }
    public abstract static class Callback {
        public abstract void onInputBufferAvailable(MediaCodec codec, int index);
        public abstract void onOutputBufferAvailable(MediaCodec codec, int index, BufferInfo info);
        public abstract void onError(MediaCodec codec, CodecException e);
        public abstract void onOutputFormatChanged(MediaCodec codec, MediaFormat format);
    }
    private static final int SLOTS = 4, SIZE = 64 * 1024;
    private final ByteBuffer[] in = new ByteBuffer[SLOTS], out = new ByteBuffer[SLOTS];
    private final ArrayDeque<Integer> freeIn = new ArrayDeque<>();
    private final ArrayDeque<Object[]> ready = new ArrayDeque<>();
    private final ArrayDeque<Integer> freeOut = new ArrayDeque<>();
    private MediaFormat format; private boolean formatSent; private Callback callback; private java.util.concurrent.ExecutorService loop;
    private volatile boolean running;

    private MediaCodec(String mime) {
        if (!"audio/raw".equals(mime)) throw new IllegalArgumentException("no decoder for " + mime + " on the PC (the extractor gives audio/raw)");
        for (int i = 0; i < SLOTS; i++) { in[i] = ByteBuffer.allocate(SIZE); out[i] = ByteBuffer.allocate(SIZE); freeIn.add(i); freeOut.add(i); }
    }
    public static MediaCodec createDecoderByType(String mime) throws java.io.IOException {
        try { return new MediaCodec(mime); } catch (IllegalArgumentException e) { throw new java.io.IOException(e.getMessage()); }
    }
    public static MediaCodec createEncoderByType(String mime) throws java.io.IOException { throw new java.io.IOException("no encoder on the PC"); }
    public static MediaCodec createByCodecName(String name) throws java.io.IOException { return createDecoderByType("audio/raw"); }
    public void setCallback(Callback cb) { callback = cb; }
    public void setCallback(Callback cb, android.os.Handler h) { callback = cb; }
    public void configure(MediaFormat f, Object surface, Object crypto, int flags) { format = f; }
    public synchronized void start() {
        running = true;
        if (callback != null) {
            loop = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "media-codec"); t.setDaemon(true); return t; });
            loop.execute(() -> { callback.onOutputFormatChanged(this, getOutputFormat()); formatSent = true; for (int i = 0; i < SLOTS; i++) { int s; synchronized (this) { s = freeIn.poll(); } final int x = s; callback.onInputBufferAvailable(this, x); } });
        }
    }
    public synchronized int dequeueInputBuffer(long timeoutUs) { Integer i = freeIn.poll(); return i == null ? INFO_TRY_AGAIN_LATER : i; }
    public ByteBuffer getInputBuffer(int i) { in[i].clear(); return in[i]; }
    public void queueInputBuffer(int i, int offset, int size, long ptsUs, int flags) {
        int o;
        synchronized (this) {
            Integer free = freeOut.poll();
            if (free == null) { freeIn.add(i); return; }
            o = free;
            ByteBuffer src = in[i]; ByteBuffer dst = out[o]; dst.clear();
            src.limit(offset + size); src.position(offset); dst.put(src); dst.flip();
            BufferInfo info = new BufferInfo(); info.set(0, size, ptsUs, flags);
            ready.add(new Object[] { o, info });
            freeIn.add(i);
        }
        if (callback != null && loop != null) loop.execute(() -> {
            Object[] r; synchronized (this) { r = ready.poll(); }
            if (r != null) callback.onOutputBufferAvailable(this, (Integer) r[0], (BufferInfo) r[1]);
            Integer next; synchronized (this) { next = freeIn.poll(); }
            if (next != null && running && (flags & BUFFER_FLAG_END_OF_STREAM) == 0) callback.onInputBufferAvailable(this, next);
        });
    }
    public synchronized int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        if (!formatSent) { formatSent = true; return INFO_OUTPUT_FORMAT_CHANGED; }
        Object[] r = ready.poll(); if (r == null) return INFO_TRY_AGAIN_LATER;
        BufferInfo b = (BufferInfo) r[1]; info.set(b.offset, b.size, b.presentationTimeUs, b.flags); return (Integer) r[0];
    }
    public ByteBuffer getOutputBuffer(int i) { return out[i]; }
    public MediaFormat getOutputFormat() { MediaFormat f = new MediaFormat(); f.setString(MediaFormat.KEY_MIME, "audio/raw"); f.setInteger(MediaFormat.KEY_SAMPLE_RATE, format == null ? 44100 : format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)); f.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format == null ? 2 : format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)); f.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT); return f; }
    public MediaFormat getInputFormat() { return format; }
    public synchronized void releaseOutputBuffer(int i, boolean render) { freeOut.add(i); }
    public synchronized void flush() { ready.clear(); freeIn.clear(); freeOut.clear(); for (int i = 0; i < SLOTS; i++) { freeIn.add(i); freeOut.add(i); } }
    public void stop() { running = false; }
    public void release() { running = false; if (loop != null) loop.shutdownNow(); }
    public void signalEndOfInputStream() {}
    public String getName() { return "jam.ffmpeg.raw"; }
    public MediaCodecInfo getCodecInfo() { return new MediaCodecInfo(); }
}
