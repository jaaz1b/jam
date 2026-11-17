package androidx.media3.common.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** media3's BaseAudioProcessor, the same contract: subclasses configure, then queue input and fill output buffers. */
public abstract class BaseAudioProcessor implements AudioProcessor {
    protected AudioFormat inputAudioFormat = AudioFormat.NOT_SET, outputAudioFormat = AudioFormat.NOT_SET;
    private AudioFormat pendingInputAudioFormat = AudioFormat.NOT_SET, pendingOutputAudioFormat = AudioFormat.NOT_SET;
    private ByteBuffer buffer = EMPTY_BUFFER, outputBuffer = EMPTY_BUFFER;
    private boolean inputEnded;
    @Override public final AudioFormat configure(AudioFormat input) throws UnhandledAudioFormatException {
        pendingInputAudioFormat = input; pendingOutputAudioFormat = onConfigure(input);
        return isActive() ? pendingOutputAudioFormat : AudioFormat.NOT_SET;
    }
    @Override public boolean isActive() { return pendingOutputAudioFormat != AudioFormat.NOT_SET; }
    @Override public void queueEndOfStream() { inputEnded = true; onQueueEndOfStream(); }
    @Override public ByteBuffer getOutput() { ByteBuffer o = outputBuffer; outputBuffer = EMPTY_BUFFER; return o; }
    @Override public boolean isEnded() { return inputEnded && outputBuffer == EMPTY_BUFFER; }
    @Override public final void flush() { flush(StreamMetadata.DEFAULT); }
    @Override public final void flush(StreamMetadata m) {
        outputBuffer = EMPTY_BUFFER; inputEnded = false;
        inputAudioFormat = pendingInputAudioFormat; outputAudioFormat = pendingOutputAudioFormat;
        onFlush(m);
    }
    @Override public final void reset() { flush(); buffer = EMPTY_BUFFER; pendingInputAudioFormat = pendingOutputAudioFormat = inputAudioFormat = outputAudioFormat = AudioFormat.NOT_SET; onReset(); }
    protected final ByteBuffer replaceOutputBuffer(int size) {
        if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()); else buffer.clear();
        outputBuffer = buffer; return buffer;
    }
    protected final boolean hasPendingOutput() { return outputBuffer.hasRemaining(); }
    protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException { return AudioFormat.NOT_SET; }
    protected void onQueueEndOfStream() {}
    protected void onFlush() {}
    protected void onFlush(StreamMetadata m) { onFlush(); }
    protected void onReset() {}
}
