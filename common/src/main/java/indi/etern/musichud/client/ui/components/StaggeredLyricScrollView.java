package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class StaggeredLyricScrollView extends ClampingScrollView {
    private static final int AUTO_RECENTER_DELAY_MILLIS = 3000;
    private final Map<LyricLine, LyricLineView> lyricLines = new HashMap<>();
    private final LinearLayout container;
    private final float LYRIC_EMPHASIZE_SCALE = 1.03f;
    private ScrollController scrollController = null;
    private boolean isAutoScrolling = false;
    private boolean isUserManuallyScrolling = false;
    private long lastUserScrollTime = 0;
    private boolean isRecenterScrolling = false;
    private int currentScrollPosition = 0;
    private LyricLineView lastHighlightedLine;
    private LyricLine lastHighlightedLyricLine;
    private final Runnable autoRecenterRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUserManuallyScrolling && System.currentTimeMillis() - lastUserScrollTime >= AUTO_RECENTER_DELAY_MILLIS) {
                recenter();
            } else if (isUserManuallyScrolling) {
                postDelayed(this, 100);
            }
        }
    };

    private void recenter() {
        isUserManuallyScrolling = false;
        isRecenterScrolling = true;
        LyricLine targetLine = null;
        LyricLine current = NowPlayingInfo.getInstance().getCurrentLyricLine();
        if (current != null) {
            targetLine = current;
        } else if (lastHighlightedLyricLine != null) {
            targetLine = lastHighlightedLyricLine;
        }

        if (targetLine != null) {
            LyricLineView target = lyricLines.get(targetLine);
            if (target != null) {
                scrollToLyric(target);
            }
        }
    }

    private final Consumer<LyricLine> lyricLineUpdateListener = this::highlightLine;
    private boolean continueUpdate = false;

    public StaggeredLyricScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        setAlpha(0);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        NowPlayingInfo.getInstance().getLyricLineUpdateListener().add(lyricLineUpdateListener);

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!isAutoScrolling && !isRecenterScrolling) {
                recenter();
            }
        });
        setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY != oldScrollY) {
                currentScrollPosition = scrollY;
                checkManualScrolling();
            }
        });

        scrollController = new ScrollController((controller, amount) -> {
            scrollTo(0, (int) amount);
        });
    }

    private void checkManualScrolling() {
        if (!isAutoScrolling && !isRecenterScrolling) {
            isUserManuallyScrolling = true;
            lastUserScrollTime = System.currentTimeMillis();
            isRecenterScrolling = false;
            removeCallbacks(autoRecenterRunnable);
            postDelayed(autoRecenterRunnable, AUTO_RECENTER_DELAY_MILLIS);
            if (scrollController.isScrolling()) {
                scrollController.abortAnimation();
            }
        }
    }

    public void setLyrics(Collection<LyricLine> lyrics) {
        if (container.getChildCount() > 0) {
            ObjectAnimator slideOut = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0, -getWidth());
            slideOut.setInterpolator(Easings.EASE_IN_OUT_QUINT);
            slideOut.setDuration(300);
            slideOut.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    container.removeAllViews();
                    buildLyricRows(lyrics);
                    container.setTranslationX(getWidth());
                    ObjectAnimator slideIn = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0);
                    slideIn.setInterpolator(Easings.EASE_IN_OUT_QUINT);
                    slideIn.setDuration(300);
                    slideIn.start();
                }
            });
            slideOut.start();
        } else {
            buildLyricRows(lyrics);
        }
    }

    void highlightLine(LyricLine lyricLine) {
        MuiModApi.postToUiThread(() -> {
            if (lyricLine == null) {
                if (lastHighlightedLine != null) {
                    fadeLyricLine(lastHighlightedLine);
                    lastHighlightedLine = null;
                }
                lastHighlightedLyricLine = null;
                return;
            }
            LyricLineView target = lyricLines.get(lyricLine);
            if (target == null) return;

            if (lastHighlightedLine != null && lastHighlightedLine != target) {
                fadeLyricLine(lastHighlightedLine);
            }
            emphasizeLyricLine(target);
            lastHighlightedLine = target;
            lastHighlightedLyricLine = lyricLine;

            if (!isUserManuallyScrolling) {
                scrollToLyric(target);
            }
        });
    }

    private void buildLyricRows(Collection<LyricLine> lyrics) {
        lyricLines.clear();

        if (lyrics == null) return;

        Context context = getContext();
        container.addView(new RatedHeightView(context, 0.3f, () -> this));
        for (LyricLine line : lyrics) {
            LyricLineView row = new LyricLineView(context, line);
            container.addView(row);
            lyricLines.put(line, row);
        }
        container.addView(new RatedHeightView(context, 0.7f, () -> this));

        post(() -> {
            requestLayout();
            initializeScrollToCurrentLyric();
        });
    }

    private void initializeScrollToCurrentLyric() {
        LyricLine current = NowPlayingInfo.getInstance().getCurrentLyricLine();
        if (current != null) {
            highlightLine(current);
            LyricLineView lyricLineView = lyricLines.get(current);
            if (lyricLineView != null) {
                jumpToLyric(lyricLineView);
            }
        } else if (!lyricLines.isEmpty()) {
            jumpToTop();
        }
        ObjectAnimator alpha = ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1f);
        alpha.setDuration(300);
        alpha.setInterpolator(Easings.EASE_OUT_QUAD);
        alpha.start();
    }

    private void jumpToTop() {
        if (scrollController == null) return;
        isAutoScrolling = true;
        scrollController.abortAnimation();
        int maxScroll = Math.max(0, container.getHeight() - getHeight());
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(currentScrollPosition, 0);
        scrollController.abortAnimation();
        scrollController.setStartValue(currentScrollPosition);
        scrollController.scrollTo(0, 0);
        scrollController.abortAnimation();
        postDelayed(() -> isAutoScrolling = false, 50);
    }

    private void jumpToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
        int targetTop = getRelativeTop(target, container);
        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> jumpToLyric(target));
            return;
        }
        int targetScrollY = targetTop - scrollViewHeight / 3;
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));

        isAutoScrolling = true;
        scrollController.abortAnimation();
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(currentScrollPosition, 0);
        scrollController.abortAnimation();
        scrollController.setStartValue(currentScrollPosition);
        scrollController.scrollTo(targetScrollY, 0);
        scrollController.abortAnimation();
        postDelayed(() -> isAutoScrolling = false, 50);
    }

    private void scrollToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
        int targetTop = getRelativeTop(target, container);
        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> scrollToLyric(target));
            return;
        }
        int targetScrollY = targetTop - scrollViewHeight / 3;
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));
        int currentY = currentScrollPosition;
        if (Math.abs(targetScrollY - currentY) < 5) {
            if (isRecenterScrolling) isRecenterScrolling = false;
            return;
        }
        scrollController.scrollTo(currentY, 0);
        scrollController.abortAnimation();
        scrollController.setStartValue(currentY);
        scrollController.setMaxScroll(maxScroll);
        int duration = isRecenterScrolling ? Math.min(300 + Math.abs(targetScrollY - currentY) / 5, 600) : 300;
        isAutoScrolling = true;
        scrollController.scrollTo(targetScrollY, duration);
        if (isRecenterScrolling) {
            postDelayed(() -> {
                isRecenterScrolling = false;
                isAutoScrolling = false;
                isUserManuallyScrolling = false;
            }, duration + 50);
        } else {
            postDelayed(() -> isAutoScrolling = false, duration + 50);
        }
    }

    private int getRelativeTop(View child, View root) {
        int top = 0;
        View current = child;
        while (current != root && current != null) {
            top += current.getTop();
            current = (View) current.getParent();
        }
        return top;
    }

    private void emphasizeLyricLine(LyricLineView lyricLineView) {
        if (lyricLineView.type == LyricLine.Type.META_DATA) {
            return;
        }
        IntProperty<TextView> TEXT_COLOR = new IntProperty<>("textColor") {
            @Override
            public void setValue(TextView view, int color) {
                view.setTextColor(color);
            }

            @Override
            public Integer get(TextView view) {
                return view.getCurrentTextColor();
            }
        };
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(lyricLineView.mainText, TEXT_COLOR, Theme.FADE_TEXT_COLOR, Theme.EMPHASIZE_TEXT_COLOR);
        colorAnim.setEvaluator(ColorEvaluator.getInstance());

        lyricLineView.setPivotX(0f);
        int height = lyricLineView.getHeight();
        lyricLineView.setPivotY(Math.min(height, dp(24)));
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(lyricLineView, View.SCALE_X, 1f, LYRIC_EMPHASIZE_SCALE);
        scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(lyricLineView, View.SCALE_Y, 1f, LYRIC_EMPHASIZE_SCALE);
        scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, colorAnim);
        set.setDuration(250);
        set.start();
    }

    private void fadeLyricLine(LyricLineView lyricLineView) {
        if (lyricLineView.type == LyricLine.Type.META_DATA) {
            return;
        }

        IntProperty<TextView> TEXT_COLOR = new IntProperty<>("textColor") {
            @Override
            public void setValue(TextView view, int color) {
                view.setTextColor(color);
            }

            @Override
            public Integer get(TextView view) {
                return view.getCurrentTextColor();
            }
        };
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(lyricLineView.mainText, TEXT_COLOR, Theme.EMPHASIZE_TEXT_COLOR, Theme.FADE_TEXT_COLOR);
        colorAnim.setEvaluator(ColorEvaluator.getInstance());

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(lyricLineView, View.SCALE_X, LYRIC_EMPHASIZE_SCALE, 1f);
        scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(lyricLineView, View.SCALE_Y, LYRIC_EMPHASIZE_SCALE, 1f);
        scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, colorAnim);
        set.setDuration(250);
        set.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startUpdateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopUpdateLoop();
        removeCallbacks(autoRecenterRunnable);
        NowPlayingInfo.getInstance().getLyricLineUpdateListener().remove(lyricLineUpdateListener);
        if (scrollController != null) {
            scrollController.abortAnimation();
        }
    }

    private void startUpdateLoop() {
        continueUpdate = true;
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (continueUpdate) {
                scrollController.update(MuiModApi.getElapsedTime());
                invalidate();
                startUpdateLoop();
            }
        });
    }

    private void stopUpdateLoop() {
        continueUpdate = false;
    }

    public static class LyricLineView extends LinearLayout {
        TextView mainText;
        TextView subText;
        LyricLine.Type type;

        public LyricLineView(Context context, LyricLine lyricLine) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            type = lyricLine.getType();

            {
                LinearLayout mainLine = new LinearLayout(getContext());
                mainLine.setOrientation(LinearLayout.VERTICAL);

                mainText = new TextView(getContext());
                mainText.setText(lyricLine.getText());
                mainText.setTextStyle(TextPaint.BOLD);
                mainText.setTextColor(Theme.FADE_TEXT_COLOR); // 初始为淡化色
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
                        subText.setTextColor(Theme.FADE_TEXT_COLOR);
                        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                        subParams.setMargins(0, dp(6), dp(32), 0);
                        mainLine.addView(subText, subParams);
                    }
                }

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0.9f);
                addView(mainLine, params);
            }

            View blank = new View(getContext());
            LinearLayout.LayoutParams blankParams = new LinearLayout.LayoutParams(0, MATCH_PARENT, 0.1f);
            addView(blank, blankParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, dp(32), 0, 0);
            setLayoutParams(params);
        }
    }

    public static class RatedHeightView extends View {
        private final Supplier<View> targetSupplier;
        private final float heightPercent;

        public RatedHeightView(Context context, float heightRate, Supplier<View> targetSupplier) {
            super(context);
            this.heightPercent = heightRate;
            this.targetSupplier = targetSupplier;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // 获取父视图（ScrollView）的高度
            View parent = targetSupplier.get();
            if (parent != null) {
                int parentHeight = parent.getHeight();
                int targetHeight = (int) (parentHeight * heightPercent);

                int width = MeasureSpec.getSize(widthMeasureSpec);
                setMeasuredDimension(width, targetHeight);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }
}