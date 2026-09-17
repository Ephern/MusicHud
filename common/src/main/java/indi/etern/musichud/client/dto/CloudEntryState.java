package indi.etern.musichud.client.dto;

/**
 * Visual state of one cloud-drive list entry.
 * <ul>
 *   <li>{@link #UPLOADED} — a track returned by the server listing</li>
 *   <li>{@link #UPLOADING} — client-direct upload in flight</li>
 *   <li>{@link #QUEUED} — waiting for the sequential worker</li>
 *   <li>{@link #FAILED} — upload failed, retryable</li>
 *   <li>{@link #CANCELLED} — cancelled by the user, retryable</li>
 *   <li>{@link #COMPLETED} — uploaded; the inline "done" indication is still to be shown</li>
 *   <li>{@link #COMPLETED_PRESENTED} — the indication already played; behaves like a normal row</li>
 * </ul>
 */
public enum CloudEntryState {
    UPLOADED,
    UPLOADING,
    MATCHING,
    QUEUED,
    FAILED,
    CANCELLED,
    COMPLETED,
    COMPLETED_PRESENTED;

    public boolean isUploadedLike() {
        return this == UPLOADED || this == COMPLETED_PRESENTED;
    }
}
