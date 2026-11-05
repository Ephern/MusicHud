package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Compact requester marker: the avatar alone, the player name lives in the tooltip.
 */
public class PusherWidget extends FrameLayout {
    private final PlayerHeadView pusherHeadView;
    private final Tooltip tooltip;

    public PusherWidget(Context context) {
        super(context);
        pusherHeadView = new PlayerHeadView(context);
        addView(pusherHeadView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        tooltip = Tooltip.attach(this, null);
    }

    public void clear() {
        pusherHeadView.setPlayerSkinSupplier(null);
        tooltip.setText(null);
    }

    public void bind(@NonNull PusherInfo pusherInfo) {
        pusherHeadView.setPlayerSkinSupplier(() -> {
            try {
                var skin = PlayerInfoUtil.getPlayerSkin(PlayerInfoUtil.getPlayerInfoByUUID(pusherInfo.playerUUID()));
                pusherHeadView.setVisibility(skin == null ? GONE : VISIBLE);
                return skin;
            } catch (Exception ignored) {
            }
            pusherHeadView.setVisibility(GONE);
            return null;
        });
        tooltip.setText(I18n.get(MusicHud.MOD_ID + ".text.pusherSource") + pusherInfo.playerName());
    }

    @Override
    protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
        tooltip.handleHover(event);
        return super.dispatchHoverEvent(event);
    }
}
