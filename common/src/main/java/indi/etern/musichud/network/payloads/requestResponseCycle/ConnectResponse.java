package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import static indi.etern.musichud.MusicHud.LOGGER;

public record ConnectResponse(boolean accepted, Version serverVersion) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectResponse> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ConnectResponse::accepted,
                    Version.PACKET_CODEC,
                    ConnectResponse::serverVersion,
                    ConnectResponse::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ConnectResponse.class, CODEC,
                    (payload, player) -> {
                        LOGGER.info("Connecting {}", payload.accepted() ? "accepted" : "denied");
                        if (payload.accepted()) {
                            if (Version.capableWith(payload.serverVersion)) {
                                MusicHud.setStatus(MusicHud.ConnectStatus.CONNECTED);
                                LoginService.getInstance().loginToServer();
                            } else {
                                LoginService.getInstance().logout();
                                MusicHud.setStatus(MusicHud.ConnectStatus.INCAPABLE);
                            }
                        } else {
                            MusicHud.setStatus(MusicHud.ConnectStatus.INCAPABLE);
                        }
                    }
            );
        }
    }
}