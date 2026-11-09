package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.result.ActionResult;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.PlaybackTask;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.components.buttons.VoteSkipButton;
import indi.etern.musichud.client.ui.components.cards.MusicCollectionCard;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.LayoutTransitionReflow;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.client.utils.ui.SpringValue;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.Unregister;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SidebarView extends LinearLayout {

    private final int cardPadding = dp(4);
    private LinearLayout nextToPlayRow;
    private LinearLayout queueRow;

    public enum Mode {
        PLAYER,
        LIBRARY
    }

    private static final int SIDE_WIDTH_DP = 280;
    private static final int SIDE_MARGIN_DP = 12;
    private static final int PANEL_ANIMATION_DURATION = 300;
    private static final SpringInterpolator PANEL_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) PANEL_ANIMATION_DURATION / 1000, 1);
    private static final float CARD_COVER_MIN_SCALE = 0.8f;
    private static final int CARD_ANIM_DURATION_MS = 350;
    private static final SpringInterpolator MUSIC_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) CARD_ANIM_DURATION_MS / 1000, 1);
    private static final DateTimeFormatter FORMATTER_WITH_HOUR = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FORMATTER_WITHOUT_HOUR = DateTimeFormatter.ofPattern("mm:ss");
    private static final int IDLE_SOURCE_LOAD_ATTEMPTS = 3;
    private static final SpringInterpolator NEXT_TO_PLAY_INTERPOLATOR = new SpringInterpolator(0.25f, 1);
    private static final int NEXT_TO_PLAY_ANIM_DURATION_MS =
            Math.round(NEXT_TO_PLAY_INTERPOLATOR.getDuration() * 1000);
    private static final float NEXT_TO_PLAY_MIN_SCALE = 0.95f;
    private static final String ICON_SOURCE = "/assets/music_hud/textures/gui/icons/square_arrow_out_up_right.png";
    private static final int ACTION_ICON_DP = 16;
    private static final int PUSHER_PADDING_DP = 8;

    // Morph springs use normalized positions: 0 = expanded player, 1 = compact library header.
    private static final float MORPH_TIME_SCALE = 1f;
    private static final float MORPH_DAMPING = 1f;
    private static final float EXPAND_TEXT_X_RESPONSE = 0.32f;
    private static final float EXPAND_TEXT_Y_RESPONSE = 0.28f;
    private static final float EXPAND_COVER_RESPONSE = 0.38f;
    private static final float EXPAND_SLIDE_RESPONSE = 0.24f;
    private static final float COLLAPSE_COVER_RESPONSE = 0.2f;
    private static final float COLLAPSE_TEXT_X_RESPONSE = 0.26f;
    private static final float COLLAPSE_TEXT_Y_RESPONSE = 0.38f;
    private static final float COLLAPSE_SLIDE_RESPONSE = 0.24f;
    private static final float MORPH_SETTLE_EPSILON = 1e-3f;
    private static final float MORPH_SETTLE_VELOCITY = 0.05f;

    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final MusicService musicService = MusicService.getInstance();
    private static final Logger LOGGER = MusicHud.getLogger(SidebarView.class);
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final AtomicInteger progressUpdaterToken = new AtomicInteger(0);

    private final int sideWidth;

    @Getter
    private boolean visible;
    private AnimatorSet panelAnimator;
    @Getter
    private Mode mode = Mode.PLAYER;
    private final CollapsibleHeader headerContainer;
    private final LowerPageViewport lowerViewport;
    private LinearLayout sideContent;
    private LayoutTransition sideContentTransition;
    private final SpringValue coverSpring = new SpringValue(0.3f, 1f);
    private final SpringValue textXSpring = new SpringValue(0.3f, 1f);
    private final SpringValue textYSpring = new SpringValue(0.3f, 1f);
    private final SpringValue slideSpring = new SpringValue(0.3f, 1f);
    private final Choreographer.FrameCallback morphFrameCallback =
            (choreographer, frameTimeNanos) -> advanceMorph(frameTimeNanos);
    private boolean morphRunning;
    private boolean modeAnimating;
    private ValueAnimator headerAnimator;
    private float headerVisibleFraction = 1f;
    private boolean playerVisible = true;
    /** Queued until the sliding-out panel leaves the screen, so the header never reflows mid-move. */
    private boolean pendingPlayerGone;
    private ScrollView libraryPage;
    private StaggeredLyricScrollView lyricsScrollView;
    private MusicInfoCard activeCard;
    private MusicInfoCard stagedCard;
    private LinearLayout buttonsLayout;
    private ToggleTrackLikeStateButton likeButton;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private VoteSkipButton skipCurrentButton;
    private PusherWidget pusherWidget;
    private ImageButton sourceButton;
    private AnimatorSet cardAnimator;
    private AnimatorSet coverScaleAnimator;
    private boolean cardSwitching;
    private Traceable<MusicDetail> pendingSwitch;
    private MusicDetail displayedDetail;
    private Traceable<MusicDetail> displayedTrace;
    private long progressUpdaterStartTime;
    private long progressTextUpdateCount;

    private record CardKey(boolean local, long id, Class<?> type) {
        static CardKey of(boolean local, IdlePlaySource idlePlaySource) {
            return new CardKey(local, idlePlaySource.getId(), idlePlaySource.getType());
        }
    }

    private final Set<IdlePlaySource> serverIdlePlaySources =
            musicService.getIdlePlaySourceState().external().getSources();
    private final Set<IdlePlaySource> clientIdlePlaySources =
            musicService.getIdlePlaySourceState().local().getSources();
    private final Map<CardKey, MusicCollectionCard> idlePlaySourceCardMap = new ConcurrentHashMap<>();
    private MusicTrackItem nextToPlayItem;
    private AnimatorSet nextToPlayAnimator;
    private Traceable<MusicDetail> pendingNextToPlay;
    private ImageButton rotateNextToPlayButton;
    private LinearLayout playQueueListView;
    private LinearLayout clientIdlePlaySourceView;
    private LinearLayout serverIdlePlaySourceView;
    private LinearLayout clientIdlePlaySourceCardsList;
    private LinearLayout serverIdlePlaySourceCardsList;
    private LocalPlayer localPlayer = Minecraft.getInstance().player;
    private Consumer<QueueItem> musicQueuePushListener;
    private BiConsumer<Integer, QueueItem> musicQueueRemoveListener;
    private Unregister localAddRegister;
    private Unregister localRemoveRegister;
    private Unregister serverAddRegister;
    private Unregister serverRemoveRegister;
    private boolean audioListenerRegistered;

    private final Consumer<StreamAudioPlayer.Status> statusListener =
            status -> MuiModApi.postToUiThread(() -> setAudioStatus(status));

    private final Consumer<IdlePlaySource> localAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            CardKey key = CardKey.of(true, collection);
            if (!idlePlaySourceCardMap.containsKey(key)) {
                addIdlePlaySourceTo(collection, clientIdlePlaySourceCardsList, true);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private final Consumer<IdlePlaySource> localRemoveListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            if (hasSourceWithKey(clientIdlePlaySources, collection)) {
                return;
            }
            MusicCollectionCard view = idlePlaySourceCardMap.remove(CardKey.of(true, collection));
            if (view != null) {
                clientIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private final Consumer<IdlePlaySource> serverRemoveListener = idlePlaySource -> {
        MuiModApi.postToUiThread(() -> {
            if (hasSourceWithKey(serverIdlePlaySources, idlePlaySource)) {
                return;
            }
            MusicCollectionCard view = idlePlaySourceCardMap.remove(CardKey.of(false, idlePlaySource));
            if (view != null) {
                serverIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };
    private final Consumer<IdlePlaySource> serverAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            CardKey key = CardKey.of(false, collection);
            if ((localPlayer != null && !collection.getPusherInfo().playerUUID().equals(localPlayer.getUUID()))
                    && !idlePlaySourceCardMap.containsKey(key)) {
                addIdlePlaySourceTo(collection, serverIdlePlaySourceCardsList, false);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };

    public SidebarView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        sideWidth = dp(SIDE_WIDTH_DP + SIDE_MARGIN_DP * 2);
        PanelStyle.applyDynamically(this, dp(16),
                new InsetBackgroundFactory.Padding(0, 0, 0, 0),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        headerContainer = new CollapsibleHeader(context);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        headerParams.setMargins(dp(SIDE_MARGIN_DP), 0, dp(SIDE_MARGIN_DP), 0);
        addView(headerContainer, headerParams);

        lowerViewport = new LowerPageViewport(context);
        LinearLayout.LayoutParams lowerParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
        lowerParams.setMargins(0, 0, 0, dp(SIDE_MARGIN_DP));
        addView(lowerViewport, lowerParams);

        buildPlayerHeader(context);
        buildLibraryPage(context);
        lowerViewport.setPages(lyricsScrollView, libraryPage);
        settleMode();
        refreshLibrary();
    }

    /**
     * Header visibility is independent from the player/library mode: Home hides the whole player
     * while the lower area stays on the library.
     *
     * <p>The header never animates out. With {@code withPanelTransition} it stays intact while the
     * panel slides away and is dropped once the panel is gone; a reveal animates only when the
     * panel is already open and stays open, otherwise it is placed before the panel slides in.</p>
     */
    public void setPlayerVisible(boolean visible, boolean withPanelTransition) {
        boolean unchanged = playerVisible == visible;
        playerVisible = visible;
        if (!visible) {
            updateProgressUpdaterForMode();
            if (withPanelTransition && (this.visible || panelAnimator != null)) {
                pendingPlayerGone = true;
                cancelHeaderAnimator();
                // Freeze a partially revealed header so it does not reflow while the panel moves.
                if (headerContainer != null && headerContainer.getVisibility() == VISIBLE) {
                    applyHeaderVisibleFraction(1f);
                }
                releaseLowerHold();
                return;
            }
            pendingPlayerGone = false;
            cancelHeaderAnimator();
            hidePlayerHeader();
            releaseLowerHold();
            return;
        }
        pendingPlayerGone = false;
        if (headerAnimator != null) {
            updateProgressUpdaterForMode();
            return;
        }
        boolean animateInPlace = withPanelTransition && this.visible
                && panelAnimator == null && getHeight() > 0;
        if (!animateInPlace) {
            cancelHeaderAnimator();
            showPlayerHeader();
            releaseLowerHold();
            updateProgressUpdaterForMode();
            return;
        }
        if (unchanged && Math.abs(headerVisibleFraction - 1f) < 1e-4f) {
            updateProgressUpdaterForMode();
            return;
        }
        cancelHeaderAnimator();
        holdLowerSizes();
        updateProgressUpdaterForMode();
        headerContainer.setVisibility(VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(headerVisibleFraction, 1f);
        animator.setDuration(PANEL_ANIMATION_DURATION);
        animator.setInterpolator(PANEL_SWITCH_INTERPOLATOR);
        animator.addUpdateListener(a -> applyHeaderVisibleFraction((Float) a.getAnimatedValue()));
        headerAnimator = animator;
        animator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (headerAnimator != animator) {
                    return;
                }
                headerAnimator = null;
                applyHeaderVisibleFraction(1f);
                releaseLowerHold();
                updateProgressUpdaterForMode();
            }
        });
        animator.start();
    }

    private void hidePlayerHeader() {
        if (headerContainer == null) {
            return;
        }
        applyHeaderVisibleFraction(0f);
        headerContainer.setVisibility(GONE);
    }

    private void showPlayerHeader() {
        if (headerContainer == null) {
            return;
        }
        headerContainer.setVisibility(VISIBLE);
        applyHeaderVisibleFraction(1f);
    }

    private void applyPendingPlayerGone() {
        if (!pendingPlayerGone) {
            return;
        }
        pendingPlayerGone = false;
        // Leaving Home may have revived the player before the panel finished hiding.
        if (playerVisible) {
            return;
        }
        cancelHeaderAnimator();
        hidePlayerHeader();
        releaseLowerHold();
    }

    private void holdLowerSizes() {
        if (!modeAnimating && lowerViewport != null) {
            lowerViewport.holdSizes();
        }
    }

    private void releaseLowerHold() {
        if (!modeAnimating && lowerViewport != null) {
            lowerViewport.releaseSizes();
        }
    }

    private void applyHeaderVisibleFraction(float fraction) {
        headerVisibleFraction = Math.clamp(fraction, 0f, 1f);
        if (headerContainer != null) {
            headerContainer.setVisibleFraction(headerVisibleFraction);
        }
    }

    private void cancelHeaderAnimator() {
        ValueAnimator animator = headerAnimator;
        headerAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    /** Width the docked panel takes in the parent layout (margins + padding + content). */
    public int getDockedWidth() {
        return dp(SIDE_MARGIN_DP) * 2 + getPanelWidth();
    }

    /** Panel width including its own horizontal padding, excluding the outer margins. */
    public int getPanelWidth() {
        return getPaddingLeft() + getPaddingRight() + sideWidth;
    }

    public void show(boolean animate) {
        if (visible) {
            return;
        }
        pendingPlayerGone = false;
        visible = true;
        cancelPanelAnimator();
        setVisibility(VISIBLE);
        if (!animate) {
            setTranslationX(0f);
            setAlpha(1f);
        } else {
            float distance = panelSlideDistance();
            setTranslationX(-distance);
            setAlpha(0f);
            ObjectAnimator slideIn = ObjectAnimator.ofFloat(this, View.TRANSLATION_X, -distance, 0f);
            slideIn.setDuration(PANEL_ANIMATION_DURATION);
            slideIn.setInterpolator(PANEL_SWITCH_INTERPOLATOR);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f);
            fadeIn.setDuration(PANEL_ANIMATION_DURATION);
            fadeIn.setInterpolator(PANEL_SWITCH_INTERPOLATOR);
            AnimatorSet shown = new AnimatorSet();
            shown.playTogether(slideIn, fadeIn);
            shown.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    // A finished slide-in must not keep looking like a still-running panel.
                    if (panelAnimator == shown) {
                        panelAnimator = null;
                    }
                }
            });
            panelAnimator = shown;
            shown.start();
        }
        if (lyricsScrollView != null && lyricsScrollView.getVisibility() == VISIBLE) {
            lyricsScrollView.reinitialize();
        }
    }

    public void hide(boolean animate) {
        if (!visible) {
            if (panelAnimator == null) {
                applyPendingPlayerGone();
            }
            return;
        }
        if (!animate || getWidth() <= 0) {
            snapHidden();
            return;
        }
        visible = false;
        cancelPanelAnimator();
        if (lyricsScrollView != null) {
            lyricsScrollView.suspendLyricFollowingAndHide();
        }
        float distance = getWidth();
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(this, View.TRANSLATION_X,
                getTranslationX(), -distance);
        slideOut.setDuration(PANEL_ANIMATION_DURATION);
        slideOut.setInterpolator(Easing.EASE_IN_QUINT);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(this, View.ALPHA, getAlpha(), 0f);
        fadeOut.setDuration(PANEL_ANIMATION_DURATION);
        fadeOut.setInterpolator(Easing.EASE_IN_OUT_CUBIC);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(slideOut, fadeOut);
        panelAnimator = set;
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // Stale callbacks must not hide a panel that a newer show() already revived.
                if (panelAnimator != set) {
                    return;
                }
                panelAnimator = null;
                setVisibility(GONE);
                setTranslationX(0f);
                setAlpha(1f);
                applyPendingPlayerGone();
            }
        });
        set.start();
    }

    private void snapHidden() {
        visible = false;
        if (lyricsScrollView != null) {
            lyricsScrollView.suspendLyricFollowingAndHide();
        }
        cancelPanelAnimator();
        setTranslationX(0f);
        setAlpha(1f);
        setVisibility(GONE);
        applyPendingPlayerGone();
    }

    private void cancelPanelAnimator() {
        if (panelAnimator != null) {
            AnimatorSet running = panelAnimator;
            panelAnimator = null;
            running.cancel();
        }
    }

    private float panelSlideDistance() {
        int width = getWidth();
        return width > 0 ? width : getPanelWidth();
    }

    public void setMode(Mode mode) {
        setMode(mode, false);
    }

    /**
     * Switches the lower half between lyrics and library. The player header stays in place and
     * only morphs to its compact layout while the two lower pages slide.
     */
    public void setMode(Mode mode, boolean animate) {
        boolean sameTarget = this.mode == mode;
        this.mode = mode;
        if (lowerViewport == null) {
            return;
        }
        // Morphing is only meaningful in a settled, on-screen panel; anything else settles so the
        // panel opens straight into its final layout.
        if (!animate || !visible || panelAnimator != null || getHeight() <= 0) {
            settleMode();
            return;
        }
        float target = targetProgress();
        if (sameTarget && !morphRunning && springsAt(target)) {
            return;
        }
        startMorphAnimation(target);
    }

    private float targetProgress() {
        return mode == Mode.LIBRARY ? 1f : 0f;
    }

    private boolean springsAt(float target) {
        return isAtTarget(coverSpring, target)
                && isAtTarget(textXSpring, target)
                && isAtTarget(textYSpring, target)
                && isAtTarget(slideSpring, target);
    }

    private static boolean isAtTarget(SpringValue spring, float target) {
        return Math.abs(spring.getValue() - target) < MORPH_SETTLE_EPSILON
                && Math.abs(spring.getVelocity()) < MORPH_SETTLE_VELOCITY;
    }

    private void startMorphAnimation(float target) {
        prepareLowerAnimation(target);
        setContentTransitionsEnabled(false);
        if (!modeAnimating) {
            modeAnimating = true;
            updateProgressUpdaterForMode();
        }
        configureMorphSprings(target);
        long now = MuiModApi.getFrameTimeNanos();
        coverSpring.setTarget(target, now);
        textXSpring.setTarget(target, now);
        textYSpring.setTarget(target, now);
        slideSpring.setTarget(target, now);
        if (!morphRunning) {
            morphRunning = true;
            Choreographer.getInstance().postFrameCallback(morphFrameCallback);
        }
    }

    /** Expand uses fast text springs and a slow cover; collapse leads with the cover. */
    private void configureMorphSprings(float target) {
        if (target < 0.5f) {
            coverSpring.setResponse(morphResponse(EXPAND_COVER_RESPONSE));
            textXSpring.setResponse(morphResponse(EXPAND_TEXT_X_RESPONSE));
            textYSpring.setResponse(morphResponse(EXPAND_TEXT_Y_RESPONSE));
            slideSpring.setResponse(morphResponse(EXPAND_SLIDE_RESPONSE));
        } else {
            coverSpring.setResponse(morphResponse(COLLAPSE_COVER_RESPONSE));
            textXSpring.setResponse(morphResponse(COLLAPSE_TEXT_X_RESPONSE));
            textYSpring.setResponse(morphResponse(COLLAPSE_TEXT_Y_RESPONSE));
            slideSpring.setResponse(morphResponse(COLLAPSE_SLIDE_RESPONSE));
        }
        coverSpring.setDamping(MORPH_DAMPING);
        textXSpring.setDamping(MORPH_DAMPING);
        textYSpring.setDamping(MORPH_DAMPING);
        slideSpring.setDamping(MORPH_DAMPING);
    }

    private void advanceMorph(long frameTimeNanos) {
        if (!morphRunning) {
            return;
        }
        coverSpring.update(frameTimeNanos);
        textXSpring.update(frameTimeNanos);
        textYSpring.update(frameTimeNanos);
        slideSpring.update(frameTimeNanos);
        applyMorph();
        boolean settled = coverSpring.isSettled(MORPH_SETTLE_EPSILON, MORPH_SETTLE_VELOCITY)
                && textXSpring.isSettled(MORPH_SETTLE_EPSILON, MORPH_SETTLE_VELOCITY)
                && textYSpring.isSettled(MORPH_SETTLE_EPSILON, MORPH_SETTLE_VELOCITY)
                && slideSpring.isSettled(MORPH_SETTLE_EPSILON, MORPH_SETTLE_VELOCITY);
        if (settled) {
            settleMode();
        } else {
            Choreographer.getInstance().postFrameCallback(morphFrameCallback);
        }
    }

    /**
     * Freezes both lower pages at the height they will have once the switch settles, so neither
     * the lyrics nor the library list reflows while the viewport grows or shrinks with the header.
     */
    private void prepareLowerAnimation(float target) {
        boolean targetShowsLyrics = target < 0.5f
                && clientConfig.getEnableLyricsSidebar()
                && notAbsoluteMusicLyric();
        boolean lyricsVisibleNow = lyricsScrollView.getVisibility() == VISIBLE;
        if (targetShowsLyrics) {
            lyricsScrollView.setVisibility(VISIBLE);
            lyricsScrollView.reinitialize();
        }
        libraryPage.setVisibility(VISIBLE);

        int contentHeight = getContentHeight();
        int bottomMargin = dp(SIDE_MARGIN_DP);
        int lyricsRest = Math.max(0, contentHeight - measureHeaderHeight(false) - bottomMargin);
        int libraryRest = Math.max(0, contentHeight - measureHeaderHeight(true) - bottomMargin);
        lowerViewport.freeze(lyricsRest, libraryRest);
        if (!lyricsVisibleNow && !targetShowsLyrics) {
            lyricsScrollView.setVisibility(GONE);
        }
        applyMorph();
    }

    /** Measures the header at a static end state without disturbing the running springs. */
    private int measureHeaderHeight(boolean compact) {
        if (sideContent == null) {
            return 0;
        }
        float value = compact ? 1f : 0f;
        activeCard.setMorph(value, value, value);
        stagedCard.setMorph(value, value, value);
        sideContent.measure(
                MeasureSpec.makeMeasureSpec(dp(SIDE_WIDTH_DP), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return sideContent.getMeasuredHeight();
    }

    private int getContentHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private void applyMorph() {
        float cover = coverSpring.getValue();
        float x = textXSpring.getValue();
        float y = textYSpring.getValue();
        if (activeCard != null) {
            activeCard.setMorph(cover, x, y);
        }
        if (stagedCard != null) {
            stagedCard.setMorph(cover, x, y);
        }
        if (lowerViewport != null) {
            lowerViewport.setSlideProgress(slideSpring.getValue());
        }
    }

    private void snapMorphTo(float target) {
        coverSpring.jumpTo(target);
        textXSpring.jumpTo(target);
        textYSpring.jumpTo(target);
        slideSpring.jumpTo(target);
        applyMorph();
    }

    private void stopMorphLoop() {
        modeAnimating = false;
        if (morphRunning) {
            morphRunning = false;
            Choreographer.getInstance().removeFrameCallback(morphFrameCallback);
        }
    }

    /** Applies the exact final state: the inactive page leaves the layout/render pipeline. */
    private void settleMode() {
        stopMorphLoop();
        snapMorphTo(targetProgress());
        if (lowerViewport != null) {
            lowerViewport.settle(mode == Mode.LIBRARY);
        }
        if (lyricsScrollView != null) {
            applyLyricsVisibility(shouldShowLyrics());
            libraryPage.setVisibility(mode == Mode.LIBRARY ? VISIBLE : GONE);
        }
        setContentTransitionsEnabled(true);
        updateProgressUpdaterForMode();
    }

    private void setContentTransitionsEnabled(boolean enabled) {
        if (sideContent != null) {
            sideContent.setLayoutTransition(enabled ? sideContentTransition : null);
        }
        if (activeCard != null) {
            activeCard.setContentTransitionsEnabled(enabled);
        }
        if (stagedCard != null) {
            stagedCard.setContentTransitionsEnabled(enabled);
        }
    }

    private void updateProgressUpdaterForMode() {
        if (!playerVisible) {
            progressUpdaterToken.incrementAndGet();
            return;
        }
        MusicDetail current = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        if (current != null && !current.equals(MusicDetail.NONE)) {
            startProgressUpdater(current);
        } else {
            progressUpdaterToken.incrementAndGet();
        }
    }

    private void buildPlayerHeader(Context context) {
        int availableWidth = dp(SIDE_WIDTH_DP);
        int margin = dp(SIDE_MARGIN_DP);

        sideContent = new LinearLayout(context);
        sideContent.setOrientation(VERTICAL);
        headerContainer.addView(sideContent, new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        sideContent.addView(new View(context), new LinearLayout.LayoutParams(MATCH_PARENT, margin));

        FrameLayout cardWrapper = new FrameLayout(context);
        activeCard = new MusicInfoCard(context, availableWidth);
        stagedCard = new MusicInfoCard(context, availableWidth);
        cardWrapper.addView(activeCard, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        cardWrapper.addView(stagedCard, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        stagedCard.setVisibility(INVISIBLE);
        stagedCard.setTranslationX(availableWidth);
        stagedCard.setAlpha(0f);
        stagedCard.getAlbumImage().setScaleX(CARD_COVER_MIN_SCALE);
        stagedCard.getAlbumImage().setScaleY(CARD_COVER_MIN_SCALE);
        sideContent.addView(cardWrapper, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(HORIZONTAL);
        actionsRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        sideContent.addView(actionsRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(HORIZONTAL);
        LayoutTransition layoutTransition = new LayoutTransition();
//        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        LayoutTransitionReflow.attach(layoutTransition);
        buttonsLayout.setLayoutTransition(layoutTransition);
        buttonsLayout.setGravity(Gravity.START);
        actionsRow.addView(buttonsLayout, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        {
            InsetBackgroundFactory buttonsBackground = InsetBackgroundFactory.builder()
                    .backgroundColor(Theme.GHOST_BUTTON_STATES)
                    .cornerRadius(dp(4)).build();
            int buttonSize = dp(36);

            pusherWidget = new PusherWidget(context);
            pusherWidget.setVisibility(GONE);
            buttonsBackground.applyBackgroundTo(pusherWidget);
            int pusherPadding = dp(PUSHER_PADDING_DP);
            pusherWidget.setPadding(pusherPadding, pusherPadding, pusherPadding, pusherPadding);
            buttonsLayout.addView(pusherWidget, new LinearLayout.LayoutParams(buttonSize, buttonSize));

            int iconSize = dp(ACTION_ICON_DP);
            sourceButton = new ImageButton(context);
            sourceButton.setVisibility(GONE);
            sourceButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Image sourceIcon = ImageUtils.getImageFromResource(ICON_SOURCE);
            if (sourceIcon != null) {
                sourceButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(
                        context.getResources(), sourceIcon, iconSize, iconSize), dp(1)));
            }
            sourceButton.setOnClickListener(this::onSourceClick);
            buttonsBackground.applyBackgroundTo(sourceButton);
            buttonsLayout.addView(sourceButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

            likeButton = new ToggleTrackLikeStateButton(context);
            buttonsBackground.applyBackgroundTo(likeButton);
            buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
            addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
            buttonsBackground.applyBackgroundTo(addToPlaylistButton);
            buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
            skipCurrentButton = new VoteSkipButton(context);
            buttonsBackground.applyBackgroundTo(skipCurrentButton);
            buttonsLayout.addView(skipCurrentButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        }
        buttonsLayout.setVisibility(GONE);

        sideContentTransition = new LayoutTransition();
        sideContentTransition.enableTransitionType(LayoutTransition.CHANGING);
        LayoutTransitionReflow.attach(sideContentTransition);
        sideContent.setLayoutTransition(sideContentTransition);

        lyricsScrollView = new StaggeredLyricScrollView(context);
        lyricsScrollView.setMainLyricSize(Theme.SIDEBAR_MAIN_LYRIC_SIZE);
        lyricsScrollView.setSubLyricSize(Theme.SIDEBAR_SUB_LYRIC_SIZE);

        stagedCard.bind(Traceable.of(MusicDetail.NONE), availableWidth);
    }

    public void bindMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace,
                          Queue<LyricLine> lines, boolean animate) {
        MusicDetail musicDetail = musicDetailTrace == null ? MusicDetail.NONE : musicDetailTrace.value();
        if (playerVisible && musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
            startProgressUpdater(musicDetail);
        }
        if (!animate || activeCard == null || stagedCard == null) {
            // Initial display (or cards not built yet): bind in place without animating.
            if (activeCard != null) {
                activeCard.bind(musicDetailTrace, sideWidth);
                displayedDetail = musicDetail;
                displayedTrace = musicDetailTrace;
                updateCoverScale(false);
            }
        } else if (cardSwitching) {
            // Keep only the newest request; it plays once the running transition settles.
            pendingSwitch = musicDetailTrace;
        } else if (isSameMusic(displayedDetail, musicDetail)) {
            // Same track re-synced (startAt / reconnect / config refresh): refresh contents in place.
            activeCard.bind(musicDetailTrace, sideWidth);
            displayedDetail = musicDetail;
            displayedTrace = musicDetailTrace;
        } else {
            startCardSwitch(musicDetailTrace);
        }
        if (lyricsScrollView != null) {
            if (notAbsoluteMusicLyric()) {
                lyricsScrollView.switchLyrics(musicDetail, lines);
            } else {
                lyricsScrollView.clearLyrics();
            }
        }
        updateActionButtons(displayedDetail);
        checkNextToPlay(nextToPlayTrace);
        updateLyricsVisibility();
    }

    /** Binds the sidebar action row to the track that is actually displayed. */
    private void updateActionButtons(MusicDetail detail) {
        if (buttonsLayout == null) {
            return;
        }
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            buttonsLayout.setVisibility(GONE);
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
            bindPusher(null);
            bindSource(null);
            return;
        }
        skipCurrentButton.reset();
        likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(detail).currentUsersLikeList());
        addToPlaylistButton.bindMusicDetail(detail);
        bindPusher(detail);
        bindSource(displayedTrace == null ? null : displayedTrace.source());
        buttonsLayout.setVisibility(VISIBLE);
    }

    private void bindPusher(MusicDetail detail) {
        PusherInfo pusherInfo = detail == null ? null : detail.getPusherInfo();
        if (pusherInfo == null || PusherInfo.EMPTY.equals(pusherInfo)) {
            pusherWidget.clear();
            pusherWidget.setVisibility(GONE);
        } else {
            pusherWidget.bind(pusherInfo);
            pusherWidget.setVisibility(VISIBLE);
        }
    }

    private void bindSource(SourceMeta source) {
        if (source == null) {
            sourceButton.setTag(null);
            sourceButton.setVisibility(GONE);
            Tooltip.attach(sourceButton, null);
            return;
        }
        sourceButton.setTag(source);
        sourceButton.setVisibility(VISIBLE);
        Tooltip.attach(sourceButton, buildSourceLabel(source));
    }

    private void onSourceClick(View view) {
        Object tag = sourceButton.getTag();
        if (!(tag instanceof SourceMeta sourceMeta)) {
            return;
        }
        Class<?> type = sourceMeta.type();
        if (!MusicCollection.class.isAssignableFrom(type)) {
            return;
        }
        Context context = getContext();
        //noinspection unchecked
        MusicService.getInstance()
                .loadMusicCollectionDetail(sourceMeta.id(), (Class<? extends MusicCollection>) type)
                .thenAccept(collection -> MuiModApi.postToUiThread(() -> {
                    RouterContainer router = MainFragment.getInstance().getRouterContainer();
                    if (router != null) {
                        router.pushNavigate(new MusicCollectionDetailView(context, collection));
                    }
                }));
    }

    private static SpannableString buildSourceLabel(SourceMeta source) {
        SpannableString text = new SpannableString("    " + source.name());
        String iconPath = Album.class.isAssignableFrom(source.type())
                ? "/assets/music_hud/textures/gui/icons/disc_album.png"
                : "/assets/music_hud/textures/gui/icons/list_music.png";
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            text.setSpan(ImageUtils.getIconSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String playModeIconPath = switch (source.playMode()) {
            case RANDOM -> "/assets/music_hud/textures/gui/icons/shuffle.png";
            case SEQUENTIAL -> "/assets/music_hud/textures/gui/icons/repeat.png";
            case INTELLIGENT -> "/assets/music_hud/textures/gui/icons/heart_pulse.png";
        };
        Image modeIcon = ImageUtils.getImageFromResource(playModeIconPath);
        if (modeIcon != null) {
            text.setSpan(ImageUtils.getIconSpan(modeIcon), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private static boolean isSameMusic(MusicDetail a, MusicDetail b) {
        MusicDetail normalizedA = a == null ? MusicDetail.NONE : a;
        MusicDetail normalizedB = b == null ? MusicDetail.NONE : b;
        return normalizedA.equals(normalizedB);
    }

    /**
     * Starts the card-style right-to-left switch: the active card slides out to the left while the
     * staged card slides in from the right. Both cards move in parallel sharing one spring so the
     * seam never gaps; album covers scale uniformly between {@link #CARD_COVER_MIN_SCALE} and 1.
     */
    private void startCardSwitch(Traceable<MusicDetail> musicDetailTrace) {
        int width = sideWidth;
        cardSwitching = true;
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
            coverScaleAnimator = null;
        }
        float targetCoverScale = isCoverExpanded() ? 1 : CARD_COVER_MIN_SCALE;

        // Prepare the incoming card off-screen right; visible before bind so image lazy-load triggers.
        stagedCard.setVisibility(VISIBLE);
        stagedCard.setTranslationX(width);
        stagedCard.setAlpha(0f);
        stagedCard.setMorph(coverSpring.getValue(), textXSpring.getValue(), textYSpring.getValue());
        UrlImageView activeAlbumImageView = activeCard.getAlbumImage();
        UrlImageView stagedAlbumImageView = stagedCard.getAlbumImage();
        stagedAlbumImageView.setScaleX(CARD_COVER_MIN_SCALE);
        stagedAlbumImageView.setScaleY(CARD_COVER_MIN_SCALE);
        stagedCard.bind(musicDetailTrace, width);

        ObjectAnimator outX = ObjectAnimator.ofFloat(activeCard, View.TRANSLATION_X, 0f, -width);
        ObjectAnimator outA = ObjectAnimator.ofFloat(activeCard, View.ALPHA, 1f, 0f);
        ObjectAnimator outSx = ObjectAnimator.ofFloat(activeAlbumImageView, View.SCALE_X,
                activeAlbumImageView.getScaleX(), CARD_COVER_MIN_SCALE);
        ObjectAnimator outSy = ObjectAnimator.ofFloat(activeAlbumImageView, View.SCALE_Y,
                activeAlbumImageView.getScaleY(), CARD_COVER_MIN_SCALE);
        ObjectAnimator inX = ObjectAnimator.ofFloat(stagedCard, View.TRANSLATION_X, width, 0f);
        ObjectAnimator inA = ObjectAnimator.ofFloat(stagedCard, View.ALPHA, 0f, 1f);
        ObjectAnimator inSx = ObjectAnimator.ofFloat(stagedAlbumImageView, View.SCALE_X,
                CARD_COVER_MIN_SCALE, targetCoverScale);
        ObjectAnimator inSy = ObjectAnimator.ofFloat(stagedAlbumImageView, View.SCALE_Y,
                CARD_COVER_MIN_SCALE, targetCoverScale);
        List<ObjectAnimator> animators = List.of(outX, outA, outSx, outSy, inX, inA, inSx, inSy);
        for (ObjectAnimator animator : animators) {
            animator.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
            animator.setDuration(CARD_ANIM_DURATION_MS);
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(outX, outA, outSx, outSy, inX, inA, inSx, inSy);
        final Traceable<MusicDetail> newTrace = musicDetailTrace;
        final AnimatorSet thisAnimator = set;
        cardAnimator = set;
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // Stale callbacks (canceled / replaced) must not settle the newer transition.
                if (cardAnimator != thisAnimator) {
                    return;
                }
                settleCardSwitch(newTrace);
            }
        });
        set.start();
    }

    private void settleCardSwitch(Traceable<MusicDetail> newTrace) {
        cardAnimator = null;
        MusicDetail newDetail = newTrace == null ? null : newTrace.value();

        MusicInfoCard oldCard = activeCard;
        oldCard.setVisibility(INVISIBLE);
        oldCard.setTranslationX(0f);
        oldCard.setAlpha(0f);
        oldCard.getAlbumImage().setScaleX(1f);
        oldCard.getAlbumImage().setScaleY(1f);

        activeCard = stagedCard;
        stagedCard = oldCard;
        activeCard.setTranslationX(0f);
        activeCard.setAlpha(1f);
        activeCard.setVisibility(VISIBLE);
        displayedDetail = newDetail;
        displayedTrace = newTrace;
        cardSwitching = false;
        // Recycle the outgoing card immediately so no stale content leaks into its next slide-in.
        stagedCard.clear();
        updateCoverScale(true);

        // Drain the latest-wins slot, if any switch arrived while animating.
        if (pendingSwitch != null) {
            Traceable<MusicDetail> pending = pendingSwitch;
            pendingSwitch = null;
            MusicDetail pendingDetail = pending.value();
            if (isSameMusic(displayedDetail, pendingDetail)) {
                activeCard.bind(pending, sideWidth);
                displayedDetail = pendingDetail;
                displayedTrace = pending;
            } else {
                startCardSwitch(pending);
            }
        }
        updateActionButtons(displayedDetail);
    }

    private static boolean isCoverExpanded() {
        MusicDetail detail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (detail == null || detail.equals(MusicDetail.NONE) || PlaybackTask.getTargetGain() == 0) {
            return false;
        }
        StreamAudioPlayer.Status status = StreamAudioPlayer.getInstance().getStatus();
        return status == StreamAudioPlayer.Status.PLAYING;
    }

    /** Re-applies the cover scale (e.g. after a mute toggle) on the UI thread. */
    public void updateCoverScale(boolean animate) {
        if (activeCard == null) {
            return;
        }
        View cover = activeCard.getAlbumImage();
        float target = isCoverExpanded() ? 1f : CARD_COVER_MIN_SCALE;
        if (!animate) {
            cover.setScaleX(target);
            cover.setScaleY(target);
            return;
        }
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cover, View.SCALE_X, target);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cover, View.SCALE_Y, target);
        scaleX.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
        scaleY.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
        scaleX.setDuration(CARD_ANIM_DURATION_MS);
        scaleY.setDuration(CARD_ANIM_DURATION_MS);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        coverScaleAnimator = set;
        set.start();
    }

    private void startProgressUpdater(MusicDetail musicDetail) {
        int token = progressUpdaterToken.incrementAndGet();
        // The volatile field may be nulled by a concurrent switch-to-idle/stop between the UI task
        // being posted and running, so fall back to the track's own duration instead of NPE-ing.
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        if (musicDuration == null) {
            musicDuration = Duration.ofMillis(musicDetail.getDurationMillis());
        }
        DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                FORMATTER_WITH_HOUR : FORMATTER_WITHOUT_HOUR;
        // 兜底退出：startAt 可能因任务被取代/失败永不触发，超时后结束进度循环防泄漏
        long currentTimeMillis = System.currentTimeMillis();
        long deadline = currentTimeMillis + musicDuration.toMillis() + 120_000;
        progressUpdaterStartTime = currentTimeMillis;
        progressTextUpdateCount = -1;
        postFrameCallback(musicDetail, formatter, deadline, token);
    }

    private void postFrameCallback(MusicDetail musicDetail, DateTimeFormatter formatter, long deadline, int token) {
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (progressUpdaterToken.get() != token) return;
            MusicInfoCard card = activeCard;
            if (card != null) {
                long currentTimeMillis = System.currentTimeMillis();
                long count = (currentTimeMillis - progressUpdaterStartTime) / 1000;
                if (count > progressTextUpdateCount) {
                    progressTextUpdateCount = count;
                    Duration playedDuration = nowPlayingInfo.getPlayedDuration();
                    String playedTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds()));
                    card.getPlayedTimeText().setText(playedTimeString);
                }
                card.getProgressBar().setProgress((int) (nowPlayingInfo.getProgressRate()
                        * card.getProgressBar().getMax()));
                if (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())
                        && nowPlayingInfo.getProgressRate() < 1
                        && System.currentTimeMillis() < deadline) {
                    postFrameCallback(musicDetail, formatter, deadline, token);
                }
            }
        });
    }

    public void setAudioStatus(StreamAudioPlayer.Status status) {
        if (activeCard != null && activeCard.getProgressBar() != null) {
            activeCard.getProgressBar().setIndeterminate(
                    status == StreamAudioPlayer.Status.BUFFERING || status == StreamAudioPlayer.Status.RETRYING);
        }
        updateCoverScale(true);
    }

    private static boolean notAbsoluteMusicLyric() {
        Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
        return lines != null && !lines.isEmpty()
                && lines.stream().filter(l -> l.getType() == LyricLine.Type.NORMAL).count() > 1;
    }

    public void updateLyricsVisibility() {
        // The switch settles lyrics itself; external refreshes must not resize the moving page.
        if (lyricsScrollView == null || modeAnimating) {
            return;
        }
        applyLyricsVisibility(shouldShowLyrics());
        libraryPage.setVisibility(mode == Mode.LIBRARY ? VISIBLE : GONE);
    }

    private boolean shouldShowLyrics() {
        return mode == Mode.PLAYER
                && clientConfig.getEnableLyricsSidebar()
                && notAbsoluteMusicLyric();
    }

    private void applyLyricsVisibility(boolean show) {
        if (lyricsScrollView == null) {
            return;
        }
        boolean visibleNow = lyricsScrollView.getVisibility() == VISIBLE;
        if (show == visibleNow) {
            return;
        }
        if (show) {
            lyricsScrollView.setVisibility(VISIBLE);
            lyricsScrollView.reinitialize();
        } else {
            lyricsScrollView.suspendLyricFollowingAndHide();
            lyricsScrollView.setVisibility(GONE);
        }
    }

    public void refreshLyrics() {
        if (lyricsScrollView != null) {
            lyricsScrollView.refreshLinesStyle();
        }
    }

    private void buildLibraryPage(Context context) {
        libraryPage = new ScrollView(context);
        libraryPage.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        libraryPage.setFillViewport(true);
        libraryPage.setVisibility(GONE);
        int margin = dp(SIDE_MARGIN_DP);

        LinearLayout scrollViewContainer = new LinearLayout(context);
        scrollViewContainer.setOrientation(VERTICAL);
        libraryPage.addView(scrollViewContainer, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        LayoutTransition transition = new LayoutTransition();
        transition.setAnimateParentHierarchy(false);
        transition.enableTransitionType(LayoutTransition.CHANGING);
        LayoutTransitionReflow.attach(transition);
        scrollViewContainer.setLayoutTransition(transition);

        int dp24 = dp(24);
        {
            nextToPlayRow = new LinearLayout(context);
            nextToPlayRow.setOrientation(VERTICAL);
            nextToPlayRow.setVisibility(GONE);
            LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params1.setMargins(margin, dp(16), margin, 0);
            scrollViewContainer.addView(nextToPlayRow, params1);

            LinearLayout nextToPlayHeader = new LinearLayout(context);
            nextToPlayHeader.setOrientation(HORIZONTAL);
            nextToPlayHeader.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams nextToPlayHeaderParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp24);
            nextToPlayHeaderParams.setMargins(0, 0, 0, dp(8));
            nextToPlayRow.addView(nextToPlayHeader, nextToPlayHeaderParams);

            TextView nextToPlayTitle = new TextView(context);
            nextToPlayTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nextToPlayTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            nextToPlayTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.nextToPlay"));
            nextToPlayHeader.addView(nextToPlayTitle, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            rotateNextToPlayButton = new ImageButton(context);
            rotateNextToPlayButton.setVisibility(GONE);
            rotateNextToPlayButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Tooltip.attach(rotateNextToPlayButton, I18n.get(MusicHud.MOD_ID + ".button.rotateNextToPlay"));
            Image rotateIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/refresh_ccw_dot.png");
            rotateNextToPlayButton.setImageDrawable(
                    new ScaledImageDrawable(context.getResources(), rotateIcon, dp(16), dp(16)));
            InsetBackgroundFactory.builder()
                    .inset(0)
                    .cornerRadius(dp(4))
                    .build()
                    .applyBackgroundTo(rotateNextToPlayButton);
            rotateNextToPlayButton.setOnClickListener(v -> musicService.rotateNextToPlay().thenAccept(result -> {
                if (result.actionResult() == ActionResult.FAIL) {
                    ToastUtil.show(I18n.get(result.message()));
                }
            }));
            LinearLayout.LayoutParams rotateButtonParams = new LinearLayout.LayoutParams(dp24, dp24);
            rotateButtonParams.setMargins(dp(8), 0, 0, 0);
            nextToPlayHeader.addView(rotateNextToPlayButton, rotateButtonParams);

            nextToPlayItem = new MusicTrackItem(context);
            nextToPlayRow.addView(nextToPlayItem, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        {
            queueRow = new LinearLayout(context);
            queueRow.setOrientation(VERTICAL);
            LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params1.setMargins(margin, dp(16), margin, 0);
            scrollViewContainer.addView(queueRow, params1);

            TextView queueTitle = new TextView(context);
            queueTitle.setGravity(Gravity.CENTER);
            queueTitle.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            queueTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            queueTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.playQueue"));
            queueTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            queueTitle.setSingleLine();
            queueRow.addView(queueTitle, new LayoutParams(WRAP_CONTENT, dp24));

            playQueueListView = new LinearLayout(context);
            playQueueListView.setOrientation(VERTICAL);
            LayoutTransition queueTransition = new LayoutTransition();
            queueTransition.enableTransitionType(LayoutTransition.CHANGING);
            LayoutTransitionReflow.attach(queueTransition);
            playQueueListView.setLayoutTransition(queueTransition);
            LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, dp(8), 0, -dp(2));
            queueRow.addView(playQueueListView, params);
        }
        {
            clientIdlePlaySourceView = new LinearLayout(context);
            clientIdlePlaySourceView.setVisibility(GONE);
            clientIdlePlaySourceView.setOrientation(VERTICAL);
            LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            int marginOffset = cardPadding + dp(1);
            params1.setMargins(margin - marginOffset, dp(16), margin - marginOffset, 0);
            scrollViewContainer.addView(clientIdlePlaySourceView, params1);

            TextView clientIdlePlaySourceViewTitle = new TextView(context);
            clientIdlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            clientIdlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            clientIdlePlaySourceViewTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.idlePlaySources"));
            Tooltip.attach(clientIdlePlaySourceViewTitle, I18n.get(MusicHud.MOD_ID + ".text.idlePlaySourcesDescription"));
            LinearLayout.LayoutParams clientTitleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            clientTitleParams.setMargins(marginOffset, dp(16), marginOffset, 0);
            clientIdlePlaySourceView.addView(clientIdlePlaySourceViewTitle, clientTitleParams);

            clientIdlePlaySourceCardsList = new LinearLayout(context);
            clientIdlePlaySourceCardsList.setOrientation(VERTICAL);
            LayoutTransition transition1 = new LayoutTransition();
            transition1.enableTransitionType(LayoutTransition.CHANGING);
            LayoutTransitionReflow.attach(transition1);
            clientIdlePlaySourceCardsList.setLayoutTransition(transition1);
            LinearLayout.LayoutParams clientCardsParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            clientCardsParams.setMargins(0, dp(4), 0, 0);
            clientIdlePlaySourceView.addView(clientIdlePlaySourceCardsList, clientCardsParams);
        }
        {
            serverIdlePlaySourceView = new LinearLayout(context);
            serverIdlePlaySourceView.setVisibility(GONE);
            serverIdlePlaySourceView.setOrientation(VERTICAL);
            LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            int marginOffset = cardPadding + dp(1);
            params1.setMargins(margin - marginOffset, dp(16), margin - marginOffset, 0);
            serverIdlePlaySourceView.setLayoutParams(params1);
            scrollViewContainer.addView(serverIdlePlaySourceView, params1);

            TextView serverIdlePlaySourceViewTitle = new TextView(context);
            serverIdlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            serverIdlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            serverIdlePlaySourceViewTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.othersIdlePlaySources"));
            Tooltip.attach(serverIdlePlaySourceViewTitle, I18n.get(MusicHud.MOD_ID + ".text.idlePlaySourcesDescription"));
            LinearLayout.LayoutParams serverTitleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            serverTitleParams.setMargins(marginOffset, dp(16), marginOffset, 0);
            serverIdlePlaySourceView.addView(serverIdlePlaySourceViewTitle, serverTitleParams);

            serverIdlePlaySourceCardsList = new LinearLayout(context);
            serverIdlePlaySourceCardsList.setOrientation(VERTICAL);
            LayoutTransition transition1 = new LayoutTransition();
            transition1.enableTransitionType(LayoutTransition.CHANGING);
            LayoutTransitionReflow.attach(transition1);
            serverIdlePlaySourceCardsList.setLayoutTransition(transition1);
            LinearLayout.LayoutParams serverCardsParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            serverCardsParams.setMargins(0, dp(4), 0, 0);
            serverIdlePlaySourceView.addView(serverIdlePlaySourceCardsList, serverCardsParams);
        }
    }

    /** Rebuilds the library lists from the current queue / idle play source state. */
    private void refreshLibrary() {
        if (playQueueListView == null) {
            return;
        }
        playQueueListView.removeAllViews();
        idlePlaySourceCardMap.clear();
        clientIdlePlaySourceCardsList.removeAllViews();
        serverIdlePlaySourceCardsList.removeAllViews();
        localPlayer = Minecraft.getInstance().player;

        clientIdlePlaySources.forEach(idlePlaySource -> {
            if (!idlePlaySourceCardMap.containsKey(CardKey.of(true, idlePlaySource))) {
                addIdlePlaySourceTo(idlePlaySource, clientIdlePlaySourceCardsList, true);
            }
        });
        serverIdlePlaySources.forEach(idlePlaySource -> {
            if (localPlayer != null && !idlePlaySource.getPusherInfo().playerUUID().equals(localPlayer.getUUID())
                    && !idlePlaySourceCardMap.containsKey(CardKey.of(false, idlePlaySource))) {
                addIdlePlaySourceTo(idlePlaySource, serverIdlePlaySourceCardsList, false);
            }
        });
        checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
        checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);

        Queue<QueueItem> queue = musicService.getMusicQueue();
        checkQueue(queue);
        // Snapshot before iterating: the client queue is a plain ArrayDeque mutated on network
        // threads and its iterator is fail-fast
        for (QueueItem item : List.copyOf(queue)) {
            addMusicQueueItem(item, playQueueListView);
        }
    }

    private static boolean hasSourceWithKey(Set<IdlePlaySource> sources, IdlePlaySource idlePlaySource) {
        return sources.stream().anyMatch(c -> c.getId() == idlePlaySource.getId()
                && c.getType() == idlePlaySource.getType());
    }

    private void addIdlePlaySourceTo(IdlePlaySource idlePlaySource, ViewGroup targetView, boolean local) {
        CardKey key = CardKey.of(local, idlePlaySource);
        MusicCollection musicCollection = idlePlaySource.getMusicCollection();
        if (MusicCollections.isUsable(musicCollection)) {
            addInternal(idlePlaySource, musicCollection, targetView, key);
        } else {
            loadIdlePlaySourceCard(idlePlaySource, targetView, key, IDLE_SOURCE_LOAD_ATTEMPTS);
        }
    }

    /**
     * Loads a card's collection, retrying transient failures so the card is not built from a
     * failure sentinel (which would break id-based recovery and the detail page).
     */
    private void loadIdlePlaySourceCard(IdlePlaySource idlePlaySource, ViewGroup targetView,
                                        CardKey key, int attemptsLeft) {
        musicService.loadMusicCollectionDetail(idlePlaySource.getId(), idlePlaySource.getType())
                .whenComplete((collection, throwable) -> {
                    if (throwable == null && MusicCollections.isUsable(collection)) {
                        MuiModApi.postToUiThread(() -> addInternal(idlePlaySource, collection, targetView, key));
                    } else if (attemptsLeft > 0) {
                        MusicHud.EXECUTOR.execute(() -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            loadIdlePlaySourceCard(idlePlaySource, targetView, key, attemptsLeft - 1);
                        });
                    } else {
                        LOGGER.warn("Failed to load idle play source card {} ({})",
                                idlePlaySource.getType().getSimpleName(), idlePlaySource.getId(), throwable);
                    }
                });
    }

    private void addInternal(IdlePlaySource idlePlaySource, MusicCollection musicCollection,
                             ViewGroup targetView, CardKey key) {
        MusicCollectionCard child = new MusicCollectionCard(getContext(), musicCollection, idlePlaySource.getPusherInfo(), dp(56));
        child.setImageCornerRadius(dp(4));
        child.setBackgroundPadding(cardPadding);
        child.setOrientation(HORIZONTAL);
        targetView.addView(child, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        idlePlaySourceCardMap.put(key, child);
    }

    private void checkQueue(Queue<QueueItem> queue) {
        if (queue.isEmpty()) {
            queueRow.setVisibility(GONE);
            checkNextToPlay(NowPlayingInfo.getInstance().getNextToPlayMusic());
        } else {
            queueRow.setVisibility(VISIBLE);
            QueueItem peek = queue.peek();
            checkNextToPlay(peek == null ? Traceable.of(MusicDetail.NONE) : peek.musicDetail());
        }
    }

    private void checkIdlePlaySources(Set<IdlePlaySource> idlePlaySources, View targetView) {
        targetView.setVisibility(idlePlaySources.isEmpty() ? GONE : VISIBLE);
        checkQueue(musicService.getMusicQueue());
    }

    private void checkNextToPlay(Traceable<MusicDetail> nextIdle) {
        Queue<QueueItem> musicQueue = musicService.getMusicQueue();
        boolean hasIdlePlaySources = !musicService.getIdlePlaySourceState().local().getSources().isEmpty()
                || !musicService.getIdlePlaySourceState().external().getSources().isEmpty();
        MusicDetail next = hasIdlePlaySources && nextIdle != null ? nextIdle.value() : null;
        boolean show = musicQueue.isEmpty() && next != null && !next.equals(MusicDetail.NONE);
        applyNextToPlay(show, show ? nextIdle : null);
    }

    /**
     * Drives visibility and content of the "next to play" row. When the row is already showing a
     * different track, the switch is animated: the old content shrinks to {@link #NEXT_TO_PLAY_MIN_SCALE}
     * and fades out, then the new content is bound and enters in reverse.
     */
    private void applyNextToPlay(boolean show, Traceable<MusicDetail> nextIdle) {
        if (!show) {
            pendingNextToPlay = null;
            cancelNextToPlayAnimation();
            nextToPlayRow.setVisibility(GONE);
            resetNextToPlayItemVisual();
            return;
        }
        if (nextToPlayAnimator != null) {
            // A switch is already in flight: keep only the newest target.
            pendingNextToPlay = nextIdle;
            return;
        }
        boolean itemShown = nextToPlayRow.getVisibility() == VISIBLE;
        nextToPlayRow.setVisibility(VISIBLE);
        if (itemShown && Objects.equals(nextToPlayItem.getMusicTrace(), nextIdle)) {
            // Same track re-synced: refresh in place without replaying the switch animation.
            updateNextToPlayRotateButton(nextIdle);
            return;
        }
        if (!itemShown) {
            // First appearance: bind in place, no outgoing content to animate.
            nextToPlayRow.setVisibility(VISIBLE);
            nextToPlayItem.bindData(nextIdle);
            resetNextToPlayItemVisual();
            updateNextToPlayRotateButton(nextIdle);
            return;
        }
        startNextToPlaySwitch(nextIdle);
    }

    private void startNextToPlaySwitch(Traceable<MusicDetail> target) {
        pendingNextToPlay = null;
        ObjectAnimator outAlpha = ObjectAnimator.ofFloat(nextToPlayItem, View.ALPHA,
                nextToPlayItem.getAlpha(), 0f);
        ObjectAnimator outScaleX = ObjectAnimator.ofFloat(nextToPlayItem, View.SCALE_X,
                nextToPlayItem.getScaleX(), NEXT_TO_PLAY_MIN_SCALE);
        ObjectAnimator outScaleY = ObjectAnimator.ofFloat(nextToPlayItem, View.SCALE_Y,
                nextToPlayItem.getScaleY(), NEXT_TO_PLAY_MIN_SCALE);
        AnimatorSet outSet = new AnimatorSet();
        outSet.playTogether(outAlpha, outScaleX, outScaleY);
        outSet.setDuration(NEXT_TO_PLAY_ANIM_DURATION_MS);
        outSet.setInterpolator(NEXT_TO_PLAY_INTERPOLATOR);
        nextToPlayAnimator = outSet;
        outSet.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // Stale callbacks (canceled / replaced) must not settle a newer transition.
                if (nextToPlayAnimator != outSet) {
                    return;
                }
                Traceable<MusicDetail> bindTarget = target;
                // A newer target may have arrived during the fade-out; skip the stale one entirely.
                if (pendingNextToPlay != null) {
                    bindTarget = pendingNextToPlay;
                    pendingNextToPlay = null;
                }
                nextToPlayItem.clearData();
                nextToPlayItem.bindData(bindTarget);
                updateNextToPlayRotateButton(bindTarget);
                post(SidebarView.this::startNextToPlayEnter);
            }
        });
        outSet.start();
    }

    private void startNextToPlayEnter() {
        nextToPlayItem.setAlpha(0f);
        nextToPlayItem.setScaleX(NEXT_TO_PLAY_MIN_SCALE);
        nextToPlayItem.setScaleY(NEXT_TO_PLAY_MIN_SCALE);
        ObjectAnimator inAlpha = ObjectAnimator.ofFloat(nextToPlayItem, View.ALPHA, 0f, 1f);
        ObjectAnimator inScaleX = ObjectAnimator.ofFloat(nextToPlayItem, View.SCALE_X,
                NEXT_TO_PLAY_MIN_SCALE, 1f);
        ObjectAnimator inScaleY = ObjectAnimator.ofFloat(nextToPlayItem, View.SCALE_Y,
                NEXT_TO_PLAY_MIN_SCALE, 1f);
        AnimatorSet inSet = new AnimatorSet();
        inSet.playTogether(inAlpha, inScaleX, inScaleY);
        inSet.setDuration(NEXT_TO_PLAY_ANIM_DURATION_MS);
        inSet.setInterpolator(NEXT_TO_PLAY_INTERPOLATOR);
        nextToPlayAnimator = inSet;
        inSet.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (nextToPlayAnimator != inSet) {
                    return;
                }
                nextToPlayAnimator = null;
                resetNextToPlayItemVisual();
                if (pendingNextToPlay != null) {
                    Traceable<MusicDetail> pending = pendingNextToPlay;
                    pendingNextToPlay = null;
                    applyNextToPlay(true, pending);
                }
            }
        });
        inSet.start();
    }

    private void cancelNextToPlayAnimation() {
        AnimatorSet animator = nextToPlayAnimator;
        // Clear the field first so the cancel callback is treated as stale and does not advance.
        nextToPlayAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void resetNextToPlayItemVisual() {
        nextToPlayItem.setAlpha(1f);
        nextToPlayItem.setScaleX(1f);
        nextToPlayItem.setScaleY(1f);
    }

    private void updateNextToPlayRotateButton(Traceable<MusicDetail> nextIdle) {
        MusicDetail next = nextIdle == null ? null : nextIdle.value();
        rotateNextToPlayButton.setVisibility(
                next != null && isLocalPlayer(next.getPusherInfo().playerUUID()) ? VISIBLE : GONE);
    }

    /** Re-renders only the idle "next to play" row (e.g. after a server-side reroll). */
    public void updateNextToPlay(Traceable<MusicDetail> nextIdle) {
        MuiModApi.postToUiThread(() -> checkNextToPlay(nextIdle));
    }

    private static boolean isLocalPlayer(UUID playerUUID) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getUUID().equals(playerUUID);
    }

    private void addMusicQueueItem(QueueItem item, LinearLayout playQueueView) {
        Traceable<MusicDetail> musicTrace = item.musicDetail();
        MusicDetail musicDetail = musicTrace.value();
        MusicTrackItem musicListItem = new MusicTrackItem(getContext());
        musicListItem.bindData(musicTrace);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dp(4));

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && musicDetail.getPusherInfo().playerUUID().equals(player.getUUID())) {
            musicListItem.addActionButton(
                    "/assets/music_hud/textures/gui/icons/trash_2.png",
                    I18n.get(MusicHud.MOD_ID + ".button.remove"),
                    v -> musicService.sendRemoveMusicFromQueue(item));
        }
        musicListItem.setLayoutParams(layoutParams);
        playQueueView.addView(musicListItem, layoutParams);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // The panel can float above the page; consume taps on empty areas so they never reach it.
        return true;
    }

    private void registerLibraryListeners() {
        if (localAddRegister != null) {
            return;
        }
        localAddRegister = musicService.getIdlePlaySourceState().local().onAdd(localAddListener);
        localRemoveRegister = musicService.getIdlePlaySourceState().local().onRemove(localRemoveListener);
        serverAddRegister = musicService.getIdlePlaySourceState().external().onAdd(serverAddListener);
        serverRemoveRegister = musicService.getIdlePlaySourceState().external().onRemove(serverRemoveListener);
        musicQueuePushListener = item -> MuiModApi.postToUiThread(() -> {
            addMusicQueueItem(item, playQueueListView);
            checkQueue(musicService.getMusicQueue());
        });
        musicQueueRemoveListener = (removeIndex, item) -> MuiModApi.postToUiThread(() -> {
            if (removeIndex >= 0 && removeIndex < playQueueListView.getChildCount()) {
                playQueueListView.removeViewAt(removeIndex);
            }
            checkQueue(musicService.getMusicQueue());
        });
        musicService.getMusicQueuePushListeners().add(musicQueuePushListener);
        musicService.getMusicQueueRemoveListeners().add(musicQueueRemoveListener);
    }

    private void unregisterLibraryListeners() {
        if (localAddRegister != null) {
            localAddRegister.unregister();
            localAddRegister = null;
        }
        if (localRemoveRegister != null) {
            localRemoveRegister.unregister();
            localRemoveRegister = null;
        }
        if (serverAddRegister != null) {
            serverAddRegister.unregister();
            serverAddRegister = null;
        }
        if (serverRemoveRegister != null) {
            serverRemoveRegister.unregister();
            serverRemoveRegister = null;
        }
        if (musicQueuePushListener != null) {
            musicService.getMusicQueuePushListeners().remove(musicQueuePushListener);
            musicQueuePushListener = null;
        }
        if (musicQueueRemoveListener != null) {
            musicService.getMusicQueueRemoveListeners().remove(musicQueueRemoveListener);
            musicQueueRemoveListener = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerLibraryListeners();
        if (!audioListenerRegistered) {
            StreamAudioPlayer.getInstance().getStatusChangeListener().add(statusListener);
            audioListenerRegistered = true;
        }
        setAudioStatus(StreamAudioPlayer.getInstance().getStatus());
        updateProgressUpdaterForMode();
    }

    @Override
    protected void onDetachedFromWindow() {
        unregisterLibraryListeners();
        stopMorphLoop();
        cancelHeaderAnimator();
        snapMorphTo(targetProgress());
        if (lowerViewport != null) {
            lowerViewport.settle(mode == Mode.LIBRARY);
        }
        setContentTransitionsEnabled(true);
        if (audioListenerRegistered) {
            StreamAudioPlayer.getInstance().getStatusChangeListener().remove(statusListener);
            audioListenerRegistered = false;
        }
        cancelNextToPlayAnimation();
        if (cardAnimator != null) {
            cardAnimator.cancel();
            cardAnimator = null;
        }
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
            coverScaleAnimator = null;
        }
        cardSwitching = false;
        pendingSwitch = null;
        pendingPlayerGone = false;
        // Stop the progress loop; its callback also bails when the token changes.
        progressUpdaterToken.incrementAndGet();
        super.onDetachedFromWindow();
    }

    /** Re-reads all sidebar content after a global refresh (config / login / reconnect). */
    public void refresh() {
        refreshLibrary();
        updateCoverScale(true);
        refreshLyrics();
        updateLyricsVisibility();
    }

    private static float morphResponse(float seconds) {
        return seconds * MORPH_TIME_SCALE;
    }

    /**
     * Collapses by scaling its measured height and alpha; the child keeps its natural height so the
     * header contents never reflow while the footer expands.
     */
    private static final class CollapsibleHeader extends ViewGroup {
        private float visibleFraction = 1f;

        CollapsibleHeader(Context context) {
            super(context);
            setClipChildren(true);
        }

        void setVisibleFraction(float fraction) {
            float clamped = Math.clamp(fraction, 0f, 1f);
            if (visibleFraction == clamped) {
                return;
            }
            visibleFraction = clamped;
            setAlpha(clamped);
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int width = widthMode == MeasureSpec.UNSPECIFIED
                    ? Math.max(getSuggestedMinimumWidth(), getWidth())
                    : MeasureSpec.getSize(widthMeasureSpec);
            int childWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int naturalHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                naturalHeight = Math.max(naturalHeight, child.getMeasuredHeight());
            }
            int height = Math.round(naturalHeight * visibleFraction);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int childLeft = getPaddingLeft();
            int childTop = getPaddingTop();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
            }
        }
    }

    /**
     * Holds the lyrics and library pages. During a switch the pages keep the heights they will have
     * at the end of the animation and are only translated, so the viewport clipping changes while
     * the page contents themselves never reflow.
     */
    private static final class LowerPageViewport extends ViewGroup {
        private View lyricsPage;
        private View libraryPage;
        private int lyricsRestHeight = -1;
        private int libraryRestHeight = -1;
        private boolean frozen;
        private float slideProgress;

        LowerPageViewport(Context context) {
            super(context);
            setClipChildren(true);
        }

        void setPages(View lyrics, View library) {
            lyricsPage = lyrics;
            libraryPage = library;
            addView(lyrics, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            addView(library, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }

        void freeze(int lyricsHeight, int libraryHeight) {
            lyricsRestHeight = Math.max(0, lyricsHeight);
            libraryRestHeight = Math.max(0, libraryHeight);
            frozen = true;
            measureRest(lyricsPage, lyricsRestHeight);
            measureRest(libraryPage, libraryRestHeight);
            setSlideProgress(slideProgress);
            requestLayout();
        }

        void holdSizes() {
            if (frozen) {
                return;
            }
            frozen = true;
            requestLayout();
        }

        void releaseSizes() {
            if (!frozen) {
                return;
            }
            frozen = false;
            lyricsRestHeight = -1;
            libraryRestHeight = -1;
            requestLayout();
        }

        void settle(boolean libraryActive) {
            frozen = false;
            lyricsRestHeight = -1;
            libraryRestHeight = -1;
            slideProgress = libraryActive ? 1f : 0f;
            applyTranslations();
            requestLayout();
        }

        void setSlideProgress(float progress) {
            slideProgress = Math.clamp(progress, 0f, 1f);
            applyTranslations();
        }

        private void applyTranslations() {
            int width = getWidth();
            if (lyricsPage != null) {
                lyricsPage.setTranslationX(-slideProgress * width);
            }
            if (libraryPage != null) {
                libraryPage.setTranslationX((1f - slideProgress) * width);
            }
        }

        private void measureRest(View page, int height) {
            if (page == null || page.getVisibility() == GONE) {
                return;
            }
            int width = getWidth();
            if (width <= 0) {
                return;
            }
            page.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (!frozen) {
                measureFull(lyricsPage, width, height);
                measureFull(libraryPage, width, height);
            }
            setMeasuredDimension(width, height);
        }

        private static void measureFull(View page, int width, int height) {
            if (page == null || page.getVisibility() == GONE) {
                return;
            }
            page.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutPage(lyricsPage);
            layoutPage(libraryPage);
        }

        private static void layoutPage(View page) {
            if (page == null || page.getVisibility() == GONE) {
                return;
            }
            page.layout(0, 0, page.getMeasuredWidth(), page.getMeasuredHeight());
        }
    }
}
