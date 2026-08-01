package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.IdentifiedBeans;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.interfaces.Unregister;
import lombok.Getter;

import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SubscribeState<T extends IdentifiedBeans> implements ISubscribeState<T> {
    record ClassIdPair(long id, Class<?> clazz) {

    }

    private static final ConcurrentHashMap<ClassIdPair, CopyOnWriteArrayList<Consumer<Boolean>>>
            modifyListeners = new ConcurrentHashMap<>();
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


    @Getter
    private final long beanId;
    private final Function<Long, CompletableFuture<T>> fullLoader;
    private final Supplier<CompletableFuture<SequencedSet<T>>> subscribedSetSupplier;
    private final BiConsumer<T, Boolean> subscribeAction;
    private T t;
    private final Class<T> tClass;

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

    private CompletableFuture<T> loadT() {
        if (t != null) {
            return CompletableFuture.completedFuture(t);
        }
        return fullLoader.apply(beanId).thenApply(t -> {
            this.t = t;
            return t;
        });
    }

    @Override
    public CompletableFuture<Boolean> isSubscribed() {
        return loadT().thenCompose(t ->
                subscribedSetSupplier.get()
                        .thenApply(ts -> ts.contains(t))
        );
    }

    @Override
    public CompletableFuture<Void> subscribe() {
        return loadT().thenCompose(t ->
                subscribedSetSupplier.get()
                        .thenAccept(ts -> {
                            subscribeAction.accept(t, true);
                            notifySubscribe(new ClassIdPair(beanId, tClass), true);
                            ts.addFirst(t);
                        }));
    }

    @Override
    public CompletableFuture<Void> unsubscribe() {
        return loadT().thenCompose(t ->
                subscribedSetSupplier.get()
                        .thenAccept(ts -> {
                            subscribeAction.accept(t, false);
                            notifySubscribe(new ClassIdPair(beanId, tClass), false);
                            ts.remove(t);
                        }));
    }

    @Override
    public Unregister onOthersModify(Consumer<Boolean> listener) {
        return registerModifyListener(new ClassIdPair(beanId, tClass), listener);
    }
}
