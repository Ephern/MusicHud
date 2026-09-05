package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.*;

import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Setter
public class MusicResourceInfo {
    public static final ByteBufCodec<MusicResourceInfo> CODEC = ByteBufCodec.composite(
            Codecs.LONG, MusicResourceInfo::getId,
            Codecs.STRING_UTF8, MusicResourceInfo::getUrl,
            Codecs.INT, MusicResourceInfo::getBitrate,
            Quality.CODEC, MusicResourceInfo::getQuality,
            Codecs.LONG, MusicResourceInfo::getSize,
            Codecs.ofEnum(FormatType.class), MusicResourceInfo::getType,
            Codecs.STRING_UTF8, MusicResourceInfo::getMd5,
            Codecs.ofEnum(Fee.class), MusicResourceInfo::getFee,
            Codecs.INT, MusicResourceInfo::getTime,
            MusicResourceInfo::new
    );
    public static final MusicResourceInfo NONE = new MusicResourceInfo();
    @Getter
    long id;
    String url = "";
    @SerializedName("br")
    @Getter
    int bitrate;
    @SerializedName("level")
    Quality quality = Quality.NONE;
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

    public Quality getQuality() {
        return Objects.requireNonNullElse(quality, Quality.NONE);
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
