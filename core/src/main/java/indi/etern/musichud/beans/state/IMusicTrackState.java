package indi.etern.musichud.beans.state;

import indi.etern.musichud.interfaces.Unregister;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IMusicTrackState {
    IMusicTrackState NONE = new IMusicTrackState() {
        @Override
        public IPlaylistSubState currentUsersLikeList() {
            return IPlaylistSubState.NONE;
        }

        @Override
        public IPlaylistSubState playlist(long playlistId) {
            return IPlaylistSubState.NONE;
        }
    };

    IPlaylistSubState currentUsersLikeList();

    IPlaylistSubState playlist(long playlistId);

    interface IPlaylistSubState {
        IPlaylistSubState NONE = new IPlaylistSubState() {
            @Override
            public long playlistId() {
                return -1;
            }

            @Override
            public CompletableFuture<Boolean> isContained() {
                return CompletableFuture.failedFuture(new IllegalStateException());
            }

            @Override
            public CompletableFuture<Void> add() {
                return CompletableFuture.failedFuture(new IllegalStateException());
            }

            @Override
            public CompletableFuture<Void> remove() {
                return CompletableFuture.failedFuture(new IllegalStateException());
            }

            @Override
            public Unregister onExternalModify(Consumer<Boolean> listener) {
                throw new IllegalStateException();
            }
        };

        long playlistId();

        CompletableFuture<Boolean> isContained();

        CompletableFuture<Void> add();

        CompletableFuture<Void> remove();

        Unregister onExternalModify(Consumer<Boolean> listener);

        default CompletableFuture<Boolean> toggle() {
            return isContained().thenCompose(contained ->
                    contained ? remove().thenApply(v -> false)
                            : add().thenApply(v -> true));
        }
    }
}
