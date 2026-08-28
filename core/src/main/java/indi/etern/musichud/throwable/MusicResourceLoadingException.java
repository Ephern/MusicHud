package indi.etern.musichud.throwable;

import indi.etern.musichud.beans.music.MusicDetail;
import lombok.Getter;

@Getter
public class MusicResourceLoadingException extends RuntimeException {
    private final long id;
    private final boolean usingSubstitute;
    private MusicDetail musicDetail = null;

    public MusicResourceLoadingException(Throwable cause, MusicDetail musicDetail, boolean usingSubstitute) {
        super("Failed to load music resource for music: " + musicDetail.getName() + "(ID: " + musicDetail.getId() + ")", cause);
        this.id = musicDetail.getId();
        this.musicDetail = musicDetail;
        this.usingSubstitute = usingSubstitute;
    }

    public MusicResourceLoadingException(Throwable cause, long id, boolean usingSubstitute) {
        super("Failed to load music resource for music: unknown (ID: " + id + ")", cause);
        this.id = id;
        this.usingSubstitute = usingSubstitute;
    }
}
