package indi.etern.musichud.utils.collections;

import com.google.common.collect.ForwardingSet;
import indi.etern.musichud.interfaces.Unregister;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ObservableSequencedSet<E> extends ForwardingSet<E> implements SequencedSet<E> {
    private final SequencedSet<E> delegate;

    private final Set<Consumer<E>> addListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<E>> removeListeners = ConcurrentHashMap.newKeySet();

    public ObservableSequencedSet(SequencedSet<E> delegate) {
        this.delegate = delegate;
    }

    public ObservableSequencedSet(Integer integer) {
        this.delegate = new LinkedHashSet<>(integer);
    }

    public ObservableSequencedSet() {
        this(0);
    }

    @Override
    @NotNull
    protected Set<E> delegate() {
        return this.delegate;
    }

    @Override
    public boolean add(E element) {
        boolean changed = super.add(element);
        if (changed) {
            addListeners.forEach(l -> l.accept(element));
        }
        return changed;
    }

    @Override
    public void addFirst(E e) {
        delegate.addFirst(e);
        addListeners.forEach(l -> l.accept(e));
    }

    @Override
    public void addLast(E e) {
        delegate.addLast(e);
        addListeners.forEach(l -> l.accept(e));
    }

    @Override
    public E removeFirst() {
        E e = delegate.removeFirst();
        removeListeners.forEach(l -> l.accept(e));
        return e;
    }

    @Override
    public E removeLast() {
        E e = delegate.removeLast();
        removeListeners.forEach(l -> l.accept(e));
        return e;
    }

    @Override
    public boolean remove(Object object) {
        boolean changed = super.remove(object);
        if (changed) {
            //noinspection unchecked
            removeListeners.forEach(l -> l.accept((E) object));
        }
        return changed;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> collection) {
        List<? extends E> actuallyAdded = collection.stream().filter(object -> !contains(object)).toList();
        boolean changed = super.addAll(collection);
        if (changed) {
            actuallyAdded.forEach(e -> {
                addListeners.forEach(actuallyAdded::forEach);
            });
        }
        return changed;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        List<?> actuallyRemoved = collection.stream().filter(this::contains).toList();
        boolean changed = super.removeAll(collection);
        if (changed) {
            //noinspection unchecked
            removeListeners.forEach(l -> actuallyRemoved.forEach(i -> l.accept((E) i)));
        }
        return changed;
    }

    @Override
    public void clear() {
        Set<E> copy = Set.copyOf(this);
        super.clear();
        removeListeners.forEach(copy::forEach);
    }

    public Unregister registerOnAdd(Consumer<E> consumer) {
        addListeners.add(consumer);
        return () -> addListeners.remove(consumer);
    }

    public Unregister registerOnRemove(Consumer<E> consumer) {
        removeListeners.add(consumer);
        return () -> removeListeners.remove(consumer);
    }

    @Override
    @NotNull
    public SequencedSet<E> reversed() {
        return new ObservableSequencedSet<>(delegate.reversed());
    }
}
