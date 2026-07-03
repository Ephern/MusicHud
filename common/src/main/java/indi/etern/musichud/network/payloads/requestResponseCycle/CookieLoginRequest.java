package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record CookieLoginRequest(LoginCookieInfo loginCookieInfo, boolean tryRefresh) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, CookieLoginRequest> CODEC =
            StreamCodec.composite(
                    LoginCookieInfo.STREAM_CODEC,
                    CookieLoginRequest::loginCookieInfo,
                    ByteBufCodecs.BOOL,
                    CookieLoginRequest::tryRefresh,
                    CookieLoginRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    CookieLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((loginRequest, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginWithCookie(loginRequest.loginCookieInfo, loginRequest.tryRefresh, player);
                    })
            );
        }

    }
}
