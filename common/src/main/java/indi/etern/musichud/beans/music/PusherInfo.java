package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.Codecs;
import lombok.NonNull;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

public record PusherInfo(long uid, @NonNull UUID playerUUID, @NonNull String playerName) {
    public static final StreamCodec<RegistryFriendlyByteBuf, PusherInfo> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            PusherInfo::uid,
            Codecs.UUID,
            PusherInfo::playerUUID,
            ByteBufCodecs.STRING_UTF8,
            PusherInfo::playerName,
            PusherInfo::new
    );
    public static final PusherInfo EMPTY = new PusherInfo(0L, new UUID(0L, 0L), "");

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PusherInfo pusherInfo && pusherInfo.playerUUID.equals(playerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUUID);
    }
}
