package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.animation.*;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.AutoFlowGridLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.MusicListItem;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Queue;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class HomeView extends LinearLayout {
    private static final int AUTO_RECENTER_DELAY = 3000; // 3秒后自动归位
    @Getter
    private static HomeView instance;
    private final ScrollController lyricScrollController;
    private final HashMap<LyricLine, TextView> textViewMap = new LinkedHashMap<>();
    private final HashMap<MusicCollection, MusicCollectionCard> idlePlaySourceCardMap = new HashMap<>();
    volatile boolean continueUpdateScroll = false;
    private TextView lastHighlightLine;
    private LinearLayout lyricLinesView;
    private ScrollView lyricScrollView;
    private long lastUserScrollTime = 0;
    private boolean isUserManuallyScrolling = false;
    // 标记是否已经初始化滚动
    private boolean hasInitializedScroll = false;
    // 标记是否正在进行归位滚动
    private boolean isRecenterScroll = false;
    // 记录当前滚动位置
    private int currentScrollPosition = 0;
    // 标记是否正在进行自动滚动（由滚动控制器引起的）
    private boolean isAutoScrolling = false;
    private final Runnable autoRecenterRunnable = new Runnable() {
        @Override
        public void run() {
            // 如果用户已经停止了手动滚动一段时间
            if (isUserManuallyScrolling && System.currentTimeMillis() - lastUserScrollTime >= AUTO_RECENTER_DELAY) {
                isUserManuallyScrolling = false;
                isRecenterScroll = true; // 标记为归位滚动

                // 自动归位到当前歌词
                LyricLine currentLyric = NowPlayingInfo.getInstance().getCurrentLyricLine();
                if (currentLyric != null) {
                    TextView currentTextView = textViewMap.get(currentLyric);
                    if (currentTextView != null) {
                        scrollToLyric(currentTextView);
                    }
                }
            } else if (isUserManuallyScrolling) {
                // 继续等待
                postDelayed(this, 100);
            }
        }
    };
    private final Consumer<LyricLine> lyricLineUpdateListener = new Consumer<>() {
        @Override
        public void accept(LyricLine lyricLine) {
            if (lyricLine == null) {
                if (lastHighlightLine != null) {
                    fadeText(lastHighlightLine);
                }
            } else {
                TextView lineTextView = textViewMap.get(lyricLine);
                if (lineTextView != null) {
                    post(() -> {
                        emphasizeText(lineTextView);
                        if (lastHighlightLine != null && lastHighlightLine != lineTextView) {
                            fadeText(lastHighlightLine);
                        }
                        lastHighlightLine = lineTextView;

                        // 只有在用户没有手动滚动时才自动滚动
                        if (!isUserManuallyScrolling) {
                            scrollToLyric(lineTextView);
                        }
                    });
                }
            }
        }
    };
    private FrameLayout lyricLinesWrapper;

    public HomeView(Context context) {
        super(context);

        // 初始化滚动控制器的监听器
        // 用于存储滚动控制器的监听器
        ScrollController.IListener scrollListener = (controller, amount) -> {
            if (lyricScrollView != null) {
                lyricScrollView.scrollTo(0, (int) amount);
                currentScrollPosition = (int) amount;
            }
        };

        // 初始化滚动控制器
        lyricScrollController = new ScrollController(scrollListener);

        refresh();
    }

    private void emphasizeText(TextView textView) {
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
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(textView, TEXT_COLOR, Theme.FADE_TEXT_COLOR, Theme.EMPHASIZE_TEXT_COLOR);
        colorAnim.setEvaluator(ColorEvaluator.getInstance());

        textView.requestLayout();
        textView.setPivotX(0f);
        int height = textView.getHeight();
        textView.setPivotY(Math.min(height, dp(24)));
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(textView, View.SCALE_X, 1f, 1.1f);
        scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1f, 1.1f);
        scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, colorAnim);
        set.setDuration(250);
        set.start();
    }

    private void fadeText(TextView textView) {
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
        ObjectAnimator colorAnim = ObjectAnimator.ofInt(textView, TEXT_COLOR, Theme.EMPHASIZE_TEXT_COLOR, Theme.FADE_TEXT_COLOR);
        colorAnim.setEvaluator(ColorEvaluator.getInstance());

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(textView, View.SCALE_X, 1.1f, 1f);
        scaleX.setInterpolator(Easings.EASE_IN_OUT_QUAD);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1.1f, 1f);
        scaleY.setInterpolator(Easings.EASE_IN_OUT_QUAD);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, colorAnim);
        set.setDuration(250);
        set.start();
    }

    private void setupScrollListener() {
        if (lyricScrollView == null) return;

        lyricScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY != oldScrollY) {
                currentScrollPosition = scrollY;

                // 检查是否是滚动控制器引起的滚动
                if (isAutoScrolling) {
                    // 这是自动滚动，不标记为用户手动滚动
                    return;
                }

                long currentTime = System.currentTimeMillis();

                isUserManuallyScrolling = true;
                lastUserScrollTime = currentTime;
                isRecenterScroll = false; // 重置归位标记

                // 取消之前的自动归位计时器
                removeCallbacks(autoRecenterRunnable);

                // 设置新的自动归位计时器
                postDelayed(autoRecenterRunnable, AUTO_RECENTER_DELAY);

                // 用户滚动时，如果正在自动滚动，停止自动滚动
                if (lyricScrollController.isScrolling()) {
                    lyricScrollController.abortAnimation();
                    isAutoScrolling = false;
                }
            }
        });
    }

    public void refresh() {
        instance = this;
        Context context = getContext();
        removeAllViews();
        MusicService musicService = MusicService.getInstance();

        boolean enabled = ClientConfigDefinition.enable.get();
        if (MusicHud.getStatus() != MusicHud.ConnectStatus.CONNECTED || !enabled) {
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        setOrientation(HORIZONTAL);
        {
            LinearLayout lyricsView = new LinearLayout(context);
            lyricsView.setOrientation(VERTICAL);
            LayoutParams lyricsViewParams = new LayoutParams(0, MATCH_PARENT, 3);
            addView(lyricsView, lyricsViewParams);

            TextView title = new TextView(context);
            title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            title.setText(I18n.get("music_hud.text.lyrics"));
            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(16));
            lyricsView.addView(title, params);

            lyricScrollView = new ScrollView(context);
            lyricScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            lyricScrollView.setVerticalScrollBarEnabled(false);
            lyricScrollView.setHorizontalScrollBarEnabled(false);
            lyricScrollView.setFillViewport(true);
            lyricsView.addView(lyricScrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            lyricLinesWrapper = new FrameLayout(context);

            lyricScrollView.addView(lyricLinesWrapper, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            // 重新设置监听器
            setupScrollListener();
        }
        {
            LinearLayout queueView = new LinearLayout(context);
            queueView.setOrientation(VERTICAL);
            LayoutParams queueViewParams = new LayoutParams(0, MATCH_PARENT, 2);
            addView(queueView, queueViewParams);

            var scrollView = new ScrollView(context);
            scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            scrollView.setFillViewport(true);
            queueView.addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout scrollViewContainer = new LinearLayout(context);
            scrollViewContainer.setOrientation(VERTICAL);
            scrollView.addView(scrollViewContainer, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            LayoutTransition transition1 = new LayoutTransition();
            transition1.enableTransitionType(LayoutTransition.CHANGING);
            scrollViewContainer.setLayoutTransition(transition1);

            TextView title = new TextView(context);
            title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            title.setText(I18n.get("music_hud.text.playlist"));
            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(32));
            scrollViewContainer.addView(title, params);

            LinearLayout playQueueListView = new LinearLayout(context);
            playQueueListView.setOrientation(VERTICAL);
            LayoutParams queueViewParams1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            queueViewParams1.setMargins(0, 0, 0, dp(48));
            playQueueListView.setMinimumHeight(dp(256));
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            playQueueListView.setLayoutTransition(transition);
            scrollViewContainer.addView(playQueueListView, queueViewParams1);

            LinearLayout idlePlaySourceView = new LinearLayout(context);
            idlePlaySourceView.setOrientation(VERTICAL);
            LayoutParams idlePlaylistViewParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            idlePlaySourceView.setLayoutParams(idlePlaylistViewParams);

            TextView idlePlaySourceViewTitle = new TextView(context);
            idlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            idlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            idlePlaySourceViewTitle.setText(I18n.get("music_hud.text.idlePlaySources"));
            LayoutParams params2 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            idlePlaySourceView.addView(idlePlaySourceViewTitle, params2);

            TextView idlePlaySourceViewDescription = new TextView(context);
            idlePlaySourceViewDescription.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            idlePlaySourceViewDescription.setTextSize(Theme.TEXT_SIZE_NORMAL);
            idlePlaySourceViewDescription.setText(I18n.get("music_hud.text.idlePlaySourcesDescription"));
            LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            idlePlaySourceView.addView(idlePlaySourceViewDescription, params3);

            AutoFlowGridLayout idlePlaySourceCardsList = new AutoFlowGridLayout(context);
            LayoutParams params4 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params4.setMargins(0, dp(16), 0, 0);
            idlePlaySourceView.addView(idlePlaySourceCardsList, params4);

            scrollViewContainer.addView(idlePlaySourceView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            musicService.getIdlePlaySources().forEach(playlist -> {
                MusicCollectionCard child = new MusicCollectionCard(context, playlist);
                idlePlaySourceCardsList.addView(child);
                idlePlaySourceCardMap.put(playlist, child);
            });

            Consumer<MusicCollection> addListener = collection -> {
                MuiModApi.postToUiThread(() -> {
                    MusicCollectionCard child = new MusicCollectionCard(context, collection);
                    idlePlaySourceCardsList.addView(child);
                    idlePlaySourceCardMap.put(collection, child);
                });
            };
            Consumer<MusicCollection> removeListener = collection -> {
                MuiModApi.postToUiThread(() -> {
                    MusicCollectionCard view = idlePlaySourceCardMap.get(collection);
                    if (view != null) {
                        idlePlaySourceCardsList.removeView(view);
                        idlePlaySourceCardMap.remove(collection);
                    }
                });
            };
            musicService.getIdlePlaySourceAddListeners().add(addListener);
            musicService.getIdlePlaylistRemoveListeners().add(removeListener);

            playQueueListView.removeAllViews();

            Queue<MusicDetail> queue = musicService.getMusicQueue();
            for (MusicDetail musicDetail : queue) {
                addMusicQueueItem(musicDetail, playQueueListView);
            }

            musicService.getMusicQueuePushListeners().add(musicDetail -> {
                MuiModApi.postToUiThread(() -> {
                    addMusicQueueItem(musicDetail, playQueueListView);
                });
            });
            musicService.getMusicQueueRemoveListeners().add((removeIndex, musicDetail) -> {
                MuiModApi.postToUiThread(() -> {
                    if (removeIndex >= 0 && removeIndex < playQueueListView.getChildCount()) {
                        playQueueListView.removeViewAt(removeIndex);
                    }
                });
            });

            NowPlayingInfo.getInstance().getLyricLineUpdateListener().add(lyricLineUpdateListener);

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    // 开始更新滚动控制器
                    startScrollControllerUpdate();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    NowPlayingInfo.getInstance().getLyricLineUpdateListener().remove(lyricLineUpdateListener);

                    // 清理资源
                    stopScrollControllerUpdate();
                    if (lyricScrollController != null) {
                        lyricScrollController.abortAnimation();
                    }
                    removeCallbacks(autoRecenterRunnable);
                    musicService.getIdlePlaylistRemoveListeners().remove(removeListener);
                    musicService.getIdlePlaySourceAddListeners().remove(addListener);
                    instance = null;
                }
            });
        }
    }

    private void initializeScrollToCurrentLyric() {
        // 重置初始化标记
        hasInitializedScroll = false;

        LyricLine currentLyric = NowPlayingInfo.getInstance().getCurrentLyricLine();
        if (currentLyric != null) {
            TextView currentTextView = textViewMap.get(currentLyric);
            if (currentTextView != null && !hasInitializedScroll) {
                hasInitializedScroll = true;
                // 第一次加载直接跳转，不使用平滑滚动
                jumpToLyric(currentTextView);
            }
        }
        // 如果没有当前歌词，但歌词列表不为空，滚动到顶部
        else if (!textViewMap.isEmpty() && !hasInitializedScroll) {
            hasInitializedScroll = true;
            jumpToTop();
        }
    }

    private void jumpToTop() {
        if (lyricScrollView == null || lyricLinesView == null || lyricScrollController == null) return;

        // 停止当前动画
        lyricScrollController.abortAnimation();

        // 设置滚动范围
        int scrollViewHeight = lyricScrollView.getHeight();
        int maxScroll = Math.max(0, lyricLinesView.getHeight() - scrollViewHeight);
        lyricScrollController.setMaxScroll(maxScroll);

        // 设置起始值和目标值相同，然后直接跳转
        lyricScrollController.setStartValue(0);
        lyricScrollController.scrollTo(0, 0); // 0ms立即跳转
        currentScrollPosition = 0;
    }

    private void jumpToLyric(TextView targetLyric) {
        if (lyricScrollView == null || lyricLinesView == null || lyricScrollController == null || targetLyric == null)
            return;
        int scrollViewHeight = lyricScrollView.getHeight();
        if (scrollViewHeight <= 0) {
            // 延迟执行，直到视图布局完成
            post(() -> jumpToLyric(targetLyric));
            return;
        }

        // 计算目标歌词在ScrollView中的位置
        int targetTop = 0;
        View current = targetLyric;

        while (current != lyricLinesView) {
            targetTop += current.getTop();
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }

        // 计算目标滚动位置（让歌词位于ScrollView的1/3位置）
        int targetScrollY = targetTop - scrollViewHeight / 3;

        // 确保滚动位置在有效范围内
        int maxScroll = Math.max(0, lyricLinesView.getHeight() + dp(256) - scrollViewHeight);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));

        isRecenterScroll = true;
        isAutoScrolling = true;
        // 停止当前动画
        lyricScrollController.abortAnimation();

        // 设置滚动范围
        lyricScrollController.setMaxScroll(maxScroll);

        // 设置起始值
        lyricScrollController.setStartValue(targetScrollY);

        // 立即跳转（0ms动画）
        lyricScrollController.scrollTo(targetScrollY, 0);
        currentScrollPosition = targetScrollY;

        postDelayed(() -> {
            isRecenterScroll = false;
            isAutoScrolling = false;
        }, 50);
    }

    private void scrollToLyric(TextView targetLyric) {
        if (lyricScrollView == null || lyricScrollController == null || targetLyric == null) return;

        int scrollViewHeight = lyricScrollView.getHeight();
        if (scrollViewHeight <= 0) {
            // 延迟执行，直到视图布局完成
            post(() -> scrollToLyric(targetLyric));
            return;
        }

        // 计算目标歌词在ScrollView中的位置
        int targetTop = 0;
        View current = targetLyric;

        while (current != lyricLinesView) {
            targetTop += current.getTop();
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }

        // 计算目标滚动位置（让歌词位于ScrollView的1/3位置）
        int targetScrollY = targetTop - scrollViewHeight / 3;

        // 确保滚动位置在有效范围内
        int maxScroll = Math.max(0, lyricLinesView.getHeight() + dp(256) - scrollViewHeight);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));

        // 获取当前滚动位置
        int currentScrollY = currentScrollPosition;

        // 如果已经在目标位置附近，不执行滚动
        if (Math.abs(targetScrollY - currentScrollY) < 5) {
            isRecenterScroll = false; // 重置归位标记
            return;
        }

        lyricScrollController.scrollTo(currentScrollY, 0);
        // 停止当前的滚动动画
        lyricScrollController.abortAnimation();

        // 设置滚动范围
        lyricScrollController.setMaxScroll(maxScroll);

        // 设置起始值
        lyricScrollController.setStartValue(currentScrollY);

        // 计算动画时长（基于滚动距离）
        int scrollDistance = Math.abs(targetScrollY - currentScrollY);
        int animationDuration;

        // 如果是归位滚动，使用更长的动画时间，更平滑
        animationDuration = Math.min(400 + scrollDistance / 5, 600);

        // 标记为自动滚动
        isAutoScrolling = true;

        // 执行平滑滚动
        lyricScrollController.scrollTo(targetScrollY, animationDuration);

        // 如果是归位滚动，滚动完成后重置标记
        if (isRecenterScroll) {
            postDelayed(() -> {
                isRecenterScroll = false;
                isAutoScrolling = false;
                // 归位滚动完成后，确保用户滚动状态重置
                isUserManuallyScrolling = false;
            }, animationDuration + 100);
        } else {
            // 非归位滚动的自动滚动完成后重置标记
            postDelayed(() -> isAutoScrolling = false, animationDuration + 100);
        }
    }

    private void addMusicQueueItem(MusicDetail musicDetail, LinearLayout playQueueView) {
        var musicListItem = new MusicListItem(getContext());
        musicListItem.bindData(musicDetail);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dp(16));
        LinearLayout actions = new LinearLayout(getContext());

        assert Minecraft.getInstance().player != null;
        if (musicDetail.getPusherInfo().playerUUID().equals(Minecraft.getInstance().player.getUUID())) {
            Button removeButton = new Button(getContext());
            removeButton.setText(I18n.get("music_hud.button.remove"));
            removeButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            removeButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            Drawable background = ButtonInsetBackground.builder()
                    .inset(1)
                    .padding(new ButtonInsetBackground.Padding(dp(8), dp(2), dp(2), dp(8)))
                    .cornerRadius(dp(4))
                    .build().get();
            removeButton.setBackground(background);
            removeButton.setOnClickListener(v -> {
                MusicService.getInstance().sendRemoveMusicFromQueue(playQueueView.indexOfChild(musicListItem), musicDetail);
            });
            actions.addView(removeButton, new LayoutParams(WRAP_CONTENT, dp(MusicListItem.imageSize)));
        }
        musicListItem.addView(actions);
        musicListItem.setLayoutParams(layoutParams);
        playQueueView.addView(musicListItem, layoutParams);
    }

    public void switchMusic(Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            textViewMap.clear();
            hasInitializedScroll = false;
            isUserManuallyScrolling = false;
            isRecenterScroll = false;
            isAutoScrolling = false;

            LinearLayout oldLyricLinesView = lyricLinesView;
            if (oldLyricLinesView != null) {
                ObjectAnimator slideOut = ObjectAnimator.ofFloat(oldLyricLinesView, View.TRANSLATION_X, 0, -lyricLinesWrapper.getWidth());
                slideOut.setInterpolator(Easings.EASE_IN_OUT_QUINT);
                slideOut.setDuration(300);
                slideOut.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        if (lyricLinesWrapper != null) {
                            lyricLinesWrapper.removeView(oldLyricLinesView);
                        }
                    }
                });
                slideOut.start();
                createLyricLinesView(lyricLines);
                ObjectAnimator slideIn = ObjectAnimator.ofFloat(lyricLinesView, View.TRANSLATION_X, lyricLinesWrapper.getWidth(), 0);
                slideIn.setInterpolator(Easings.EASE_IN_OUT_QUINT);
                slideIn.setDuration(300);
                slideIn.start();
            } else {
                createLyricLinesView(lyricLines);
            }
        });
    }

    private void createLyricLinesView(Queue<LyricLine> lyricLines) {
        if (lyricLinesWrapper == null) {
            return;
        }
        LinearLayout lyricLinesView1 = new LinearLayout(getContext());
        lyricLinesView = lyricLinesView1;
        lyricLinesView1.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params1.setMargins(0, 0, 0, dp(256));
        lyricLinesWrapper.addView(lyricLinesView1, params1);

        if (lyricLines != null) {
            for (LyricLine lyricLine : lyricLines) {
                Context context = getContext();
                if (context != null) {
                    String text = lyricLine.getText();
                    String translatedText = lyricLine.getTranslatedText();
                    if ((text == null || text.isEmpty()) && (translatedText == null || translatedText.isEmpty())) {
                        continue;
                    }

                    LinearLayout lyricTextWrapper = new LinearLayout(context);
                    lyricTextWrapper.setOrientation(HORIZONTAL);
                    LayoutParams lyricWrapperParams1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                    lyricTextWrapper.setLayoutParams(lyricWrapperParams1);

                    TextView lyricText = new TextView(context);
                    lyricText.setTextSize(Theme.TEXT_SIZE_LARGER);
                    LayoutParams lyricParams1 = new LayoutParams(0, WRAP_CONTENT, 0.85f);
                    lyricTextWrapper.addView(lyricText, lyricParams1);

                    View blankFill = new TextView(context);
                    LayoutParams blankParams1 = new LayoutParams(0, MATCH_PARENT, 0.15f);
                    lyricTextWrapper.addView(blankFill, blankParams1);

                    if (lyricLine.equals(NowPlayingInfo.getInstance().getCurrentLyricLine())) {
                        post(() -> {
                            emphasizeText(lyricText);
                            lastHighlightLine = lyricText;
                        });
                    } else {
                        lyricText.setTextColor(Theme.FADE_TEXT_COLOR);
                    }
                    lyricText.setText(text == null ? "" : text);
                    lyricText.setTextStyle(TextPaint.BOLD);
                    textViewMap.put(lyricLine, lyricText);

                    if (translatedText != null && !translatedText.isEmpty()) {
                        lyricParams1.setMargins(0, dp(16), 0, dp(4));
                        lyricLinesView1.addView(lyricTextWrapper);

                        TextView subLyricText = new TextView(context);
                        subLyricText.setTextColor(Theme.FADE_TEXT_COLOR);
                        subLyricText.setTextStyle(TextPaint.BOLD);
                        subLyricText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                        subLyricText.setText(translatedText);

                        LayoutParams subParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                        subParams.setMargins(0, 0, dp(32), 0);
                        lyricLinesView1.addView(subLyricText, subParams);
                    } else {
                        lyricParams1.setMargins(0, dp(16), 0, 0);
                        lyricLinesView1.addView(lyricTextWrapper);
                    }
                }
            }
        }
        lyricLinesView1.setAlpha(0);

        lyricLinesView1.post(() -> {
            if (lyricScrollView != null) {
                lyricLinesView1.requestLayout();
                lyricScrollView.requestLayout();
                initializeScrollToCurrentLyric();
                ObjectAnimator fadeAnimator = ObjectAnimator.ofFloat(lyricLinesView1, View.ALPHA, 0.0f, 1.0f);
                fadeAnimator.setDuration(150);
                fadeAnimator.start();
            } else {
                MusicHud.LOGGER.warn("Cannot initialize lyrics view because lyricScrollView is null");
            }
        });
    }

    private void startScrollControllerUpdate() {
        continueUpdateScroll = true;
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            lyricScrollController.update(MuiModApi.getElapsedTime());
            if (continueUpdateScroll) {
                startScrollControllerUpdate();
            }
        });
    }

    private void stopScrollControllerUpdate() {
        continueUpdateScroll = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopScrollControllerUpdate();
        if (lyricScrollController != null) {
            lyricScrollController.abortAnimation();
        }
        removeCallbacks(autoRecenterRunnable);
        instance = null;
        lastHighlightLine = null;
        lyricLinesView = null;
        lyricLinesWrapper = null;
        lastUserScrollTime = 0;
        isUserManuallyScrolling = false;
        hasInitializedScroll = false;
        isRecenterScroll = false;
        currentScrollPosition = 0;
        isAutoScrolling = false;
    }
}