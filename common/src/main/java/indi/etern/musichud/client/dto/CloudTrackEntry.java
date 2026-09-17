package indi.etern.musichud.client.dto;

import indi.etern.musichud.beans.user.cloud.CloudTrackInfo;
import indi.etern.musichud.client.services.cloud.CloudUploadTask;

public record CloudTrackEntry(long id, CloudTrackInfo cloudTrackInfo, CloudUploadTask task) {

    public CloudEntryState getState() {
        return task == null ? CloudEntryState.UPLOADED : task.getState();
    }
}
