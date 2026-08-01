package indi.etern.musichud.beans.state;

import indi.etern.musichud.interfaces.Unregister;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ISubscribeState<T> {
    long getBeanId();

    CompletableFuture<Boolean> isSubscribed();

    CompletableFuture<Void> subscribe();

    CompletableFuture<Void> unsubscribe();

    Unregister onOthersModify(Consumer<Boolean> listener);

    default CompletableFuture<Boolean> toggle() {
        return isSubscribed().thenCompose(subscribed ->
                subscribed ? unsubscribe().thenApply(v -> false)
                        : subscribe().thenApply(v -> true));
    }
}
