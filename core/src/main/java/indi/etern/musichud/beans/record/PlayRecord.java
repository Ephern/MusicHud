package indi.etern.musichud.beans.record;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

@AllArgsConstructor
public class PlayRecord<T> {
    public static <T> ByteBufCodec<PlayRecord<T>> codec(Supplier<ByteBufCodec<T>> supplier) {
        return ByteBufCodec.composite(
                Codecs.INSTANT,
                PlayRecord::getPlayTime,
                Codecs.ofEnum(ResourceType.class),
                PlayRecord::getResourceType,
                Codecs.STRING_UTF8,
                PlayRecord::getOs,
                Codecs.ofNullable(supplier.get()),
                PlayRecord::getData,
                PlayRecord::new
        );
    }

    public enum ResourceType {
        SONG, ALBUM, PLAYLIST, UNSET
    }

    Instant playTime;
    ResourceType resourceType;
    String os;
    @Getter
    T data;

    public Instant getPlayTime() {
        return Objects.requireNonNullElse(playTime, Instant.MIN);
    }

    public ResourceType getResourceType() {
        return Objects.requireNonNullElse(resourceType, ResourceType.UNSET);
    }

    public String getOs() {
        return Objects.requireNonNullElse(os, "");
    }
}