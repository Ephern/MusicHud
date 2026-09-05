package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PlaylistSpecialType;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class IdlePlaySourceWidget extends LinearLayout {
    private final IIdlePlaySourceLayerState layer = MusicService.getInstance().getIdlePlaySourceState().local();
    private final IIdlePlaySourceCollectionState modeState;
    private final MusicCollection collection;
    private final ToggleIdlePlaySourceButton toggleButton;
    private final CycleIconButton cycleButton;
    private final List<PlayMode> cycleModes = new ArrayList<>();
    private final int cycleTargetSize;
    private ValueAnimator cycleShowAnimator;
    private Unregister addRegister;
    private Unregister removeRegister;

    public IdlePlaySourceWidget(Context context, MusicCollection collection, int buttonSize) {
        super(context);
        this.collection = collection;
        this.modeState = layer.collection(collection, PlayMode.RANDOM);
        this.cycleTargetSize = buttonSize;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        var backgroundFactory = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();

        toggleButton = new ToggleIdlePlaySourceButton(context);
        backgroundFactory.applyBackgroundTo(toggleButton);
        addView(toggleButton, new LayoutParams(buttonSize, buttonSize));

        // Shown/hidden purely by animated width+alpha (no GONE/VISIBLE flips, no
        // LayoutTransition): starts collapsed and fully transparent, which is visually
        // identical to GONE but keeps the animation fully under our control
        cycleButton = new CycleIconButton(context);
        backgroundFactory.applyBackgroundTo(cycleButton);
        addView(cycleButton, new LayoutParams(0, buttonSize));
        cycleButton.setAlpha(0f);

        buildCycleStates();
        bindToggle();
    }

    private void buildCycleStates() {
        cycleModes.clear();
        cycleButton.getStates().clear();
        addCycleState(PlayMode.RANDOM, "/assets/music_hud/textures/gui/icons/shuffle.png");
        addCycleState(PlayMode.SEQUENTIAL, "/assets/music_hud/textures/gui/icons/repeat.png");
        if (supportsIntelligent()) {
            addCycleState(PlayMode.INTELLIGENT, "/assets/music_hud/textures/gui/icons/heart_pulse.png");
        }
    }

    private boolean supportsIntelligent() {
        return collection instanceof Playlist playlist
                && playlist.getSpecialType() == PlaylistSpecialType.LIKE_LIST
                && playlist.getCreator().equals(Profile.getCurrent());
    }

    private void addCycleState(PlayMode playMode, String iconPath) {
        cycleModes.add(playMode);
        cycleButton.getStates().add(new CycleIconButton.State(
                () -> I18n.get(MusicHud.MOD_ID + ".button.idlePlaySourceMode." + playMode.name()),
                () -> ImageUtils.getImageFromResource(iconPath),
                () -> modeState.updateMode(playMode),
                () -> {
                }
        ));
    }

    private void bindToggle() {
        toggleButton.bindState(new IIdlePlaySourceCollectionState() {
            @Override
            public long getCollectionId() {
                return collection.getId();
            }

            @Override
            public boolean isContained() {
                return isAttachedToLayer();
            }

            @Override
            public void add() {
                if (isAttachedToLayer()) {
                    return;
                }
                layer.add(IdlePlaySource.of(collection, PlayMode.RANDOM));
            }

            @Override
            public void remove() {
                IdlePlaySource current = currentSource();
                if (current != null) {
                    layer.remove(current);
                }
            }

            @Override
            public void updateMode(PlayMode playMode) {
                modeState.updateMode(playMode);
            }

            @Override
            public Unregister onOthersModify(Consumer<Boolean> listener) {
                return layer.onChange(c -> {
                    if (c.getId() == collection.getId() && c.getType().isInstance(collection)) {
                        listener.accept(isAttachedToLayer());
                    }
                });
            }
        });
    }

    private boolean isAttachedToLayer() {
        return layer.getSources().stream()
                .anyMatch(c -> c.getId() == collection.getId() && c.getType().isInstance(collection));
    }

    private IdlePlaySource currentSource() {
        return layer.getSources().stream()
                .filter(c -> c.getId() == collection.getId() && c.getType().isInstance(collection))
                .findFirst().orElse(null);
    }

    private void syncCycleState() {
        IdlePlaySource current = currentSource();
        if (current != null) {
            int index = cycleModes.indexOf(current.getPlayMode());
            // Swap the drawable while collapsed/transparent so it never invalidates a
            // fully visible view mid-layout
            cycleButton.apply(Math.max(index, 0));
        }
        setCycleShown(current != null);
    }

    /** Animates the cycle button between collapsed (width 0, alpha 0) and expanded
     *  (width {@link #cycleTargetSize}, alpha 1). Restarting from the current width/alpha
     *  keeps rapid toggles smooth; the animation is fully self-driven so no library
     *  transition timing can flash a wrong first frame. */
    private void setCycleShown(boolean shown) {
        int targetWidth = shown ? cycleTargetSize : 0;
        float targetAlpha = shown ? 1f : 0f;
        if (cycleShowAnimator != null) {
            cycleShowAnimator.cancel();
            cycleShowAnimator = null;
        }
        int startWidth = cycleButton.getWidth();
        float startAlpha = cycleButton.getAlpha();
        if (startWidth == targetWidth && startAlpha == targetAlpha) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        animator.addUpdateListener(a -> {
            float fraction = (Float) a.getAnimatedValue();
            setCycleWidth(Math.round(startWidth + (targetWidth - startWidth) * fraction));
            cycleButton.setAlpha(startAlpha + (targetAlpha - startAlpha) * fraction);
        });
        cycleShowAnimator = animator;
        animator.start();
    }

    private void setCycleWidth(int width) {
        LayoutParams lp = (LayoutParams) cycleButton.getLayoutParams();
        if (lp.width != width) {
            lp.width = width;
            cycleButton.setLayoutParams(lp);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addRegister = layer.onAdd(c -> {
            if (c.getId() == collection.getId() && c.getType().isInstance(collection)) {
                postSync();
            }
        });
        removeRegister = layer.onRemove(c -> {
            if (c.getId() == collection.getId() && c.getType().isInstance(collection)) {
                postSync();
            }
        });
        syncCycleState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (cycleShowAnimator != null) {
            cycleShowAnimator.cancel();
            cycleShowAnimator = null;
        }
        if (addRegister != null) {
            addRegister.unregister();
            addRegister = null;
        }
        if (removeRegister != null) {
            removeRegister.unregister();
            removeRegister = null;
        }
    }

    private void postSync() {
        MuiModApi.postToUiThread(this::syncCycleState);
    }
}
