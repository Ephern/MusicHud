package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import org.jetbrains.annotations.Nullable;

/** Wraps a value with its {@link SourceMeta} for end-to-end track source tracking. */
public record Traceable<T>(T value, @Nullable SourceMeta source) {
    public static <T> Traceable<T> of(T value) {
        return new Traceable<>(value, null);
    }

    public static <T> Traceable<T> of(T value, @Nullable SourceMeta source) {
        return new Traceable<>(value, source);
    }

    public static <T> ByteBufCodec<Traceable<T>> codec(ByteBufCodec<T> valueCodec) {
        return ByteBufCodec.composite(
                valueCodec,
                Traceable::value,
                Codecs.ofNullable(SourceMeta.CODEC),
                Traceable::source,
                Traceable::new
        );
    }
}
