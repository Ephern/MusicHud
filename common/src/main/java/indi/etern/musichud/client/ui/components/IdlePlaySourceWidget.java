package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
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
import indi.etern.musichud.client.utils.ui.Easing;
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
    private final int cycleCollapseSize = 0;
    private int cycleCurrentWidth = 0;
    private Animator cycleShowAnimator;
    private Unregister addRegister;
    private Unregister removeRegister;

    /** Animates the cycle button's LayoutParams width; the row reflows each frame. */
    private static final IntProperty<IdlePlaySourceWidget> CYCLE_WIDTH = new IntProperty<>("cycleWidth") {
        @Override
        public void setValue(IdlePlaySourceWidget widget, int width) {
            widget.setCycleWidth(width);
        }

        @Override
        public Integer get(IdlePlaySourceWidget widget) {
            return widget.cycleCurrentWidth;
        }
    };

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
        addView(cycleButton, new LayoutParams(cycleCollapseSize, buttonSize));
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

    /** Two-phase show/hide, fully self-driven. Show: width 0→target (150ms, QUAD) then
     *  alpha 0→1 (100ms, SINE). Hide: alpha 1→0 (100ms, SINE) then width target→0
     *  (150ms, QUAD). Restarting from the current width/alpha keeps rapid toggles smooth;
     *  a canceled phase never chains into the next one. */
    private void setCycleShown(boolean shown) {
        cancelCycleShowAnimator();
        ObjectAnimator first;
        ObjectAnimator second;
        if (shown) {
            if (cycleCurrentWidth == cycleTargetSize && cycleButton.getAlpha() == 1f) {
                return;
            }
            first = ObjectAnimator.ofInt(this, CYCLE_WIDTH, cycleCurrentWidth, cycleTargetSize);
            first.setDuration(150);
            first.setInterpolator(Easing.EASE_IN_OUT_CUBIC);
            second = ObjectAnimator.ofFloat(cycleButton, View.ALPHA, cycleButton.getAlpha(), 1f);
            second.setDuration(100);
            second.setInterpolator(Easing.EASE_IN_OUT_SINE);
        } else {
            if (cycleCurrentWidth == cycleCollapseSize && cycleButton.getAlpha() == 0f) {
                return;
            }
            first = ObjectAnimator.ofFloat(cycleButton, View.ALPHA, cycleButton.getAlpha(), 0f);
            first.setDuration(100);
            first.setInterpolator(Easing.EASE_IN_OUT_SINE);
            second = ObjectAnimator.ofInt(this, CYCLE_WIDTH, cycleCurrentWidth, cycleCollapseSize);
            second.setDuration(150);
            second.setInterpolator(Easing.EASE_IN_OUT_CUBIC);
        }
        second.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (cycleShowAnimator == animation) {
                    cycleShowAnimator = null;
                }
            }
        });
        first.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // A canceled phase must not chain into the next one: cancelCycleShowAnimator
                // nulls the field before canceling, so a stale end callback is ignored
                if (cycleShowAnimator != animation) {
                    return;
                }
                cycleShowAnimator = second;
                second.start();
            }
        });
        cycleShowAnimator = first;
        first.start();
    }

    private void cancelCycleShowAnimator() {
        if (cycleShowAnimator != null) {
            Animator active = cycleShowAnimator;
            cycleShowAnimator = null;
            active.cancel();
        }
    }

    private void setCycleWidth(int width) {
        cycleCurrentWidth = width;
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
        cancelCycleShowAnimator();
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
