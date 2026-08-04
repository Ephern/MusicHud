package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.client.ui.utils.ui.EasingInterpolator;
import indi.etern.musichud.client.ui.utils.ui.Easing;
import lombok.Getter;
import lombok.Setter;

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
    @Setter
    private int animationDuration = 300;
    private TransitionType transitionType = TransitionType.FADE;
    private AnimatorSet currentAnimation = null;
    private OnPageChangeListener pageChangeListener;

    private final Stack<String> routeStack = new Stack<>();

    private int dynamicViewCounter = 0;

    private final Map<String, View> dynamicViews = new HashMap<>();

    private boolean isTransitioning = false;
    private String pendingNavigationKey = null;
    private final Easing defaultEasing = Easing.EASE_IN_OUT_QUINT;

    public enum TransitionType {
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
        public abstract List<Animator> createAnimators(@ Nullable View fromPage, View toPage,
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

//        if (!pageCache.containsKey(key)) {
//            View page = factory.apply(getContext());
//            page.setVisibility(GONE);
//            page.setAlpha(0f);
//            pageCache.put(key, page);
//            addView(page, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
//        }
    }

    public void navigateToRoot(@NonNull String key) {
        navigateToRoot(key, null);
    }

    public void navigateToRoot(@NonNull String key, @Nullable TransitionType transitionType) {
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

        // 将根页面的 key 压入栈
        routeStack.push(key);

        TransitionType effectiveTransition = transitionType != null ? transitionType : this.transitionType;

        performTransition(currentPage, targetPage, key, effectiveTransition, null);
    }

    /**
     * 推入一个动态 View 到路由栈
     * @param view 要显示的 View
     */
    public void pushNavigate(@NonNull View view) {
        pushNavigate(view, TransitionType.SLIDE_LEFT);
    }

    /**
     * 推入一个动态 View 到路由栈,并指定动画类型
     * @param view 要显示的 View
     * @param transitionType 动画类型
     */
    public void pushNavigate(@NonNull View view, @Nullable TransitionType transitionType) {
        if (isTransitioning) {
            // 如果正在切换,延迟执行
            post(() -> pushNavigate(view, transitionType));
            return;
        }

        // 生成唯一的 key
        String dynamicKey = "dynamic_" + (dynamicViewCounter++);

        // 将 View 添加到容器中
        view.setVisibility(GONE);
        view.setAlpha(0f);
        addView(view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        dynamicViews.put(dynamicKey, view);

        isTransitioning = true;

        View currentPage = getCurrentViewFromStack();

        // 将动态 View 的 key 压入栈
        routeStack.push(dynamicKey);

        TransitionType effectiveTransition = transitionType != null ? transitionType : TransitionType.SLIDE_LEFT;

        performTransition(currentPage, view, dynamicKey, effectiveTransition, null);
    }

    /**
     * 弹出当前页面,返回到上一个页面
     */
    public void popNavigate() {
        popNavigate(TransitionType.SLIDE_RIGHT);
    }

    /**
     * 弹出当前页面,返回到上一个页面,并指定动画类型
     * @param transitionType 动画类型
     */
    public void popNavigate(@Nullable TransitionType transitionType) {
        if (routeStack.size() <= 1) {
            // 栈中只有一个页面,无法弹出
            return;
        }

        if (isTransitioning) {
            // 如果正在切换,延迟执行
            post(() -> popNavigate(transitionType));
            return;
        }

        isTransitioning = true;

        // 弹出当前页面
        String currentKey = routeStack.pop();
        View currentPage = getViewByKey(currentKey);

        // 获取父页面(不弹出)
        String parentKey = routeStack.peek();
        View parentPage = getViewByKey(parentKey);

        if (parentPage == null) {
            // 父页面不存在,恢复栈状态
            routeStack.push(currentKey);
            isTransitioning = false;
            return;
        }

        TransitionType effectiveTransition = transitionType != null ? transitionType : TransitionType.SLIDE_RIGHT;

        // 从当前页面切换到父页面
        performTransition(currentPage, parentPage, parentKey, effectiveTransition, null);
    }

    /**
     * 根据 key 获取对应的 View
     */
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

    /**
     * 从栈顶获取当前显示的 View
     */
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
                                   String toKey, @Nullable TransitionType customType,
                                   @Nullable Easing customEasing) {
        isTransitioning = true;

        toPage.setVisibility(VISIBLE);

        TransitionType type = customType != null ? customType : transitionType;
        Easing easing = customEasing != null ? customEasing : defaultEasing;
        TimeInterpolator interpolator = EasingInterpolator.of(easing);

        // 使用枚举的方法创建动画
        List<Animator> animators = type.createAnimators(fromPage, toPage, animationDuration, interpolator);

        if (animators.isEmpty()) {
            // NONE 类型:无动画,直接完成
            if (fromPage != null) {
                fromPage.setVisibility(GONE);
                fromPage.setAlpha(0f);
            }
            toPage.setAlpha(1f);
            onTransitionComplete(fromPage, toKey);
            return;
        }

        // 创建动画集合
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

            // 如果 fromPage 是动态 View 且不在栈中,移除它
            String fromKey = findKeyForView(fromPage);
            if (fromKey != null && fromKey.startsWith("dynamic_") && !routeStack.contains(fromKey)) {
                dynamicViews.remove(fromKey);
                removeView(fromPage);
            }
        }

        // 确保目标页面的属性正确
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
            // 触发页面切换完成回调
            if (pageChangeListener != null) {
                pageChangeListener.onPageChangeEnd(toKey);
            }
        }

        // 处理待处理的导航请求
        if (pendingNavigationKey != null) {
            String pendingKey = pendingNavigationKey;
            pendingNavigationKey = null;
            navigateToRoot(pendingKey);
        }
    }

    // 辅助方法:根据 View 查找对应的 key
    private String findKeyForView(View view) {
        // 先在 pageCache 中查找
        for (Map.Entry<String, View> entry : pageCache.entrySet()) {
            if (entry.getValue() == view) {
                return entry.getKey();
            }
        }
        // 再在 dynamicViews 中查找
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

    public void setTransitionType(@NonNull TransitionType type) {
        transitionType = type;
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