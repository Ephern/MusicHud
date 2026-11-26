package indi.etern.musichud.client.ui.components.modals;

import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.client.audio.info.AudioContainer;
import indi.etern.musichud.client.audio.info.AudioOutputInfo;
import indi.etern.musichud.client.audio.info.AudioPlaybackInfo;
import indi.etern.musichud.client.audio.info.AudioStreamInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.AudioInfoFormat;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/** Read-only audio quality details modal. */
public final class AudioQualityModal {
    private static final int LABEL_WIDTH_DP = 104;
    private static final int ROW_TOP_GAP_DP = 4;
    private static final int SECTION_TOP_GAP_DP = 12;
    /** Space reserved for the modal title, divider and button row. */
    private static final int RESERVED_HEIGHT_DP = 180;
    private static final int FALLBACK_HEIGHT_DP = 200;

    private AudioQualityModal() {
    }

    public static void show(Context context, @Nullable AudioPlaybackInfo info) {
        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".text.audioQuality.title"));
        Modal modal = new Modal(context, title, buildContent(context, info),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".button.close"),
                        (button, m) -> m.dismiss()));
        modal.show();
    }

    private static View buildContent(Context context, @Nullable AudioPlaybackInfo info) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        if (info == null) {
            addHint(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.pending"),
                    Theme.SECONDARY_TEXT_COLOR);
        } else {
            addTrackSection(content, info);
            addOutputSection(content, info);
        }
        MaxHeightScrollView scroll = new MaxHeightScrollView(context);
        scroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return scroll;
    }

    private static void addTrackSection(LinearLayout content, AudioPlaybackInfo info) {
        addSection(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.track"));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.tier"),
                AudioInfoFormat.quality(info.effectiveQuality()));
        if (info.qualityMismatch()) {
            addHint(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.mismatch"),
                    Theme.WARN_TEXT_COLOR);
        }
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.size"),
                AudioInfoFormat.size(info.sizeBytes()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.source"),
                AudioInfoFormat.source(info.source()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.reportedStream"),
                AudioInfoFormat.reportedStream(info));

        AudioStreamInfo stream = info.stream();
        if (stream == null) {
            addHint(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.pending"),
                    Theme.SECONDARY_TEXT_COLOR);
            return;
        }
        String format = stream.container().name();
        FormatType reportedType = info.reportedType();
        if (reportedType != null && reportedType != FormatType.AUTO
                && !reportedType.name().equals(format)) {
            format += " / " + reportedType.name();
        }
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.format"), format);
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.channels"),
                AudioInfoFormat.layout(stream.channelLayout()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.sampleRate"),
                AudioInfoFormat.sampleRate(stream.sampleRate()));
        if (stream.bitsPerSample() > 0) {
            addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.bitDepth"),
                    AudioInfoFormat.bitDepth(stream.bitsPerSample()));
        }
        String bitrate = AudioInfoFormat.bitrate(stream.bitrateBps());
        if (stream.container() == AudioContainer.MP3) {
            bitrate += " · " + I18n.get(MusicHud.MOD_ID + ".text.audioQuality."
                    + (stream.variableBitrate() ? "vbr" : "cbr"));
        }
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.bitrate"), bitrate);
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.duration"),
                AudioInfoFormat.duration(stream.durationMs()));
    }

    private static void addOutputSection(LinearLayout content, AudioPlaybackInfo info) {
        addSection(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.output"));
        AudioOutputInfo output = info.output();
        if (output == null) {
            addHint(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.pending"),
                    Theme.SECONDARY_TEXT_COLOR);
            return;
        }
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.channels"),
                AudioInfoFormat.layout(output.channelLayout()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.sampleRate"),
                AudioInfoFormat.sampleRate(output.sampleRate()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.bitDepth"),
                output.float32() ? "32-bit Float32" : AudioInfoFormat.bitDepth(output.bitsPerSample()));
        addRow(content, I18n.get(MusicHud.MOD_ID + ".text.audioQuality.rendering"),
                I18n.get(MusicHud.MOD_ID + ".text.audioQuality." + (output.downmixed()
                        ? "downmix" : output.discrete() ? "discrete" : "direct")));
    }

    private static void addSection(LinearLayout parent, String title) {
        TextView view = new TextView(parent.getContext());
        view.setText(title);
        view.setTextSize(Theme.TEXT_SIZE_NORMAL);
        view.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        view.setTextStyle(TextPaint.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = parent.dp(SECTION_TOP_GAP_DP);
        parent.addView(view, params);
    }

    private static void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(parent.getContext());
        labelView.setText(label);
        labelView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        labelView.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        row.addView(labelView, new LinearLayout.LayoutParams(parent.dp(LABEL_WIDTH_DP), WRAP_CONTENT));

        TextView valueView = new TextView(parent.getContext());
        valueView.setText(value);
        valueView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        valueView.setTextColor(Theme.NORMAL_TEXT_COLOR);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = parent.dp(ROW_TOP_GAP_DP);
        parent.addView(row, params);
    }

    private static void addHint(LinearLayout parent, String text, int color) {
        TextView view = new TextView(parent.getContext());
        view.setText(text);
        view.setTextSize(Theme.TEXT_SIZE_NORMAL);
        view.setTextColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = parent.dp(ROW_TOP_GAP_DP);
        parent.addView(view, params);
    }

    private static final class MaxHeightScrollView extends ScrollView {
        MaxHeightScrollView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = MeasureSpec.getSize(heightMeasureSpec);
            int cap = available > 0
                    ? Math.max(dp(80), available - dp(RESERVED_HEIGHT_DP))
                    : dp(FALLBACK_HEIGHT_DP);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }
}
