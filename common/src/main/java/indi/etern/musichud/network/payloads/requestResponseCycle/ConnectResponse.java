package indi.etern.musichud.network.payloads.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

import static indi.etern.musichud.MusicHud.LOGGER;

public record ConnectResponse(boolean accepted, Version serverVersion,
                              List<ApiProvider> availableApis) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectResponse> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
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
                    if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                        LOGGER.info("Connecting {}", payload.accepted() ? "accepted" : "denied");
                        if (payload.accepted()) {
                            if (Version.capableWith(payload.serverVersion)) {
                                MusicService.resetCurrentMusicStatus();
                                NowPlayingInfo.getInstance().stop();
                                StreamAudioPlayer.getInstance().stop();
                                if (Minecraft.getInstance().getCurrentServer() != null
                                        && MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED
                                        && clientConfig != null && clientConfig.getEnableIsolatedMode()) {
                                    LoginService.getInstance().disconnectToExternalOrIntegratedServer();
                                }

                                MusicHud.setConnectStatus(MusicHud.ConnectStatus.CONNECTED);
                                LoginService.getInstance().loginToServer(LoginService.ConnectionType.EXTERNAL);
                            } else {
                                LoginService.getInstance().logout();
                                MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
                            }
                        } else {
                            MusicHud.setConnectStatus(MusicHud.ConnectStatus.INCOMPATIBLE);
                        }
                    } else if (!payload.accepted()) {
                        LOGGER.info("Disconnected");
                        LoginService.getInstance().disconnectToExternalOrIntegratedServer();
                    }
                    MuiModApi.postToUiThread(MainFragment::refresh);
                };
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectResponse.class, CODEC,
                    receiver
            );
        }
    }
}
