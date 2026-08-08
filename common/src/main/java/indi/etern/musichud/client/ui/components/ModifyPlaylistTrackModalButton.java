package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.Privacy;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ModifyPlaylistTrackModalButton extends ImageButton {
    private final Logger logger = MusicHud.getLogger(ModifyPlaylistTrackModalButton.class);
    private final String normalTooltip;
    private MusicDetail musicDetail;
    private IMusicTrackState musicTrackState;
    private Unregister loginStateUnregister;

    public ModifyPlaylistTrackModalButton(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER);
        normalTooltip = I18n.get(MusicHud.MOD_ID + ".button.modifyMusicTrackPlaylist");
        setTooltipText(normalTooltip);
        Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/list_plus.png");
        if (icon != null) {
            var resources = getContext().getResources();
            setImageDrawable(new ScaledImageDrawable(resources, icon, dp(16), dp(16)));
        }
        loginStateUnregister = LoginService.getInstance().addLoginStateListener(state ->
                MuiModApi.postToUiThread(() -> updateLoginState(state == IClientLoginService.LoginState.LOGGED_IN)));
        updateLoginState(LoginService.getInstance().isLogined());
        setOnClickListener(v -> {
            if (musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
                showModal();
            }
        });
    }

    private void updateLoginState(boolean loggedIn) {
        if (loggedIn) {
            setEnabled(true);
            setTooltipText(normalTooltip);
        } else {
            setEnabled(false);
            setTooltipText(I18n.get(MusicHud.MOD_ID + ".text.loginRequired"));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (loginStateUnregister != null) {
            loginStateUnregister.unregister();
            loginStateUnregister = null;
        }
    }

    public void bindMusicDetail(MusicDetail musicDetail) {
        this.musicDetail = musicDetail;
        if (musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
            musicTrackState = MusicService.getInstance().getMusicTrackState(musicDetail);
        } else {
            musicTrackState = null;
        }
    }

    private void showModal() {
        IMusicTrackState trackState = musicTrackState;
        if (trackState == null) return;

        Context ctx = getContext();

        TextView titleView = new TextView(ctx);
        titleView.setText(I18n.get(MusicHud.MOD_ID + ".modal.modifyPlaylistTrack.title"));

        List<Playlist> availablePlaylists = new ArrayList<>();
        Map<Long, IMusicTrackState.IPlaylistSubState> subStates = new HashMap<>();
        Map<Long, Boolean> originalStates = new HashMap<>();
        Map<Long, Boolean> currentStates = new HashMap<>();
        Map<Long, Boolean> containedResults = new ConcurrentHashMap<>();
        AtomicInteger generation = new AtomicInteger();

        FrameLayout contentLayout = new FrameLayout(ctx);

        ProgressBar progressRing = new ProgressBar(ctx);
        progressRing.setIndeterminate(true);
        progressRing.setIndeterminateTintList(ColorStateList.valueOf(Theme.PRIMARY_COLOR));
        int dp160 = dp(160);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(WRAP_CONTENT, dp160);
        ringParams.gravity = Gravity.CENTER;
        contentLayout.addView(progressRing, ringParams);

        LinearLayout errorLayout = new LinearLayout(ctx);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setVisibility(GONE);

        TextView errorText = new TextView(ctx);
        errorText.setText(I18n.get(MusicHud.MOD_ID + ".modal.modifyPlaylistTrack.loadError"));
        errorText.setTextSize(Theme.TEXT_SIZE_LARGE);
        errorText.setTextColor(Theme.ERROR_TEXT_COLOR);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        errorLayout.addView(errorText, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Button retryButton = new Button(ctx);
        retryButton.setText(I18n.get(MusicHud.MOD_ID + ".button.retry"));
        retryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        retryButton.setTextColor(Theme.PRIMARY_COLOR);
        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .padding(new InsetBackgroundFactory.Padding(retryButton.dp(2), retryButton.dp(1), retryButton.dp(2), retryButton.dp(1)))
                .cornerRadius(retryButton.dp(4)).inset(dp(1)).build();
        backgroundFactory.applyBackgroundTo(retryButton);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        retryParams.setMargins(0, dp(4), 0, 0);
        errorLayout.addView(retryButton, retryParams);

        contentLayout.addView(errorLayout, new FrameLayout.LayoutParams(MATCH_PARENT, dp160));

        //noinspection UnstableApiUsage
        ClampingScrollView scrollView = new ClampingScrollView(ctx);
        LinearLayout listLayout = new LinearLayout(ctx);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        scrollView.setVisibility(GONE);
        contentLayout.addView(scrollView, new FrameLayout.LayoutParams(MATCH_PARENT, dp160));

        Modal.ActionButton confirmButton = new Modal.ActionButton(
                I18n.get(MusicHud.MOD_ID + ".button.confirm"),
                (actionButton, modal) -> {
                    for (Playlist playlist : availablePlaylists) {
                        long pid = playlist.getId();
                        Boolean original = originalStates.get(pid);
                        Boolean current = currentStates.get(pid);
                        if (original != null && current != null && !Objects.equals(original, current)) {
                            IMusicTrackState.IPlaylistSubState subState = subStates.get(pid);
                            if (current) {
                                subState.add();
                            } else {
                                subState.remove();
                            }
                        }
                    }
                    modal.dismiss();
                }
        );

        Modal.ActionButton cancelButton = new Modal.ActionButton(
                I18n.get(MusicHud.MOD_ID + ".button.cancel"),
                (actionButton, modal) -> modal.dismiss()
        );

        Modal modal = new Modal(ctx, titleView, contentLayout, confirmButton, cancelButton);
        modal.show();

        Runnable startLoad = () -> {
            int gen = generation.incrementAndGet();
            MuiModApi.postToUiThread(() -> {
                progressRing.setVisibility(VISIBLE);
                errorLayout.setVisibility(GONE);
                scrollView.setVisibility(GONE);
                confirmButton.setEnabled(false);
            });
            MusicService.getInstance().loadUserCollections(false)
                    .thenCompose(userCollections -> {
                        UserCategoryPlaylists userPlaylists = userCollections.getUserCategoryPlaylists();
                        if (generation.get() != gen) {
                            return CompletableFuture.completedFuture(null);
                        }
                        availablePlaylists.clear();
                        subStates.clear();
                        originalStates.clear();
                        currentStates.clear();
                        containedResults.clear();
                        listLayout.removeAllViews();
                        availablePlaylists.add(userPlaylists.getLikeList());
                        availablePlaylists.addAll(userPlaylists.getCreatedPlaylist());
                        List<CompletableFuture<?>> futures = new ArrayList<>(availablePlaylists.size());
                        for (Playlist playlist : availablePlaylists) {
                            long pid = playlist.getId();
                            IMusicTrackState.IPlaylistSubState subState = trackState.playlist(pid);
                            subStates.put(pid, subState);
                            futures.add(subState.isContained()
                                    .thenAccept(contained -> containedResults.put(pid, contained)));
                        }
                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    })
                    .whenComplete((v, throwable) -> {
                        if (generation.get() != gen) return;
                        MuiModApi.postToUiThread(() -> {
                            if (generation.get() != gen) return;
                            if (throwable != null) {
                                logger.warn("Failed to load playlist states for track {}", musicDetail, throwable);
                                progressRing.setVisibility(GONE);
                                scrollView.setVisibility(GONE);
                                errorLayout.setVisibility(VISIBLE);
                                confirmButton.setEnabled(false);
                                return;
                            }
                            for (Playlist playlist : availablePlaylists) {
                                long pid = playlist.getId();
                                Boolean contained = containedResults.get(pid);
                                if (contained == null) continue;
                                PlaylistToggleRow row = new PlaylistToggleRow(ctx, playlist, isChecked -> currentStates.put(pid, isChecked));
                                row.setChecked(contained);
                                originalStates.put(pid, contained);
                                currentStates.put(pid, contained);
                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                                params.setMargins(0, 0, dp(4), 0);
                                listLayout.addView(row, params);
                            }
                            int scrollHeight = Math.clamp((long) dp(56) * availablePlaylists.size() + dp(16), dp(120), dp(360));
                            scrollView.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, scrollHeight));
                            progressRing.setVisibility(GONE);
                            errorLayout.setVisibility(GONE);
                            scrollView.setVisibility(VISIBLE);
                            confirmButton.setEnabled(true);
                        });
                    });
        };
        retryButton.setOnClickListener(v -> startLoad.run());
        startLoad.run();
    }

    private static class PlaylistToggleRow extends LinearLayout {
        private static final int COVER_SIZE_DP = 48;
        private static final int ANIM_DURATION = 200;

        private static final int TEXT_COLOR_CHECKED = 0xFF000000;
        private static final int TEXT_COLOR_UNCHECKED = Theme.NORMAL_TEXT_COLOR;
        private static final int COUNT_COLOR_CHECKED = 0xFF333333;
        private static final int COUNT_COLOR_UNCHECKED = Theme.SECONDARY_TEXT_COLOR;

        private static final int BG_COLOR_BLANK = 0x00000000;
        private static final int BG_COLOR_HIGHLIGHT = 0xD9E0BFB7;

        private final ShapeDrawable bgDrawable;
        private final TextView nameText;
        private final TextView countText;
        private int currentBgColor = BG_COLOR_BLANK;
        private boolean checked;
        private ValueAnimator currentAnimation;

        PlaylistToggleRow(Context context, Playlist playlist, Consumer<Boolean> onChange) {
            super(context);

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(COVER_SIZE_DP) + dp(12));
            int p = dp(8);
            setPadding(p, p, p, p);

            bgDrawable = new ShapeDrawable();
            bgDrawable.setCornerRadius(dp(6));
            bgDrawable.setColor(ColorStateList.valueOf(BG_COLOR_BLANK));
            setBackground(new InsetDrawable(bgDrawable, dp(2)));

            UrlImageView coverView = new UrlImageView(context);
            coverView.loadUrl(playlist.getThumbnailCoverUrl(dp(COVER_SIZE_DP)));
            coverView.setCornerRadius(dp(4));

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(VERTICAL);

            String displayName = playlist.getName();
            if (playlist.getPrivacy() == Privacy.PRIVATE
                    && !playlist.getCreator().equals(ProfileConfigData.getInstance().getProfile())) {
                displayName = I18n.get(MusicHud.MOD_ID + ".text.privatePlaylist");
            }
            nameText = new TextView(context);
            nameText.setText(displayName);
            nameText.setSingleLine(false);
            nameText.setMaxLines(2);
            nameText.setTextColor(TEXT_COLOR_UNCHECKED);
            nameText.setTextSize(Theme.TEXT_SIZE_LARGE);

            countText = new TextView(context);
            countText.setText(I18n.get(MusicHud.MOD_ID + ".text.totalCount")
                    .replace("{}", String.valueOf(playlist.getMusicTrackCount())));
            countText.setTextColor(COUNT_COLOR_UNCHECKED);
            countText.setTextSize(Theme.TEXT_SIZE_NORMAL);

            textColumn.addView(nameText);
            textColumn.addView(countText);

            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(COVER_SIZE_DP), dp(COVER_SIZE_DP));
            coverParams.setMargins(dp(4), 0, dp(8), 0);
            addView(coverView, coverParams);

            addView(textColumn, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

            setOnClickListener(v -> {
                boolean newState = !this.checked;
                animateToState(newState);
                this.checked = newState;
                if (onChange != null) onChange.accept(newState);
            });
        }

        void setChecked(boolean checked) {
            if (this.checked == checked) return;
            this.checked = checked;
            animateToState(checked);
        }

        private void animateToState(boolean checked) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
            }

            int targetBg = checked ? BG_COLOR_HIGHLIGHT : BG_COLOR_BLANK;
            int targetName = checked ? TEXT_COLOR_CHECKED : TEXT_COLOR_UNCHECKED;
            int targetCount = checked ? COUNT_COLOR_CHECKED : COUNT_COLOR_UNCHECKED;

            int startBg = currentBgColor;
            int startName = Color.toArgb(nameText.getCurrentTextColor());
            int startCount = Color.toArgb(countText.getCurrentTextColor());
            currentBgColor = targetBg;

            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.addUpdateListener(a -> {
                float t = a.getAnimatedFraction();
                bgDrawable.setColor(ColorStateList.valueOf(ColorEvaluator.evaluate(t, startBg, targetBg)));
                nameText.setTextColor(ColorEvaluator.evaluate(t, startName, targetName));
                countText.setTextColor(ColorEvaluator.evaluate(t, startCount, targetCount));
                invalidate();
            });
            anim.setDuration(ANIM_DURATION);
            anim.setInterpolator(Easing.EASE_OUT_QUAD);
            currentAnimation = anim;
            anim.start();
        }
    }
}
