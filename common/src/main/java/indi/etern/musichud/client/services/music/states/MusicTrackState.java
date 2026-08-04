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

    @Override
    public IPlaylistSubState currentUsersLikeList() {
        return new PlaylistSubState(-1, () ->
                musicService.loadUserPlaylists(false)
                        .thenCompose(p1 ->
                                musicService.loadPlaylistDetail(p1.getLikeList().getId(), false)
                        )
        );
    }

    @Override
    public IPlaylistSubState playlist(long playlistId) {
        return new PlaylistSubState(playlistId, () -> musicService.loadPlaylistDetail(playlistId, false));
    }

    record TrackPlaylistPair(long playlistId, long musicTrackId) {
    }

    public class PlaylistSubState implements IPlaylistSubState {
        long playlistId;
        Playlist playlist;
        Supplier<CompletableFuture<Playlist>> playlistLazyLoader;

        PlaylistSubState(long playlistId, Supplier<CompletableFuture<Playlist>> playlistLazyLoader) {
            this.playlistId = playlistId;
            this.playlistLazyLoader = playlistLazyLoader;
        }

        PlaylistSubState(Supplier<CompletableFuture<Playlist>> playlistLazyLoader) {
            this.playlistId = -1;
            this.playlistLazyLoader = playlistLazyLoader;
        }

        @Override
        public long playlistId() {
            return playlistId;
        }

        @SneakyThrows
        private CompletableFuture<Playlist> loadPlaylist() {
            if (playlist != null && playlistId != -1) {
                // Follow the latest cached instance: the cache may have been replaced
                // by a refresh or re-evicted, and optimistic edits must land on the
                // same instance the UI listens to, otherwise they only get applied
                // later via the server-side fallback.
                Playlist latest = musicService.loadPlaylistDetail(playlistId, false).getNow(null);
                if (latest != null && latest != playlist) {
                    playlist = latest;
                }
                return CompletableFuture.completedFuture(playlist);
            }
            CompletableFuture<Playlist> future = new CompletableFuture<>();
            CompletableFuture<Playlist> completableFuture = playlistLazyLoader.get();
            playlist = completableFuture.getNow(null);
            if (playlist != null) {
                playlistId = playlist.getId();
                future.complete(playlist);
            } else if (completableFuture.state() == Future.State.SUCCESS) {
                playlist = completableFuture.get();
                playlistId = playlist.getId();
                future.complete(playlist);
            } else {
                MusicHud.EXECUTOR.submit(() -> {
                    try {
                        playlist = completableFuture.get();
                        playlistId = playlist.getId();
                        future.complete(playlist);
                    } catch (InterruptedException | ExecutionException e1) {
                        future.completeExceptionally(e1);
                    }
                });
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
            boolean notifyLater;
            if (playlistId != -1) {
                notifyPlaylistModified(playlistId, musicDetail.getId(), true);
                notifyLater = true;
            } else {
                notifyLater = false;
            }
            return loadPlaylist().thenCompose(playlist1 -> {
                if (notifyLater) {
                    notifyPlaylistModified(playlist1.getId(), musicDetail.getId(), true);
                }
                var tracks = playlist1.getMusicDetails();
                var edit = tracks.beginEdit();
                tracks.addFirst(musicDetail);
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.ADD);
                return RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .handle((response, throwable) -> {
                            if (throwable != null) {
                                edit.rollback();
                                throw new RuntimeException(throwable);
                            }
                            if (!response.isSuccess()) {
                                edit.rollback();
                                throw new RuntimeException(response.getMessage());
                            }
                            edit.commit();
                            return null;
                        });
            });
        }

        @Override
        public CompletableFuture<Void> remove() {
            boolean notifyLater;
            if (playlistId != -1) {
                notifyPlaylistModified(playlistId, musicDetail.getId(), false);
                notifyLater = true;
            } else {
                notifyLater = false;
            }
            return loadPlaylist().thenCompose(playlist1 -> {
                if (notifyLater) {
                    notifyPlaylistModified(playlist1.getId(), musicDetail.getId(), false);
                }
                var tracks = playlist1.getMusicDetails();
                var edit = tracks.beginEdit();
                tracks.remove(musicDetail);
                ModifyPlaylistRequest request = new ModifyPlaylistRequest(musicDetail.getId(), playlist1.getId(), ModifyType.REMOVE);
                return RequestResponseManager.send(request, ModifyPlaylistResponse.class, Duration.ofSeconds(5))
                        .handle((response, throwable) -> {
                            if (throwable != null) {
                                edit.rollback();
                                throw new RuntimeException(throwable);
                            }
                            if (!response.isSuccess()) {
                                edit.rollback();
                                throw new RuntimeException(response.getMessage());
                            }
                            edit.commit();
                            return null;
                        });
            });
        }

        @Override
        public Unregister onOthersModify(Consumer<Boolean> listener) {
            return registerModifyListener(new TrackPlaylistPair(playlistId, musicDetail.getId()), listener);
        }
    }
}
