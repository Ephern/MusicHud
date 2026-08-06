package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

import java.util.List;

public record ConnectRequest(Version clientVersion) implements C2SPayload {
    public static final ByteBufCodec<ConnectRequest> CODEC =
            ByteBufCodec.composite(Version.PACKET_CODEC, ConnectRequest::clientVersion, ConnectRequest::new);

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
                        boolean compatible = Version.compatibleWith(startQRLoginRequest.clientVersion());
                        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && !clientConfig.getEnabledInIntegratedServer()) {
                            // Integrated server with the server-side features disabled:
                            // no handshake at all, the client falls back to isolated mode on timeout.
                            return;
                        }
                        ConnectResponse response = new ConnectResponse(compatible, Version.current, List.of(ApiProvider.NCM));
                        IServerNetworkService.getInstance().sendToPlayer(player, response);
                        // Player data is NOT joined here: the client confirms the connection
                        // with ConfirmConnectRequest before joinUnlogged() is called.
                    })
            );
        }
    }
}