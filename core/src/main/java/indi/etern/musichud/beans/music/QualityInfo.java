package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class QualityInfo {
    @SerializedName("br")
    int bitRate;
    @SerializedName("size")
    long sizeBytes;
    @SerializedName("vd")
    int volumeDelta;
    @SerializedName("sr")
    int sampleRate;

    public static final QualityInfo NONE = new QualityInfo(
            0, 0, 0, 0
    );
}