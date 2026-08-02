package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.actions.ModifyType;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.client.services.music.MusicService;
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
    private static final ConcurrentHashMap<TrackPlaylistPair, CopyOnWriteArrayList<Consumer<Boolean>>>
            modifyListeners = new ConcurrentHashMap<>();
    private final MusicService musicService = MusicService.getInstance();
    MusicDetail musicDetail;

    record TrackPlaylistPair(long playlistId, long musicTrackId) {
    }

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
                CompletableFuture<Playlist> completableFuture = playlistLazyLoader.get();
                playlist = completableFuture.getNow(null);
                if (playlist != null) {
                    future.complete(playlist);
                } else if (completableFuture.state() == Future.State.SUCCESS) {
                    playlist = completableFuture.get();
                    future.complete(playlist);
                } else {
                    MusicHud.EXECUTOR.submit(() -> {
                        try {
                            playlist = completableFuture.get();
                            future.complete(playlist);
                        } catch (InterruptedException | ExecutionException e1) {
                            future.completeExceptionally(e1);
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
            return loadPlaylist().thenApply(playlist1 ->
                    playlist1.getMusicDetails().stream().anyMatch(i -> i.getId() == musicDetail.getId())
            );
        }

        @Override
        public CompletableFuture<Void> add() {
            notifyPlaylistModified(playlistId, musicDetail.getId(), true);
            return loadPlaylist().thenCompose(playlist1 -> {
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.ADD);
                return RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                throw new RuntimeException(throwable);
                            }
                            if (response.isSuccess()) {
                                playlist1.getMusicDetails().addFirst(musicDetail);
                                return;
                            } else {
                                throw new RuntimeException(response.getMessage());
                            }
                        }).thenApply(r -> null);
            });
        }

        @Override
        public CompletableFuture<Void> remove() {
            notifyPlaylistModified(playlistId, musicDetail.getId(), false);
            return loadPlaylist().thenCompose(playlist1 -> {
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.REMOVE);
                return RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                throw new RuntimeException(throwable);
                            }
                            if (response.isSuccess()) {
                                playlist1.getMusicDetails().remove(musicDetail);
                            } else {
                                throw new RuntimeException(response.getMessage());
                            }
                        }).thenApply(r -> null);
            });
        }

        @Override
        public Unregister onOthersModify(Consumer<Boolean> listener) {
            return registerModifyListener(new TrackPlaylistPair(playlistId, musicDetail.getId()), listener);
        }
    }
}
