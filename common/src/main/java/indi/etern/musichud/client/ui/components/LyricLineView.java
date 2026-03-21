package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.Easings;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LyricLineView extends LinearLayout {
    private static final float LYRIC_EMPHASIZE_SCALE = 1.03f;
    private static final float RHYTHM_EMPHASIZE_ANIMATION_SCALE = 0.85f;
    private static final float RHYTHM_EMPHASIZE_MAX_SCALE = 1.12f;
    private final FrameLayout topBlank;
    private final LinearLayout row;
    private final LyricLine lyricLine;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    TextView mainText;
    TextView subText;
    private AnimatorSet emphasizeAnimSet;

    public LyricLineView(Context context, LyricLine lyricLine) {
        super(context);
        this.lyricLine = lyricLine;
        setOrientation(LinearLayout.VERTICAL);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        {
            LinearLayout mainLine = new LinearLayout(getContext());
            mainLine.setOrientation(LinearLayout.VERTICAL);

            mainText = new TextView(getContext());
            mainText.setText(lyricLine.getText());
            mainText.setTextStyle(TextPaint.BOLD);
            mainText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            mainText.setAlpha(Theme.FADE_LYRIC_ALPHA);
            mainLine.addView(mainText);

            if (lyricLine.getType() == LyricLine.Type.META_DATA) {
                mainText.setTextSize(Theme.SUB_LYRIC_SIZE);
            } else {
                mainText.setTextSize(Theme.MAIN_LYRIC_SIZE);

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

            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0.9f);
            row.addView(mainLine, params);
        }

        View blank = new View(getContext());
        LayoutParams blankParams = new LayoutParams(0, MATCH_PARENT, 0.1f);
        row.addView(blank, blankParams);

        topBlank = new FrameLayout(context);
        topBlank.setLayoutParams(new LayoutParams(0, dp(32)));
        LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        setLayoutParams(params);

        addView(topBlank);
        LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        addView(row, rowParams);
        if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
            row.setAlpha(0);
            rowParams.setMargins(dp(2), 0, 0, 0);
            params.setMargins(0, 0, 0, -dp(64));
        }
    }

    public void emphasize() {
        Duration delta = nowPlayingInfo.getPlayedDuration().minus(lyricLine.getStartTime());
        Duration duration = lyricLine.getDuration();
        Duration stayEmphasizeDuration =
                duration == null ?
                        nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime())
                        : duration.minus(delta);
        switch (lyricLine.getType()) {
            case META_DATA -> {
            }
            case NORMAL -> {
                ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(mainText, View.ALPHA,
                        Theme.FADE_LYRIC_ALPHA, Theme.EMPHASIZE_LYRIC_ALPHA);
                row.setPivotX(0f);
                int height = row.getHeight();
                row.setPivotY(Math.max(height, dp(24)));
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, 1f, LYRIC_EMPHASIZE_SCALE);
                scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, 1f, LYRIC_EMPHASIZE_SCALE);
                scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

                emphasizeAnimSet = new AnimatorSet();
                emphasizeAnimSet.playTogether(scaleX, scaleY, alphaAnim);
                emphasizeAnimSet.setDuration(600);
                emphasizeAnimSet.setStartDelay(200);
                emphasizeAnimSet.start();
            }
            case RHYTHM -> {
                if (stayEmphasizeDuration.compareTo(Duration.of(1200, ChronoUnit.MILLIS)) <= 0) {
                    return;
                }

                emphasizeAnimSet = new AnimatorSet();
                ObjectAnimator alpha = ObjectAnimator.ofFloat(row, View.ALPHA,
                        0, 1);
                alpha.setDuration(400);
                alpha.setStartDelay(800);

                row.setPivotX(dp(22));
                row.setPivotY(Math.max(row.getHeight() / 2, dp(12)));
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, 1f, RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                scaleX.setRepeatCount(ObjectAnimator.INFINITE);
                scaleX.setRepeatMode(ObjectAnimator.REVERSE);
                scaleX.setDuration(2000);
                scaleX.setStartDelay(1200);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, 1f, RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                scaleY.setRepeatCount(ObjectAnimator.INFINITE);
                scaleY.setRepeatMode(ObjectAnimator.REVERSE);
                scaleY.setDuration(2000);
                scaleY.setStartDelay(1200);
                emphasizeAnimSet.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                emphasizeAnimSet.playTogether(alpha, scaleX, scaleY);
                emphasizeAnimSet.start();

                stayEmphasizeDuration = stayEmphasizeDuration.minus(Duration.ofMillis(1600));
            }
        }
        if (stayEmphasizeDuration.isPositive()) {
            postDelayed(this::fade, stayEmphasizeDuration.toMillis());
        } else {
            post(this::fade);
        }
    }

    public void fade() {
        if (emphasizeAnimSet != null) {
            emphasizeAnimSet.cancel();
        }
        switch (lyricLine.getType()) {
            case META_DATA -> {
            }
            case NORMAL -> {
                ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(mainText, View.ALPHA,
                        Theme.EMPHASIZE_LYRIC_ALPHA, Theme.FADE_LYRIC_ALPHA);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), 1f);
                scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), 1f);
                scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY, alphaAnim);
                set.setDuration(400);
                set.start();
            }
            case RHYTHM -> {
                {
                    AnimatorSet rhythmScaleSet1 = new AnimatorSet();
                    row.setPivotX(dp(22));
                    row.setPivotY(Math.max(row.getHeight() / 2, dp(12)));
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), RHYTHM_EMPHASIZE_MAX_SCALE);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), RHYTHM_EMPHASIZE_MAX_SCALE);
                    rhythmScaleSet1.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                    rhythmScaleSet1.playTogether(scaleX, scaleY);
                    rhythmScaleSet1.setDuration(1000);
                    rhythmScaleSet1.start();
                }
                {
                    AnimatorSet rhythmScaleSet2 = new AnimatorSet();
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, RHYTHM_EMPHASIZE_MAX_SCALE, 0.7f);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, RHYTHM_EMPHASIZE_MAX_SCALE, 0.7f);
                    ObjectAnimator rhythmAlphaAnim = ObjectAnimator.ofFloat(row, View.ALPHA, 1, 0);
                    rhythmScaleSet2.setInterpolator(Easings.EASE_IN_QUINT);
                    rhythmScaleSet2.playTogether(scaleX, scaleY, rhythmAlphaAnim);
                    rhythmScaleSet2.setDuration(400);
                    rhythmScaleSet2.setStartDelay(1000);
                    rhythmScaleSet2.start();
                }
            }
        }
    }

    public int getScrollPosition(StaggeredLyricScrollView staggeredLyricScrollView) {
        return staggeredLyricScrollView.getRelativeTop(this);
    }

    public float getTargetOffset(LyricLine activeLyricLine) {
        if (activeLyricLine != null && activeLyricLine.getType() == LyricLine.Type.RHYTHM && lyricLine.isAfter(activeLyricLine)) {
            return dp(24);
        } else {
            return 0;
        }
    }
}