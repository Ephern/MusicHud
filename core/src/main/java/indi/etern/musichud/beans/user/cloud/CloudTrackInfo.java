package indi.etern.musichud.beans.user.cloud;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

/**
 * A cloud-drive track plus its private-cloud MD5/size. The client uses {@code md5} to
 * reconcile a locally pending upload with the track that eventually appears in the listing.
 */
public record CloudTrackInfo(MusicDetail detail, String md5, long fileSize) {
    public static final ByteBufCodec<CloudTrackInfo> CODEC = ByteBufCodec.composite(
            MusicDetail.CODEC, CloudTrackInfo::detail,
            Codecs.ofNullable(Codecs.STRING_UTF8), CloudTrackInfo::md5,
            Codecs.LONG, CloudTrackInfo::fileSize,
            CloudTrackInfo::new
    );
}
