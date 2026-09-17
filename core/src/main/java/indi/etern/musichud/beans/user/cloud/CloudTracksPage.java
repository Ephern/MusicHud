package indi.etern.musichud.beans.user.cloud;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

import java.util.List;

/**
 * One page of the user's Netease cloud drive, plus the total number of cloud tracks
 * reported by the API (used for pagination termination).
 */
public record CloudTracksPage(List<CloudTrackInfo> tracks, int total, long usedBytes, long maxBytes) {
    public static final CloudTracksPage EMPTY = new CloudTracksPage(List.of(), 0, -1, -1);
    public static final ByteBufCodec<CloudTracksPage> CODEC = ByteBufCodec.composite(
            Codecs.ofList(() -> CloudTrackInfo.CODEC), CloudTracksPage::tracks,
            Codecs.INT, CloudTracksPage::total,
            Codecs.LONG, CloudTracksPage::usedBytes,
            Codecs.LONG, CloudTracksPage::maxBytes,
            CloudTracksPage::new
    );
}
