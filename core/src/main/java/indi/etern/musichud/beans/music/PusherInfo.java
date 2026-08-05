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
            Codecs.UUID, PusherInfo::getPlayerUUID,
            Codecs.STRING_UTF8, PusherInfo::getPlayerName,
            PusherInfo::new
    );
    public static final PusherInfo EMPTY = new PusherInfo(new UUID(0L, 0L), "");
    @Getter
    @NonNull
    private final UUID playerUUID;
    @Getter
    @NonNull
    private final  String playerName;

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
