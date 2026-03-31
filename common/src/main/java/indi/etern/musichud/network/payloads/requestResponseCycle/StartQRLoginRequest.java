package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.EqualsAndHashCode;
import net.minecraft.network.codec.StreamCodec;

@EqualsAndHashCode
public class StartQRLoginRequest implements C2SPayload {
    public static final StartQRLoginRequest REQUEST = new StartQRLoginRequest();
    public static final StreamCodec<Object, StartQRLoginRequest> CODEC = StreamCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    StartQRLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((startQRLoginRequest, serverPlayer) -> {
                        var qrLoginInfo = ILoginApiService.getInstance(ApiProvider.NCM).startQRLoginByPlayer(serverPlayer);
                        IServerNetworkService.getInstance().sendToPlayer(serverPlayer,new StartQRLoginResponse(qrLoginInfo.data().qrimg()));
                    })
            );
        }
    }
}