package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.user.cloud.CloudTrackInfo;
import indi.etern.musichud.client.dto.CloudEntryState;
import indi.etern.musichud.client.dto.CloudTrackEntry;
import indi.etern.musichud.client.services.cloud.CloudUploadTask;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.ByteUnitFormatter;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class CloudTrackItem extends LinearLayout {
    private static final String ICON_RETRY = "/assets/music_hud/textures/gui/icons/rotate_ccw.png";
    private static final String ICON_CANCEL = "/assets/music_hud/textures/gui/icons/x.png";
    private static final String ICON_REMOVE = "/assets/music_hud/textures/gui/icons/trash_2.png";
    private static final String ICON_DELETE = "/assets/music_hud/textures/gui/icons/trash_2.png";
    private static final String ICON_QUESTION = "/assets/music_hud/textures/gui/icons/circle_question_mark.png";

    private final MusicTrackItem musicItem;
    private final LinearLayout statusStrip;
    private final ProgressBar progressBar;
    private final TextView statusText;
    private final ImageButton actionButton;
    private final ImageButton removeButton;
    private final ImageButton deleteButton;
    private final TextView sizeTextView;

    private CloudTrackEntry entry;
    @Setter
    private Consumer<CloudTrackEntry> onDelete;
    @Setter
    private Consumer<CloudTrackEntry> onRetry;
    @Setter
    private Consumer<CloudTrackEntry> onCancel;
    @Setter
    private Consumer<CloudTrackEntry> onRemove;

    public CloudTrackItem(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        musicItem = MusicListFactory.createItem(this, (v) -> {
            CloudTrackEntry current = this.entry;
            return current == null || !current.getState().isUploadedLike();
        });
        sizeTextView = new TextView(context);
        sizeTextView.setVisibility(GONE);
        sizeTextView.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        sizeTextView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(12), 0);
        musicItem.getInfoRow().addView(sizeTextView, params);
        addView(musicItem, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        InsetBackgroundFactory iconBackground = InsetBackgroundFactory.builder()
                .inset(dp(2))
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(4))
                .build();

        deleteButton = new ImageButton(context);
        applyIcon(deleteButton, ICON_DELETE, I18n.get(MusicHud.MOD_ID + ".button.delete"));
        iconBackground.applyBackgroundTo(deleteButton);
        deleteButton.setOnClickListener(v -> {
            if (entry != null && onDelete != null) {
                onDelete.accept(entry);
            }
        });
        musicItem.getButtonsLayout().addView(deleteButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));

        statusStrip = new LinearLayout(context);
        statusStrip.setOrientation(HORIZONTAL);
        statusStrip.setGravity(Gravity.CENTER_VERTICAL);
        statusStrip.setMinimumHeight(dp(28));
        statusStrip.setVisibility(GONE);
        LayoutParams stripParams = new LayoutParams(dp(360), WRAP_CONTENT);
        stripParams.setMargins(dp(16), 0, dp(10), 0);
        musicItem.addView(statusStrip, 2, stripParams);

        progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        statusStrip.addView(progressBar, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

        statusText = new TextView(context);
        statusText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        statusText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        statusText.setSingleLine(true);
        statusText.setMaxWidth(dp(280));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        textParams.setMargins(dp(8), 0, dp(8), 0);
        statusStrip.addView(statusText, textParams);

        actionButton = new ImageButton(context);
        iconBackground.applyBackgroundTo(actionButton);
        statusStrip.addView(actionButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));
        actionButton.setOnClickListener(v -> {
            CloudTrackEntry e = this.entry;
            if (e == null) {
                return;
            }
            CloudEntryState state = e.getState();
            if (state == CloudEntryState.UPLOADING && onCancel != null) {
                onCancel.accept(e);
            } else if (onRetry != null) {
                onRetry.accept(e);
            }
        });

        removeButton = new ImageButton(context);
        applyIcon(removeButton, ICON_REMOVE, I18n.get(MusicHud.MOD_ID + ".button.remove"));
        iconBackground.applyBackgroundTo(removeButton);
        removeButton.setOnClickListener(v -> {
            if (entry != null && onRemove != null) {
                onRemove.accept(entry);
            }
        });
        statusStrip.addView(removeButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));
    }

    public void clearData() {
        musicItem.clearData();
        entry = null;
        statusStrip.setVisibility(GONE);
    }

    public void bindData(CloudTrackEntry entry) {
        this.entry = entry;
        CloudEntryState state = entry.getState();
        CloudTrackInfo cloudTrackInfo = entry.cloudTrackInfo();
        MusicDetail detail = cloudTrackInfo.detail();
        CloudUploadTask task = entry.task();
        musicItem.bindData(detail);
        long fileSize = Math.max(cloudTrackInfo.fileSize(), task == null ? 0 : task.getFileSize());
        if (fileSize > 0) {
            sizeTextView.setText(ByteUnitFormatter.formatSize(fileSize));
            sizeTextView.setVisibility(VISIBLE);
        } else {
            sizeTextView.setVisibility(GONE);
        }

        switch (state) {
            case UPLOADED, COMPLETED_PRESENTED -> {
                musicItem.getButtonsLayout().setVisibility(VISIBLE);
                statusStrip.setVisibility(GONE);
            }
            case COMPLETED -> {
                musicItem.getButtonsLayout().setVisibility(GONE);
                statusStrip.setVisibility(VISIBLE);
                long total = task == null ? 0 : Math.max(task.getFileSize(), 1);
                progressBar.setMax((int) Math.min(Integer.MAX_VALUE, total));
                progressBar.setProgress((int) Math.min(Integer.MAX_VALUE, total), false);
                progressBar.setIndeterminate(false);
                statusText.setText(I18n.get(MusicHud.MOD_ID + ".text.cloudUploadDone"));
                statusText.setTooltipText(null);
                actionButton.setVisibility(GONE);
                removeButton.setVisibility(GONE);
            }
            case UPLOADING -> {
                musicItem.getButtonsLayout().setVisibility(GONE);
                statusStrip.setVisibility(VISIBLE);
                long total = task == null ? 0 : task.getFileSize();
                long done = task == null ? 0 : task.getBytesUploaded();
                int max = Math.clamp(total, 1, Integer.MAX_VALUE);
                progressBar.setMax(max);
                progressBar.setProgress((int) Math.min(max, done), true);
                progressBar.setIndeterminate(false);
                statusText.setText(total > 0 ? (done * 100 / total) + "%" : "");
                statusText.setTooltipText(null);
                applyIcon(actionButton, ICON_CANCEL, I18n.get(MusicHud.MOD_ID + ".button.cancel"));
                actionButton.setVisibility(VISIBLE);
                removeButton.setVisibility(GONE);
            }
            case MATCHING -> {
                musicItem.getButtonsLayout().setVisibility(GONE);
                statusStrip.setVisibility(VISIBLE);
                long total = task == null ? 0 : Math.max(task.getFileSize(), 1);
                progressBar.setMax((int) Math.min(Integer.MAX_VALUE, total));
                progressBar.setProgress((int) Math.min(Integer.MAX_VALUE, total), false);
                progressBar.setIndeterminate(true);
                String hint = task == null ? "" : task.getErrorMessage();
                boolean uncertain = hint != null && !hint.isBlank();
                statusText.setText(uncertain ? hint : I18n.get(MusicHud.MOD_ID + ".text.cloudUploadMatching"));
                statusText.setTooltipText(uncertain ? hint : null);
                actionButton.setVisibility(GONE);
                removeButton.setVisibility(VISIBLE);
            }
            case QUEUED -> {
                musicItem.getButtonsLayout().setVisibility(GONE);
                statusStrip.setVisibility(VISIBLE);
                progressBar.setMax(1);
                progressBar.setProgress(0, false);
                progressBar.setIndeterminate(false);
                statusText.setText(I18n.get(MusicHud.MOD_ID + ".text.cloudUploadQueued"));
                statusText.setTooltipText(null);
                actionButton.setVisibility(GONE);
                removeButton.setVisibility(VISIBLE);
            }
            case FAILED, CANCELLED -> {
                musicItem.getButtonsLayout().setVisibility(GONE);
                statusStrip.setVisibility(VISIBLE);
                progressBar.setMax(1);
                progressBar.setProgress(0, false);
                progressBar.setIndeterminate(false);
                String message = task == null ? "" : task.getErrorMessage();
                SpannableString spannableString = new SpannableString(
                        (
                                state == CloudEntryState.CANCELLED
                                        ? I18n.get(MusicHud.MOD_ID + ".text.cloudUploadCancelled")
                                        : I18n.get(MusicHud.MOD_ID + ".text.cloudUploadFailed") + "  "
                        )
                );
                statusText.setText(spannableString);
                if (state == CloudEntryState.FAILED) {
                    Image icon = ImageUtils.getImageFromResource(ICON_QUESTION);
                    if (icon != null) {
                        int length = spannableString.length();
                        spannableString.setSpan(ImageUtils.getIconSpan(icon), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    statusText.setTooltipText(message);
                }
                applyIcon(actionButton, ICON_RETRY, I18n.get(MusicHud.MOD_ID + ".button.retry"));
                actionButton.setVisibility(VISIBLE);
                removeButton.setVisibility(VISIBLE);
            }
        }
    }

    public long boundId() {
        return entry == null ? -1 : entry.id();
    }

    private void applyIcon(ImageButton button, String iconPath, String tooltip) {
        button.setTooltipText(tooltip);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            button.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), icon, dp(16), dp(16)));
        }
    }
}
