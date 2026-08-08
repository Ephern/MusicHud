package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Album implements MusicCollection {
    public static final ByteBufCodec<Album> CODEC = ByteBufCodec.composite(
            Codecs.LONG, Album::getId,
            Codecs.STRING_UTF8, Album::getName,
            Codecs.STRING_UTF8, Album::getPicUrl,
            Codecs.STRING_UTF8, Album::getType,
            Codecs.STRING_UTF8, Album::getCompany,
            Codecs.INT, Album::getMusicTrackCount,
            Codecs.ofCollection(ObservableSequencedSet::new, () -> MusicDetail.CODEC), Album::getMusicDetails,
            Codecs.ofCollection(LinkedHashSet::new, () -> Artist.CODEC), Album::getArtists,
            PusherInfo.CODEC, Album::getPusherInfo,
            Album::new
    );
    public static final Album NONE = new Album();
    @Getter
    long id;
    String name = "";
    String picUrl = "";
    String type = "";
    String company = "";
    @SerializedName("size")
    @Getter
    int musicTrackCount;
    @SerializedName("songs")
    @Setter
    ObservableSequencedSet<MusicDetail> musicDetails = new ObservableSequencedSet<>(0);
    LinkedHashSet<Artist> artists = new LinkedHashSet<>();
    // Not contained in the original API response, set separately
    @Getter
    transient PusherInfo pusherInfo = PusherInfo.EMPTY;

    private boolean nullFiltered = false;

    public Album(
            long id,
            String name,
            String picUrl,
            String type,
            String company,
            Integer musicTrackCount,
            ObservableSequencedSet<MusicDetail> musicDetails,
            LinkedHashSet<Artist> artists,
            PusherInfo pusherInfo
    ) {
        this.id = id;
        this.name = name;
        this.picUrl = picUrl;
        this.type = type;
        this.company = company;
        this.musicTrackCount = musicTrackCount;
        this.musicDetails = musicDetails;
        this.artists = artists;
        this.pusherInfo = pusherInfo;
    }

    public String getThumbnailPicUrl(int size) {
        return picUrl + "?param=" + size + "y" + size;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    @Override
    public String getNameI18nKey() {
        return MusicHud.MOD_ID + ".text.album";
    }

    public String getPicUrl() {
        return Objects.requireNonNullElse(picUrl, "");
    }

    public String getType() {
        return Objects.requireNonNullElse(type, "");
    }

    public String getCompany() {
        return Objects.requireNonNullElse(company, "");
    }

    @Override
    public ObservableSequencedSet<MusicDetail> getMusicDetails() {
        if (musicDetails == null) {
            musicDetails = new ObservableSequencedSet<>(0);
            return musicDetails;
        }
        if (!nullFiltered || musicDetails.contains(null)) {
            musicDetails = musicDetails.stream().filter(Objects::nonNull)
                    .collect(ObservableSequencedSet::new, Set::add, ObservableSequencedSet::addAll);
            nullFiltered = true;
        }
        return musicDetails;
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailPicUrl(size);
    }

    public LinkedHashSet<Artist> getArtists() {
        return Objects.requireNonNullElse(artists, new LinkedHashSet<>());
    }

    public Album shallowCopyBriefInfo() {
        Album album = new Album();
        album.id = this.id;
        album.name = this.name;
        album.picUrl = this.picUrl;
        album.musicTrackCount = this.musicTrackCount;
        album.company = this.company;
        album.type = this.type;
        return album;
    }

    @Override
    public Album copyWithPusherInfo(PusherInfo pusherInfo) {
        Album album = shallowCopyBriefInfo();
        album.pusherInfo = pusherInfo;
        return album;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Album album
                && album.id == id
                && album.name.equals(name)
                && album.picUrl.equals(picUrl)
                && album.pusherInfo.equals(pusherInfo);
    }

    @Override
    public boolean equalsLoose(Object obj) {
        return obj instanceof Album album && id == album.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, picUrl, pusherInfo.getPlayerUUID());
    }

    public void updateFrom(Album album, boolean triggerObservable) {
        this.id = album.id;
        this.name = album.name;
        this.picUrl = album.picUrl;
        this.pusherInfo = album.pusherInfo;
        this.musicTrackCount = album.musicTrackCount;
        this.artists = album.artists;
        if (this.musicDetails == null) {
            this.musicDetails = new ObservableSequencedSet<>(0);
        } else if (!this.nullFiltered) {
            this.musicDetails = this.musicDetails.stream().filter(Objects::nonNull)
                    .collect(ObservableSequencedSet::new, Set::add, ObservableSequencedSet::addAll);
        }
        this.nullFiltered = album.nullFiltered;
        this.musicDetails.syncWith(album.getMusicDetails(), triggerObservable);
    }

    /**
     * Merges brief (summary) metadata from a user-collection listing into this instance.
     * Music details and pusher info are left untouched, so a brief object upgraded by
     * {@link #updateFrom(Album, boolean)} keeps its full track list.
     *
     * @return true if any merged field actually changed
     */
    public boolean updateFromBrief(Album brief) {
        boolean changed = false;
        if (!Objects.equals(name, brief.name)) {
            name = brief.name;
            changed = true;
        }
        if (!Objects.equals(picUrl, brief.picUrl)) {
            picUrl = brief.picUrl;
            changed = true;
        }
        if (!Objects.equals(type, brief.type)) {
            type = brief.type;
            changed = true;
        }
        if (!Objects.equals(company, brief.company)) {
            company = brief.company;
            changed = true;
        }
        if (musicTrackCount != brief.musicTrackCount) {
            musicTrackCount = brief.musicTrackCount;
            changed = true;
        }
        artists = brief.artists;
        return changed;
    }
}