package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import lombok.NonNull;
import net.minecraft.client.multiplayer.PlayerInfo;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PusherWidget extends LinearLayout {
    private final PlayerHeadView pusherHeadView;
    private final TextView pusherText;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();

    public PusherWidget(Context context) {
        super(context);
        pusherHeadView = new PlayerHeadView(context);
        LayoutParams layoutParams = new LayoutParams(dp(18), dp(18));
        layoutParams.setMargins(0, 0, dp(4), 0);
        addView(pusherHeadView, layoutParams);
        pusherText = new TextView(context);
        pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        pusherText.setSingleLine();
        LinearLayout.LayoutParams pusherTextParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        pusherTextParams.gravity = Gravity.CENTER_VERTICAL;
        addView(pusherText, pusherTextParams);

        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        setLayoutTransition(layoutTransition);
    }

    public void clear() {
        pusherHeadView.setPlayerSkinSupplier(null);
        pusherText.setText("");
    }

    public void bind(@NonNull PusherInfo pusherInfo) {
        pusherHeadView.setPlayerSkinSupplier(() -> {
            try {
                PlayerInfo pusherPlayerInfo = nowPlayingInfo.getPusherPlayerInfo();
                var skin = PlayerInfoUtil.getPlayerSkin(pusherPlayerInfo);
                pusherHeadView.setVisibility(skin == null ? GONE : VISIBLE);
                return skin;
            } catch (Exception ignored) {
            }
            pusherHeadView.setVisibility(GONE);
            return null;
        });
        pusherText.setText(pusherInfo.getPlayerName());
    }
}
