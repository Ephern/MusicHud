package indi.etern.musichud.client.utils.ui;

public interface Mixable<T extends Mixable<T>> {
    T mix(T next, float transitionProgress);
}