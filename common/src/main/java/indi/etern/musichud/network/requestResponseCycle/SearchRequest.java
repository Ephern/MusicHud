package indi.etern.musichud.network.requestResponseCycle;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.C2SPayload;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import indi.etern.musichud.server.api.MusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SearchRequest(String query, SearchType searchType, int offset) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SearchRequest::query,
            Codecs.ofEnum(SearchType.class),
            SearchRequest::searchType,
            ByteBufCodecs.INT,
            SearchRequest::offset,
            SearchRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(SearchRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        S2CPayload s2CPayload;
                        MusicApiService musicApiService = MusicApiService.getInstance();
                        switch (message.searchType) {
                            case ARTIST -> {
                                s2CPayload = new SearchArtistsResponse(message.offset, musicApiService.searchArtists(message.query, message.offset));
                            }
                            case ALBUM -> {
                                s2CPayload = new SearchAlbumsResponse(message.offset, musicApiService.searchAlbums(message.query, message.offset));
                            }
                            case MUSIC -> {
                                s2CPayload = new SearchMusicResponse(message.offset, musicApiService.searchMusic(message.query, message.offset));
                            }
                            case PLAYLIST -> {
                                s2CPayload = new SearchPlaylistsResponse(message.offset, musicApiService.searchPlaylists(message.query, message.offset));
                            }
                            default -> {
                                s2CPayload = new SearchMusicResponse(message.offset, musicApiService.searchMusic(message.query, message.offset));
                            }
                        }
                        NetworkManager.sendToPlayer(player, s2CPayload);
                    })
            );
        }
    }
}
