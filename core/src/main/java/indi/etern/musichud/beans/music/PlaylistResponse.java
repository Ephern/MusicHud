package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Objects;

public class PlaylistResponse {
    @Getter
    int code;
    @SerializedName("playlist")
    Playlist playlist;

    public Playlist getPlaylist() {
        return Objects.requireNonNullElse(playlist, Playlist.EMPTY);
    }
}
