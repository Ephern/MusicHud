package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Queue;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainFragment extends Fragment {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final String PAGE_HOME = "Home";
    private static final String ICON_LIBRARY = "/assets/music_hud/textures/gui/icons/square_library.png";
    private static final String ICON_TURNTABLE = "/assets/music_hud/textures/gui/icons/turntable.png";
    private static final int NAVIGATOR_BACKGROUND_ANIMATION_DURATION = 300;
    private static volatile MainFragment instance = null;

    static {
        ConnectionStateMachine.getConnectStatusListeners().add(status -> {
            if (instance != null && instance.visible) {
                MuiModApi.postToUiThread(() -> instance.refreshServerConnectStatus());
            }
        });
    }

    private final NowPlayingInfo playingInfo = NowPlayingInfo.getInstance();
    private boolean visible = false;
    @Setter
    private int defaultSelectedIndex = 0;
    private Button switchServerConnectButton;
    @Getter
    private RouterContainer routerContainer;
    private Button sidebarButton;
    private SidebarView sidebar;
    private LinearLayout contentRow;
    private LinearLayout navigatorWrapper;
    private ValueAnimator navigatorBackgroundAnimator;
    /**
     * Mode remembered for non-Home pages; Home always shows the library.
     */
    private SidebarView.Mode otherPageMode = SidebarView.Mode.PLAYER;

    private MainFragment() {
    }

    public static void refresh() {
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            homeView.refresh();
        }
        SearchView searchView = SearchView.getInstance();
        if (searchView != null) {
            searchView.refresh();
        }
        AccountBaseView accountBaseView = AccountBaseView.getInstance();
        if (accountBaseView != null) {
            accountBaseView.refresh();
        }
        MainFragment target = instance;
        if (target != null && target.visible && target.sidebar != null) {
            target.sidebar.refresh();
            NowPlayingInfo info = NowPlayingInfo.getInstance();
            target.applyMusic(info.getCurrentlyPlayingMusic(), info.getNextToPlayMusic(),
                    info.getLyricLines(), true);
        }
    }

    public static void switchMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace, Queue<LyricLine> lines) {
        if (instance != null && instance.visible) {
            instance.applyMusic(musicDetailTrace, nextToPlayTrace, lines, true);
        }
    }

    /**
     * Re-applies the cover scale (e.g. after a mute toggle) on the UI thread.
     */
    public static void refreshCoverScale() {
        MainFragment target = instance;
        if (target == null) {
            return;
        }
        try {
            MuiModApi.postToUiThread(() -> {
                if (target.visible && target.sidebar != null) {
                    target.sidebar.updateCoverScale(true);
                }
                HomeView homeView = HomeView.getInstance();
                if (homeView != null) {
                    homeView.updateCoverScale(true);
                }
            });
        } catch (IllegalStateException ignored) {
            // UI not ready or shutting down
        }
    }

    public static void updateNextToPlay(Traceable<MusicDetail> nextToPlayTrace) {
        MainFragment target = instance;
        if (target != null && target.visible && target.sidebar != null) {
            target.sidebar.updateNextToPlay(nextToPlayTrace);
        }
    }

    public static void refreshLyricViews() {
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            StaggeredLyricScrollView staggeredLyricScrollView = homeView.getStaggeredLyricScrollView();
            if (staggeredLyricScrollView != null) {
                MuiModApi.postToUiThread(staggeredLyricScrollView::refreshLinesStyle);
            }
        }
        MainFragment target = instance;
        if (target != null && target.visible && target.sidebar != null) {
            MuiModApi.postToUiThread(target.sidebar::refreshLyrics);
        }
    }

    public static void refreshLyricsSidebarVisibility() {
        MainFragment target = instance;
        if (target != null && target.visible && target.sidebar != null) {
            target.sidebar.updateLyricsVisibility();
        }
    }

    public static @NonNull MainFragment getInstance() {
        if (instance == null) {
            synchronized (MainFragment.class) {
                if (instance == null) {
                    instance = new MainFragment();
                }
            }
        }
        return instance;
    }

    private static boolean hasMusic() {
        MusicDetail detail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        return detail != null && !detail.equals(MusicDetail.NONE);
    }

    private void applyMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace,
                            Queue<LyricLine> lines, boolean animate) {
        if (sidebar != null) {
            sidebar.bindMusic(musicDetailTrace, nextToPlayTrace, lines, animate);
        }
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            homeView.switchMusic(musicDetailTrace, lines);
        }
    }

    private void reset() {
        visible = false;
        defaultSelectedIndex = 0;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        try {
            visible = true;
            var context = requireContext();

            var root = new FrameLayout(context);
            root.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            root.addView(new MinecraftBackgroundView(context),
                    new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            var base = new FrameLayout(context);
            int dp12 = base.dp(12);
            base.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            root.addView(base, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            // Everything except the floating control bar keeps its original row arrangement.
            contentRow = new LinearLayout(context);
            contentRow.setOrientation(LinearLayout.HORIZONTAL);
            base.addView(contentRow, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            routerContainer = new RouterContainer(context);
            routerContainer.setAnimationStyle(RouterContainer.AnimationStyle.SCALE_FADE_ROOT);
            routerContainer.setAnimationDuration(300);
            var routerParams = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
            routerParams.setMargins(routerContainer.dp(32), 0, routerContainer.dp(32), 0);
            contentRow.addView(routerContainer, routerParams);

            sidebar = new SidebarView(context);
            sidebar.setVisibility(View.GONE);
            {
                FrameLayout.LayoutParams sidebarParams =
                        new FrameLayout.LayoutParams(sidebar.getPanelWidth(), MATCH_PARENT);
                sidebarParams.gravity = Gravity.TOP | Gravity.START;
                sidebarParams.setMargins(dp12, base.dp(64), 0, dp12);
                base.addView(sidebar, sidebarParams);
            }
            otherPageMode = hasMusic() ? SidebarView.Mode.PLAYER : SidebarView.Mode.LIBRARY;

            {
                LinearLayout controlBar = new LinearLayout(context);
                var params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params.gravity = Gravity.TOP | Gravity.START;
                params.setMargins(dp12, dp12, 0, 0);
                // Added after the content row so its blur can sample what is painted underneath.
                base.addView(controlBar, params);

                {
                    navigatorWrapper = new LinearLayout(context);
                    navigatorWrapper.setOrientation(LinearLayout.HORIZONTAL);
                    int dp2 = navigatorWrapper.dp(2);
                    PanelStyle.applyDynamically(navigatorWrapper, navigatorWrapper.dp(16),
                            new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2),
                            InsetBackgroundFactory.BackdropBlur.UI_LAYER);
                    // Runs after PanelStyle's attach listener, which rebuilds the background on re-attach.
                    navigatorWrapper.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            updateNavigatorBackground(false);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            cancelNavigatorBackgroundAnimator();
                        }
                    });
                    controlBar.addView(navigatorWrapper, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                    InsetBackgroundFactory factory = InsetBackgroundFactory.builder()
                            .padding(new InsetBackgroundFactory.Padding(controlBar.dp(2), controlBar.dp(1), controlBar.dp(2), controlBar.dp(1)))
                            .cornerRadius(controlBar.dp(14)).inset(controlBar.dp(1)).build();
                    {
                        sidebarButton = new Button(context) {
                            @Override
                            protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
                                Tooltip.handleHover(this, event);
                                return super.dispatchHoverEvent(event);
                            }
                        };
                        sidebarButton.setTextSize(Theme.TEXT_SIZE_LARGE);
                        sidebarButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                        sidebarButton.setGravity(Gravity.CENTER);
                        factory.applyBackgroundTo(sidebarButton);
                        sidebarButton.setOnClickListener(b -> toggleSidebar());
                        sidebarButton.setWidth(sidebarButton.dp(40));
                        sidebarButton.setHeight(sidebarButton.dp(40));

                        navigatorWrapper.addView(sidebarButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                        refreshSidebarStatus();
                    }
                    {
                        View divider = new View(context);
                        ShapeDrawable dividerBg = new ShapeDrawable();
                        dividerBg.setShape(ShapeDrawable.VLINE);
                        dividerBg.setSize(divider.dp(1), divider.dp(1));
                        dividerBg.setColor(Theme.DIVIDER_COLOR);
                        divider.setBackground(dividerBg);
                        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(divider.dp(1), MATCH_PARENT);
                        dividerParams.setMargins(divider.dp(2), divider.dp(8), divider.dp(2), divider.dp(8));
                        navigatorWrapper.addView(divider, dividerParams);
                    }
                    {
                        NavigatorWidget navigator = new NavigatorWidget(context, routerContainer);
                        if (Minecraft.getInstance().player != null) {//in game
                            var homeNav = navigator.createNavigationPage("Home", "/assets/music_hud/textures/gui/icons/house.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.home"), HomeView::new);
                            var searchNav = navigator.createNavigationPage("Search", "/assets/music_hud/textures/gui/icons/search.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.search"), SearchView::new);
                            var accountNav = navigator.createNavigationPage("Account", "/assets/music_hud/textures/gui/icons/square_user_round.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.account"), AccountBaseView::new);
                            var settingsNav = navigator.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                            NavigatorWidget.NavigationMeta defaultMeta = List.of(homeNav, searchNav, accountNav, settingsNav).get(defaultSelectedIndex);
                            defaultMeta.select();
                        } else {
                            var settingsNav = navigator.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                            settingsNav.select();
                        }
                        navigatorWrapper.addView(navigator, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                    }
                    {
                        View divider = new View(context);
                        ShapeDrawable dividerBg = new ShapeDrawable();
                        dividerBg.setShape(ShapeDrawable.VLINE);
                        dividerBg.setSize(divider.dp(1), divider.dp(1));
                        dividerBg.setColor(Theme.DIVIDER_COLOR);
                        divider.setBackground(dividerBg);
                        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(divider.dp(1), MATCH_PARENT);
                        dividerParams.setMargins(divider.dp(2), divider.dp(8), divider.dp(2), divider.dp(8));
                        navigatorWrapper.addView(divider, dividerParams);
                    }
                    {
                        switchServerConnectButton = new Button(context) {
                            @Override
                            protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
                                Tooltip.handleHover(this, event);
                                return super.dispatchHoverEvent(event);
                            }
                        };
                        switchServerConnectButton.setTextSize(Theme.TEXT_SIZE_LARGE);
                        switchServerConnectButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                        switchServerConnectButton.setGravity(Gravity.CENTER);
                        factory.applyBackgroundTo(switchServerConnectButton);
                        switchServerConnectButton.setOnClickListener(b -> connectionManager.toggleConnection());
                        switchServerConnectButton.setWidth(switchServerConnectButton.dp(40));
                        switchServerConnectButton.setHeight(switchServerConnectButton.dp(40));

                        navigatorWrapper.addView(switchServerConnectButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                        refreshServerConnectStatus();
                    }
                }
            }
            routerContainer.setOnPageChangeListener(new RouterContainer.OnPageChangeListener() {
                @Override
                public void onPageChangeStart(@Nullable String fromKey, @NonNull String toKey) {
                    // Only a Home transition changes the sidebar's docked state, so serialize those.
                    boolean sidebarChanges = PAGE_HOME.equals(fromKey) || PAGE_HOME.equals(toKey);
                    routerContainer.setTransitionType(
                            sidebarChanges ? RouterContainer.TransitionType.SERIAL : RouterContainer.TransitionType.CROSS
                    );
                }

                @Override
                public void onPageChangeEnd(@NonNull String pageKey) {
                    // Late safety net: the panel may have been built after the initial navigation.
                    updateSidebarForPage();
                    updateNavigatorBackground(false);
                    refreshSidebarStatus();
                }

                @Override
                public void onTransitionStart(@Nullable String fromKey, @NonNull String toKey,
                                              @NonNull RouterContainer.TransitionType type) {
                    // transitionTargetKey is already set, so isHomePage() resolves the incoming page.
                    updateNavigatorBackground(true);
                    if ("Home".equals(toKey)) {
                        HomeView homeView = HomeView.getInstance();
                        if (homeView != null) {
                            StaggeredLyricScrollView scrollView = homeView.getStaggeredLyricScrollView();
                            if (scrollView != null) {
                                // HomeView is created lazily, so lyrics switched while another page was
                                // shown were dropped. Push the current state before reinitializing; the
                                // instance-based check avoids replaying the slide-in for the same content.
                                MusicDetail currentMusic = playingInfo.getCurrentlyPlayingMusicDetail();
                                Queue<LyricLine> currentLyrics = playingInfo.getLyricLines();
                                if (!scrollView.isShowing(currentMusic, currentLyrics)) {
                                    scrollView.switchLyrics(currentMusic, currentLyrics);
                                }
                                scrollView.reinitialize();
                            }
                        }
                        // The panel docks on every other page; entering Home always closes it.
                        if (sidebar != null) {
                            sidebar.setPlayerVisible(false, fromKey != null);
                            sidebar.hide(fromKey != null);
                        }
                    }
                }

                @Override
                public void onBeforeSwap(@Nullable String fromKey, @NonNull String toKey,
                                         @NonNull RouterContainer.TransitionType type) {
                    if (!"Home".equals(toKey)) {
                        HomeView homeView = HomeView.getInstance();
                        if (homeView != null) {
                            StaggeredLyricScrollView scrollView = homeView.getStaggeredLyricScrollView();
                            if (scrollView != null) {
                                scrollView.suspendLyricFollowingAndHide();
                            }
                        }
                    }
                    syncSidebarBeforeSwap();
                }
            });

            NowPlayingInfo info = NowPlayingInfo.getInstance();
            applyMusic(info.getCurrentlyPlayingMusic(), info.getNextToPlayMusic(),
                    playingInfo.getLyricLines(), false);
            updateSidebarForPage();
            updateNavigatorBackground(false);

            return root;
        } catch (Exception e) {
            visible = false;
            throw e;
        }
    }

    /**
     * Current page key; a null router or an unterminated initial navigation counts as Home.
     */
    private boolean isHomePage() {
        // Use the router of this view: the static instance is only set once attached, and the
        // initial navigation (e.g. to Home) already happened while building the view.
        if (routerContainer == null) {
            return true;
        }
        String pageKey = routerContainer.getEffectivePageKey();
        return pageKey == null || PAGE_HOME.equals(pageKey);
    }

    /**
     * Applies the sidebar state for the current page: Home floats it (library only, visibility is
     * user controlled), every other page docks it and always shows it.
     */
    private void updateSidebarForPage() {
        if (sidebar == null) {
            return;
        }
        if (isHomePage()) {
            setContentLeftPadding(0);
            sidebar.setMode(SidebarView.Mode.LIBRARY);
            sidebar.setPlayerVisible(false, false);
        } else {
            setContentLeftPadding(sidebar.getDockedWidth());
            sidebar.setPlayerVisible(true, false);
            sidebar.setMode(otherPageMode);
            sidebar.show(false);
        }
        refreshSidebarStatus();
    }

    /**
     * Frame-aligned variant for the router's swap hook: the incoming page must be laid out at its
     * final size, so a hide is applied immediately instead of sliding out into a reflow.
     */
    private void syncSidebarBeforeSwap() {
        if (sidebar == null) {
            return;
        }
        if (isHomePage()) {
            setContentLeftPadding(0);
            sidebar.setMode(SidebarView.Mode.LIBRARY, true);
            sidebar.setPlayerVisible(false, true);
            sidebar.hide(false);
        } else {
            setContentLeftPadding(sidebar.getDockedWidth());
            sidebar.setMode(otherPageMode, true);
            sidebar.setPlayerVisible(true, true);
            sidebar.show(true);
        }
        sidebar.updateLyricsVisibility();
    }

    private void setContentLeftPadding(int left) {
        if (contentRow != null && contentRow.getPaddingLeft() != left) {
            contentRow.setPadding(left, 0, contentRow.getPaddingRight(), 0);
        }
    }

    /**
     * Home hides the navigator panel so its icons float over the content; other pages always show it.
     */
    private void updateNavigatorBackground(boolean animate) {
        if (navigatorWrapper == null) {
            return;
        }
        cancelNavigatorBackgroundAnimator();
        float target = isHomePage() ? 0f : 1f;
        float current = getNavigatorBackgroundAlpha();
        if (!animate || Math.abs(current - target) < 0.001f) {
            setNavigatorBackgroundAlpha(target);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(current, target);
        animator.setDuration(NAVIGATOR_BACKGROUND_ANIMATION_DURATION);
        animator.setInterpolator(Easing.EASE_IN_OUT_CUBIC);
        animator.addUpdateListener(a -> setNavigatorBackgroundAlpha((Float) a.getAnimatedValue()));
        navigatorBackgroundAnimator = animator;
        animator.start();
    }

    private void cancelNavigatorBackgroundAnimator() {
        if (navigatorBackgroundAnimator != null) {
            navigatorBackgroundAnimator.cancel();
            navigatorBackgroundAnimator = null;
        }
    }

    private float getNavigatorBackgroundAlpha() {
        Drawable background = navigatorWrapper != null ? navigatorWrapper.getBackground() : null;
        return background != null ? background.getAlpha() : 1f;
    }

    private void setNavigatorBackgroundAlpha(float alpha) {
        Drawable background = navigatorWrapper != null ? navigatorWrapper.getBackground() : null;
        if (background != null) {
            background.setAlpha(alpha);
        }
    }

    /**
     * Toggles the sidebar: open/close on Home, player/library content elsewhere.
     */
    private void toggleSidebar() {
        if (sidebar == null) {
            return;
        }
        if (isHomePage()) {
            // Home never shows the player page, even if the mode switch raced the page transition.
            sidebar.setMode(SidebarView.Mode.LIBRARY, true);
            sidebar.setPlayerVisible(false, false);
            if (sidebar.isVisible()) {
                sidebar.hide(true);
            } else {
                sidebar.show(true);
            }
        } else {
            otherPageMode = sidebar.getMode() == SidebarView.Mode.PLAYER
                    ? SidebarView.Mode.LIBRARY : SidebarView.Mode.PLAYER;
            sidebar.setMode(otherPageMode, true);
        }
        refreshSidebarStatus();
    }

    private void refreshSidebarStatus() {
        if (sidebarButton == null) {
            return;
        }
        sidebarButton.setVisibility(View.VISIBLE);
        String icon;
        String tooltip;
        if (isHomePage()) {
            icon = ICON_LIBRARY;
            boolean open = sidebar != null && sidebar.isVisible();
            tooltip = I18n.get(MusicHud.MOD_ID + (open
                    ? ".text.sidebar.closeLibrary" : ".text.sidebar.openLibrary"));
        } else if (sidebar != null && sidebar.getMode() == SidebarView.Mode.PLAYER) {
            icon = ICON_TURNTABLE;
            tooltip = I18n.get(MusicHud.MOD_ID + ".text.sidebar.switchToLibrary");
        } else {
            icon = ICON_LIBRARY;
            tooltip = I18n.get(MusicHud.MOD_ID + ".text.sidebar.switchToPlayer");
        }
        Tooltip.attach(sidebarButton, tooltip);
        SpannableString string = new SpannableString(" ");
        Image imageFromResource = ImageUtils.getImageFromResource(icon);
        if (imageFromResource != null) {
            string.setSpan(ImageUtils.getIconSpan(imageFromResource), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sidebarButton.setText(string);
    }

    private void refreshServerConnectStatus() {
        if (Minecraft.getInstance().player != null) {
            switchServerConnectButton.setVisibility(View.VISIBLE);
            boolean singlePlayer = Minecraft.getInstance().getCurrentServer() == null;
            String buttonText = "";
            String icon = "";
            switch (ConnectionStateMachine.getConnectStatus()) {
                case CONNECTED -> {
                    icon = "/assets/music_hud/textures/gui/icons/link.png";
                    if (singlePlayer) {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.connected.integrated");
                        switchServerConnectButton.setEnabled(false);
                        Tooltip.attach(switchServerConnectButton, buttonText);
                    } else {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.connected");
                        switchServerConnectButton.setEnabled(true);
                        Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.disconnect"));
                    }
                }
                case NOT_CONNECTED -> {
                    icon = "/assets/music_hud/textures/gui/icons/unlink.png";
                    if (clientConfig.getEnableIsolatedMode()) {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.notConnected.isolated");
                    } else {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.notConnected");
                    }
                    switchServerConnectButton.setEnabled(true);
                    Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
                case INCOMPATIBLE -> {
                    icon = "/assets/music_hud/textures/gui/icons/unlink.png";
                    String template;
                    if (clientConfig.getEnableIsolatedMode()) {
                        template = I18n.get(buttonText + "\n" + MusicHud.MOD_ID + ".text.incompatibleWithServer.isolated");
                    } else {
                        template = I18n.get(buttonText + "\n" + MusicHud.MOD_ID + ".text.incompatibleWithServer");
                    }
                    buttonText = template.replace("{version}", connectionManager.getServerVersion().toString());
                    switchServerConnectButton.setEnabled(false);
                    Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
            }
            SpannableString string = new SpannableString(" ");
            Image imageFromResource = ImageUtils.getImageFromResource(icon);
            if (imageFromResource != null) {
                string.setSpan(ImageUtils.getIconSpan(imageFromResource), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            switchServerConnectButton.setText(string);
        } else {
            switchServerConnectButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelNavigatorBackgroundAnimator();
        navigatorWrapper = null;
        sidebar = null;
        contentRow = null;
        routerContainer = null;
        reset();
    }
}
