package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.UUID;

public final class PusherInfo {
    public static final ByteBufCodec<PusherInfo> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            PusherInfo::getUid,
            Codecs.UUID,
            PusherInfo::getPlayerUUID,
            Codecs.STRING_UTF8,
            PusherInfo::getPlayerName,
            PusherInfo::new
    );
    public static final PusherInfo EMPTY = new PusherInfo(0L, new UUID(0L, 0L), "");
    @Getter
    private final long uid;
    @Getter
    private final @NonNull UUID playerUUID;
    @Getter
    private final @NonNull String playerName;
    @Getter
    @Setter
    private transient Player player;

    public PusherInfo(long uid, @NonNull UUID playerUUID, @NonNull String playerName) {
        this.uid = uid;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
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
    public String toString() {
        return "PusherInfo[" +
                "uid=" + uid + ", " +
                "playerUUID=" + playerUUID + ", " +
                "playerName=" + playerName + ']';
    }

}
