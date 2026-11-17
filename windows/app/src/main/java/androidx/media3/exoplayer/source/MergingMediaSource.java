package androidx.media3.exoplayer.source;

public final class MergingMediaSource extends DesktopMediaSource {
    public MergingMediaSource(MediaSource... sources) { super(sources[0].getMediaItem(), sources[0] instanceof DesktopMediaSource ? ((DesktopMediaSource) sources[0]).data : null, "merging", sources); }
    public MergingMediaSource(boolean adjustPeriodTimeOffsets, MediaSource... sources) { this(sources); }
    public MergingMediaSource(boolean adjust, boolean clip, MediaSource... sources) { this(sources); }
}
