package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;

import java.util.List;

public class SearchMusicResponse extends SearchResultResponse {
    public static final ByteBufCodec<SearchMusicResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT,
                    SearchMusicResponse::getOffset,
                    Codecs.ofList(() -> MusicDetail.CODEC),
                    SearchMusicResponse::getResult,
                    SearchMusicResponse::new
            )
    );

    private final List<MusicDetail> result;

    public SearchMusicResponse(int offset, List<MusicDetail> result) {
        super(offset);
        this.result = result;
    }

    @Override
    public SearchType getSearchType() {
        return SearchType.MUSIC;
    }

    @Override
    public List<MusicDetail> getResult() {
        return result;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchMusicResponse.class, CODEC,
                    (message, player) -> RequestResponseManager.complete(message)
            );
        }
    }
}
