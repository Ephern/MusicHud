package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.utils.IClientDistUtil;

import java.util.List;

public record SearchAlbumsResponse(int offset,List<Album> result) implements S2CPayload {
    public static final ByteBufCodec<SearchAlbumsResponse> CODEC = ByteBufCodec.composite(
            Codecs.INT,
            SearchAlbumsResponse::offset,
            Codecs.ofList(() -> Album.CODEC),
            SearchAlbumsResponse::result,
            SearchAlbumsResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchAlbumsResponse.class, CODEC,
                    (message, player) -> {
                        IClientDistUtil.getInstance().setSearchViewAlbumsResult(message.offset, message.result);
                    }
            );
        }
    }
}
