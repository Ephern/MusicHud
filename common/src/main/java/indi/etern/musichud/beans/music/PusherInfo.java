package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

public final class PusherInfo {
    public static final ByteBufCodec<PusherInfo> CODEC = ByteBufCodec.composite(
            Codecs.VAR_LONG,
            (pusherInfo) -> -1L,// for compatibility with older versions. TODO: remove when bump to 1.3.0
            Codecs.UUID,
            PusherInfo::getPlayerUUID,
            Codecs.STRING_UTF8,
            PusherInfo::getPlayerName,
            (uid, uuid, name) -> new PusherInfo(uuid, name)
    );
    public static final PusherInfo EMPTY = new PusherInfo(new UUID(0L, 0L), "");
    @Getter
    private final @NonNull UUID playerUUID;
    @Getter
    private final @NonNull String playerName;

    public PusherInfo(@NonNull UUID playerUUID, @NonNull String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }

    public static PusherInfo ofPlayer(IPlayerClient player) {
        LoginApiService.PlayerLoginInfo loginInfo = ILoginApiService.getInstance(ApiProvider.NCM).getPlayerInfoMap().get(player.getUUID());
        PusherInfo pusherInfo = PusherInfo.EMPTY;
        if (loginInfo != null) {
            pusherInfo = new PusherInfo(
                    player.getUUID(),
                    player.getName()
            );
        }
        return pusherInfo;
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
                "playerUUID=" + playerUUID + ", " +
                "playerName=" + playerName + ']';
    }

}
