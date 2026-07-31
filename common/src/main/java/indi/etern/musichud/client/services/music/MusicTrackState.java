package indi.etern.musichud.client.services.music;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.actions.ModifyType;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.ModifyPlaylistRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.ModifyPlaylistResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@AllArgsConstructor
public class MusicTrackState implements IMusicTrackState {
    record TrackPlaylistPair(long playlistId, long musicTrackId) {}

    private final MusicService musicService = MusicService.getInstance();
    MusicDetail musicDetail;

    private static final ConcurrentHashMap<TrackPlaylistPair, CopyOnWriteArrayList<Consumer<Boolean>>>
            modifyListeners = new ConcurrentHashMap<>();

    static Unregister registerModifyListener(TrackPlaylistPair trackPlaylistPair, Consumer<Boolean> listener) {
        modifyListeners.computeIfAbsent(trackPlaylistPair, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            var list = modifyListeners.get(trackPlaylistPair);
            if (list != null) list.remove(listener);
        };
    }

    static void notifyPlaylistModified(long playlistId, long musicTrackId, boolean contained) {
        var list = modifyListeners.get(new TrackPlaylistPair(playlistId, musicTrackId));
        if (list == null) return;
        for (var listener : list) {
            listener.accept(contained);
        }
    }

    public IPlaylistSubState currentUsersLikeList() {
        return new PlaylistSubState(() ->
                musicService.loadUserPlaylists(false)
                        .thenCompose(p ->
                                musicService.loadPlaylistDetail(p.getLikeList().getId(), false)
                        )
        );
    }

    @Override
    public IPlaylistSubState playlist(long playlistId) {
        return new PlaylistSubState(() -> musicService.loadPlaylistDetail(playlistId, false));
    }

    public class PlaylistSubState implements IPlaylistSubState {
        long playlistId;
        Playlist playlist;
        Supplier<CompletableFuture<Playlist>> playlistLazyLoader;

        PlaylistSubState(Supplier<CompletableFuture<Playlist>> playlistLazyLoader) {
            this.playlistLazyLoader = playlistLazyLoader;
        }

        @Override
        public long playlistId() {
            return playlistId;
        }

        @SneakyThrows
        private CompletableFuture<Playlist> loadPlaylist() {
            CompletableFuture<Playlist> future = new CompletableFuture<>();
            if (playlist == null) {
                try {
                    playlist = playlistLazyLoader.get().get(10, TimeUnit.MILLISECONDS);
                    future.complete(playlist);
                } catch (TimeoutException e) {
                    MusicHud.EXECUTOR.submit(() -> {
                        try {
                            playlist = playlistLazyLoader.get().get();
                            future.complete(playlist);
                        } catch (InterruptedException | ExecutionException e1) {
                            throw new RuntimeException(e1);
                        }
                    });
                }
            } else {
                future.complete(playlist);
            }
            return future;
        }

        @Override
        public CompletableFuture<Boolean> isContained() {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            loadPlaylist().thenAccept(playlist1 -> {
                future.complete(playlist1.getMusicDetails().contains(musicDetail));
            });
            return future;
        }

        @Override
        public CompletableFuture<Void> add() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            notifyPlaylistModified(playlistId, musicDetail.getId(), true);
            loadPlaylist().thenAccept(playlist1 -> {
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.ADD);
                RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                future.completeExceptionally(throwable);
                                return;
                            }
                            if (response.isSuccess()) {
                                playlist1.getMusicDetails().addFirst(musicDetail);
                                future.complete(null);
                            } else {
                                future.completeExceptionally(new RuntimeException(response.getMessage()));
                            }
                        });
            });
            return future;
        }

        @Override
        public CompletableFuture<Void> remove() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            notifyPlaylistModified(playlistId, musicDetail.getId(), false);
            loadPlaylist().thenAccept(playlist1 -> {
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.REMOVE);
                RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                future.completeExceptionally(throwable);
                                return;
                            }
                            if (response.isSuccess()) {
                                playlist1.getMusicDetails().remove(musicDetail);
                                future.complete(null);
                            } else {
                                future.completeExceptionally(new RuntimeException(response.getMessage()));
                            }
                        });
            });
            return future;
        }

        @Override
        public Unregister onExternalModify(Consumer<Boolean> listener) {
            return registerModifyListener(new TrackPlaylistPair(playlistId, musicDetail.getId()), listener);
        }
    }
}
