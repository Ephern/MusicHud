package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.atomic.AtomicInteger;

public class ToggleTrackLikeStateButton extends ToggleIconButton {
    private static final long TOGGLE_DEBOUNCE_DELAY_MILLIS = 800;
    private final AtomicInteger toggleVersion = new AtomicInteger(0);
    protected IMusicTrackState.IPlaylistSubState playlistSubState;
    private Unregister unregister = null;
    private Unregister loginStateUnregister;

    public ToggleTrackLikeStateButton(Context context) {
        super(context, new Appearance(
                () -> I18n.get(MusicHud.MOD_ID + ".button.toggleMusicLike.remove"),
                () -> I18n.get(MusicHud.MOD_ID + ".button.toggleMusicLike.add"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart_filled.png"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart.png")
        ));
        loginStateUnregister = LoginService.getInstance().addLoginStateListener(state ->
                MuiModApi.postToUiThread(this::applyLoginState));
    }

    @Override
    public boolean performClick() {
        boolean initialChecked = isChecked();
        boolean b = super.performClick();
        IMusicTrackState.IPlaylistSubState ps = playlistSubState;
        if (ps != null) {
            final boolean targetState = isChecked();
            final int version = toggleVersion.incrementAndGet();
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(TOGGLE_DEBOUNCE_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (toggleVersion.get() != version) return;
                if (targetState == initialChecked) return;
                if (targetState) {
                    ps.add();
                } else {
                    ps.remove();
                }
            });
        }
        return b;
    }

    public void bindMusicList(IMusicTrackState.IPlaylistSubState playlistSubState) {
        this.playlistSubState = playlistSubState;
        applyLoginState();
    }

    private void applyLoginState() {
        if (unregister != null) {
            unregister.unregister();
            unregister = null;
        }
        IMusicTrackState.IPlaylistSubState ps = playlistSubState;
        if (ps == null) {
            setEnabled(true);
            setTooltipText(isChecked() ? getTooltipTextOn() : getTooltipTextOff());
            return;
        }
        if (!LoginService.getInstance().isLogined()) {
            setEnabled(false);
            setTooltipText(I18n.get(MusicHud.MOD_ID + ".text.loginRequired"));
            return;
        }
        setEnabled(true);
        setTooltipText(isChecked() ? getTooltipTextOn() : getTooltipTextOff());
        ps.isContained().whenComplete((contains, throwable) -> {
            if (throwable != null) {
                throw new RuntimeException(throwable);
            }
            MuiModApi.postToUiThread(() -> {
                if (this.playlistSubState != ps || !LoginService.getInstance().isLogined()) return;
                setChecked(contains);
                unregister = ps.onOthersModify(checked -> MuiModApi.postToUiThread(() -> {
                    if (this.playlistSubState != ps || !LoginService.getInstance().isLogined()) return;
                    setChecked(checked);
                }));
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (unregister != null) {
            unregister.unregister();
            unregister = null;
        }
        if (loginStateUnregister != null) {
            loginStateUnregister.unregister();
            loginStateUnregister = null;
        }
    }
}
