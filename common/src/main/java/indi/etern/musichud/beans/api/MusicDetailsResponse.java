package indi.etern.musichud.beans.api;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PrivilegeInfo;
import indi.etern.musichud.interfaces.PostProcessable;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

public class MusicDetailsResponse implements PostProcessable {
    @Getter
    int code;
    @SerializedName("songs")
    List<MusicDetail> musicDetails;
    List<PrivilegeInfo> privileges = List.of();

    public void postProcess() {
        int index = 0;
        if (musicDetails != null && privileges != null && musicDetails.size() == privileges.size()) {
            for (MusicDetail musicDetail : musicDetails) {
                musicDetail.setPrivilege(privileges.get(index++));
            }
        } else {
            throw new IllegalStateException();
        }
    }

    public List<MusicDetail> getMusicDetails() {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return List.of();
        }
        return musicDetails.stream().filter(Objects::nonNull).toList();
    }

    public List<PrivilegeInfo> getPrivileges() {
        if (privileges == null || privileges.isEmpty()) {
            return List.of();
        }
        return privileges.stream().filter(Objects::nonNull).toList();
    }
}
