package indi.etern.musichud.utils.collections;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SynchronizedSequencedSet<E> implements SequencedSet<E> {
    private final SequencedSet<E> delegate;

    public SynchronizedSequencedSet(SequencedSet<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized int size() {
        return delegate.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public synchronized boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public synchronized @NotNull Iterator<E> iterator() {
        return new ArrayList<>(delegate).iterator();
    }

    @Override
    public synchronized Object @NotNull [] toArray() {
        return delegate.toArray();
    }

    @Override
    public synchronized <T> T @NotNull [] toArray(@NotNull T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public synchronized boolean add(E e) {
        return delegate.add(e);
    }

    @Override
    public synchronized boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public synchronized boolean containsAll(@NotNull Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public synchronized boolean addAll(@NotNull Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public synchronized boolean retainAll(@NotNull Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public synchronized boolean removeAll(@NotNull Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public synchronized void clear() {
        delegate.clear();
    }

    @Override
    public synchronized E getFirst() {
        return delegate.getFirst();
    }

    @Override
    public synchronized E getLast() {
        return delegate.getLast();
    }

    @Override
    public synchronized void addFirst(E e) {
        delegate.addFirst(e);
    }

    @Override
    public synchronized void addLast(E e) {
        delegate.addLast(e);
    }

    @Override
    public synchronized E removeFirst() {
        return delegate.removeFirst();
    }

    @Override
    public synchronized E removeLast() {
        return delegate.removeLast();
    }

    @Override
    public synchronized boolean removeIf(@NotNull Predicate<? super E> filter) {
        return delegate.removeIf(filter);
    }

    @Override
    public synchronized @NotNull SequencedSet<E> reversed() {
        return new SynchronizedSequencedSet<>(delegate.reversed());
    }

    @Override
    public synchronized @NotNull Spliterator<E> spliterator() {
        return new ArrayList<>(delegate).spliterator();
    }

    @Override
    public synchronized @NotNull Stream<E> stream() {
        return new ArrayList<>(delegate).stream();
    }

    @Override
    public synchronized @NotNull Stream<E> parallelStream() {
        return new ArrayList<>(delegate).parallelStream();
    }

    @Override
    public synchronized void forEach(@NotNull Consumer<? super E> action) {
        delegate.forEach(action);
    }

    @Override
    public synchronized boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public synchronized int hashCode() {
        return delegate.hashCode();
    }
}
