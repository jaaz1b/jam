package app.jam.desktop.media

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DesktopMediaSource
import androidx.media3.exoplayer.source.MediaSource
import app.jam.desktop.shim.Desk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ExoPlayer on the PC. The playlist, the state machine and the listener calls are ExoPlayer's (on the
 * main thread); an item's bytes come from its MediaSource's DataSource (MediaServer), ffmpeg decodes
 * them, and the PCM goes through the sink the app's renderers factory makes (its audio processors,
 * the speed, the volume) to Windows' audio. One engine thread per player; the next song is decoded
 * ahead for a gapless change.
 */
class DesktopPlayer(
    private val context: Context,
    renderers: RenderersFactory,
    private val sourceFactory: MediaSource.Factory?,
    private var attributes: AudioAttributes?,
    @Volatile private var pauseAtEnd: Boolean,
    private val seekBackMs: Long,
    private val seekForwardMs: Long,
) : ExoPlayer {
    private val sink: AudioSink = (renderers as? DefaultRenderersFactory)?.desktopAudioSink() ?: androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).build()
    private val listeners = CopyOnWriteArrayList<Player.Listener>()
    private val items = ArrayList<MediaItem>()
    private val sources = ArrayList<MediaSource?>()
    @Volatile private var index = 0
    @Volatile private var state = Player.STATE_IDLE
    @Volatile private var playWhenReady = false
    @Volatile private var repeat = Player.REPEAT_MODE_OFF
    @Volatile private var shuffle = false
    @Volatile private var volume = 1f
    @Volatile private var params = PlaybackParameters.DEFAULT
    @Volatile private var skipSilence = false
    @Volatile private var error: PlaybackException? = null
    @Volatile private var released = false
    @Volatile private var seekMs = 0L          // the position while nothing is heard yet (after a seek, a load)
    @Volatile private var durationMs = C.TIME_UNSET
    @Volatile private var audioFormat: Format? = null
    @Volatile private var isPlayingNow = false
    @Volatile private var loading = false
    private var trackParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    private val sessionId = sessions.incrementAndGet()
    private val commands = LinkedBlockingQueue<() -> Unit>()
    private var current: Run? = null
    private var preloaded: Run? = null
    private var sinkFailed = false
    private val thread = Thread(::loop, "jam-player-$sessionId").apply { isDaemon = true; start() }

    init {
        sink.setAudioSessionId(sessionId)
        sink.configure(pcmFormat, 0, null)
        attributes?.let { sink.setAudioAttributes(it) }
    }

    /** One item being decoded: its ffmpeg, its PCM chunks (read ahead by a thread of its own). */
    private inner class Run(val itemIndex: Int, val startMs: Long, val item: MediaItem, val source: MediaSource?) {
        val url: String
        val decode: Ffmpeg.Decode
        val chunks = ArrayBlockingQueue<ByteArray>(96)   // ~2 s ahead
        @Volatile var eof = false
        @Volatile var frames = 0L
        @Volatile var stopped = false
        var described = false
        init {
            val ds = (source as? DesktopMediaSource)?.data ?: (sourceFactory as? androidx.media3.exoplayer.source.DefaultMediaSourceFactory)?.dataSourceFactory()
                ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
            val lc = item.localConfiguration
            url = MediaServer.register(MediaServer.Entry(ds, lc?.uri ?: android.net.Uri.EMPTY, lc?.customCacheKey, emptyMap()))
            val clipEnd = item.clippingConfiguration.endPositionMs.takeIf { it != C.TIME_END_OF_SOURCE }
            decode = Ffmpeg.decode(url, startMs + item.clippingConfiguration.startPositionMs, clipEnd)
            Thread({
                val buf = ByteArray(3840 * 2) // 40 ms
                try {
                    while (!stopped) {
                        var got = 0
                        while (got < buf.size) { val n = decode.pcm.read(buf, got, buf.size - got); if (n < 0) break; got += n }
                        if (got > 0) chunks.put(buf.copyOf(got - got % 4))
                        if (got < buf.size) break
                    }
                } catch (_: Throwable) {}
                eof = true
                if (!stopped) runCatching { chunks.put(ByteArray(0)) }
            }, "jam-decode").apply { isDaemon = true }.start()
        }
        fun stop() { stopped = true; chunks.clear(); decode.stop(); MediaServer.unregister(url) }
        fun failure(): PlaybackException? {
            if (frames > 0) return null
            runCatching { decode.process.waitFor(2, TimeUnit.SECONDS) }
            val exit = runCatching { decode.process.exitValue() }.getOrDefault(-1)
            if (exit == 0) return null
            val src = MediaServer.entry(url)?.error
            val code = when (src) {
                is HttpDataSource.InvalidResponseCodeException -> PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                is java.io.FileNotFoundException -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                is androidx.media3.datasource.DataSourceException -> src.reason
                is java.net.SocketTimeoutException -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                is java.io.IOException -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                else -> if ("Invalid data" in decode.log) PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED else PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            }
            return PlaybackException(src?.message ?: decode.log.lines().lastOrNull { it.isNotBlank() } ?: "decoding failed", src, code)
        }
    }

    // ---- the engine thread
    private fun loop() {
        while (!released) {
            val cmd = commands.poll(if (current != null && playWhenReady && state != Player.STATE_ENDED) 0 else 20, TimeUnit.MILLISECONDS)
            if (cmd != null) { runCatching(cmd).onFailure { Desk.log('E', "Player", "command: $it", it) }; continue }
            val r = current ?: continue
            if (!playWhenReady || state == Player.STATE_ENDED) continue
            val chunk = r.chunks.poll(20, TimeUnit.MILLISECONDS)
            if (chunk == null) { if (state == Player.STATE_READY && !r.eof) setState(Player.STATE_BUFFERING); continue }
            if (chunk.isEmpty()) { ended(r); continue }
            if (state != Player.STATE_READY) setState(Player.STATE_READY)
            if (!r.described && (r.decode.durationMs > 0 || r.frames > Ffmpeg.RATE)) { r.described = true; describe(r) }
            val pts = (r.startMs + r.frames * 1000 / Ffmpeg.RATE) * 1000
            r.frames += chunk.size / 4
            runCatching { sink.handleBuffer(ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN), pts, 1) }.onFailure { if (!sinkFailed) { sinkFailed = true; Desk.log('E', "Player", "sink: $it", it) } }
            maybePreload(r)
        }
        current?.stop(); preloaded?.stop()
    }

    private fun describe(r: Run) {
        if (r.decode.durationMs > 0) durationMs = r.decode.durationMs - r.item.clippingConfiguration.startPositionMs
        audioFormat = Format.Builder().setSampleMimeType(Ffmpeg.mimeOf(r.decode.codec)).setCodecs(r.decode.codec).setSampleRate(r.decode.sourceRate)
            .setChannelCount(r.decode.sourceChannels).setAverageBitrate(r.decode.bitrate).build()
        loading = false
        Desk.post { listeners.forEach { it.onTracksChanged(currentTracks) } }
    }

    private fun maybePreload(r: Run) {
        if (preloaded != null || repeat == Player.REPEAT_MODE_ONE || pauseAtEnd) return
        val d = r.decode.durationMs.takeIf { it > 0 } ?: return
        if (d - (r.startMs + r.frames * 1000 / Ffmpeg.RATE) > 6000) return
        val next = nextIndex(index) ?: return
        preloaded = runCatching { Run(next, 0, items[next], sources.getOrNull(next)) }.getOrNull()
    }

    private fun ended(r: Run) {
        val failure = r.failure()
        if (failure != null) { fail(failure); return }
        runCatching { sink.playToEndOfStream() }
        r.stop(); current = null
        val cur = index
        val next = if (repeat == Player.REPEAT_MODE_ONE) cur else nextIndex(cur)
        if (next == null) {
            seekMs = durationMs.coerceAtLeast(0)
            setState(Player.STATE_ENDED)
            updatePlaying()
            return
        }
        val reason = if (next == cur) Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT else Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        index = next
        val pre = preloaded?.takeIf { it.itemIndex == next && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO }
        preloaded = null
        sink.flush()
        durationMs = C.TIME_UNSET; seekMs = 0
        current = pre ?: Run(next, 0, items[next], sources.getOrNull(next))
        if (pre == null) setState(Player.STATE_BUFFERING)
        val item = items[next]
        Desk.post {
            listeners.forEach { it.onPositionDiscontinuity(position(cur), position(next), Player.DISCONTINUITY_REASON_AUTO_TRANSITION); it.onMediaItemTransition(item, reason) }
            events(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY)
        }
        if (pauseAtEnd) { playWhenReady = false; sink.pause(); notifyPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) }
    }

    private fun fail(e: PlaybackException) {
        current?.stop(); current = null; preloaded?.stop(); preloaded = null
        error = e
        Desk.log('W', "Player", "error ${e.errorCodeName}: ${e.message}", null)
        setState(Player.STATE_IDLE)
        updatePlaying()
        Desk.post { listeners.forEach { it.onPlayerErrorChanged(e); it.onPlayerError(e) }; events(Player.EVENT_PLAYER_ERROR) }
    }

    private fun nextIndex(i: Int): Int? = when {
        i + 1 < items.size -> i + 1
        repeat == Player.REPEAT_MODE_ALL && items.isNotEmpty() -> 0
        else -> null
    }

    /** Starts [i] at [ms] (engine thread). */
    private fun load(i: Int, ms: Long) {
        current?.stop(); current = null
        preloaded?.takeIf { it.itemIndex != i || ms != 0L }?.let { it.stop(); preloaded = null }
        sink.flush()
        if (i !in items.indices) { setState(if (items.isEmpty()) Player.STATE_ENDED else Player.STATE_IDLE); return }
        seekMs = ms; durationMs = C.TIME_UNSET; loading = true
        current = preloaded?.also { preloaded = null } ?: Run(i, ms, items[i], sources.getOrNull(i))
        setState(Player.STATE_BUFFERING)
        if (playWhenReady) sink.play()
    }

    private fun setState(s: Int) {
        if (state == s) return
        state = s
        updatePlaying()
        Desk.post { listeners.forEach { it.onPlaybackStateChanged(s) }; events(Player.EVENT_PLAYBACK_STATE_CHANGED) }
    }
    private fun updatePlaying() {
        val now = playWhenReady && state == Player.STATE_READY
        if (now == isPlayingNow) return
        isPlayingNow = now
        Desk.post { listeners.forEach { it.onIsPlayingChanged(now) }; events(Player.EVENT_IS_PLAYING_CHANGED) }
    }
    private fun events(vararg e: Int) { val ev = Player.Events(e.toSet()); listeners.forEach { it.onEvents(this, ev) } }
    private fun notifyPlayWhenReady(reason: Int) { val p = playWhenReady; Desk.post { listeners.forEach { it.onPlayWhenReadyChanged(p, reason) }; events(Player.EVENT_PLAY_WHEN_READY_CHANGED) } }
    private fun position(i: Int) = Player.PositionInfo(i, i, items.getOrNull(i), i, i, currentPosition, currentPosition, -1, -1)
    private fun post(c: () -> Unit) { commands.put(c) }

    // ---- the playlist (main thread; the engine follows)
    private fun source(item: MediaItem): MediaSource? = sourceFactory?.createMediaSource(item)
    private fun timelineChanged() { val t = currentTimeline; listeners.forEach { it.onTimelineChanged(t, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }; events(Player.EVENT_TIMELINE_CHANGED) }

    private fun replaceAll(list: List<Pair<MediaItem, MediaSource?>>, start: Int, startMs: Long) {
        val before = currentMediaItem
        synchronized(items) { items.clear(); sources.clear(); list.forEach { items += it.first; sources += it.second } }
        index = start.coerceIn(0, (items.size - 1).coerceAtLeast(0)); seekMs = startMs.coerceAtLeast(0)
        val i = index; val ms = seekMs
        post { preloaded?.stop(); preloaded = null; if (state != Player.STATE_IDLE) load(i, ms) else { current?.stop(); current = null; sink.flush() } }
        timelineChanged()
        val now = currentMediaItem
        if (now != before) { listeners.forEach { it.onMediaItemTransition(now, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }; events(Player.EVENT_MEDIA_ITEM_TRANSITION) }
    }
    override fun setMediaItems(list: List<MediaItem>) = setMediaItems(list, true)
    override fun setMediaItems(list: List<MediaItem>, reset: Boolean) = replaceAll(list.map { it to source(it) }, if (reset) 0 else index, if (reset) 0 else currentPosition)
    override fun setMediaItems(list: List<MediaItem>, startIndex: Int, startPositionMs: Long) = replaceAll(list.map { it to source(it) }, startIndex, if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs)
    override fun setMediaItem(item: MediaItem) = setMediaItems(listOf(item))
    override fun setMediaItem(item: MediaItem, startPositionMs: Long) = setMediaItems(listOf(item), 0, startPositionMs)
    override fun setMediaItem(item: MediaItem, reset: Boolean) = setMediaItems(listOf(item), reset)
    override fun setMediaSource(s: MediaSource) = replaceAll(listOf(s.mediaItem to s), 0, 0)
    override fun setMediaSource(s: MediaSource, startPositionMs: Long) = replaceAll(listOf(s.mediaItem to s), 0, startPositionMs)
    override fun setMediaSource(s: MediaSource, reset: Boolean) = replaceAll(listOf(s.mediaItem to s), 0, if (reset) 0 else currentPosition)
    override fun setMediaSources(s: List<MediaSource>) = replaceAll(s.map { it.mediaItem to it }, 0, 0)
    override fun addMediaSource(s: MediaSource) = insert(items.size, listOf(s.mediaItem to s))
    override fun addMediaSource(i: Int, s: MediaSource) = insert(i, listOf(s.mediaItem to s))
    override fun addMediaItem(item: MediaItem) = insert(items.size, listOf(item to source(item)))
    override fun addMediaItem(i: Int, item: MediaItem) = insert(i, listOf(item to source(item)))
    override fun addMediaItems(list: List<MediaItem>) = insert(items.size, list.map { it to source(it) })
    override fun addMediaItems(i: Int, list: List<MediaItem>) = insert(i, list.map { it to source(it) })
    private fun insert(at0: Int, list: List<Pair<MediaItem, MediaSource?>>) {
        if (list.isEmpty()) return
        val at = at0.coerceIn(0, items.size)
        val wasEmpty = items.isEmpty()
        synchronized(items) { items.addAll(at, list.map { it.first }); sources.addAll(at, list.map { it.second }) }
        if (!wasEmpty && at <= index) shift(list.size)
        post { preloaded?.stop(); preloaded = null }
        timelineChanged()
        if (wasEmpty) { listeners.forEach { it.onMediaItemTransition(currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }; if (state != Player.STATE_IDLE) { val i = index; post { load(i, 0) } } }
    }
    /** The current item moved by [d] places: its run keeps playing (the engine goes by [index]). */
    private fun shift(d: Int) { index += d }

    override fun moveMediaItem(from: Int, to: Int) = moveMediaItems(from, from + 1, to)
    override fun moveMediaItems(from: Int, to: Int, newIndex: Int) {
        if (from >= to) return
        val cur = index
        synchronized(items) {
            val its = items.subList(from, to).toList(); val srcs = sources.subList(from, to).toList()
            repeat(to - from) { items.removeAt(from); sources.removeAt(from) }
            val at = newIndex.coerceIn(0, items.size)
            items.addAll(at, its); sources.addAll(at, srcs)
            index = when {
                cur in from until to -> at + (cur - from)
                cur < from && cur >= at -> cur + (to - from)
                cur > from && cur < at + (to - from) && cur >= to -> cur - (to - from)
                else -> cur
            }
        }
        post { preloaded?.stop(); preloaded = null }
        timelineChanged()
    }
    override fun replaceMediaItem(i: Int, item: MediaItem) = replaceMediaItems(i, i + 1, listOf(item))
    override fun replaceMediaItems(from: Int, to: Int, list: List<MediaItem>) {
        val playingReplaced = index in from until to
        synchronized(items) { repeat(to - from) { items.removeAt(from); sources.removeAt(from) }; items.addAll(from, list); sources.addAll(from, list.map { source(it) }) }
        if (!playingReplaced && index >= to) index += list.size - (to - from)
        timelineChanged()
        if (playingReplaced && state != Player.STATE_IDLE) { val i = index.coerceAtMost(items.size - 1); val ms = currentPosition; post { load(i, ms) } }
    }
    override fun removeMediaItem(i: Int) = removeMediaItems(i, i + 1)
    override fun removeMediaItems(from: Int, to0: Int) {
        val to = to0.coerceAtMost(items.size)
        if (from >= to) return
        val removedCurrent = index in from until to
        synchronized(items) { repeat(to - from) { items.removeAt(from); sources.removeAt(from) } }
        when {
            index >= to -> index -= to - from
            removedCurrent -> {
                index = from.coerceAtMost(items.size - 1).coerceAtLeast(0)
                val i = index
                if (items.isEmpty()) post { current?.stop(); current = null; sink.flush(); setState(Player.STATE_ENDED) }
                else if (state != Player.STATE_IDLE) post { load(i, 0) }
                listeners.forEach { it.onMediaItemTransition(currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
            }
        }
        post { preloaded?.stop(); preloaded = null }
        timelineChanged()
    }
    override fun clearMediaItems() = removeMediaItems(0, items.size)

    // ---- transport
    override fun prepare() {
        error = null
        if (items.isEmpty()) { post { setState(Player.STATE_ENDED) }; return }
        if (state != Player.STATE_IDLE) return
        val i = index; val ms = seekMs
        post { load(i, ms) }
    }
    override fun play() = setPlayWhenReady(true)
    override fun pause() = setPlayWhenReady(false)
    override fun setPlayWhenReady(p: Boolean) {
        if (playWhenReady == p) return
        playWhenReady = p
        post { if (p) sink.play() else sink.pause(); updatePlaying() }
        listeners.forEach { it.onPlayWhenReadyChanged(p, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
    }
    override fun getPlayWhenReady() = playWhenReady
    override fun getPlaybackState() = state
    override fun getPlaybackSuppressionReason() = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun isPlaying() = isPlayingNow
    override fun getPlayerError() = error
    override fun isLoading() = loading
    override fun stop() { post { current?.stop(); current = null; preloaded?.stop(); preloaded = null; sink.flush();  setState(Player.STATE_IDLE) } }
    override fun release() { if (released) return; released = true; commands.put { }; thread.join(500); runCatching { current?.stop(); preloaded?.stop(); sink.release() } }
    override fun isReleased() = released

    override fun seekToDefaultPosition() = seekTo(index, 0)
    override fun seekToDefaultPosition(i: Int) = seekTo(i, 0)
    override fun seekTo(ms: Long) = seekTo(index, ms)
    override fun seekTo(i: Int, ms0: Long) {
        if (i !in items.indices && items.isNotEmpty()) return
        val ms = if (ms0 == C.TIME_UNSET) 0 else ms0.coerceAtLeast(0)
        val old = position(index)
        val changed = i != index
        index = i; seekMs = ms
        if (state == Player.STATE_ENDED) post { setState(Player.STATE_BUFFERING) }
        if (state != Player.STATE_IDLE) post { load(i, ms) }
        listeners.forEach { it.onPositionDiscontinuity(old, position(i), Player.DISCONTINUITY_REASON_SEEK) }
        if (changed) listeners.forEach { it.onMediaItemTransition(items.getOrNull(i), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) }
        events(*if (changed) intArrayOf(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION) else intArrayOf(Player.EVENT_POSITION_DISCONTINUITY))
    }
    override fun getSeekBackIncrement() = seekBackMs
    override fun seekBack() = seekTo((currentPosition - seekBackMs).coerceAtLeast(0))
    override fun getSeekForwardIncrement() = seekForwardMs
    override fun seekForward() = seekTo(currentPosition + seekForwardMs)
    override fun hasPreviousMediaItem() = previousMediaItemIndex != C.INDEX_UNSET
    override fun seekToPreviousMediaItem() { val p = previousMediaItemIndex; if (p != C.INDEX_UNSET) seekTo(p, 0) }
    override fun getMaxSeekToPreviousPosition() = 3000L
    override fun seekToPrevious() { if (currentPosition > 3000 || !hasPreviousMediaItem()) seekTo(0) else seekToPreviousMediaItem() }
    override fun hasNextMediaItem() = nextMediaItemIndex != C.INDEX_UNSET
    override fun seekToNextMediaItem() { val n = nextMediaItemIndex; if (n != C.INDEX_UNSET) seekTo(n, 0) }
    override fun seekToNext() = seekToNextMediaItem()
    override fun setRepeatMode(m: Int) { if (repeat == m) return; repeat = m; post { preloaded?.stop(); preloaded = null }; listeners.forEach { it.onRepeatModeChanged(m) }; events(Player.EVENT_REPEAT_MODE_CHANGED) }
    override fun getRepeatMode() = repeat
    override fun setShuffleModeEnabled(on: Boolean) { shuffle = on; listeners.forEach { it.onShuffleModeEnabledChanged(on) } }
    override fun getShuffleModeEnabled() = shuffle
    override fun setPlaybackParameters(p: PlaybackParameters) { params = p; sink.playbackParameters = p; listeners.forEach { it.onPlaybackParametersChanged(p) } }
    override fun setPlaybackSpeed(speed: Float) = setPlaybackParameters(PlaybackParameters(speed))
    override fun getPlaybackParameters() = params
    override fun setVolume(v: Float) { volume = v.coerceIn(0f, 1f); sink.setVolume(volume); listeners.forEach { it.onVolumeChanged(volume) } }
    override fun getVolume() = volume
    override fun setSkipSilenceEnabled(on: Boolean) { skipSilence = on; sink.skipSilenceEnabled = on; listeners.forEach { it.onSkipSilenceEnabledChanged(on) } }
    override fun getSkipSilenceEnabled() = skipSilence
    override fun setAudioAttributes(a: AudioAttributes, handleAudioFocus: Boolean) { attributes = a; sink.audioAttributes = a }
    override fun getAudioAttributes(): AudioAttributes = attributes ?: AudioAttributes.DEFAULT
    override fun setHandleAudioBecomingNoisy(on: Boolean) {}
    override fun getAudioSessionId() = sessionId
    override fun setAudioSessionId(id: Int) {}
    override fun setSeekParameters(p: SeekParameters) {}
    override fun setPauseAtEndOfMediaItems(on: Boolean) { pauseAtEnd = on }
    override fun getPauseAtEndOfMediaItems() = pauseAtEnd
    override fun setWakeMode(mode: Int) {}
    override fun setForegroundMode(on: Boolean) {}
    override fun getAudioFormat(): Format? = audioFormat
    override fun getVideoFormat(): Format? = null
    override fun setVideoScalingMode(mode: Int) {}
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun setVideoTextureView(v: android.view.TextureView?) {}
    override fun clearVideoTextureView(v: android.view.TextureView?) {}
    override fun setVideoSurface(s: android.view.Surface?) {}
    override fun clearVideoSurface() {}
    override fun isCommandAvailable(c: Int) = true
    override fun getAvailableCommands(): Player.Commands = Player.Commands.EMPTY
    override fun getApplicationLooper(): Looper = Looper.getMainLooper()
    override fun addListener(l: Player.Listener) { listeners += l }
    override fun removeListener(l: Player.Listener) { listeners -= l }

    // ---- what is playing
    override fun getCurrentTracks(): Tracks = audioFormat?.let { Tracks(listOf(Tracks.Group(C.TRACK_TYPE_AUDIO, it))) } ?: Tracks.EMPTY
    override fun getTrackSelectionParameters() = trackParams
    override fun setTrackSelectionParameters(p: TrackSelectionParameters) { trackParams = p }
    override fun getMediaMetadata(): MediaMetadata = currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
    override fun getCurrentTimeline(): Timeline {
        val snap = synchronized(items) { items.toList() }
        val cur = index; val dur = durationMs
        return object : Timeline() {
            override fun getWindowCount() = snap.size
            override fun getWindow(i: Int, w: Window, p: Long): Window { w.mediaItem = snap[i]; w.durationUs = if (i == cur && dur != C.TIME_UNSET) dur * 1000 else C.TIME_UNSET; w.isSeekable = true; w.firstPeriodIndex = i; w.lastPeriodIndex = i; w.uid = i; return w }
            override fun getPeriodCount() = snap.size
            override fun getPeriod(i: Int, p: Period, setIds: Boolean): Period { p.windowIndex = i; p.uid = i; p.durationUs = if (i == cur && dur != C.TIME_UNSET) dur * 1000 else C.TIME_UNSET; return p }
        }
    }
    override fun getCurrentPeriodIndex() = index
    override fun getCurrentMediaItemIndex() = index
    override fun getNextMediaItemIndex(): Int = when { index + 1 < items.size -> index + 1; repeat == Player.REPEAT_MODE_ALL && items.isNotEmpty() -> 0; repeat == Player.REPEAT_MODE_ONE && items.isNotEmpty() -> index.let { if (it + 1 < items.size) it + 1 else C.INDEX_UNSET }; else -> C.INDEX_UNSET }
    override fun getPreviousMediaItemIndex(): Int = when { index > 0 -> index - 1; repeat == Player.REPEAT_MODE_ALL && items.isNotEmpty() -> items.size - 1; else -> C.INDEX_UNSET }
    override fun getCurrentMediaItem(): MediaItem? = synchronized(items) { items.getOrNull(index) }
    override fun getMediaItemCount() = items.size
    override fun getMediaItemAt(i: Int): MediaItem = synchronized(items) { items[i] }
    override fun getDuration(): Long = durationMs
    override fun getCurrentPosition(): Long {
        if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) return seekMs
        val us = sink.getCurrentPositionUs(false)
        return if (us == AudioSink.CURRENT_POSITION_NOT_SET) seekMs else (us / 1000).coerceAtLeast(0).also { seekMs = it }
    }
    override fun getBufferedPosition(): Long = current?.let { r -> r.startMs + r.frames * 1000 / Ffmpeg.RATE } ?: currentPosition
    override fun getBufferedPercentage(): Int = if (durationMs > 0) (bufferedPosition * 100 / durationMs).toInt().coerceIn(0, 100) else 0
    override fun getTotalBufferedDuration(): Long = current?.let { r -> (r.chunks.size * 40L) + (r.startMs + r.frames * 1000 / Ffmpeg.RATE - currentPosition).coerceAtLeast(0) } ?: 0
    override fun isCurrentMediaItemDynamic() = false
    override fun isCurrentMediaItemLive() = durationMs == C.TIME_UNSET && state == Player.STATE_READY
    override fun isCurrentMediaItemSeekable() = true
    override fun getContentDuration() = duration
    override fun getContentPosition() = currentPosition
    override fun getContentBufferedPosition() = bufferedPosition

    companion object {
        private val sessions = AtomicInteger(1000)
        val pcmFormat: Format = Format.Builder().setSampleMimeType("audio/raw").setSampleRate(Ffmpeg.RATE).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
    }
}
