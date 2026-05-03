package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.network.Codecs;
import lombok.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Artist {
    public static final StreamCodec<RegistryFriendlyByteBuf,Artist> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            Artist::getId,
            ByteBufCodecs.STRING_UTF8,
            Artist::getName,
            ByteBufCodecs.STRING_UTF8,
            Artist::getAvatarUrl,
            ByteBufCodecs.INT,
            Artist::getAlbumCount,
            ByteBufCodecs.INT,
            Artist::getMusicCount,
            ByteBufCodecs.STRING_UTF8,
            Artist::getDescription,
            Codecs.ofList(() -> MusicDetail.CODEC),// Attention! may cause loop if abuse (MusicDetails <=> Artists)
            Artist::getMusicDetails,
            ByteBufCodecs.INT,
            Artist::getTotalMusicCount,
            Artist::new
    );
    long id;
    String name = "";
    @SerializedName(value = "avatar", alternate = "img1v1Url")
    String avatarUrl = "";
    @SerializedName("albumSize")
    int albumCount;
    @SerializedName("musicSize")
    int musicCount;
    @SerializedName("briefDesc")
    String description = "";
    List<MusicDetail> musicDetails = new ArrayList<>();
    @Setter
    int totalMusicCount;

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }
    public String getAvatarUrl() {return Objects.requireNonNullElse(avatarUrl, "");}
    public String getAvatarThumbnailUrl(int size) {return Objects.requireNonNullElse(avatarUrl, "") + "?param=" + size + "y" + size;}
    public String getDescription() {
        return Objects.requireNonNullElse(description, "");
    }
    public List<MusicDetail> getMusicDetails() {
        return Objects.requireNonNullElse(musicDetails, new ArrayList<>());
    }

    public CompletableFuture<Artist> loadDetail() {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return MusicService.getInstance().loadArtist(id);
        } else return CompletableFuture.completedFuture(this);
    }

    public CompletableFuture<List<MusicDetail>> loadMoreMusic() {
        CompletableFuture<List<MusicDetail>> future = new CompletableFuture<>();
        MusicService.getInstance().loadArtistMusic(id, musicDetails.size()).thenAccept(musicDetails1 -> {
            musicDetails.addAll(musicDetails1);
            future.complete(musicDetails1);
        });
        return future;
    }
}