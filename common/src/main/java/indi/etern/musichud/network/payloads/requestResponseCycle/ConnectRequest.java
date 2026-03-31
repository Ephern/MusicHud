package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record ConnectRequest(Version clientVersion) implements C2SPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, ConnectRequest> CODEC =
            StreamCodec.composite(Version.PACKET_CODEC, ConnectRequest::clientVersion, ConnectRequest::new);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((startQRLoginRequest, serverPlayer) -> {
                        boolean compatible = Version.capableWith(startQRLoginRequest.clientVersion());
                        ConnectResponse response = new ConnectResponse(compatible, Version.current, List.of(ApiProvider.NCM));
                        IServerNetworkService.getInstance().sendToPlayer(serverPlayer, response);
                        if (compatible) {
                            ILoginApiService instance = ILoginApiService.getInstance(ApiProvider.NCM);
                            instance.joinUnlogged(serverPlayer);
                            MusicPlayerServerService.getInstance().sendSyncPlayingStatusToPlayer(serverPlayer);
                        }
                    })
            );
        }
    }
}