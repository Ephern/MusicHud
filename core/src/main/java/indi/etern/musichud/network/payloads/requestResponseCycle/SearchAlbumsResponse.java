package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;

import java.util.List;

public class SearchAlbumsResponse extends SearchResultResponse {
    public static final ByteBufCodec<SearchAlbumsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT,
                    SearchAlbumsResponse::getOffset,
                    Codecs.ofList(() -> Album.CODEC),
                    SearchAlbumsResponse::getResult,
                    SearchAlbumsResponse::new
            )
    );

    private final List<Album> result;

    public SearchAlbumsResponse(int offset, List<Album> result) {
        super(offset);
        this.result = result;
    }

    @Override
    public SearchType getSearchType() {
        return SearchType.ALBUM;
    }

    @Override
    public List<Album> getResult() {
        return result;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchAlbumsResponse.class, CODEC,
                    (message, player) -> RequestResponseManager.complete(message)
            );
        }
    }
}
