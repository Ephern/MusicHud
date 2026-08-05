package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;

import java.util.List;

public class SearchArtistsResponse extends SearchResultResponse {
    public static final ByteBufCodec<SearchArtistsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT,
                    SearchArtistsResponse::getOffset,
                    Codecs.ofList(() -> Artist.CODEC),
                    SearchArtistsResponse::getResult,
                    SearchArtistsResponse::new
            )
    );

    private final List<Artist> result;

    public SearchArtistsResponse(int offset, List<Artist> result) {
        super(offset);
        this.result = result;
    }

    @Override
    public SearchType getSearchType() {
        return SearchType.ARTIST;
    }

    @Override
    public List<Artist> getResult() {
        return result;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchArtistsResponse.class, CODEC,
                    (message, player) -> RequestResponseManager.complete(message)
            );
        }
    }
}
