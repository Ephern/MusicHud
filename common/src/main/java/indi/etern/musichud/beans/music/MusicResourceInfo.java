package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.Codecs;
import lombok.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Setter
public class MusicResourceInfo {
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicResourceInfo> CODEC = Codecs.composite(
            ByteBufCodecs.VAR_LONG,
            MusicResourceInfo::getId,
            ByteBufCodecs.STRING_UTF8,
            MusicResourceInfo::getUrl,
            ByteBufCodecs.INT,
            MusicResourceInfo::getBitrate,
            ByteBufCodecs.VAR_LONG,
            MusicResourceInfo::getSize,
            Codecs.ofEnum(FormatType.class),
            MusicResourceInfo::getType,
            ByteBufCodecs.STRING_UTF8,
            MusicResourceInfo::getMd5,
            Codecs.ofEnum(Fee.class),
            MusicResourceInfo::getFee,
            ByteBufCodecs.INT,
            MusicResourceInfo::getTime,
            MusicResourceInfo::new
    );
    public static final MusicResourceInfo NONE = new MusicResourceInfo();
    @Getter
    long id;
    String url = "";
    @SerializedName("br")
    @Getter
    int bitrate;
    @Getter
    long size;//byte
    FormatType type = FormatType.AUTO;
    String md5 = "";
    Fee fee = Fee.UNSET;
    @Getter
    int time;

    public static MusicResourceInfo from(String url, MusicDetail musicDetail) {
        MusicResourceInfo musicResourceInfo = new MusicResourceInfo();
        musicResourceInfo.url = url;
        musicResourceInfo.id = musicDetail.getId();
        if (musicResourceInfo.md5 == null)
            musicResourceInfo.md5 = "";
        musicResourceInfo.time = musicDetail.getDurationMillis();
        return musicResourceInfo;
    }

    public String getUrl() {
        return Objects.requireNonNullElse(url, "");
    }

    public FormatType getType() {
        return Objects.requireNonNullElse(type, FormatType.AUTO);
    }

    public String getMd5() {
        return Objects.requireNonNullElse(md5, "");
    }

    public Fee getFee() {
        return Objects.requireNonNullElse(fee, Fee.UNSET);
    }
}
