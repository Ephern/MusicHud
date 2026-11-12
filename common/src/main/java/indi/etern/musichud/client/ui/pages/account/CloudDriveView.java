package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.result.ActionResult;
import indi.etern.musichud.beans.user.cloud.CloudTrackInfo;
import indi.etern.musichud.beans.user.cloud.CloudTracksPage;
import indi.etern.musichud.client.dto.CloudEntryState;
import indi.etern.musichud.client.dto.CloudTrackEntry;
import indi.etern.musichud.client.services.cloud.CloudUploadService;
import indi.etern.musichud.client.services.cloud.CloudUploadTask;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.CloudTrackItem;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.Tooltip;
import indi.etern.musichud.client.ui.components.modals.Modal;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.layouts.CloudTrackItemAdapter;
import indi.etern.musichud.client.ui.layouts.VirtualizedListLayout;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.ByteUnitFormatter;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.LayoutTransitionReflow;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Paths;
import java.util.*;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class CloudDriveView extends FrameLayout {
    private static final int PAGE_SIZE = 100;

    private final LinearLayout topBar;
    private final ProgressBar loadingRing;
    private final VirtualizedListLayout<CloudTrackEntry, CloudTrackItem> virtualList;
    private final TextView emptyText;
    private final TextView errorText;
    private final ScrollView scrollView;

    private final Map<Long, CloudTrackInfo> serverTracks = new LinkedHashMap<>();
    private final Set<Long> presentedResolvedIds = new HashSet<>();
    private final Set<Long> scheduledCompletion = new HashSet<>();
    private final ProgressBar usageBar;
    private final LinearLayout usageInfo;
    private final TextView usageText;
    private final int usageBarWidth;
    private Set<Long> lastEntryIds = Set.of();

    private boolean loading;
    private boolean endReached;
    private int total = -1;
    private Unregister uploadChangeUnregister;

    public CloudDriveView(Context context) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        FrameLayout content = new FrameLayout(context);
        addView(content, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setClipToPadding(false);
        content.addView(scrollView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        virtualList = new VirtualizedListLayout<>(context, new CloudTrackItemAdapter(
                this::confirmDelete, this::retryTask, this::cancelTask, this::removeTask));
        virtualList.setDefaultItemHeight(dp(72));
        scrollView.addView(virtualList, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            updateVirtualWindow();
            maybeLoadMore(scrollY, v.getHeight());
        });
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateVirtualWindow());
        scrollView.post(this::updateVirtualWindow);

        loadingRing = new ProgressBar(context);
        loadingRing.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        progressParams.gravity = Gravity.CENTER;
        content.addView(loadingRing, progressParams);
        loadingRing.setVisibility(GONE);

        emptyText = new TextView(context);
        emptyText.setText(I18n.get(MusicHud.MOD_ID + ".text.cloudEmpty"));
        emptyText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        emptyText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(GONE);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        emptyParams.gravity = Gravity.CENTER;
        content.addView(emptyText, emptyParams);

        errorText = new TextView(context);
        errorText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        errorText.setTextColor(Theme.ERROR_TEXT_COLOR);
        errorText.setGravity(Gravity.CENTER);
        errorText.setVisibility(GONE);
        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        errorParams.gravity = Gravity.CENTER;
        content.addView(errorText, errorParams);

        topBar = new LinearLayout(context);
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        LayoutTransitionReflow.attach(layoutTransition);
        topBar.setLayoutTransition(layoutTransition);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClickable(true);

        InsetBackgroundFactory buttonBackgroundFactory = InsetBackgroundFactory.builder()
                .inset(dp(1))
                .cornerRadius(dp(14))
                .build();

        ImageButton backButton = new ImageButton(context);
        Tooltip.attach(backButton, I18n.get(MusicHud.MOD_ID + ".button.back"));
        Image backIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/arrow_left.png");
        if (backIcon != null) {
            backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), backIcon, dp(16), dp(16)));
        }
        backButton.setOnClickListener(view -> {
            RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
            if (routerContainer != null) {
                routerContainer.popNavigate();
            }
        });
        buttonBackgroundFactory.applyBackgroundTo(backButton);
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(context);
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        title.setText(I18n.get(MusicHud.MOD_ID + ".button.cloud"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        titleParams.setMargins(dp(4), 0, dp(4), 0);
        topBar.addView(title, titleParams);

        ImageButton refreshButton = new ImageButton(context);
        buttonBackgroundFactory.applyBackgroundTo(refreshButton);
        Tooltip.attach(refreshButton, I18n.get(MusicHud.MOD_ID + ".button.refresh"));
        refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image refreshIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
        if (refreshIcon != null) {
            refreshButton.setImageDrawable(new InsetDrawable(
                    new ScaledImageDrawable(getContext().getResources(), refreshIcon, dp(12), dp(16)), dp(3)));
        }
        refreshButton.setOnClickListener(view -> refresh());
        topBar.addView(refreshButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        ImageButton uploadButton = new ImageButton(context);
        buttonBackgroundFactory.applyBackgroundTo(uploadButton);
        Tooltip.attach(uploadButton, I18n.get(MusicHud.MOD_ID + ".button.upload"));
        uploadButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image uploadIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/upload.png");
        if (uploadIcon != null) {
            uploadButton.setImageDrawable(new InsetDrawable(
                    new ScaledImageDrawable(getContext().getResources(), uploadIcon, dp(12), dp(16)), dp(3)));
        }
        uploadButton.setOnClickListener(view -> pickAndUpload());
        topBar.addView(uploadButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        usageInfo = new LinearLayout(context);
        usageInfo.setVisibility(GONE);
        usageInfo.setOrientation(LinearLayout.HORIZONTAL);
        usageInfo.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams usageParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        usageParams.setMargins(0, 0, dp(12), 0);
        topBar.addView(usageInfo, usageParams);
        {
            View usageDivider = new View(context);
            usageDivider.setVisibility(GONE);
            ShapeDrawable dividerBg = new ShapeDrawable();
            dividerBg.setShape(ShapeDrawable.VLINE);
            dividerBg.setSize(usageDivider.dp(1), usageDivider.dp(1));
            dividerBg.setColor(Theme.DIVIDER_COLOR);
            usageDivider.setBackground(dividerBg);
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(usageDivider.dp(1), MATCH_PARENT);
            dividerParams.setMargins(usageDivider.dp(2), usageDivider.dp(8),
                    usageDivider.dp(2), usageDivider.dp(8));
            topBar.addView(usageDivider, dividerParams);
        }
        {
            usageBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
            usageBarWidth = dp(160);
            usageBar.setMax(usageBarWidth);
            LinearLayout.LayoutParams usageBarParams = new LinearLayout.LayoutParams(usageBarWidth, WRAP_CONTENT);
            usageBarParams.setMargins(dp(12), 0, dp(8), 0);
            usageInfo.addView(usageBar, usageBarParams);
        }
        {
            usageText = new TextView(context, null);
            usageText.setTextSize(Theme.TEXT_SIZE_NORMAL);
            usageText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            usageInfo.addView(usageText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }

        PanelStyle.applyDynamically(topBar, dp(16),
                new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(12), 0, 0);
        // Added last so its backdrop blur samples the list painted underneath.
        addView(topBar, topBarParams);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (uploadChangeUnregister == null) {
                    uploadChangeUnregister = CloudUploadService.getInstance()
                            .addOnChange(() -> MuiModApi.postToUiThread(CloudDriveView.this::rebuildEntries));
                }
                if (serverTracks.isEmpty() && !loading && !endReached) {
                    refresh();
                } else {
                    rebuildEntries();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (uploadChangeUnregister != null) {
                    uploadChangeUnregister.unregister();
                    uploadChangeUnregister = null;
                }
                scheduledCompletion.clear();
            }
        });
    }

    /**
     * The list inset equals the space the floating bar occupies, which is only known after measure.
     * Applying it before laying children out keeps the first frame below the bar.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        int inset = lp.topMargin + topBar.getMeasuredHeight();
        if (scrollView.getPaddingTop() != inset) {
            scrollView.setPadding(0, inset, 0, 0);
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private void updateVirtualWindow() {
        int viewport = scrollView.getHeight();
        if (viewport <= 0) {
            return;
        }
        // The list starts below the bar, so shift the window up by that inset to keep the
        // content covered by the bar realized (it is what the bar's blur samples).
        virtualList.updateWindow(scrollView.getScrollY() - scrollView.getPaddingTop(), viewport);
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refresh() {
        serverTracks.clear();
        presentedResolvedIds.clear();
        lastEntryIds = Set.of();
        total = -1;
        endReached = false;
        loading = false;
        errorText.setVisibility(GONE);
        emptyText.setVisibility(GONE);
        CloudUploadService.getInstance().prunePresented();
        virtualList.resetItems(List.of());
        loadNext(true);
    }

    private void loadNext(boolean firstPage) {
        if (loading || endReached) {
            return;
        }
        loading = true;
        if (firstPage) {
            loadingRing.setVisibility(VISIBLE);
        }
        MusicService.getInstance().loadCloudTracks(serverTracks.size(), PAGE_SIZE).thenAccept(result ->
                MuiModApi.postToUiThread(() -> {
                    if (!isAttachedToWindow()) {
                        return;
                    }
                    loading = false;
                    loadingRing.setVisibility(GONE);

                    CloudTracksPage page = result.extraData();
                    long usedBytes = page.usedBytes();
                    long maxBytes = page.maxBytes();
                    usageBar.setProgress((int) (usedBytes * usageBarWidth / maxBytes));
                    usageText.setText(ByteUnitFormatter.formatSize(usedBytes) + " / " + ByteUnitFormatter.formatSize(maxBytes));
                    usageInfo.setVisibility(VISIBLE);

                    if (result.actionResult() != ActionResult.SUCCESS) {
                        showError(result.message());
                        return;
                    }
                    errorText.setVisibility(GONE);
                    int before = serverTracks.size();
                    for (CloudTrackInfo info : page.tracks()) {
                        if (info == null || info.detail() == null) {
                            continue;
                        }
                        serverTracks.putIfAbsent(info.detail().getId(), info);
                    }
                    CloudUploadService.getInstance().reconcileMatches(page.tracks());
                    int added = serverTracks.size() - before;
                    if (page.total() > 0) {
                        total = page.total();
                    }
                    if (page.tracks().isEmpty() || added == 0 || (total > 0 && serverTracks.size() >= total)) {
                        endReached = true;
                    }
                    rebuildEntries();
                    scrollView.post(this::updateVirtualWindow);
                }));
    }

    private void maybeLoadMore(int scrollY, int viewportHeight) {
        if (endReached || loading) {
            return;
        }
        int height = virtualList.getHeight();
        if (height > 0 && scrollY + viewportHeight >= scrollView.getPaddingTop() + height - dp(64)) {
            loadNext(false);
        }
    }

    private void rebuildEntries() {
        Map<Long, CloudTrackEntry> entries = new LinkedHashMap<>();
        Set<Long> localResolved = new HashSet<>();
        List<CloudUploadTask> tasks = CloudUploadService.getInstance().snapshot();
        for (CloudUploadTask task : tasks) {
            entries.put(task.getId(), new CloudTrackEntry(task.getId(), buildDisplayDetail(task), task));
            if (task.getState() == CloudEntryState.COMPLETED_PRESENTED) {
                Long resolved = parseId(task.getResolvedTrackId());
                if (resolved != null) {
                    localResolved.add(resolved);
                    presentedResolvedIds.add(resolved);
                }
            }
        }
        for (CloudTrackInfo detail : serverTracks.values()) {
            long id = detail.detail().getId();
            if (localResolved.contains(id) || presentedResolvedIds.contains(id)) {
                continue;
            }
            entries.putIfAbsent(id, new CloudTrackEntry(id, detail, null));
        }
        Set<Long> ids = new HashSet<>(entries.keySet());
        if (ids.equals(lastEntryIds)) {
            // Membership unchanged (progress/state-only): rebind in place, no window churn.
            virtualList.updateItems(new ArrayList<>(entries.values()));
        } else if (lastEntryIds.isEmpty()) {
            // First population (initial load / after refresh): bulk replace without the
            // per-item insertion animation, which would stutter with many tracks.
            virtualList.resetItems(new ArrayList<>(entries.values()));
            lastEntryIds = ids;
        } else {
            // Incremental changes (pagination append, queued uploads, removals) keep animating.
            virtualList.syncItems(new ArrayList<>(entries.values()));
            lastEntryIds = ids;
        }
        for (CloudUploadTask task : tasks) {
            if (task.getState() == CloudEntryState.COMPLETED) {
                scheduleCompletion(task.getId());
            }
        }
        emptyText.setVisibility(entries.isEmpty() ? VISIBLE : GONE);
    }

    private void scheduleCompletion(long id) {
        if (!scheduledCompletion.add(id)) {
            return;
        }
        postDelayed(() -> {
            scheduledCompletion.remove(id);
            if (isAttachedToWindow()) {
                CloudUploadService.getInstance().markPresented(id);
            }
        }, CloudUploadService.COMPLETION_PRESENTATION_MILLIS);
    }

    private CloudTrackInfo buildDisplayDetail(CloudUploadTask task) {
        if (task.getResolvedDetail() != null) {
            return new CloudTrackInfo(task.getResolvedDetail(), "", 0);
        }
        long displayId = 0;
        Long resolved = parseId(task.getResolvedTrackId());
        if (resolved != null) {
            displayId = resolved;
        }
        String artistName = task.getArtist();
        List<Artist> artists = artistName == null || artistName.isBlank()
                ? List.of()
                : List.of(new Artist(-1, artistName, "", 0, 0, "", List.of(), 0));
        String cover = task.getCoverDataUri() != null ? task.getCoverDataUri() : MusicHud.ICON_BASE64;
        Album album = new Album(0, task.getAlbum() == null ? "" : task.getAlbum(), cover, "", "", 0,
                new ObservableSequencedSet<>(0), new LinkedHashSet<>(), new LinkedHashSet<>());
        return new CloudTrackInfo(MusicDetail.of(displayId, task.getSong(), task.getDurationMillis(), artists, album), "", 0);
    }

    private void retryTask(CloudTrackEntry entry) {
        if (entry.task() != null) {
            CloudUploadService.getInstance().retry(entry.task().getId());
        }
    }

    private void cancelTask(CloudTrackEntry entry) {
        if (entry.task() != null) {
            CloudUploadService.getInstance().cancel(entry.task().getId());
        }
    }

    private void removeTask(CloudTrackEntry entry) {
        if (entry.task() != null) {
            CloudUploadService.getInstance().remove(entry.task().getId());
        }
    }

    private void confirmDelete(CloudTrackEntry entry) {
        MusicDetail detail = entry.cloudTrackInfo().detail();
        if (detail == null || detail.getId() <= 0) {
            return;
        }
        LinearLayout warningContent = new LinearLayout(getContext());
        warningContent.setOrientation(LinearLayout.VERTICAL);
        TextView warningText = new TextView(getContext());
        warningText.setText(I18n.get(MusicHud.MOD_ID + ".modal.deleteCloudTrack.warning"));
        warningText.setTextSize(Theme.TEXT_SIZE_LARGE);
        warningText.setTextColor(Theme.NORMAL_TEXT_COLOR);
        warningContent.addView(warningText);
        new Modal(getContext(), warningContent,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".modal.deleteCloudTrack.button1"), (button, modal) -> {
                    modal.dismiss();
                    deleteTrack(entry);
                }),
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".modal.deleteCloudTrack.button2"), (button, modal) -> modal.dismiss())
        ).show();
    }

    private void deleteTrack(CloudTrackEntry entry) {
        MusicDetail detail = entry.cloudTrackInfo().detail();
        long trackId = detail.getId();
        MusicService.getInstance().deleteCloudTrack(trackId).thenAccept(result ->
                MuiModApi.postToUiThread(() -> {
                    if (!isAttachedToWindow()) {
                        return;
                    }
                    if (result.actionResult() == ActionResult.SUCCESS) {
                        if (entry.task() != null) {
                            CloudUploadService.getInstance().remove(entry.task().getId());
                        }
                        serverTracks.remove(trackId);
                        presentedResolvedIds.remove(trackId);
                        rebuildEntries();
                        ToastUtil.show(Toast.makeText(getContext(),
                                I18n.get(MusicHud.MOD_ID + ".text.cloudDeleteSuccess"), Toast.LENGTH_SHORT));
                    } else {
                        showError(result.message());
                    }
                }));
    }

    private void showError(String message) {
        errorText.setText(I18n.get(MusicHud.MOD_ID + ".text.cloudLoadError")
                + (message == null || message.isBlank() ? "" : "\n" + message));
        errorText.setVisibility(VISIBLE);
        emptyText.setVisibility(GONE);
    }

    private void pickAndUpload() {
        String selected;
        Set<String> allowedTypes = Set.of("*.mp3", "*.wav", "*.flac");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(allowedTypes.size());
            for (String type : allowedTypes) {
                patterns.put(stack.UTF8(type));
            }
            patterns.flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    I18n.get(MusicHud.MOD_ID + ".text.cloudUploadTitle"),
                    null,
                    patterns,
                    I18n.get(MusicHud.MOD_ID + ".text.cloudUploadFilter").replace("{}", String.join(", ", allowedTypes)),
                    true);
        }
        if (selected == null || selected.isBlank()) {
            return;
        }
        for (String path : selected.split("\\|")) {
            if (path.isBlank()) continue;
            CloudUploadService.getInstance().enqueue(Paths.get(path));
        }
    }
}
