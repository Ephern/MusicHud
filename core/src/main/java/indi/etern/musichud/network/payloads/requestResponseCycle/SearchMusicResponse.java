package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.utils.IClientDistUtil;

import java.util.List;

public record SearchMusicResponse(int offset, List<MusicDetail> result) implements S2CPayload {
    public static final ByteBufCodec<SearchMusicResponse> CODEC = ByteBufCodec.composite(
            Codecs.INT,
            SearchMusicResponse::offset,
            Codecs.ofList(() -> MusicDetail.CODEC),
            SearchMusicResponse::result,
            SearchMusicResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchMusicResponse.class, CODEC,
                    (message, player) -> {
                        IClientDistUtil.getInstance().setSearchViewMusicsResult(message.offset, message.result());
                    }
            );
        }
    }
}
