package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Compact requester marker: the avatar alone, the player name lives in the tooltip.
 */
public class PusherWidget extends FrameLayout {
    private static final String FALLBACK_ICON_PATH = "/assets/music_hud/textures/gui/icons/user_star.png";
    private static final int DEFAULT_FALLBACK_ICON_SIZE_DP = 16;
    private static final float DEFAULT_FALLBACK_ICON_ALPHA = 0.8f;

    private final PlayerHeadView pusherHeadView;
    private final ImageView fallbackIconView;
    private final Tooltip tooltip;
    private int fallbackIconSizePx;
    private PusherInfo boundPusher;

    public PusherWidget(Context context) {
        super(context);
        fallbackIconView = new ImageView(context);
        fallbackIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Image fallbackImage = ImageUtils.getImageFromResource(FALLBACK_ICON_PATH);
        if (fallbackImage != null) {
            var drawable = new ScaledImageDrawable(getContext().getResources(), fallbackImage);
            drawable.setFilter(true);
            fallbackIconView.setImageDrawable(drawable);
        }
        fallbackIconView.setAlpha(DEFAULT_FALLBACK_ICON_ALPHA);
        fallbackIconSizePx = dp(DEFAULT_FALLBACK_ICON_SIZE_DP);
        addView(fallbackIconView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        pusherHeadView = new PlayerHeadView(context);
        addView(pusherHeadView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        pusherHeadView.setOnAvatarStateChangedListener(this::updateFallbackIcon);

        tooltip = Tooltip.attach(this, null);
    }

    /**
     * Icon edge length in pixels, shrunk to fit the padded content box.
     */
    public void setFallbackIconSize(int sizePx) {
        fallbackIconSizePx = Math.max(0, sizePx);
        requestLayout();
    }

    public void setFallbackIconAlpha(float alpha) {
        fallbackIconView.setAlpha(Math.clamp(alpha, 0f, 1f));
    }

    public void clear() {
        boundPusher = null;
        pusherHeadView.setPlayerSkinSupplier(null);
        tooltip.setText(null);
    }

    public void bind(@NonNull PusherInfo pusherInfo) {
        // A different requester drops the latched skin; a null sample alone must not.
        if (!pusherInfo.equals(boundPusher)) {
            boundPusher = pusherInfo;
            pusherHeadView.resetSkin();
        }
        pusherHeadView.setPlayerSkinSupplier(() -> {
            try {
                return PlayerInfoUtil.getPlayerSkin(PlayerInfoUtil.getPlayerInfoByUUID(pusherInfo.playerUUID()));
            } catch (Exception ignored) {
                return null;
            }
        });
        tooltip.setText(I18n.get(MusicHud.MOD_ID + ".text.pusherSource") + pusherInfo.playerName());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int contentW = right - left - getPaddingLeft() - getPaddingRight();
        int contentH = bottom - top - getPaddingTop() - getPaddingBottom();
        int box = Math.min(contentW, contentH);
        if (box <= 0) return;
        float scale = Math.min(1f, (float) fallbackIconSizePx / box);
        fallbackIconView.setPivotX(fallbackIconView.getWidth() / 2f);
        fallbackIconView.setPivotY(fallbackIconView.getHeight() / 2f);
        fallbackIconView.setScaleX(scale);
        fallbackIconView.setScaleY(scale);
    }

    private void updateFallbackIcon() {
        fallbackIconView.setVisibility(pusherHeadView.isAvatarReady() ? GONE : VISIBLE);
    }

    @Override
    protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
        tooltip.handleHover(event);
        return super.dispatchHoverEvent(event);
    }
}
