package indi.etern.musichud.client.ui.layouts;

import icyllis.modernui.view.ViewGroup;
import indi.etern.musichud.client.dto.CloudTrackEntry;
import indi.etern.musichud.client.ui.components.CloudTrackItem;

import java.util.function.Consumer;

public class CloudTrackItemAdapter implements VirtualizedListLayout.Adapter<CloudTrackEntry, CloudTrackItem> {
    private final Consumer<CloudTrackEntry> onDelete;
    private final Consumer<CloudTrackEntry> onRetry;
    private final Consumer<CloudTrackEntry> onCancel;
    private final Consumer<CloudTrackEntry> onRemove;

    public CloudTrackItemAdapter(Consumer<CloudTrackEntry> onDelete,
                                 Consumer<CloudTrackEntry> onRetry,
                                 Consumer<CloudTrackEntry> onCancel,
                                 Consumer<CloudTrackEntry> onRemove) {
        this.onDelete = onDelete;
        this.onRetry = onRetry;
        this.onCancel = onCancel;
        this.onRemove = onRemove;
    }

    @Override
    public long idOf(CloudTrackEntry item) {
        return item.id();
    }

    @Override
    public CloudTrackItem createItem(ViewGroup parent) {
        CloudTrackItem item = new CloudTrackItem(parent.getContext());
        item.setOnDelete(onDelete);
        item.setOnRetry(onRetry);
        item.setOnCancel(onCancel);
        item.setOnRemove(onRemove);
        return item;
    }

    @Override
    public void clearItem(CloudTrackItem view) {
        view.clearData();
    }

    @Override
    public void bindItem(CloudTrackItem view, CloudTrackEntry item) {
        view.bindData(item);
    }

    @Override
    public long boundIdOf(CloudTrackItem view) {
        return view.boundId();
    }
}
