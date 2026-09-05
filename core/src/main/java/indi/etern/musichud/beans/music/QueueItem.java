package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

import java.util.UUID;

public record QueueItem(Traceable<MusicDetail> musicDetail, UUID queueUniqueID) {
    public static final ByteBufCodec<QueueItem> CODEC = ByteBufCodec.composite(
            Traceable.codec(MusicDetail.CODEC),
            QueueItem::musicDetail,
            Codecs.UUID,
            QueueItem::queueUniqueID,
            QueueItem::new
    );
}
