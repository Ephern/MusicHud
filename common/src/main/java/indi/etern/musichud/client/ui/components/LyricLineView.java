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

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LyricLineView extends LinearLayout {
    private static final float LYRIC_EMPHASIZE_SCALE = 1.03f;
    private static final float RHYTHM_EMPHASIZE_SCALE = 1.07f;
    private static final float RHYTHM_EMPHASIZE_MAX_SCALE = 1.1f;
    private final FrameLayout topBlank;
    private final LinearLayout row;
    private final LyricLine lyricLine;
    TextView mainText;
    TextView subText;
    private AnimatorSet rhythmScaleSet;

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
        addView(row);
        if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
            row.setAlpha(0);
//            params.setMargins(0, -dp(64), 0, 0);
        }
    }

    public void emphasize() {
        Duration delta = NowPlayingInfo.getInstance().getPlayedDuration().minus(lyricLine.getStartTime());
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

                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY, alphaAnim);
                set.setDuration(600);
                set.setStartDelay(200);
                set.start();

                if (lyricLine.getDuration() != null) {
                    postDelayed(this::fade, lyricLine.getDuration().minus(delta).toMillis());
                }
            }
            case RHYTHM -> {
/*                ValueAnimator animator = ValueAnimator.ofInt(-dp(64), 0);
                animator.setDuration(500);
                animator.addUpdateListener(animation -> {
                    int animatedValue = (Integer) animation.getAnimatedValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
                    params.setMargins(0, animatedValue, 0, 0);
                    setLayoutParams(params);
                });
                animator.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                animator.start();*/
                if (lyricLine.getDuration() != null) {
                    postDelayed(this::fade, Math.max(0,lyricLine.getDuration().minus(delta).toMillis() - 1400));
                }

                ObjectAnimator rhythmAlphaAnim = ObjectAnimator.ofFloat(row, View.ALPHA,
                        Theme.FADE_LYRIC_ALPHA, Theme.EMPHASIZE_LYRIC_ALPHA);
                rhythmAlphaAnim.setDuration(400);
                rhythmAlphaAnim.setStartDelay(0);
                rhythmAlphaAnim.start();

                rhythmScaleSet = new AnimatorSet();
                row.setPivotX(0f);
                row.setPivotY(Math.max(row.getHeight()/2, dp(12)));
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, 1f, RHYTHM_EMPHASIZE_SCALE);
                scaleX.setRepeatCount(ObjectAnimator.INFINITE);
                scaleX.setRepeatMode(ObjectAnimator.REVERSE);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, 1f, RHYTHM_EMPHASIZE_SCALE);
                scaleY.setRepeatCount(ObjectAnimator.INFINITE);
                scaleY.setRepeatMode(ObjectAnimator.REVERSE);
                rhythmScaleSet.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                rhythmScaleSet.playTogether(scaleX, scaleY);
                rhythmScaleSet.setDuration(2000);
                rhythmScaleSet.setStartDelay(500);
                rhythmScaleSet.start();
            }
        }
    }

    public void fade() {
        switch (lyricLine.getType()) {
            case META_DATA -> {
            }
            case NORMAL -> {
                ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(mainText, View.ALPHA,
                        Theme.EMPHASIZE_LYRIC_ALPHA, Theme.FADE_LYRIC_ALPHA);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, LYRIC_EMPHASIZE_SCALE, 1f);
                scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, LYRIC_EMPHASIZE_SCALE, 1f);
                scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY, alphaAnim);
                set.setDuration(400);
                set.start();
            }
            case RHYTHM -> {
                rhythmScaleSet.end();
                {
                    AnimatorSet rhythmScaleSet1 = new AnimatorSet();
                    row.setPivotX(0f);
                    row.setPivotY(Math.max(row.getHeight()/2, dp(12)));
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), RHYTHM_EMPHASIZE_MAX_SCALE);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), RHYTHM_EMPHASIZE_MAX_SCALE);
                    rhythmScaleSet1.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                    rhythmScaleSet1.playTogether(scaleX, scaleY);
                    rhythmScaleSet1.setDuration(1000);
                    rhythmScaleSet1.start();
                }
                {
                    AnimatorSet rhythmScaleSet2 = new AnimatorSet();
                    row.setPivotX(0f);
                    row.setPivotY(Math.max(row.getHeight() / 2, dp(12)));
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), 0);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), 0);
                    rhythmScaleSet2.setInterpolator(Easings.EASE_IN_QUINT);
                    rhythmScaleSet2.playTogether(scaleX, scaleY);
                    rhythmScaleSet2.setDuration(400);
                    rhythmScaleSet2.setStartDelay(1000);
                    rhythmScaleSet2.start();
                }

/*
                ValueAnimator animator = ValueAnimator.ofInt(0, -dp(64));
                animator.setDuration(500);
                animator.addUpdateListener(animation -> {
                    int animatedValue = (Integer) animation.getAnimatedValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
                    params.setMargins(0, animatedValue, 0, 0);
                    setLayoutParams(params);
                });
                animator.setStartDelay(1400);
                animator.setInterpolator(Easings.EASE_IN_OUT_QUAD);
                animator.start();
*/
            }
        }
    }

    public int getScrollPosition(StaggeredLyricScrollView staggeredLyricScrollView) {
        return staggeredLyricScrollView.getRelativeTop(this) /*+ (lyricLine.getType() == LyricLine.Type.RHYTHM ? dp(64) : 0)*/;
    }
}
