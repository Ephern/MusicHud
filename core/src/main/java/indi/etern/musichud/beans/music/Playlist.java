package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
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
            Codecs.LONG, Playlist::getPlayedCount,
            Codecs.ofEnum(PlaylistSpecialType.class), Playlist::getSpecialType,
            Profile.CODEC, Playlist::getCreator,
            Codecs.ofEnum(Privacy.class), Playlist::getPrivacy,
            Codecs.ofCollection(ObservableSequencedSet::new, () -> MusicDetail.CODEC), Playlist::getTracks,
            Codecs.ofNullable(Codecs.ofCollection(ObservableSequencedSet::new, () -> MusicDetail.CODEC)), Playlist::getIntelligentList,
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
    long playedCount;
    String coverImgId_str = "";
    String coverImgUrl = MusicHud.ICON_BASE64;
    PlaylistSpecialType specialType;
    Profile creator = Profile.ANONYMOUS;
    Privacy privacy = Privacy.PUBLIC;
    @Setter
    ObservableSequencedSet<MusicDetail> tracks = new ObservableSequencedSet<>(0);
    /**
     * Intelligent play mode recommendations of one player; null until first fetch. Synced via CODEC, not synced by updateFrom.
     */
    @Getter
    @Setter
    ObservableSequencedSet<MusicDetail> intelligentList;

    private boolean nullFiltered = false;

    protected Playlist(
            long id,
            String name,
            long coverImgId,
            String coverImgId_str,
            String coverImgUrl,
            int musicTrackCount,
            long playedCount,
            PlaylistSpecialType specialType,
            Profile creator,
            Privacy privacy,
            ObservableSequencedSet<MusicDetail> tracks,
            ObservableSequencedSet<MusicDetail> intelligentList
    ) {
        this.id = id;
        this.name = name;
        this.coverImgId = coverImgId;
        this.coverImgId_str = coverImgId_str;
        this.coverImgUrl = coverImgUrl;
        this.musicTrackCount = musicTrackCount;
        this.playedCount = playedCount;
        this.specialType = specialType;
        this.creator = creator;
        this.privacy = privacy;
        this.tracks = tracks;
        this.intelligentList = intelligentList;
    }

    public static Playlist privacyBlocked(long id, Profile creator) {
        Playlist playlist = new Playlist();
        playlist.id = id;
        playlist.privacy = Privacy.PRIVATE;
        playlist.creator = creator;
        playlist.specialType = PlaylistSpecialType.NORMAL;
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
        return switch (specialType) {
            case LIKE_LIST -> MusicHud.MOD_ID + ".text.likeList";
            case USER_SPECIFIC -> MusicHud.MOD_ID + ".text.recommendlist";
            default -> MusicHud.MOD_ID + ".text.playlist";
        };
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        if (coverImgUrl.startsWith("data:image")) {
            return coverImgUrl;
        } else {
            return size >= 0 ? coverImgUrl + "?param=" + size + "y" + size : coverImgUrl;
        }
    }

    @Override
    public ObservableSequencedSet<MusicDetail> getMusicDetails() {
        return getTracks();
    }

    public String getCoverImgId_str() {
        return Objects.requireNonNullElse(coverImgId_str, "");
    }

    public String getCoverImgUrl() {
        return Objects.requireNonNullElse(coverImgUrl, "");
    }

    public Profile getCreator() {
        return Objects.requireNonNullElse(creator, Profile.ANONYMOUS);
    }

    public Privacy getPrivacy() {
        return Objects.requireNonNullElse(privacy, Privacy.PUBLIC);
    }

    public ObservableSequencedSet<MusicDetail> getTracks() {
        if (tracks == null) {
            tracks = new ObservableSequencedSet<>(0);
            return tracks;
        }
        if (!nullFiltered || tracks.contains(null)) {
            filterTracksNullItem();
            nullFiltered = true;
        }
        return tracks;
    }

    private void filterTracksNullItem() {
        tracks = tracks.stream().filter(Objects::nonNull)
                .collect(ObservableSequencedSet::new, Set::add, ObservableSequencedSet::addAll);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id
                && playlist.name.equals(name)
                && playlist.coverImgUrl.equals(coverImgUrl);
    }

    @Override
    public boolean equalsLoose(Object obj) {
        return obj instanceof Playlist playlist
                && playlist.id == id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, coverImgUrl);
    }

    public Playlist sensitiveErased() {
        if (privacy == Privacy.PRIVATE) {
            Playlist playlist = new Playlist();
            playlist.id = id;
            playlist.name = "Private Playlist";
            playlist.coverImgId = -1;
            playlist.coverImgUrl = MusicHud.ICON_BASE64;
            playlist.specialType = PlaylistSpecialType.NORMAL;
            playlist.playedCount = -1;
            playlist.musicTrackCount = -1;
            playlist.creator = creator == Profile.ANONYMOUS ? Profile.PRIVATE_MASK : creator;
            playlist.privacy = privacy;
            return playlist;
        } else {
            return this;
        }
    }

    public void updateFrom(Playlist playlist, boolean triggerObservable) {
        this.id = playlist.id;
        this.name = playlist.name;
        getTracks().syncWith(playlist.getTracks(), triggerObservable);
        this.specialType = playlist.specialType;
        if (specialType != PlaylistSpecialType.USER_SPECIFIC) {
            this.coverImgId = playlist.coverImgId;
            this.coverImgUrl = playlist.coverImgUrl;
        }
        this.creator = playlist.creator;
        this.privacy = playlist.privacy;
        this.playedCount = playlist.playedCount;
        this.musicTrackCount = playlist.musicTrackCount;
    }

    /**
     * Merges brief (summary) metadata from a user-collection listing into this instance.
     * Tracks and pusher info are left untouched, so a brief object upgraded by
     * {@link #updateFrom(Playlist, boolean)} keeps its full track list.
     *
     * @return true if any merged field actually changed
     */
    public boolean updateFromBrief(Playlist brief) {
        boolean changed = false;
        if (!Objects.equals(name, brief.name)) {
            name = brief.name;
            changed = true;
        }
        if (musicTrackCount != brief.musicTrackCount) {
            musicTrackCount = brief.musicTrackCount;
            changed = true;
        }
        if (playedCount != brief.playedCount) {
            playedCount = brief.playedCount;
            changed = true;
        }
        if (specialType != brief.specialType) {
            specialType = brief.specialType;
            changed = true;
        }
        if (specialType != PlaylistSpecialType.USER_SPECIFIC) {
            if (coverImgId != brief.coverImgId) {
                coverImgId = brief.coverImgId;
                changed = true;
            }
            if (!Objects.equals(coverImgId_str, brief.coverImgId_str)) {
                coverImgId_str = brief.coverImgId_str;
                changed = true;
            }
            if (!Objects.equals(coverImgUrl, brief.coverImgUrl)) {
                coverImgUrl = brief.coverImgUrl;
                changed = true;
            }
        }
        if (!Objects.equals(creator, brief.creator)) {
            creator = brief.creator;
            changed = true;
        }
        if (privacy != brief.privacy) {
            privacy = brief.privacy;
            changed = true;
        }
        return changed;
    }

    public PlaylistSpecialType getSpecialType() {
        return Objects.requireNonNullElse(specialType, PlaylistSpecialType.NORMAL);
    }
}
