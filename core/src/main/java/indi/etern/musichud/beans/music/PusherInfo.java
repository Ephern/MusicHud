package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.IPlayerClient;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record PusherInfo(@NonNull UUID playerUUID, @NonNull String playerName) {
    public static final ByteBufCodec<PusherInfo> CODEC = ByteBufCodec.composite(
            Codecs.UUID, PusherInfo::playerUUID,
            Codecs.STRING_UTF8, PusherInfo::playerName,
            PusherInfo::new
    );
    public static final PusherInfo EMPTY = new PusherInfo(new UUID(0L, 0L), "");

    public static PusherInfo ofPlayer(IPlayerClient player) {
        return new PusherInfo(
                player.getUUID(),
                player.getName()
        );
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PusherInfo pusherInfo && pusherInfo.playerUUID.equals(playerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUUID);
    }

    @Override
    public @NotNull String toString() {
        return "PusherInfo[" +
                "playerUUID=" + playerUUID + ", " +
                "playerName=" + playerName + ']';
    }

}
