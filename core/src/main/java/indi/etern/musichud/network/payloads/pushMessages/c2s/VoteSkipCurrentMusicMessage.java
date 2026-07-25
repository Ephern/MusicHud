package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record VoteSkipCurrentMusicMessage(long id) implements C2SPayload {
    public static final ByteBufCodec<VoteSkipCurrentMusicMessage> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            VoteSkipCurrentMusicMessage::id,
            VoteSkipCurrentMusicMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    VoteSkipCurrentMusicMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        MusicPlayerServerService.getInstance().voteSkipCurrent(message.id, player.getUUID());
                    })
            );
        }
    }
}
