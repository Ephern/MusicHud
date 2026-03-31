package indi.etern.musichud.beans.api;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.beans.music.MusicDetail;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

public class MusicDetailsResponse {
    @Getter
    int code;
    @SerializedName("songs")
    List<MusicDetail> musicDetails;

    public List<MusicDetail> getMusicDetails() {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return List.of();
        }
        return musicDetails.stream().filter(Objects::nonNull).toList();
    }
}
