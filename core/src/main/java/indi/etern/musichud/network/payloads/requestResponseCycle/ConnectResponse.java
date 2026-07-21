package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.utils.IClientDistUtil;

import java.util.List;

import static indi.etern.musichud.MusicHud.LOGGER;

public record ConnectResponse(boolean accepted, Version serverVersion,
                              List<ApiProvider> availableApis) implements S2CPayload {
    public static final ByteBufCodec<ConnectResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    ConnectResponse::accepted,
                    Version.PACKET_CODEC,
                    ConnectResponse::serverVersion,
                    Codecs.ofList(() -> Codecs.ofEnum(ApiProvider.class)),
                    ConnectResponse::availableApis,
                    ConnectResponse::new
            );

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
            NetworkReceiver<ConnectResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (payload, player) -> {
                    IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
                    IClientLoginService clientLoginService = IClientLoginService.getInstance();
                    if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                        LOGGER.info("Connecting {}", payload.accepted() ? "accepted" : "denied");
                        if (payload.accepted()) {
                            if (Version.compatibleWith(payload.serverVersion)) {
                                MusicHud.EXECUTOR.execute(IClientMusicService.getInstance()::checkAndResetInitialSync);
                                if (clientDistUtil.inIntegratedServer()
                                        && MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED
                                        && clientConfig.getEnableIsolatedMode()) {
                                    clientLoginService.disconnectToExternalOrIntegratedServer();
                                }

                                MusicHud.setConnectStatus(MusicHud.ConnectStatus.CONNECTED);
                                clientLoginService.loginToServer(IClientLoginService.ConnectionType.EXTERNAL);
                            } else {
                                clientLoginService.logout();
                                MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
                            }
                        } else {
                            MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
                        }
                    } else if (!payload.accepted()) {
                        LOGGER.info("Disconnected");
                        clientLoginService.disconnectToExternalOrIntegratedServer();
                    }
                    clientDistUtil.refreshMainGUI();
                };
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectResponse.class, CODEC,
                    receiver
            );
        }
    }
}
