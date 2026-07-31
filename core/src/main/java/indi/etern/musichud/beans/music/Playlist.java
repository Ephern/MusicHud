package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Playlist implements MusicCollection {
    public static final ByteBufCodec<Playlist> CODEC = ByteBufCodec.composite(
            Codecs.LONG, Playlist::getId,
            Codecs.STRING_UTF8, Playlist::getName,
            Codecs.LONG, Playlist::getCoverImgId,
            Codecs.STRING_UTF8, Playlist::getCoverImgId_str,
            Codecs.STRING_UTF8, Playlist::getCoverImgUrl,
            Codecs.INT, Playlist::getMusicTrackCount,
            Codecs.INT, Playlist::getPlayedCount,
            Profile.CODEC, Playlist::getCreator,
            Codecs.ofEnum(Privacy.class), Playlist::getPrivacy,
            Codecs.ofCollection(LinkedHashSet::new, () -> MusicDetail.CODEC), Playlist::getTracks,
            PusherInfo.CODEC, Playlist::getPusherInfo,
            Playlist::new
    );

    public static final Playlist EMPTY = new Playlist();

    @Getter
    long id = -1;
    String name = "";
    @Getter
    long coverImgId = -1;
    @SerializedName("trackCount")
    @Getter
    @Setter
    int musicTrackCount;
    @SerializedName("playCount")
    @Getter
    int playedCount;
    String coverImgId_str = "";
    String coverImgUrl = MusicHud.ICON_BASE64;
    Profile creator = Profile.ANONYMOUS;
    Privacy privacy = Privacy.PUBLIC;
    @Setter
    SequencedSet<MusicDetail> tracks = new LinkedHashSet<>(0);

    // Not contained in the original API response, set separately
    @Getter
    PusherInfo pusherInfo = PusherInfo.EMPTY;

    private boolean nullFiltered = false;

    protected Playlist(
            long id,
            String name,
            long coverImgId,
            String coverImgId_str,
            String coverImgUrl,
            int musicTrackCount,
            int playedCount,
            Profile creator,
            Privacy privacy,
            SequencedSet<MusicDetail> tracks,
            PusherInfo pusherInfo
    ) {
        this.id = id;
        this.name = name;
        this.coverImgId = coverImgId;
        this.coverImgId_str = coverImgId_str;
        this.coverImgUrl = coverImgUrl;
        this.musicTrackCount = musicTrackCount;
        this.playedCount = playedCount;
        this.creator = creator;
        this.privacy = privacy;
        this.tracks = tracks;
        this.pusherInfo = pusherInfo;
    }

    public static Playlist privacyBlocked(long id, Profile creator) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.privacy = Privacy.PRIVATE;
        playlist.creator = creator;
        return playlist;
    }

    public static Playlist empty(long id) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        return playlist;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    @Override
    public String getNameI18nKey() {
        return MusicHud.MOD_ID + ".text.playlist";
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailCoverUrl(size);
    }

    @Override
    public SequencedSet<MusicDetail> getMusicDetails() {
        return getTracks();
    }

    public String getCoverImgId_str() {
        return Objects.requireNonNullElse(coverImgId_str, "");
    }

    public String getCoverImgUrl() {
        return Objects.requireNonNullElse(coverImgUrl, "");
    }

    public String getThumbnailCoverUrl(int size) {
        if (coverImgUrl.startsWith("data:image")) {
            return coverImgUrl;
        } else {
            return coverImgUrl + "?param=" + size + "y" + size;
        }
    }

    public Profile getCreator() {
        return Objects.requireNonNullElse(creator, Profile.ANONYMOUS);
    }

    public Privacy getPrivacy() {
        return Objects.requireNonNullElse(privacy, Privacy.PUBLIC);
    }

    public SequencedSet<MusicDetail> getTracks() {
        if (tracks == null || tracks.isEmpty()) {
            return new LinkedHashSet<>(0);
        }
        if (!nullFiltered) {
            filterTracksNullItem();
            nullFiltered = true;
        }
        return tracks;
    }

    private void filterTracksNullItem() {
        tracks = tracks.stream().filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id
                && playlist.name.equals(name)
                && playlist.coverImgUrl.equals(coverImgUrl)
                && playlist.pusherInfo.equals(pusherInfo);
    }

    @Override
    public boolean equalsLoose(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, coverImgUrl, pusherInfo.getPlayerUUID());
    }

    @Override
    public Playlist copyWithPusherInfo(PusherInfo pusherInfo) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.name = name;
        playlist.coverImgId = coverImgId;
        playlist.coverImgUrl = coverImgUrl;
        playlist.tracks = tracks;
        playlist.creator = creator;
        playlist.privacy = privacy;
        playlist.pusherInfo = pusherInfo;
        return playlist;
    }

    public Playlist copyWithSensitiveErased() {
        if (privacy == Privacy.PRIVATE) {
            Playlist playlist = new Playlist();
            playlist.id = id;
            playlist.name = "Private Playlist";
            playlist.coverImgId = -1;
            playlist.coverImgUrl = MusicHud.ICON_BASE64;
            playlist.creator = creator == Profile.ANONYMOUS ? Profile.PRIVATE_MASK : creator;
            playlist.privacy = privacy;
            playlist.pusherInfo = pusherInfo;
            return playlist;
        } else {
            return copyWithPusherInfo(pusherInfo);
        }
    }
}
