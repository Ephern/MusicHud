package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.utils.image.ImageUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Loads the artwork of the current track: one download, one decode, explicit ownership via
 * {@link TrackArtwork}. Requests are serialized by {@link CoverRequestGate}; the result is published
 * to {@link BackgroundState}.
 */
public final class CoverImageStore {
    private static volatile CoverImageStore instance;

    private static final int THUMBNAIL_BOX = 128;
    /** Headroom so the GPU only downscales. */
    private static final float SOURCE_HEADROOM = 1.5f;
    private static final int BACKGROUND_BOX = FluidBackgroundSettings.COVER_TARGET_MAX_LONG_EDGE;

    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final CoverRequestGate coverRequests = new CoverRequestGate();
    private volatile int albumArtSourceSize = BACKGROUND_BOX;

    private CoverImageStore() {
    }

    public static CoverImageStore getInstance() {
        if (instance == null) {
            synchronized (CoverImageStore.class) {
                if (instance == null) {
                    instance = new CoverImageStore();
                }
            }
        }
        return instance;
    }

    public long beginRequest() {
        return coverRequests.begin();
    }

    public void invalidate() {
        coverRequests.begin();
    }

    public void preload(Album album) {
        // Only the raw bytes are worth prefetching; the textures belong to TrackArtwork.
        ImageUtils.prefetchBytes(album.getPicUrl());
    }

    /** Promotes the artwork to the display-size bucket. */
    public void setSourceSize(int displayLongEdgePx,
                              Predicate<MusicDetail> currentTrack,
                              Consumer<Duration> onLoaded) {
        if (displayLongEdgePx <= 0) return;
        int sourceSize = quantizeSourceSize(displayLongEdgePx);
        MusicDetail current;
        long token;
        synchronized (coverRequests) {
            if (albumArtSourceSize == sourceSize) return;
            albumArtSourceSize = sourceSize;
            token = coverRequests.begin();
            current = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
            MusicHud.LOGGER.debug("Album source size {} px", sourceSize);
        }
        if (current != null && current != MusicDetail.NONE) {
            loadAndSwitchAlbumImageWithRetry(current, token, sourceSize, currentTrack, onLoaded);
        }
    }

    /** Power-of-two bucket of the display size, clamped to the background box. */
    static int quantizeSourceSize(int displayLongEdgePx) {
        int wanted = Math.max(1, (int) Math.ceil(displayLongEdgePx * SOURCE_HEADROOM));
        int bucket = Integer.highestOneBit(wanted);
        int box = bucket == wanted ? wanted : bucket << 1;
        return Math.clamp(box, BACKGROUND_BOX, ImageUtils.CANONICAL_MAX);
    }

    public void load(MusicDetail musicDetail, long token,
                     Predicate<MusicDetail> currentTrack, Consumer<Duration> onLoaded) {
        loadAndSwitchAlbumImageWithRetry(
                musicDetail, token, albumArtSourceSize, currentTrack, onLoaded);
    }

    private void loadAndSwitchAlbumImageWithRetry(MusicDetail musicDetail, long token,
                                                  int sourceSize,
                                                  Predicate<MusicDetail> currentTrack,
                                                  Consumer<Duration> onLoaded) {
        coverRequests.runIfCurrent(token, () -> currentTrack.test(musicDetail), () -> {
            CompletableFuture<Void> future = loadAlbumImage(
                    musicDetail, token, sourceSize, currentTrack, onLoaded);
            future.exceptionallyAsync(e -> {
                boolean retry = coverRequests.runIfCurrent(token,
                        () -> currentTrack.test(musicDetail), BackgroundState.getInstance()::reset);
                if (!retry) return null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                // Admission and launch share the publication lock; replacement cannot slip between them.
                loadAndSwitchAlbumImageWithRetry(
                        musicDetail, token, sourceSize, currentTrack, onLoaded);
                return null;
            }, MusicHud.EXECUTOR);
        });
    }

    private CompletableFuture<Void> loadAlbumImage(MusicDetail musicDetail, long token,
                                                   int sourceSize,
                                                   Predicate<MusicDetail> currentTrack,
                                                   Consumer<Duration> onLoaded) {
        if (sourceSize <= 0) return CompletableFuture.completedFuture(null);
        Album album = musicDetail.getAlbum();
        return CompletableFuture
                .supplyAsync(() -> TrackArtwork.load(album.getPicUrl(), THUMBNAIL_BOX, BACKGROUND_BOX,
                                sourceSize),
                        MusicHud.EXECUTOR)
                .thenAccept(artwork -> {
                    if (artwork == null) return;
                    boolean accepted = coverRequests.runIfCurrent(token,
                            () -> currentTrack.test(musicDetail), () -> {
                                TrackArtworkStore.getInstance().publish(artwork);
                                BackgroundState.getInstance().startTransition(
                                        new BackgroundData(artwork.images()));
                                if (onLoaded != null) {
                                    onLoaded.accept(nowPlayingInfo.getMusicDuration());
                                }
                            });
                    if (!accepted) {
                        // Superseded before publication: nobody will ever display it.
                        artwork.close();
                    }
                });
    }
}