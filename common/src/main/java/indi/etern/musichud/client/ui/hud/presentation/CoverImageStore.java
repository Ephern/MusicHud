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
 * {@link TrackArtwork}.
 * <p>
 * Publication is serialized by {@link CoverRequestGate}; the store only publishes the
 * resulting {@link BackgroundData} to {@link BackgroundState}.
 */
public final class CoverImageStore {
    private static volatile CoverImageStore instance;

    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final CoverRequestGate coverRequests = new CoverRequestGate();
    private volatile int albumImageThumbnailSize = 128;

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
        // The artwork itself is decoded once into TrackArtwork-owned textures; warming the shared
        // texture cache here would only pay for a decode nobody samples. The raw bytes are still
        // worth prefetching so the decode can start immediately.
        ImageUtils.prefetchBytes(album.getPicUrl());
    }

    public void setThumbnailSize(int thumbnailSize,
                                 Predicate<MusicDetail> currentTrack,
                                 Consumer<Duration> onLoaded) {
        if (thumbnailSize <= 0) return;
        thumbnailSize = Math.max(128, thumbnailSize);
        MusicDetail current;
        long token;
        synchronized (coverRequests) {
            if (albumImageThumbnailSize == thumbnailSize) return;
            albumImageThumbnailSize = thumbnailSize;
            token = coverRequests.begin();
            current = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        }
        if (current != null && current != MusicDetail.NONE) {
            loadAndSwitchAlbumImageWithRetry(current, token, thumbnailSize, currentTrack, onLoaded);
        }
    }

    public void load(MusicDetail musicDetail, long token,
                     Predicate<MusicDetail> currentTrack, Consumer<Duration> onLoaded) {
        loadAndSwitchAlbumImageWithRetry(
                musicDetail, token, albumImageThumbnailSize, currentTrack, onLoaded);
    }

    private void loadAndSwitchAlbumImageWithRetry(MusicDetail musicDetail, long token,
                                                  int thumbnailSize,
                                                  Predicate<MusicDetail> currentTrack,
                                                  Consumer<Duration> onLoaded) {
        coverRequests.runIfCurrent(token, () -> currentTrack.test(musicDetail), () -> {
            CompletableFuture<Void> future = loadAlbumImage(
                    musicDetail, token, thumbnailSize, currentTrack, onLoaded);
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
                        musicDetail, token, thumbnailSize, currentTrack, onLoaded);
                return null;
            }, MusicHud.EXECUTOR);
        });
    }

    private CompletableFuture<Void> loadAlbumImage(MusicDetail musicDetail, long token,
                                                   int thumbnailSize,
                                                   Predicate<MusicDetail> currentTrack,
                                                   Consumer<Duration> onLoaded) {
        if (thumbnailSize <= 0) return CompletableFuture.completedFuture(null);
        Album album = musicDetail.getAlbum();
        // The scene background never samples the cover above COVER_TARGET_MAX_LONG_EDGE (the
        // canonical target is clamped to it), so uploading the full-resolution artwork only costs a
        // multi-megabyte render-thread decode/upload per track. Both sizes are derived from a single
        // decode, and the textures belong to TrackArtwork instead of the shared texture cache.
        int backgroundSize = FluidBackgroundSettings.COVER_TARGET_MAX_LONG_EDGE;
        return CompletableFuture
                .supplyAsync(() -> TrackArtwork.load(album.getPicUrl(), thumbnailSize, backgroundSize),
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