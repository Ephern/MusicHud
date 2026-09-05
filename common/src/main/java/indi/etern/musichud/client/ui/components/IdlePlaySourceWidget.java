package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
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
    private Unregister addRegister;
    private Unregister removeRegister;

    public IdlePlaySourceWidget(Context context, MusicCollection collection, int buttonSize) {
        super(context);
        this.collection = collection;
        this.modeState = layer.collection(collection, PlayMode.RANDOM);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(200);
        // Animate only this widget's own layout; never propagate to the ancestor hierarchy
        transition.setAnimateParentHierarchy(false);
        transition.disableTransitionType(LayoutTransition.APPEARING);
        transition.disableTransitionType(LayoutTransition.DISAPPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        transition.enableTransitionType(LayoutTransition.CHANGING);
        setLayoutTransition(transition);

        var backgroundFactory = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();

        toggleButton = new ToggleIdlePlaySourceButton(context);
        backgroundFactory.applyBackgroundTo(toggleButton);
        addView(toggleButton, new LayoutParams(buttonSize, buttonSize));

        cycleButton = new CycleIconButton(context);
        backgroundFactory.applyBackgroundTo(cycleButton);
        cycleButton.setVisibility(GONE);
        addView(cycleButton, new LayoutParams(buttonSize, buttonSize));

        buildCycleStates();
        bindToggle();
        syncCycleState();
    }

    private void buildCycleStates() {
        cycleModes.clear();
        cycleButton.getStates().clear();
        addCycleState(PlayMode.SEQUENTIAL, "/assets/music_hud/textures/gui/icons/repeat.png");
        addCycleState(PlayMode.RANDOM, "/assets/music_hud/textures/gui/icons/shuffle.png");
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
        if (current == null) {
            cycleButton.setVisibility(GONE);
            return;
        }
        int index = cycleModes.indexOf(current.getPlayMode());
        cycleButton.setVisibility(VISIBLE);
        cycleButton.apply(Math.max(index, 0));
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
