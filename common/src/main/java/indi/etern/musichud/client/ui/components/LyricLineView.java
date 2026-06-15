package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.Easing;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class LyricLineView extends LinearLayout {
    private static final float LYRIC_EMPHASIZE_SCALE = 1.02f;
    private static final float RHYTHM_EMPHASIZE_ANIMATION_SCALE = 0.85f;
    private static Logger logger;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private LinearLayout mainLine;
    TextView subText;
    private LinearLayout row;
    private LyricLine lyricLine;
    private View mainText;
    private Animator emphasizeAnim;

    public LyricLineView(Context context, LyricLine lyricLine) {
        super(context);
        try {
            this.lyricLine = lyricLine;
            setOrientation(LinearLayout.VERTICAL);

            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            {
                mainLine = new LinearLayout(getContext());
                mainLine.setOrientation(LinearLayout.VERTICAL);

                if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
                    row.setScaleX(RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                    row.setScaleY(RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                    LinearLayout rhythmLine = new LinearLayout(getContext());
                    rhythmLine.setOrientation(LinearLayout.HORIZONTAL);
                    rhythmLine.setAlpha(0);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, dp(30));
                    rhythmLine.setLayoutParams(params);

                    for (int i = 0; i < 3; i++) {
                        TextView dot = new TextView(getContext());
                        dot.setText("●");
                        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                        dotParams.setMargins(dp(2), 0, dp(2), 0);
                        dot.setLayoutParams(dotParams);
                        dot.setAlpha(Theme.FADE_LYRIC_ALPHA);
                        dot.setId(i);
                        rhythmLine.addView(dot);
                    }
                    this.mainText = rhythmLine;
                    mainLine.addView(rhythmLine);
                } else {
                    LyricHighlightTextView mainText = new LyricHighlightTextView(getContext(), lyricLine);
                    this.mainText = mainText;
                    mainText.setTextStyle(TextPaint.BOLD);
                    mainText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
//                mainText.setAlpha(Theme.FADE_LYRIC_ALPHA);
                    mainLine.addView(mainText);
                    if (lyricLine.getType() == LyricLine.Type.META_DATA) {
                        mainText.setTextSize(Theme.SUB_LYRIC_SIZE);
                    } else {
                        mainText.setTextSize(Theme.MAIN_LYRIC_SIZE);

                        refreshSubLyricLine();
                    }
                }
                LayoutParams params = new LayoutParams(0, WRAP_CONTENT, 0.9f);
                row.addView(mainLine, params);
            }

            View blank = new View(getContext());
            LayoutParams blankParams = new LayoutParams(0, MATCH_PARENT, 0.1f);
            row.addView(blank, blankParams);

            FrameLayout topBlank = new FrameLayout(context);
            topBlank.setLayoutParams(new LayoutParams(0, dp(32)));
            LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            setLayoutParams(params);

            addView(topBlank);
            LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            addView(row, rowParams);
            if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
                rowParams.setMargins(dp(2), 0, 0, 0);
                params.setMargins(0, 0, 0, -dp(64));
            }
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While configure LyricLineView", e);
        }
    }

    public void refreshSubLyricLine() {
        if (clientConfig.getShowTranslatedCnLyrics()) {
            if (subText == null) {
                String subLyric = lyricLine.getTranslatedText();
                if (subLyric != null && !subLyric.isEmpty()) {
                    subText = new TextView(getContext());
                    subText.setText(subLyric);
                    subText.setTextSize(Theme.SUB_LYRIC_SIZE);
                    subText.setTextStyle(TextPaint.BOLD);
                    subText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                    subText.setAlpha(Theme.FADE_LYRIC_ALPHA);
                    LayoutParams subParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    subParams.setMargins(0, dp(6), dp(32), 0);
                    mainLine.addView(subText, subParams);
                }
            }
            if (subText != null/* && subText.getParent() == null*/) {
                subText.setVisibility(VISIBLE);
            }
        } else {
            if (subText != null) {
                subText.setVisibility(GONE);
//                mainLine.removeView(subText);
            }
        }
    }

    public void emphasize() {
        Duration delta = nowPlayingInfo.getPlayedDuration().minus(lyricLine.getStartTime());
        Duration duration = lyricLine.getDuration();
        Duration stayEmphasizeDuration =
                duration == null ?
                        nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime())
                        : duration.minus(delta);
        stayEmphasizeDuration = stayEmphasizeDuration.minus(Duration.of(800, ChronoUnit.MILLIS));
        switch (lyricLine.getType()) {
            case META_DATA -> {
            }
            case NORMAL -> {
                if (mainText instanceof LyricHighlightTextView highlightTextView) {
                    highlightTextView.emphasize();
                    highlightTextView.setOnFade(this::fadeNormalLine);
                }
                row.setPivotX(0f);
                int height = row.getHeight();
                row.setPivotY(Math.max(height, dp(24)));
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, 1f, LYRIC_EMPHASIZE_SCALE);
                scaleX.setInterpolator(Easing.EASE_IN_OUT_QUAD);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, 1f, LYRIC_EMPHASIZE_SCALE);
                scaleY.setInterpolator(Easing.EASE_IN_OUT_QUAD);

                AnimatorSet emphasizeAnimSet = new AnimatorSet();
                emphasizeAnim = emphasizeAnimSet;
                emphasizeAnimSet.playTogether(scaleX, scaleY/*, alphaAnim*/);
                emphasizeAnimSet.setDuration(600);
                emphasizeAnimSet.setStartDelay(200);
                emphasizeAnimSet.start();
            }
            case RHYTHM -> {
                long stayMillis = stayEmphasizeDuration.toMillis();
                stayMillis -= RhythmAnimator.FADE_OUT_PEAK_MS;
                RhythmAnimator rhythmAnim = new RhythmAnimator(row, mainText, stayMillis);
                if (!rhythmAnim.isValid()) {
                    return;
                }

                row.setPivotX((float) mainText.getWidth() / 2);
                row.setPivotY(Math.max(row.getHeight() / 2, dp(12)));
                rhythmAnim.start();
                emphasizeAnim = rhythmAnim;

                long dotDuration = rhythmAnim.getDotFadeDuration(stayMillis);
                for (int i = 0; i < 3; i++) {
                    View viewById = mainText.findViewById(i);
                    if (viewById != null) {
                        ObjectAnimator dotAlpha = ObjectAnimator.ofFloat(viewById, View.ALPHA,
                                viewById.getAlpha(), Theme.EMPHASIZE_LYRIC_ALPHA);
                        dotAlpha.setDuration(dotDuration);
                        dotAlpha.setStartDelay(800 + stayMillis * i / 3);
                        dotAlpha.start();
                    }
                }
                return;
            }
        }
        if (stayEmphasizeDuration.isPositive()) {
            postDelayed(this::fade, stayEmphasizeDuration.toMillis());
        } else {
            post(this::fade);
        }
    }

    private void fadeNormalLine() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), 1f);
        scaleX.setInterpolator(Easing.EASE_IN_OUT_QUAD);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), 1f);
        scaleY.setInterpolator(Easing.EASE_IN_OUT_QUAD);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(400);
        set.start();
    }

    public void fade() {
        if (emphasizeAnim != null) {
            emphasizeAnim.cancel();
        }
    }

    public int getScrollPosition(StaggeredLyricScrollView staggeredLyricScrollView) {
        return staggeredLyricScrollView.getRelativeTop(this);
    }

    public float getTargetOffset(LyricLine activeLyricLine) {
        if (activeLyricLine != null && activeLyricLine.getType() == LyricLine.Type.RHYTHM && lyricLine.isAfter(activeLyricLine)) {
            return dp(30);
        } else {
            return 0;
        }
    }
}