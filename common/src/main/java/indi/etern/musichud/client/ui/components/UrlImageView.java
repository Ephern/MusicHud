package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.RoundedImageDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class UrlImageView extends FrameLayout {
    private final ProgressBar progressRing;
    private final LinearLayout errorLayout;
    private final int detectBorder;
    private final ImageButton retryButton;
    private boolean circular = false;
    private ImageView imageView;
    private ImageView nextImageView;
    private String currentURLString;
    @Setter
    private int cornerRadius = -1;
    // 延迟加载相关字段
    private String pendingUrl = null;
    private boolean hasLoadedImage = false;
    private int loadTargetW = 0;
    private int loadTargetH = 0;
    /** Size this load actually requests: the caller's, or the view's own size when it gave none. */
    private int effectiveLoadW = 0;
    private int effectiveLoadH = 0;
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
    @Setter
    @Getter
    private int transitionDuration = 400;

    /**
     * Shared ModernUI textures, keyed by URL only. A texture is reused for any view at or below its
     * size (the drawable scales anyway) and replaced when a clearly larger request arrives, so the
     * key space is the set of URLs instead of the set of (url, layout size) pairs. Uploads are the
     * expensive part of a bind: with a size-qualified key, scrolling a list of a few hundred covers
     * produced a new upload per rebound row (measured: 1506 evictions against 1277 hits).
     */
    private static final long UI_TEXTURE_BUDGET = 64L * 1024 * 1024;
    private static final int UI_TEXTURE_MIN_ENTRIES = 64;
    private static final Map<String, Image> UI_TEXTURES =
            new LinkedHashMap<>(256, 0.75f, true);
    private static long uiTextureBytes;

    private static final AtomicLong imageCacheHits = new AtomicLong();
    private static final AtomicLong imageCacheUploads = new AtomicLong();
    private static final AtomicLong imageCacheEvictions = new AtomicLong();

    /** Cache state for the render stats line. */
    public static String describeImageCache() {
        synchronized (UI_TEXTURES) {
            return " | uicache: hit=" + imageCacheHits.get()
                    + " upload=" + imageCacheUploads.get()
                    + " evict=" + imageCacheEvictions.get()
                    + " live=" + UI_TEXTURES.size()
                    + " " + (uiTextureBytes / 1048576L) + "MiB";
        }
    }

    private static Image reuseTexture(String url, int wantW, int wantH) {
        synchronized (UI_TEXTURES) {
            Image cached = UI_TEXTURES.get(url);
            if (cached == null) {
                return null;
            }
            // Reuse unless the cached texture is clearly smaller than this view; that converges on
            // the largest request per URL instead of re-uploading between sizes.
            if (cached.getWidth() * 4 >= wantW * 3 && cached.getHeight() * 4 >= wantH * 3) {
                imageCacheHits.incrementAndGet();
                return cached;
            }
            return null;
        }
    }

    private static void storeTexture(String url, Image image) {
        synchronized (UI_TEXTURES) {
            Image previous = UI_TEXTURES.put(url, image);
            if (previous != null) {
                uiTextureBytes -= textureBytes(previous);
            }
            uiTextureBytes += textureBytes(image);
            imageCacheUploads.incrementAndGet();
            Iterator<Map.Entry<String, Image>> eldest = UI_TEXTURES.entrySet().iterator();
            while (uiTextureBytes > UI_TEXTURE_BUDGET && UI_TEXTURES.size() > UI_TEXTURE_MIN_ENTRIES
                    && eldest.hasNext()) {
                Map.Entry<String, Image> entry = eldest.next();
                if (entry.getValue() == image) {
                    continue;
                }
                uiTextureBytes -= textureBytes(entry.getValue());
                eldest.remove();
                imageCacheEvictions.incrementAndGet();
            }
        }
    }

    private static long textureBytes(Image image) {
        return (long) Math.max(1, image.getWidth()) * Math.max(1, image.getHeight()) * 4L;
    }

    /**
     * Texture size for a {@code loadUrl(url)} call, i.e. when the caller did not state one: cover
     * the view's own size (drawable scaling handles the rest) instead of uploading a 1024x1024
     * texture for a row-sized thumbnail.
     */
    private int implicitTargetSize() {
        int side = Math.max(getWidth(), getHeight());
        if (side <= 0) {
            side = dp(96);
        }
        return Math.clamp(side, 32, 1024);
    }

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

        retryButton = new ImageButton(context);
        retryButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        StateListDrawable icon = new StateListDrawable();
        var resources = getContext().getResources();
        Image rotate = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_ccw.png");
        if (rotate != null) {
            icon.addState(StateSet.get(StateSet.VIEW_STATE_HOVERED), new ScaledImageDrawable(resources, rotate, dp(12), dp(20)));
        }
        Image off = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/image_off.png");
        if (off != null) {
            icon.addState(StateSet.WILD_CARD, new ScaledImageDrawable(resources, off, dp(12), dp(20)));
        }

        retryButton.setImageDrawable(icon);
        InsetBackgroundFactory.builder()
                .padding(new InsetBackgroundFactory.Padding(dp(8), dp(8), dp(8), dp(8)))
                .cornerRadius(dp(4)).inset(dp(1)).build()
                .applyBackgroundTo(retryButton);
        retryButton.setOnClickListener(v -> {
            if (currentURLString != null) {
                loadUrl(currentURLString);
            }
        });
        errorLayout.addView(retryButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

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
        loadUrl(urlString, 0, 0);
    }

    /**
     * 加载图片并降采样到不超过 targetW x targetH（保持长宽比）。
     * 传入与原图相同的 URL 时可复用 ImageUtils 的字节缓存（含 JMTC 下载的全尺寸图）。
     */
    public void loadUrl(String urlString, int targetW, int targetH) {
        if (urlString != null && urlString.equals(currentURLString)
                && targetW == loadTargetW && targetH == loadTargetH
                && (hasLoadedImage || loadFuture != null)) {
            // Virtual lists re-bind their rows every layout pass; rebinding the same URL at the same
            // size must not re-upload the image (that alone allocated a texture per card per frame).
            return;
        }
        loadTargetW = targetW;
        loadTargetH = targetH;
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
        if (loadTargetW <= 0 || loadTargetH <= 0) {
            // No explicit size from the caller: cover the view, not a 1024x1024 texture.
            int side = implicitTargetSize();
            effectiveLoadW = side;
            effectiveLoadH = side;
        } else {
            effectiveLoadW = loadTargetW;
            effectiveLoadH = loadTargetH;
        }
        Image cached = reuseTexture(urlString, effectiveLoadW, effectiveLoadH);
        if (cached != null) {
            // Re-binding a recycled row normally re-uploads the same artwork; the upload is by far
            // the most expensive part of a bind, so reuse the ModernUI texture when we have it.
            setLoading(false);
            errorLayout.setVisibility(GONE);
            hasLoadedImage = true;
            createDrawable(cached, urlString);
            return;
        }
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
                //noinspection resource
                ImageTextureData imageTextureData = ImageUtils.loadBase64(base64String);
                MuiModApi.postToUiThread(() -> {
                    if (base64String.equals(currentURLString)) {
                        try (Bitmap bitmap = imageTextureData.convertToBitmap()) {
                            if (bitmap == null) {
                                throw new RuntimeException("failed to load image from base64 string");
                            }
                            Image textureFromBitmap = Image.createTextureFromBitmap(bitmap);
                            if (textureFromBitmap != null) {
                                ImageUtils.noteUiUpload();
                                storeTexture(base64String, textureFromBitmap);
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
        int targetW = effectiveLoadW;
        int targetH = effectiveLoadH;
        loadFuture = CompletableFuture.runAsync(() -> {
            if (urlString.equals(currentURLString)) {
                try {
                    // Borrowed from the shared source cache: never close it, the cache owns it and
                    // other views may be reading the same pixels. ModernUI uploads its own texture.
                    CompletableFuture<icyllis.modernui.graphics.Bitmap> future =
                            ImageUtils.loadBitmap(urlString, targetW, targetH);
                    future.thenAcceptAsync(bitmap -> {
                        if (urlString.equals(currentURLString)) {
                            MuiModApi.postToUiThread(() -> {
                                if (bitmap != null) {
                                    try {
                                        Image textureFromBitmap = Image.createTextureFromBitmap(bitmap);
                                        if (textureFromBitmap != null) {
                                            ImageUtils.noteUiUpload();
                                            storeTexture(urlString, textureFromBitmap);
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
        int imageViewWidth = getWidth();
        int imageViewHeight = getHeight();
        boolean useFilter = image.getWidth() * image.getHeight() >= 0.25 * imageViewWidth * imageViewHeight;
        if (ImageUtils.isPixelArt(urlString)) {
            // Hard-edged art must not be smoothed when it is enlarged; minification still filters.
            boolean enlarged = image.getWidth() < imageViewWidth || image.getHeight() < imageViewHeight;
            useFilter = !enlarged;
        }
        drawable.setFilter(useFilter);

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
        if (currentAnimator != null) {
            AnimatorSet old = currentAnimator;
            currentAnimator = null;
            old.cancel();
        }
        nextImageView.setImageDrawable(drawable);
        progressRing.setVisibility(GONE);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(imageView, View.ALPHA, 0f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(nextImageView, View.ALPHA, 1f);

        fadeOut.setDuration(transitionDuration);
        fadeIn.setDuration(transitionDuration);

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
        retryButton.setTooltipText(message);
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
        loadTargetW = 0;
        loadTargetH = 0;
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
