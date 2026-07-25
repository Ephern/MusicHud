package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Artist {
    public static final ByteBufCodec<Artist> CODEC = ByteBufCodec.composite(
            Codecs.VAR_LONG,//TODO replace with Codecs.LONG in 1.3.0
            Artist::getId,
            Codecs.STRING_UTF8,
            Artist::getName,
            Codecs.STRING_UTF8,
            Artist::getAvatarUrl,
            Codecs.INT,
            Artist::getAlbumCount,
            Codecs.INT,
            Artist::getMusicCount,
            Codecs.STRING_UTF8,
            Artist::getDescription,
            Codecs.ofList(() -> MusicDetail.CODEC),// Attention! may cause loop if abuse (MusicDetails <=> Artists)
            Artist::getMusicDetails,
            Codecs.INT,
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
        if (musicDetails == null) {
            musicDetails = new ArrayList<>();
        }
        return musicDetails;
    }
}