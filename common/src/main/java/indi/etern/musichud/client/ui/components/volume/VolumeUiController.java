package indi.etern.musichud.client.ui.components.volume;

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.interfaces.ClientConfig;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Owns the single volume flyout shared by every media control bar. The flyout lives in the screen's
 * overlay layer, so it can extend beyond the bar and the page it belongs to; the bars only
 * contribute their icon as an anchor.
 *
 * <p>Opening is hover-driven with a short dwell, closing is delayed so the pointer can travel
 * between the icon and the panel, and the panel stays open while the slider is being dragged.</p>
 */
public final class VolumeUiController {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final int OPEN_DELAY_MS = 100;
    private static final int CLOSE_DELAY_MS = 280;
    /** Coalesces a click that arrives twice for the same press; mute is a discrete action. */
    private static final long MUTE_COALESCE_MS = 80;
    private static final VolumeUiController INSTANCE = new VolumeUiController();

    private final Set<VolumeIconButton> buttons =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Runnable openRunnable = this::open;
    private final Runnable closeRunnable = this::close;

    private FrameLayout overlayLayer;
    private VolumePanel panel;
    private VolumeIconButton activeAnchor;
    private VolumeIconButton suppressedAnchor;
    private boolean openPending;
    private boolean iconHovered;
    private boolean panelHovered;
    /** Authoritative UI state; the config is written from it, not the other way around. */
    private int level;
    private boolean muted;
    private long lastMuteToggleTime = Long.MIN_VALUE / 2;

    private final VolumeIconButton.Listener iconListener = new VolumeIconButton.Listener() {
        @Override
        public void onToggleMute(VolumeIconButton button) {
            // A click only toggles mute.
            cancelOpen();
            toggleMute();
        }

        @Override
        public void onScroll(VolumeIconButton button, MotionEvent event) {
            activeAnchor = button;
            cancelOpen();
            if (!isPanelShowing()) {
                open();
            }
            adjust(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
        }

        @Override
        public void onHover(VolumeIconButton button, MotionEvent event) {
            handleIconHover(button, event);
        }
    };

    private final VolumePanel.Listener panelListener = new VolumePanel.Listener() {
        @Override
        public void onValueChanged(int value) {
            cancelClose();
            setVolume(value, false);
        }

        @Override
        public void onTrackingStopped() {
            saveVolume();
            if (!panelHovered && !iconHovered) {
                scheduleClose();
            }
        }

        @Override
        public void onScroll(float notches) {
            cancelClose();
            adjust(notches);
        }

        @Override
        public void onHoverChanged(boolean hovered) {
            panelHovered = hovered;
            if (hovered) {
                cancelClose();
            } else if ((panel == null || !panel.isDragging()) && !iconHovered) {
                // Dispatch reaches both sides in one pass: hovering the icon is not "leaving".
                scheduleClose();
            }
        }
    };

    private VolumeUiController() {
        level = Math.clamp(clientConfig.getSoundVolume(), 0, 100);
        muted = clientConfig.getMuted();
    }

    public static VolumeUiController getInstance() {
        return INSTANCE;
    }

    /** The overlay layer is the screen root; the panel is its last child so it draws on top. */
    public void attachLayer(FrameLayout layer) {
        overlayLayer = layer;
        if (panel == null) {
            panel = new VolumePanel(layer.getContext());
            panel.setListener(panelListener);
            layer.addView(panel, new FrameLayout.LayoutParams(0, 0));
        }
        refreshAll();
    }

    public void detachLayer() {
        cancelOpen();
        cancelClose();
        setSuppressedAnchor(null);
        if (panel != null && panel.getParent() instanceof ViewGroup parent) {
            parent.removeView(panel);
        }
        panel = null;
        overlayLayer = null;
        activeAnchor = null;
        iconHovered = false;
        panelHovered = false;
    }

    public void register(VolumeIconButton button) {
        buttons.add(button);
        button.setListener(iconListener);
        button.setLevel(level, muted);
    }

    public void unregister(VolumeIconButton button) {
        buttons.remove(button);
        button.setListener(null);
        if (suppressedAnchor == button) {
            setSuppressedAnchor(null);
        }
        if (activeAnchor == button) {
            cancelOpen();
            activeAnchor = null;
            iconHovered = false;
            hidePanel();
        }
    }

    /** Closes the flyout immediately, e.g. when its page or the panel goes away. */
    public void dismiss() {
        cancelOpen();
        cancelClose();
        iconHovered = false;
        hidePanel();
    }

    /** Re-reads the level from the config; safe to call from any thread. */
    public void refresh() {
        MuiModApi.postToUiThread(this::refreshAll);
    }

    private void handleIconHover(VolumeIconButton button, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER -> {
                activeAnchor = button;
                iconHovered = true;
                cancelClose();
                if (isPanelShowing()) {
                    if (panel != null) {
                        panel.refreshAnchor();
                    }
                } else if (!button.isPressed()) {
                    scheduleOpen();
                }
            }
            case MotionEvent.ACTION_HOVER_MOVE -> {
                activeAnchor = button;
                iconHovered = true;
                if (isPanelShowing()) {
                    // A relayout elsewhere can move the icon under a stationary pointer.
                    if (panel != null) {
                        panel.refreshAnchor();
                    }
                } else if (!button.isPressed()) {
                    scheduleOpen();
                }
            }
            case MotionEvent.ACTION_HOVER_EXIT -> {
                iconHovered = false;
                cancelOpen();
                // The flyout is dispatched before the icon in the same pass, so an exit aimed at
                // the flyout must not arm a close behind its back.
                if (!panelHovered) {
                    scheduleClose();
                }
            }
            default -> {
            }
        }
    }

    private boolean isPanelShowing() {
        return panel != null && panel.isShowing();
    }

    private void scheduleOpen() {
        if (overlayLayer == null || openPending) {
            return;
        }
        openPending = true;
        overlayLayer.postDelayed(openRunnable, OPEN_DELAY_MS);
    }

    private void cancelOpen() {
        openPending = false;
        if (overlayLayer != null) {
            overlayLayer.removeCallbacks(openRunnable);
        }
    }

    private void scheduleClose() {
        if (overlayLayer == null) {
            return;
        }
        overlayLayer.removeCallbacks(closeRunnable);
        overlayLayer.postDelayed(closeRunnable, CLOSE_DELAY_MS);
    }

    private void cancelClose() {
        if (overlayLayer != null) {
            overlayLayer.removeCallbacks(closeRunnable);
        }
    }

    private void open() {
        openPending = false;
        VolumeIconButton anchor = activeAnchor;
        if (panel == null || overlayLayer == null || anchor == null
                || !anchor.isShown() || anchor.isPressed()) {
            return;
        }
        applyVolumeState();
        if (panel.showFor(anchor, overlayLayer)) {
            panelHovered = false;
            // The flyout is the readout now; the tooltip would land on top of it.
            setSuppressedAnchor(anchor);
        }
    }

    private void close() {
        if (panel != null && panel.isDragging()) {
            return;
        }
        hidePanel();
    }

    private void hidePanel() {
        panelHovered = false;
        setSuppressedAnchor(null);
        if (panel != null) {
            panel.hide();
        }
    }

    private void setSuppressedAnchor(VolumeIconButton target) {
        if (suppressedAnchor == target) {
            return;
        }
        if (suppressedAnchor != null) {
            suppressedAnchor.setTooltipSuppressed(false);
        }
        suppressedAnchor = target;
        if (target != null) {
            target.setTooltipSuppressed(true);
        }
    }

    private void toggleMute() {
        long now = System.currentTimeMillis();
        if (now - lastMuteToggleTime < MUTE_COALESCE_MS) {
            return;
        }
        lastMuteToggleTime = now;
        muted = !muted;
        clientConfig.setMuted(muted);
        applyVolumeState();
        saveVolume();
        MainFragment.refreshCoverScale();
    }

    private void adjust(float notches) {
        if (notches == 0f) {
            return;
        }
        int step = Math.max(1, clientConfig.getSoundVolumeInterval());
        int notchesToApply = Math.max(1, Math.round(Math.abs(notches)));
        int delta = (int) Math.signum(notches) * notchesToApply * step;
        setVolume(level + delta, true);
    }

    /**
     * Mutes at zero and unmutes above it, so the slider's position always matches the stored
     * level. A drag only saves on release; wheel notches save immediately.
     */
    private void setVolume(int newLevel, boolean save) {
        int target = Math.clamp(newLevel, 0, 100);
        boolean mutedBefore = muted;
        if (level == target && !mutedBefore) {
            return;
        }
        level = target;
        muted = target == 0;
        clientConfig.forceSetSoundVolume(target);
        applyVolumeState();
        if (mutedBefore != muted) {
            MainFragment.refreshCoverScale();
        }
        if (save) {
            saveVolume();
        }
    }

    /**
     * Written immediately, never deferred: the config port reloads the spec from disk shortly
     * after a save, so a deferred write lets that reload restore the value we just replaced.
     */
    private void saveVolume() {
        clientConfig.save();
    }

    private void refreshAll() {
        level = Math.clamp(clientConfig.getSoundVolume(), 0, 100);
        muted = clientConfig.getMuted();
        applyVolumeState();
    }

    /** Applies the controller's level and mute state to every icon and the flyout. */
    private void applyVolumeState() {
        for (VolumeIconButton button : buttons) {
            button.setLevel(level, muted);
        }
        if (panel != null) {
            panel.setLevel(level, muted);
        }
    }
}
