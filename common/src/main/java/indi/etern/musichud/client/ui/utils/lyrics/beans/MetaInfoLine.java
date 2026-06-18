package indi.etern.musichud.client.ui.utils.lyrics.beans;

import com.google.gson.annotations.SerializedName;

import java.time.Duration;
import java.util.List;

public class MetaInfoLine {
    @SerializedName("t")
    protected int timestampMillis;
    @SerializedName("c")
    protected List<MetaInfoStringWrapper> metaInfoStrings;
    @SerializedName("li")
    protected String avatarUrl;
    // example orpheus://nm/artist/home?id=90331&type=artist
    // "90331" is artist's id
    @SerializedName("or")
    protected String internalLink;

    public Duration getTimestampDuration() {
        return Duration.ofMillis(timestampMillis);
    }

    public String getText() {
        return metaInfoStrings == null ? "" : metaInfoStrings.stream().map(metaInfoStringWrapper -> metaInfoStringWrapper.string).reduce((s1, s2) -> s1 + " " + s2).orElse(null);
    }

    public static class MetaInfoStringWrapper {
        @SerializedName("tx")
        protected String string;
    }
}
