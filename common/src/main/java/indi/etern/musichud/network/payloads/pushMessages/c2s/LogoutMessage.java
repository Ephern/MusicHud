package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LogoutMessage implements C2SPayload {
    public static final LogoutMessage MESSAGE = new LogoutMessage();
    public static final ByteBufCodec<LogoutMessage> CODEC = ByteBufCodec.unit(MESSAGE);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    LogoutMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
                        loginApiService.logout(player);
                    })
            );
        }
    }
}
