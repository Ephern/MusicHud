package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record ScrobbleMessage(long id, int playedInSecond, int durationInSecond, int bitrate, Quality quality) implements C2SPayload {
    public static final ByteBufCodec<ScrobbleMessage> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            ScrobbleMessage::id,
            Codecs.INT,
            ScrobbleMessage::playedInSecond,
            Codecs.INT,
            ScrobbleMessage::durationInSecond,
            Codecs.INT,
            ScrobbleMessage::bitrate,
            Quality.CODEC,
            ScrobbleMessage::quality,
            ScrobbleMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ScrobbleMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        IMusicApiService.getInstance(ApiProvider.NCM)
                                .scrobble(message.id, message.playedInSecond, message.durationInSecond, message.bitrate, message.quality, player.getUUID());
                    })
            );
        }
    }
}
