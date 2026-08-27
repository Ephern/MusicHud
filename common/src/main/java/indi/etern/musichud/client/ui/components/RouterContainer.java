package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.client.utils.ui.Easing;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

/*
* 相较于Fragment在切换时保留了上下文
* */
@SuppressWarnings("unused")
public class RouterContainer extends FrameLayout {
    @Getter
    private static RouterContainer instance;

    private final Map<String, View> pageCache = new HashMap<>();
    private final Map<String, Function<Context, View>> pageFactories = new HashMap<>();
    private String currentPageKey = null;
    @Getter
    private int animationDuration = 300;
    private AnimationStyle animationStyle = AnimationStyle.FADE;
    private TransitionType transitionType = TransitionType.CROSS;
    private AnimatorSet currentAnimation = null;
    private OnPageChangeListener pageChangeListener;

    private final Stack<String> routeStack = new Stack<>();

    private int dynamicViewCounter = 0;

    private final Map<String, View> dynamicViews = new HashMap<>();

    private boolean isTransitioning = false;
    private String pendingNavigationKey = null;
    private final Easing defaultEasing = Easing.EASE_IN_OUT_QUINT;

    public enum TransitionType {
        CROSS,
        SERIAL
    }

    public enum AnimationStyle {
        FADE {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();

                if (fromPage != null) {
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    fadeOut.setDuration(duration);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(fadeOut);
                }

                toPage.setAlpha(0f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                fadeIn.setDuration(duration);
                fadeIn.setInterpolator(interpolator);
                animators.add(fadeIn);

                return animators;
            }
        },

        SLIDE_LEFT {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();
                int width = instance.getWidth();

                if (fromPage != null) {
                    ObjectAnimator slideOut = ObjectAnimator.ofFloat(fromPage, View.TRANSLATION_X,
                            0f, -width);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    slideOut.setDuration(duration);
                    fadeOut.setDuration(duration);
                    slideOut.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(slideOut);
                    animators.add(fadeOut);
                }

                toPage.setTranslationX(width);
                toPage.setAlpha(0f);
                ObjectAnimator slideIn = ObjectAnimator.ofFloat(toPage, View.TRANSLATION_X,
                        width, 0f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                slideIn.setDuration(duration);
                fadeIn.setDuration(duration);
                slideIn.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(slideIn);
                animators.add(fadeIn);

                return animators;
            }
        },

        SLIDE_RIGHT {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();
                int width = instance.getWidth();

                if (fromPage != null) {
                    ObjectAnimator slideOut = ObjectAnimator.ofFloat(fromPage, View.TRANSLATION_X,
                            0f, width);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    slideOut.setDuration(duration);
                    fadeOut.setDuration(duration);
                    slideOut.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(slideOut);
                    animators.add(fadeOut);
                }

                toPage.setTranslationX(-width);
                toPage.setAlpha(0f);
                ObjectAnimator slideIn = ObjectAnimator.ofFloat(toPage, View.TRANSLATION_X,
                        -width, 0f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                slideIn.setDuration(duration);
                fadeIn.setDuration(duration);
                slideIn.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(slideIn);
                animators.add(fadeIn);

                return animators;
            }
        },

        SCALE {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();

                if (fromPage != null) {
                    ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(fromPage, View.SCALE_X, 1f, 0.8f);
                    ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(fromPage, View.SCALE_Y, 1f, 0.8f);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    scaleOutX.setDuration(duration);
                    scaleOutY.setDuration(duration);
                    fadeOut.setDuration(duration);
                    scaleOutX.setInterpolator(interpolator);
                    scaleOutY.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(scaleOutX);
                    animators.add(scaleOutY);
                    animators.add(fadeOut);
                }

                toPage.setScaleX(0.8f);
                toPage.setScaleY(0.8f);
                toPage.setAlpha(0f);
                ObjectAnimator scaleInX = ObjectAnimator.ofFloat(toPage, View.SCALE_X, 0.8f, 1f);
                ObjectAnimator scaleInY = ObjectAnimator.ofFloat(toPage, View.SCALE_Y, 0.8f, 1f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                scaleInX.setDuration(duration);
                scaleInY.setDuration(duration);
                fadeIn.setDuration(duration);
                scaleInX.setInterpolator(interpolator);
                scaleInY.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(scaleInX);
                animators.add(scaleInY);
                animators.add(fadeIn);

                return animators;
            }
        },

        SCALE_FADE_ROOT {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();

                if (fromPage != null) {
                    ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(fromPage, View.SCALE_X, 1f, 0.97f);
                    ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(fromPage, View.SCALE_Y, 1f, 0.97f);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    scaleOutX.setDuration(duration);
                    scaleOutY.setDuration(duration);
                    fadeOut.setDuration(duration);
                    scaleOutX.setInterpolator(interpolator);
                    scaleOutY.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(scaleOutX);
                    animators.add(scaleOutY);
                    animators.add(fadeOut);
                }

                toPage.setScaleX(0.97f);
                toPage.setScaleY(0.97f);
                toPage.setAlpha(0f);
                ObjectAnimator scaleInX = ObjectAnimator.ofFloat(toPage, View.SCALE_X, 0.97f, 1f);
                ObjectAnimator scaleInY = ObjectAnimator.ofFloat(toPage, View.SCALE_Y, 0.97f, 1f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                scaleInX.setDuration(duration);
                scaleInY.setDuration(duration);
                fadeIn.setDuration(duration);
                scaleInX.setInterpolator(interpolator);
                scaleInY.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(scaleInX);
                animators.add(scaleInY);
                animators.add(fadeIn);

                return animators;
            }
        },

        SCALE_FADE_PUSH {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();

                if (fromPage != null) {
                    ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(fromPage, View.SCALE_X, 1f, 1.02f);
                    ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(fromPage, View.SCALE_Y, 1f, 1.02f);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    scaleOutX.setDuration(duration);
                    scaleOutY.setDuration(duration);
                    fadeOut.setDuration(duration);
                    scaleOutX.setInterpolator(interpolator);
                    scaleOutY.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(scaleOutX);
                    animators.add(scaleOutY);
                    animators.add(fadeOut);
                }

                toPage.setScaleX(1.02f);
                toPage.setScaleY(1.02f);
                toPage.setAlpha(0f);
                ObjectAnimator scaleInX = ObjectAnimator.ofFloat(toPage, View.SCALE_X, 0.97f, 1f);
                ObjectAnimator scaleInY = ObjectAnimator.ofFloat(toPage, View.SCALE_Y, 0.97f, 1f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                scaleInX.setDuration(duration);
                scaleInY.setDuration(duration);
                fadeIn.setDuration(duration);
                scaleInX.setInterpolator(interpolator);
                scaleInY.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(scaleInX);
                animators.add(scaleInY);
                animators.add(fadeIn);

                return animators;
            }
        },

        SCALE_FADE_POP {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                List<Animator> animators = new ArrayList<>();

                if (fromPage != null) {
                    ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(fromPage, View.SCALE_X, 1f, 0.97f);
                    ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(fromPage, View.SCALE_Y, 1f, 0.97f);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(fromPage, View.ALPHA,
                            fromPage.getAlpha(), 0f);
                    scaleOutX.setDuration(duration);
                    scaleOutY.setDuration(duration);
                    fadeOut.setDuration(duration);
                    scaleOutX.setInterpolator(interpolator);
                    scaleOutY.setInterpolator(interpolator);
                    fadeOut.setInterpolator(interpolator);
                    animators.add(scaleOutX);
                    animators.add(scaleOutY);
                    animators.add(fadeOut);
                }

                toPage.setScaleX(0.97f);
                toPage.setScaleY(0.97f);
                toPage.setAlpha(0f);
                ObjectAnimator scaleInX = ObjectAnimator.ofFloat(toPage, View.SCALE_X, 1.02f, 1f);
                ObjectAnimator scaleInY = ObjectAnimator.ofFloat(toPage, View.SCALE_Y, 1.02f, 1f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(toPage, View.ALPHA, 0f, 1f);
                scaleInX.setDuration(duration);
                scaleInY.setDuration(duration);
                fadeIn.setDuration(duration);
                scaleInX.setInterpolator(interpolator);
                scaleInY.setInterpolator(interpolator);
                fadeIn.setInterpolator(interpolator);
                animators.add(scaleInX);
                animators.add(scaleInY);
                animators.add(fadeIn);

                return animators;
            }
        },

        NONE {
            @Override
            public List<Animator> createAnimators(View fromPage, View toPage,
                                                  int duration, TimeInterpolator interpolator) {
                return Collections.emptyList();
            }
        };

        /**
         * 创建过渡动画
         * @param fromPage 当前页面,可能为 null
         * @param toPage 目标页面
         * @param duration 动画持续时间
         * @param interpolator 缓动函数
         * @return 动画列表
         */
        public abstract List<Animator> createAnimators(@Nullable View fromPage, View toPage,
                                                       int duration, TimeInterpolator interpolator);
    }

    public interface OnPageChangeListener {
        void onPageChangeStart(@Nullable String fromKey, @NonNull String toKey);
        void onPageChangeEnd(@NonNull String pageKey);
    }

    public RouterContainer(Context context) {
        super(context);
        RouterContainer thisInstance = this;
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                instance = thisInstance;
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
            }
        });
    }

    public void registerPage(@NonNull String key, @NonNull Function<Context, View> factory) {
        pageFactories.put(key, factory);
    }

    public void navigateToRoot(@NonNull String key) {
        navigateToRoot(key, null);
    }

    public void navigateToRoot(@NonNull String key, @Nullable AnimationStyle animationStyle) {
        if (key.equals(currentPageKey) && routeStack.size() == 1 && !isTransitioning) {
            return;
        }

        if (!pageFactories.containsKey(key)) {
            throw new IllegalArgumentException("Page not registered: " + key);
        }

        while (!routeStack.empty()) {
            String popKey = routeStack.pop();
            View view = dynamicViews.get(popKey);
            if (view != null) {
                removeView(view);
            }
        }

        if (isTransitioning) {
            pendingNavigationKey = key;
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.end();
                routeStack.push(key);
            }
            return;
        }

        isTransitioning = true;

        if (pageChangeListener != null) {
            pageChangeListener.onPageChangeStart(currentPageKey, key);
        }

        View targetPage = getOrCreatePage(key);
        View currentPage = currentPageKey != null ? pageCache.get(currentPageKey) : null;

        routeStack.push(key);

        AnimationStyle effectiveStyle = animationStyle != null ? animationStyle : this.animationStyle;

        performTransition(currentPage, targetPage, key, effectiveStyle, null);
    }

    public void pushNavigate(@NonNull View view) {
        pushNavigate(view, AnimationStyle.SCALE_FADE_PUSH);
    }

    public void pushNavigate(@NonNull View view, @Nullable AnimationStyle animationStyle) {
        if (isTransitioning) {
            post(() -> pushNavigate(view, animationStyle));
            return;
        }

        String dynamicKey = "dynamic_" + (dynamicViewCounter++);

        view.setVisibility(GONE);
        view.setAlpha(0f);
        addView(view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        dynamicViews.put(dynamicKey, view);

        isTransitioning = true;

        View currentPage = getCurrentViewFromStack();

        if (pageChangeListener != null) {
            pageChangeListener.onPageChangeStart(currentPageKey, dynamicKey);
        }

        routeStack.push(dynamicKey);

        AnimationStyle effectiveStyle = animationStyle != null ? animationStyle : AnimationStyle.SCALE_FADE_PUSH;

        performTransition(currentPage, view, dynamicKey, effectiveStyle, null);
    }

    public void popNavigate() {
        popNavigate(AnimationStyle.SCALE_FADE_POP);
    }

    public void popNavigate(@Nullable AnimationStyle animationStyle) {
        if (routeStack.size() <= 1) {
            return;
        }

        if (isTransitioning) {
            post(() -> popNavigate(animationStyle));
            return;
        }

        isTransitioning = true;

        String currentKey = routeStack.pop();
        View currentPage = getViewByKey(currentKey);

        String parentKey = routeStack.peek();
        View parentPage = getViewByKey(parentKey);

        if (parentPage == null) {
            routeStack.push(currentKey);
            isTransitioning = false;
            return;
        }

        if (pageChangeListener != null) {
            pageChangeListener.onPageChangeStart(currentKey, parentKey);
        }

        AnimationStyle effectiveStyle = animationStyle != null ? animationStyle : AnimationStyle.SCALE_FADE_POP;

        performTransition(currentPage, parentPage, parentKey, effectiveStyle, null);
    }

    private View getViewByKey(Object key) {
        if (key instanceof String keyStr) {
            if (keyStr.startsWith("dynamic_")) {
                return dynamicViews.get(keyStr);
            } else {
                return pageCache.get(keyStr);
            }
        }
        return null;
    }

    private View getCurrentViewFromStack() {
        if (routeStack.isEmpty()) {
            return null;
        }
        String currentKey = routeStack.peek();
        return getViewByKey(currentKey);
    }

    private View getOrCreatePage(@NonNull String key) {
        View page = pageCache.get(key);
        if (page == null) {
            Function<Context, View> factory = pageFactories.get(key);
            if (factory != null) {
                page = factory.apply(getContext());
                page.setVisibility(GONE);
                page.setAlpha(0f);
                pageCache.put(key, page);
                addView(page, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            }
        }
        return page;
    }

    private void performTransition(@Nullable View fromPage, @NonNull View toPage,
                                   String toKey, @Nullable AnimationStyle customStyle,
                                   @Nullable Easing customEasing) {
        isTransitioning = true;

        toPage.setVisibility(VISIBLE);

        AnimationStyle style = customStyle != null ? customStyle : animationStyle;
        Easing easing = customEasing != null ? customEasing : defaultEasing;

        List<Animator> animators = style.createAnimators(fromPage, toPage, animationDuration, easing);

        if (animators.isEmpty()) {
            if (fromPage != null) {
                fromPage.setVisibility(GONE);
                fromPage.setAlpha(0f);
            }
            toPage.setAlpha(1f);
            onTransitionComplete(fromPage, toKey);
            return;
        }

        if (transitionType == TransitionType.SERIAL && fromPage != null) {
            for (Animator animator : animators) {
                if (animator instanceof ObjectAnimator oa) {
                    Object target = oa.getTarget();
                    if (target == toPage) {
                        oa.setStartDelay(animationDuration);
                    } else if (target == fromPage) {
                        oa.addListener(new AnimatorListener() {
                            @Override
                            public void onAnimationEnd(@NonNull Animator animation) {
                                fromPage.setVisibility(GONE);
                                fromPage.setTranslationX(0f);
                                fromPage.setScaleX(1f);
                                fromPage.setScaleY(1f);
                                fromPage.setAlpha(0f);
                            }
                        });
                    }
                }
            }
        }

        currentAnimation = new AnimatorSet();
        currentAnimation.playTogether(animators);

        final View finalFromPage = fromPage;
        currentAnimation.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                onTransitionComplete(finalFromPage, toKey);
            }
        });
        currentAnimation.start();
    }

    private void onTransitionComplete(@Nullable View fromPage, String toKey) {
        if (fromPage != null) {
            fromPage.setVisibility(GONE);
            fromPage.setTranslationX(0f);
            fromPage.setScaleX(1f);
            fromPage.setScaleY(1f);
            fromPage.setAlpha(0f);

            String fromKey = findKeyForView(fromPage);
            if (fromKey != null && fromKey.startsWith("dynamic_") && !routeStack.contains(fromKey)) {
                dynamicViews.remove(fromKey);
                removeView(fromPage);
            }
        }

        View toPage = toKey != null ? pageCache.get(toKey) : null;
        if (toPage == null && toKey != null && toKey.startsWith("dynamic_")) {
            toPage = dynamicViews.get(toKey);
        }

        if (toPage != null) {
            toPage.setVisibility(VISIBLE);
            toPage.setTranslationX(0f);
            toPage.setScaleX(1f);
            toPage.setScaleY(1f);
            toPage.setAlpha(1f);
        }

        currentAnimation = null;
        isTransitioning = false;
        if (toKey != null) {
            currentPageKey = toKey;
            if (pageChangeListener != null) {
                pageChangeListener.onPageChangeEnd(toKey);
            }
        }

        if (pendingNavigationKey != null) {
            String pendingKey = pendingNavigationKey;
            pendingNavigationKey = null;
            navigateToRoot(pendingKey);
        }
    }

    private String findKeyForView(View view) {
        for (Map.Entry<String, View> entry : pageCache.entrySet()) {
            if (entry.getValue() == view) {
                return entry.getKey();
            }
        }
        for (Map.Entry<String, View> entry : dynamicViews.entrySet()) {
            if (entry.getValue() == view) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    public String getCurrentPageKey() {
        return currentPageKey;
    }

    @Nullable
    public View getCurrentPage() {
        if (currentPageKey == null) {
            return null;
        }
        View page = pageCache.get(currentPageKey);
        if (page == null && currentPageKey.startsWith("dynamic_")) {
            page = dynamicViews.get(currentPageKey);
        }
        return page;
    }

    public void setAnimationStyle(@NonNull AnimationStyle style) {
        animationStyle = style;
    }

    public void setTransitionType(@NonNull TransitionType type) {
        transitionType = type;
    }

    @NonNull
    public TransitionType getTransitionType() {
        return transitionType;
    }

    public void setAnimationDuration(int duration) {
        animationDuration = duration;
    }

    public void setOnPageChangeListener(@Nullable OnPageChangeListener listener) {
        pageChangeListener = listener;
    }

    public void clearAllPageCache() {
        for (View page : pageCache.values()) {
            removeView(page);
        }
        for (View page : dynamicViews.values()) {
            removeView(page);
        }
        pageCache.clear();
        dynamicViews.clear();
        routeStack.clear();
        currentPageKey = null;
    }

    public boolean isPageRegistered(@NonNull String key) {
        return pageFactories.containsKey(key);
    }

    @NonNull
    public Set<String> getRegisteredPageKeys() {
        return new HashSet<>(pageFactories.keySet());
    }
}