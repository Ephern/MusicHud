package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;

import java.util.List;

public class SearchPlaylistsResponse extends SearchResultResponse {
    public static final ByteBufCodec<SearchPlaylistsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT,
                    SearchPlaylistsResponse::getOffset,
                    Codecs.ofList(() -> Playlist.CODEC),
                    SearchPlaylistsResponse::getResult,
                    SearchPlaylistsResponse::new
            )
    );

    private final List<Playlist> result;

    public SearchPlaylistsResponse(int offset, List<Playlist> result) {
        super(offset);
        this.result = result;
    }

    @Override
    public SearchType getSearchType() {
        return SearchType.PLAYLIST;
    }

    @Override
    public List<Playlist> getResult() {
        return result;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchPlaylistsResponse.class, CODEC,
                    (message, player) -> RequestResponseManager.complete(message)
            );
        }
    }
}
