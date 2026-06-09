package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record ConnectRequest(Version clientVersion) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectRequest> CODEC =
            StreamCodec.composite(Version.PACKET_CODEC, ConnectRequest::clientVersion, ConnectRequest::new);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        private static ClientConfig clientConfig;

        static {
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                try {
                    clientConfig = ClientConfig.getInstance();
                } catch (UnsupportedOperationException e) {
                    clientConfig = null;
                }
            }
        }

        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((startQRLoginRequest, player) -> {
                        ILoginApiService instance = ILoginApiService.getInstance(ApiProvider.NCM);
                        boolean compatible = Version.capableWith(startQRLoginRequest.clientVersion());
                        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && clientConfig != null && !clientConfig.getEnabledInIntegratedServer()) {
                            if (compatible) {
                                instance.joinUnlogged(player);
                            }
                            return;
                        }
                        ConnectResponse response = new ConnectResponse(compatible, Version.current, List.of(ApiProvider.NCM));
                        IServerNetworkService.getInstance().sendToPlayer(player, response);
                        if (compatible) {
                            instance.joinUnlogged(player);
                            MusicPlayerServerService.getInstance().sendSyncPlayingStatusToPlayer(player);
                        }
                    })
            );
        }
    }
}