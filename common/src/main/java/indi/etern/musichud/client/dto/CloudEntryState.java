package indi.etern.musichud.client.dto;

/**
 * Visual state of one cloud-drive list entry. {@link #COMPLETED} still has to show the inline
 * "done" indication, {@link #COMPLETED_PRESENTED} already did and behaves like a normal row.
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
