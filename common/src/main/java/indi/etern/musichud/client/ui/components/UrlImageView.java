package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.RoundedImageDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.CompletableFuture;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class UrlImageView extends FrameLayout {
    private final ProgressBar progressRing;
    private final TextView errorText;
    private final LinearLayout errorLayout;
    private final int detectBorder;
    private boolean circular = false;
    private ImageView imageView;
    private ImageView nextImageView;
    private String currentURLString;
    @Setter
    private int cornerRadius = -1;
    // 延迟加载相关字段
    private String pendingUrl = null;
    private boolean hasLoadedImage = false;
    private boolean isAttachedToWindow = false;
    private CompletableFuture<Void> loadFuture;
    private AnimatorSet currentAnimator;
    // 滚动监听器
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::checkVisibilityAndLoad;
    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        checkVisibilityAndLoad();
        return true;
    };
    private float aspectRatio = 1.0f; // 默认长宽比

    public UrlImageView(Context context) {
        super(context);

        if (cornerRadius == -1) {
            cornerRadius = dp(20);
        }
        detectBorder = dp(128);

        imageView = new ImageView(context);
        nextImageView = new ImageView(context);
        nextImageView.setAlpha(0f);
        imageView.setAdjustViewBounds(true);
        nextImageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nextImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(imageView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        addView(nextImageView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        progressRing = new ProgressBar(context);
        progressRing.setIndeterminate(true);
        progressRing.setIndeterminateTintList(ColorStateList.valueOf(Theme.PRIMARY_COLOR));
        progressRing.setVisibility(GONE);

        var params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        addView(progressRing, params);

        errorLayout = new LinearLayout(context);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setVisibility(GONE);

        errorText = new TextView(context);
        errorText.setText(I18n.get(MusicHud.MOD_ID + ".button.loadingError"));
        errorText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        errorText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        errorText.setTextColor(Theme.ERROR_TEXT_COLOR);
        errorText.setGravity(Gravity.CENTER);
        errorLayout.addView(errorText, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Button retryButton = new Button(context);
        retryButton.setText(I18n.get(MusicHud.MOD_ID + ".button.retry"));
        retryButton.setTextSize(Theme.TEXT_SIZE_SMALL);
        retryButton.setTextColor(Theme.PRIMARY_COLOR);
        InsetBackgroundFactory.builder()
                .padding(new InsetBackgroundFactory.Padding(retryButton.dp(2), retryButton.dp(1), retryButton.dp(2), retryButton.dp(1)))
                .cornerRadius(retryButton.dp(4)).inset(dp(1)).build()
                .applyBackgroundTo(retryButton);
        retryButton.setOnClickListener(v -> {
            if (currentURLString != null) {
                loadUrl(currentURLString);
            }
        });
        var retryParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        retryParams.setMargins(0, dp(4), 0, 0);
        errorLayout.addView(retryButton, retryParams);

        addView(errorLayout, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (aspectRatio > 0) {
            int width = getMeasuredWidth();
            int height = (int) (width / aspectRatio);

            // 设置测量尺寸
            setMeasuredDimension(width, height);

            // 同时设置内部ImageView的尺寸
            LayoutParams params = new LayoutParams(width, height);
            imageView.setLayoutParams(params);
            nextImageView.setLayoutParams(params);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;

        // 添加滚动和绘制监听器
        ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnScrollChangedListener(scrollListener);
        observer.addOnPreDrawListener(preDrawListener);

        // 检查是否需要加载待处理的图片
        checkVisibilityAndLoad();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedToWindow = false;

        // 移除监听器
        ViewTreeObserver observer = getViewTreeObserver();
        observer.removeOnScrollChangedListener(scrollListener);
        observer.removeOnPreDrawListener(preDrawListener);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            checkVisibilityAndLoad();
        }
    }

    /**
     * 检查 View 是否在可见区域内,如果是则加载待处理的图片
     */
    private void checkVisibilityAndLoad() {
        if (!isAttachedToWindow || hasLoadedImage || pendingUrl == null) {
            return;
        }

        if (isVisibleInScrollContainer()) {
            // View 现在可见,开始加载图片
            String urlToLoad = pendingUrl;
            pendingUrl = null;
            hasLoadedImage = true;
            actuallyLoadUrl(urlToLoad);
        }
    }

    /**
     * 检查 View 是否在滚动容器的可见区域内
     */
    private boolean isVisibleInScrollContainer() {
        if (getVisibility() != VISIBLE) {
            return false;
        }

        if (getWidth() == 0 || getHeight() == 0) {
            return false;
        }

        // 获取 View 在窗口中的位置
        int[] location = new int[2];
        getLocationInWindow(location);
        int viewTop = location[1];
        int viewBottom = viewTop + getHeight();

        // 获取根 View 的高度作为可见区域
        View rootView = getRootView();
        if (rootView == null) {
            return true; // 如果没有根 View,假设可见
        }

        int screenHeight = rootView.getHeight();

        // 检查 View 是否与可见区域有交集
        return viewBottom > -detectBorder && viewTop < screenHeight + detectBorder;
    }

    /**
     * 公开的加载方法,设置待加载的 URL
     */
    public void loadUrl(String urlString) {
        cancelLoad();
        currentURLString = urlString;
        pendingUrl = urlString;
        hasLoadedImage = false;

        // 如果已经可见,立即加载
        if (isAttachedToWindow && isVisibleInScrollContainer()) {
            checkVisibilityAndLoad();
        }
    }

    /**
     * 实际执行加载的方法
     */
    private void actuallyLoadUrl(String urlString) {
        setLoading(true);
        errorLayout.setVisibility(GONE);

        if (urlString.startsWith("data:")) {
            loadBase64Image(urlString);
        } else {
            loadNetworkImage(urlString);
        }
    }

    private void loadBase64Image(String base64String) {
        cancelLoad();
        loadFuture = CompletableFuture.runAsync(() -> {
            if (base64String.equals(currentURLString)) {
                ImageTextureData imageTextureData = ImageUtils.loadBase64(base64String);
                MuiModApi.postToUiThread(() -> {
                    if (base64String.equals(currentURLString)) {
                        try (Bitmap bitmap = imageTextureData.convertToBitmap()) {
                            if (bitmap == null) {
                                throw new RuntimeException("failed to load image from base64 string");
                            }
                            Image textureFromBitmap = Image.createTextureFromBitmap(bitmap);
                            if (textureFromBitmap != null) {
                                createDrawable(textureFromBitmap, base64String);
                            }
                        } catch (Exception e) {
                            showError(I18n.get(MusicHud.MOD_ID + ".button.loadingError"));
                        } finally {
                            imageTextureData.close();
                        }
                    }
                });
            }
        }, MusicHud.EXECUTOR);
    }

    private void loadNetworkImage(String urlString) {
        loadFuture = CompletableFuture.runAsync(() -> {
            if (urlString.equals(currentURLString)) {
                try {
                    ImageUtils.downloadAsync(urlString).thenAcceptAsync(imageTextureData -> {
                        if (urlString.equals(currentURLString)) {
                            MuiModApi.postToUiThread(() -> {
                                if (imageTextureData != null) {
                                    try (Bitmap bitmap = imageTextureData.convertToBitmap()) {
                                        if (bitmap == null) {
                                            throw new RuntimeException("failed to load image");
                                        }
                                        Image textureFromBitmap = Image.createTextureFromBitmap(bitmap);
                                        if (textureFromBitmap != null) {
                                            createDrawable(textureFromBitmap, urlString);
                                        }
                                    } catch (Exception e) {
                                        showError(I18n.get(MusicHud.MOD_ID + ".button.loadingError"));
                                    }
                                } else {
                                    showError(I18n.get(MusicHud.MOD_ID + ".button.downloadError"));
                                }
                            });
                        }
                    }, MusicHud.EXECUTOR).exceptionally((e) -> {
                        MuiModApi.postToUiThread(() -> showError(I18n.get(MusicHud.MOD_ID + ".button.loadingError")));
                        return null;
                    });
                } catch (Exception e) {
                    MuiModApi.postToUiThread(() -> showError(I18n.get(MusicHud.MOD_ID + ".button.loadingError")));
                }
            }
        }, MusicHud.EXECUTOR);
    }

    private void createDrawable(Image image, String urlString) {
        if (!urlString.equals(currentURLString)) {
            return;
        }
        float ratio = (float) image.getWidth() / image.getHeight();
        setAspectRatio(ratio);

        RoundedImageDrawable drawable = new RoundedImageDrawable(
                getContext().getResources(),
                image
        );
        drawable.setFilter(false);

        if (circular) {
            drawable.setCircular(true);
            setImageWithAnimation(drawable);
        } else {
            // 使用 OnLayoutChangeListener 确保在布局完成后设置圆角
            setImageWithAnimation(drawable);
            int imageHeight = image.getHeight();
            int height = getHeight();
            if (height > 0) {
                float actualCornerRadius = (float) (cornerRadius * imageHeight) / height;
                drawable.setCornerRadius(actualCornerRadius);
            }
            addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int height = bottom - top;
                    if (height > 0) {
                        float actualCornerRadius = (float) (cornerRadius * imageHeight) / height;
                        drawable.setCornerRadius(actualCornerRadius);
                        invalidate();
                        removeOnLayoutChangeListener(this);
                    }
                }
            });
        }
    }

    private void setImageWithAnimation(RoundedImageDrawable drawable) {
        // 取消上一次未完成的过渡动画, 防止其继续改动透明度/交换图层引用导致残留
        if (currentAnimator != null) {
            AnimatorSet old = currentAnimator;
            currentAnimator = null;
            old.cancel();
        }
        nextImageView.setImageDrawable(drawable);
        progressRing.setVisibility(GONE);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(imageView, View.ALPHA, 0f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(nextImageView, View.ALPHA, 1f);

        fadeOut.setDuration(400);
        fadeIn.setDuration(400);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fadeOut, fadeIn);
        animatorSet.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (currentAnimator != animatorSet) {
                    return;
                }
                currentAnimator = null;
                ImageView temp = imageView;
                imageView = nextImageView;
                nextImageView = temp;
            }
        });
        currentAnimator = animatorSet;
        animatorSet.start();
    }

    private void showError(String message) {
        progressRing.setVisibility(GONE);
        errorText.setText(message);
        errorLayout.setVisibility(VISIBLE);
    }

    public void clear() {
        if (currentAnimator != null) {
            AnimatorSet old = currentAnimator;
            currentAnimator = null;
            old.cancel();
        }
        progressRing.setVisibility(GONE);
        errorLayout.setVisibility(GONE);
        setLoading(false);
        // 清除残留图片, 防止旧图在淡入动画结束后重新可见
        imageView.setImageDrawable(null);
        nextImageView.setImageDrawable(null);
        imageView.setAlpha(0);
        nextImageView.setAlpha(0);

        // 清除延迟加载状态
        currentURLString = null;
        pendingUrl = null;
        hasLoadedImage = false;
    }

    public void setLoading(boolean loading) {
        if (loading) {
            progressRing.setVisibility(VISIBLE);
            errorLayout.setVisibility(GONE);
        } else {
            progressRing.setVisibility(GONE);
        }
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
        if (imageView.getDrawable() instanceof RoundedImageDrawable roundedImageDrawable) {
            roundedImageDrawable.setCircular(true);
            imageView.invalidate();
        }
        if (nextImageView.getDrawable() instanceof RoundedImageDrawable roundedImageDrawable) {
            roundedImageDrawable.setCircular(true);
            nextImageView.invalidate();
        }
    }

    public void cancelLoad() {
        pendingUrl = null;
        if (loadFuture != null) {
            loadFuture.cancel(true);
        }
    }
}