package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Second step of the connect handshake: the client confirms that it accepted the
 * {@link ConnectResponse}, and only then the server registers the player
 * ({@link ILoginApiService#joinUnlogged}). This keeps server-side player data in sync
 * with the client's actual connection state.
 */
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ConfirmConnectMessage implements C2SPayload {
    public static final ConfirmConnectMessage MESSAGE = new ConfirmConnectMessage();
    public static final ByteBufCodec<ConfirmConnectMessage> CODEC = ByteBufCodec.unit(MESSAGE);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConfirmConnectMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).joinUnlogged(player);
                    })
            );
        }
    }
}
