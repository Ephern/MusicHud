package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.IdentifiedBeans;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.interfaces.Unregister;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SubscribeState<T extends IdentifiedBeans> implements ISubscribeState<T> {
    private static final ConcurrentHashMap<ClassIdPair, CopyOnWriteArrayList<Consumer<Boolean>>>
            modifyListeners = new ConcurrentHashMap<>();
    private static final Logger logger = MusicHud.getLogger(SubscribeState.class);
    @Getter
    private final long beanId;
    private final Function<Long, CompletableFuture<T>> fullLoader;
    private final Supplier<CompletableFuture<SequencedSet<T>>> subscribedSetSupplier;
    private final BiConsumer<T, Boolean> subscribeAction;
    private final Class<T> tClass;
    private T t;
    public SubscribeState(long id, Class<T> tClass,
                          Function<Long, CompletableFuture<T>> fullLoader,
                          Supplier<CompletableFuture<SequencedSet<T>>> subscribedSetSupplier,
                          BiConsumer<T, Boolean> subscribeAction) {
        beanId = id;
        this.tClass = tClass;
        this.fullLoader = fullLoader;
        this.subscribedSetSupplier = subscribedSetSupplier;
        this.subscribeAction = subscribeAction;
    }

    static Unregister registerModifyListener(ClassIdPair classIdPair, Consumer<Boolean> listener) {
        modifyListeners.computeIfAbsent(classIdPair, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            var list = modifyListeners.get(classIdPair);
            if (list != null) list.remove(listener);
        };
    }

    static void notifySubscribe(ClassIdPair classIdPair, boolean subscribed) {
        var list = modifyListeners.get(classIdPair);
        if (list == null) return;
        for (var listener : list) {
            listener.accept(subscribed);
        }
    }

    private CompletableFuture<T> loadT() {
        if (t != null) {
            return CompletableFuture.completedFuture(t);
        }
        return fullLoader.apply(beanId).thenApply(t -> {
            this.t = t;
            return t;
        });
    }

    private void handleThrowable(String action, Throwable throwable) {
        if (throwable != null) {
            logger.error("Failed to {}: {}", action, throwable);
        }
    }

    @Override
    public CompletableFuture<Boolean> isSubscribed() {
        return subscribedSetSupplier.get()
                .thenApply(ts -> ts.stream().anyMatch(i -> i.getId() == beanId))
                .whenComplete((unused, throwable) -> handleThrowable("list", throwable));
    }

    @Override
    public CompletableFuture<Void> subscribe() {
        return loadT().thenCompose(t ->
                subscribedSetSupplier.get()
                        .thenAccept(ts -> {
                            subscribeAction.accept(t, true);
                            notifySubscribe(new ClassIdPair(beanId, tClass), true);
                            ts.addFirst(t);
                        }).whenComplete(((unused, throwable) -> handleThrowable("subscribe", throwable)))
                .whenComplete((unused, throwable) -> handleThrowable("loadT", throwable)));
    }

    @Override
    public CompletableFuture<Void> unsubscribe() {
        return loadT().thenCompose(t ->
                subscribedSetSupplier.get()
                        .thenAccept(ts -> {
                            subscribeAction.accept(t, false);
                            notifySubscribe(new ClassIdPair(beanId, tClass), false);
                            ts.remove(t);
                        }).whenComplete((unused, throwable) -> handleThrowable("unsubscribe", throwable)))
                .whenComplete((unused1, throwable1) -> handleThrowable("loadT", throwable1));
    }

    @Override
    public Unregister onOthersModify(Consumer<Boolean> listener) {
        return registerModifyListener(new ClassIdPair(beanId, tClass), listener);
    }

    record ClassIdPair(long id, Class<?> clazz) {

    }
}
