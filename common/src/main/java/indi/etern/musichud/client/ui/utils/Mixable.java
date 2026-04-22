package indi.etern.musichud.client.ui.utils;

public interface Mixable<T extends Mixable<T>> {
    T mix(T next, float transitionProgress);
}