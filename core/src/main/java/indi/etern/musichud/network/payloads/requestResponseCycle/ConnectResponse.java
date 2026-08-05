package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IConnectionManager;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ApiProvider;

import java.util.List;


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
        public void register() {
            NetworkReceiver<ConnectResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (payload, player) -> IConnectionManager.getInstance().onConnectResponse(payload);
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectResponse.class, CODEC,
                    receiver
            );
        }
    }
}
