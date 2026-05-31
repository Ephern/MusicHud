package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GetMusicResourceRequest(long id,Quality quality,String retryForUrl) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetMusicResourceRequest> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    GetMusicResourceRequest::id,
                    Codecs.ofEnum(Quality.class),
                    GetMusicResourceRequest::quality,
                    ByteBufCodecs.STRING_UTF8,
                    GetMusicResourceRequest::retryForUrl,
                    GetMusicResourceRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetMusicResourceRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        var currentMusicResourceInfo = MusicPlayerServerService.getInstance().getMusicResourceInfo(request.id, request.quality, request.retryForUrl, player);
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetMusicResourceResponse(currentMusicResourceInfo));
                    })
            );
        }
    }
}
