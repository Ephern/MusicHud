package indi.etern.musichud.client.services.cloud;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.dto.CloudEntryState;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

/** Mutable client-side state of one queued cloud upload. */
@Getter
@Setter
public final class CloudUploadTask {
    private final long id;
    private final Path file;
    private final String fileName;

    private volatile CloudEntryState state = CloudEntryState.QUEUED;
    private volatile boolean cancelRequested;

    private volatile String md5;
    private volatile String songId;
    private volatile String resourceId;
    private volatile long fileSize;
    private volatile long bytesUploaded;
    private volatile int durationMillis;
    /** Metadata/MD5 prepared; the worker only picks up prepared tasks. */
    private volatile boolean prepared;
    /** This upload used {@code offset} chunks instead of a single request. */
    private volatile boolean chunked;

    private volatile String song = "";
    private volatile String artist = "";
    private volatile String album = "";
    /** {@code data:image/...;base64,...} from embedded artwork, or null for the icon fallback. */
    private volatile String coverDataUri;

    private volatile String errorMessage = "";
    /** Real Netease track id resolved from the complete response, when available. */
    private volatile String resolvedTrackId;
    /** Full detail fetched on demand once {@link #resolvedTrackId} is known, for display. */
    private volatile MusicDetail resolvedDetail;

    public CloudUploadTask(long id, Path file) {
        this.id = id;
        this.file = file;
        this.fileName = file.getFileName().toString();
    }
}
