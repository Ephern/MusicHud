package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchRequest extends ApiRequestPayload {
    public static final ByteBufCodec<SearchRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8,
                    SearchRequest::getQuery,
                    Codecs.ofEnum(SearchType.class),
                    SearchRequest::getSearchType,
                    Codecs.INT,
                    SearchRequest::getOffset,
                    SearchRequest::new
            )
    );

    private final String query;
    private final SearchType searchType;
    private final int offset;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(SearchRequest.class, CODEC, (message, player) -> {
                IMusicApiService musicApiService = IMusicApiService.getInstance(ApiProvider.NCM);
                return switch (message.getSearchType()) {
                    case ARTIST -> ResponseResult.of(new SearchArtistsResponse(message.getOffset(), musicApiService.searchArtists(message.getQuery(), message.getOffset())));
                    case ALBUM -> ResponseResult.of(new SearchAlbumsResponse(message.getOffset(), musicApiService.searchAlbums(message.getQuery(), message.getOffset())));
                    case MUSIC -> ResponseResult.of(new SearchMusicResponse(message.getOffset(), musicApiService.searchMusic(message.getQuery(), message.getOffset())));
                    case PLAYLIST -> ResponseResult.of(new SearchPlaylistsResponse(message.getOffset(), musicApiService.searchPlaylists(message.getQuery(), message.getOffset())));
                    default -> ResponseResult.of(new SearchMusicResponse(message.getOffset(), musicApiService.searchMusic(message.getQuery(), message.getOffset())));
                };
            });
        }
    }
}
