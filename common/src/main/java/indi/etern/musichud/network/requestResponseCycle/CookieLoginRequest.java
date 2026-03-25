package indi.etern.musichud.network.requestResponseCycle;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.AccountDetail;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.C2SPayload;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.server.api.LoginApiService;
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
            NetworkRegisterUtil.autoRegisterPayload(
                    CookieLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((loginRequest, serverPlayer) -> {
                        LoginApiService loginApiService = LoginApiService.getInstance();
                        if (loginRequest.tryRefresh) {
                            try {
                                loginApiService.refreshAndSend(serverPlayer, loginRequest.loginCookieInfo);
                            } catch (Exception e) {
                                NetworkManager.sendToPlayer(serverPlayer,
                                        new LoginResultMessage(false,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                Profile.ANONYMOUS
                                        )
                                );
                            }
                        } else if (loginRequest.loginCookieInfo.type() != LoginType.ANONYMOUS) {
                            try {
                                AccountDetail accountDetail =
                                        loginApiService.loadUserProfile(serverPlayer, loginRequest.loginCookieInfo);
                                NetworkManager.sendToPlayer(serverPlayer,
                                        new LoginResultMessage(true,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                accountDetail.getProfile()
                                        )
                                );
                            } catch (Exception e) {
                                NetworkManager.sendToPlayer(serverPlayer,
                                        new LoginResultMessage(false,
                                                "",
                                                loginRequest.loginCookieInfo,
                                                Profile.ANONYMOUS
                                        )
                                );
                            }
                        }
                        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(serverPlayer));
                    })
            );
        }
    }
}
