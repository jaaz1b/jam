package androidx.media3.exoplayer;

import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/** The audio half of media3's renderers: what makes the sink the decoded sound goes through. */
public class DefaultRenderersFactory implements RenderersFactory {
    public static final int EXTENSION_RENDERER_MODE_OFF = 0, EXTENSION_RENDERER_MODE_ON = 1, EXTENSION_RENDERER_MODE_PREFER = 2;
    private final android.content.Context context;
    private boolean floatOutput, trackParams;
    public DefaultRenderersFactory(android.content.Context c) { context = c; }
    public final DefaultRenderersFactory setEnableAudioFloatOutput(boolean b) { floatOutput = b; return this; }
    public final DefaultRenderersFactory setEnableAudioTrackPlaybackParams(boolean b) { trackParams = b; return this; }
    public final DefaultRenderersFactory setExtensionRendererMode(int m) { return this; }
    public final DefaultRenderersFactory setEnableDecoderFallback(boolean b) { return this; }
    public final DefaultRenderersFactory forceEnableMediaCodecAsynchronousQueueing() { return this; }
    public final DefaultRenderersFactory setAllowedVideoJoiningTimeMs(long ms) { return this; }
    protected AudioSink buildAudioSink(android.content.Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
        return new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).build();
    }
    /** The engine's call (app.jam.desktop.media): the sink, made by this factory or a subclass. */
    public final AudioSink desktopAudioSink() { return buildAudioSink(context, floatOutput, trackParams); }
}
