package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.audio.info.AudioPlaybackInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.modals.AudioQualityModal;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import org.jetbrains.annotations.Nullable;

/** Waveform icon and quality name button. */
public class AudioQualityWidget extends Button {
    private static final String WAVEFORM_ICON = "/assets/music_hud/textures/gui/icons/audio_waveform.png";

    private final AudioInfoTracker tracker;
    private @Nullable AudioPlaybackInfo info;

    public AudioQualityWidget(Context context) {
        super(context);
        setVisibility(GONE);
        setMinWidth(0);
        setMinHeight(0);
        setSingleLine(true);
        setTextSize(Theme.TEXT_SIZE_NORMAL);
        setTextColor(Theme.SECONDARY_TEXT_COLOR);
        setGravity(Gravity.CENTER);
        setPadding(dp(8), dp(2), dp(10), dp(2));
        InsetBackgroundFactory.builder().cornerRadius(dp(4)).build().applyBackgroundTo(this);
        setOnClickListener(v -> AudioQualityModal.show(getContext(), info));
        tracker = new AudioInfoTracker(this, this::onAudioInfo);
    }

    /** Binds to a track and hides until a known quality is available. */
    public void bind(long musicId) {
        info = null;
        hide();
        tracker.bind(musicId);
    }

    public void clear() {
        tracker.clear();
        info = null;
        hide();
    }

    private void onAudioInfo(AudioPlaybackInfo info) {
        this.info = info;
        Quality tier = info.effectiveQuality().canonical();
        if (tier == Quality.NONE) {
            hide();
            return;
        }
        updateLabel(tier);
        setVisibility(VISIBLE);
    }

    private void hide() {
        setText("");
        setVisibility(GONE);
    }

    private void updateLabel(Quality tier) {
        String name = AudioInfoFormat.shortQuality(tier);
        Image icon = ImageUtils.getImageFromResource(WAVEFORM_ICON);
        if (icon == null) {
            setText(name);
            return;
        }
        SpannableString text = new SpannableString("  " + name);
        text.setSpan(ImageUtils.getIconSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setText(text);
    }
}
