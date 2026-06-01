package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;

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
                        ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
                        IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
                        if (loginRequest.tryRefresh) {
                            try {
                                loginApiService.refreshAndSend(player, loginRequest.loginCookieInfo);
                            } catch (Exception e) {
                                serverNetworkService.sendToPlayer(player,
                                        new LoginResultMessage(false,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                Profile.ANONYMOUS
                                        )
                                );
                            }
                        } else if (loginRequest.loginCookieInfo.type() != LoginType.ANONYMOUS) {
                            try {
                                Profile profile =
                                        loginApiService.loadUserProfile(player, loginRequest.loginCookieInfo);
                                serverNetworkService.sendToPlayer(player,
                                        new LoginResultMessage(true,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                profile
                                        )
                                );
                            } catch (Exception e) {
                                serverNetworkService.sendToPlayer(player,
                                        new LoginResultMessage(false,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                Profile.ANONYMOUS
                                        )
                                );
                            }
                        }
                        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(loginApiService.getLoginInfoByPlayer(player)));
                    })
            );
        }
    }
}
