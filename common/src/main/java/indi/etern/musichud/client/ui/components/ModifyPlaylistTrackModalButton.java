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
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.Privacy;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.client.ui.utils.ui.Easing;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ModifyPlaylistTrackModalButton extends ImageButton {
    private final Logger logger = MusicHud.getLogger(ModifyPlaylistTrackModalButton.class);
    private MusicDetail musicDetail;
    private IMusicTrackState musicTrackState;

    public ModifyPlaylistTrackModalButton(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER);
        String tooltip = I18n.get(MusicHud.MOD_ID + ".button.modifyCurrentMusicPlaylist");
        setTooltipText(tooltip);
        Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/list_plus.png");
        if (icon != null) {
            var resources = getContext().getResources();
            setImageDrawable(new ScaledImageDrawable(resources, icon, dp(8), dp(16), dp(16)));
        }
        setOnClickListener(v -> {
            if (musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
                showModal();
            }
        });
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

        MusicService.getInstance().loadUserPlaylists(false).thenAccept(userPlaylists -> {
            MuiModApi.postToUiThread(() -> {
                Context ctx = getContext();

                TextView titleView = new TextView(ctx);
                titleView.setText(I18n.get(MusicHud.MOD_ID + ".modal.modifyPlaylistTrack.title"));

                List<Playlist> availablePlaylists = new ArrayList<>();
                availablePlaylists.add(userPlaylists.getLikeList());
                availablePlaylists.addAll(userPlaylists.getCreatedPlaylist());

                Map<Long, IMusicTrackState.IPlaylistSubState> subStates = new ConcurrentHashMap<>();
                Map<Long, Boolean> originalStates = new ConcurrentHashMap<>();
                Map<Long, Boolean> currentStates = new ConcurrentHashMap<>();
                Set<Long> userToggled = ConcurrentHashMap.newKeySet();

                LinearLayout listLayout = new LinearLayout(ctx);
                listLayout.setOrientation(LinearLayout.VERTICAL);

                for (Playlist playlist : availablePlaylists) {
                    long pid = playlist.getId();
                    IMusicTrackState.IPlaylistSubState subState = trackState.playlist(pid);
                    subStates.put(pid, subState);

                    PlaylistToggleRow row = new PlaylistToggleRow(ctx, playlist, isChecked -> {
                        currentStates.put(pid, isChecked);
                        userToggled.add(pid);
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                    params.setMargins(0, 0, dp(4), 0);
                    listLayout.addView(row, params);

                    try {
                        subState.isContained().thenAccept(contained -> {
                            MuiModApi.postToUiThread(() -> {
                                if (!userToggled.contains(pid)) {
                                    originalStates.put(pid, contained);
                                    currentStates.put(pid, contained);
                                    row.setChecked(contained);
                                } else {
                                    originalStates.put(pid, contained);
                                }
                            });
                        });
                    } catch (Exception e) {
                        logger.warn("Failed to check if track is in playlist {}", playlist.getName(), e);
                    }
                }

                //noinspection UnstableApiUsage
                ClampingScrollView scrollView = new ClampingScrollView(ctx);
                scrollView.addView(listLayout, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                int scrollHeight = Math.clamp((long) dp(56) * availablePlaylists.size() + dp(16), dp(120), dp(360));
                scrollView.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, scrollHeight));

                LinearLayout contentLayout = new LinearLayout(ctx);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
                contentLayout.addView(scrollView);

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

                new Modal(ctx, titleView, contentLayout, confirmButton, cancelButton).show();
            });
        });
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
        private final Consumer<Boolean> onChange;

        PlaylistToggleRow(Context context, Playlist playlist, Consumer<Boolean> onChange) {
            super(context);
            this.onChange = onChange;

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

        private ValueAnimator currentAnimation;

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
