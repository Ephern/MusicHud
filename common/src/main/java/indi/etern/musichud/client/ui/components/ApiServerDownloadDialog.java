package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.style.URLSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ArrayAdapter;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.server.api.ApiBinaryUpdateService;
import indi.etern.musichud.server.api.ApiServerFetcher;
import indi.etern.musichud.server.api.ApiServerManager;
import net.minecraft.util.Util;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Button + dialog for downloading the binary API server.
 * <p>
 * The ongoing download lives in a static {@link Session}, so closing the dialog
 * (or rebuilding the whole settings page) never loses the task: reopening the
 * dialog simply re-subscribes the new views to the same session.
 */
public class ApiServerDownloadDialog {
    private enum Page {IDLE, DOWNLOADING, DONE}

    /**
     * UI-independent download state shared by all dialog instances.
     */
    private static final class Session {
        private record Snapshot(Page page, long downloaded, long total,
                                Path targetDir, Path tempFile, String tag) {
        }

        private Page page = Page.IDLE;
        private CompletableFuture<?> future;
        private AtomicBoolean cancelled = new AtomicBoolean(false);
        private long downloaded;
        private long total = -1;
        private Path targetDir;
        private Path tempFile;
        private String tag = "";
        private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

        private synchronized Snapshot snapshot() {
            return new Snapshot(page, downloaded, total, targetDir, tempFile, tag);
        }

        private synchronized Page getPage() {
            return page;
        }

        private synchronized AtomicBoolean cancelFlag() {
            return cancelled;
        }

        /**
         * IDLE -> DOWNLOADING, returns false if a download is already running.
         */
        private synchronized boolean tryStart(Path dir, String releaseTag) {
            if (page == Page.DOWNLOADING) {
                return false;
            }
            page = Page.DOWNLOADING;
            targetDir = dir;
            tag = releaseTag;
            tempFile = null;
            downloaded = 0;
            total = -1;
            cancelled = new AtomicBoolean(false);
            fire();
            return true;
        }

        private synchronized void setFuture(CompletableFuture<?> f) {
            future = f;
        }

        private void reportProgress(long done, long all) {
            synchronized (this) {
                downloaded = done;
                total = all;
            }
            fire();
        }

        private void finishSuccess(Path tmp) {
            synchronized (this) {
                page = Page.DONE;
                tempFile = tmp;
                future = null;
            }
            fire();
        }

        private void finishFailure() {
            synchronized (this) {
                page = Page.IDLE;
                tempFile = null;
                future = null;
            }
            fire();
        }

        private void cancel() {
            CompletableFuture<?> f;
            synchronized (this) {
                cancelled.set(true);
                f = future;
            }
            if (f != null) {
                f.cancel(true);
            }
        }

        private void reset() {
            synchronized (this) {
                page = Page.IDLE;
                future = null;
                tempFile = null;
                tag = "";
                downloaded = 0;
                total = -1;
                cancelled = new AtomicBoolean(false);
            }
            fire();
        }

        private void addListener(Runnable l) {
            listeners.add(l);
        }

        private void removeListener(Runnable l) {
            listeners.remove(l);
        }

        private void fire() {
            for (Runnable l : listeners) {
                l.run();
            }
        }
    }

    private static final Session SESSION = new Session();

    private final Context context;
    private final InsetBackgroundFactory backgroundFactory;
    private final Consumer<String> onInstalled;

    private TextView title;
    private LinearLayout idlePage;
    private LinearLayout progressPage;
    private LinearLayout donePage;
    private TextView releaseNameLabel;
    private TextView existingVersionWarning;
    private EditText directoryTextInput;
    private Spinner proxySpinner;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView doneDesc;

    private Modal.ActionButton confirmBtn;
    private Modal.ActionButton cancelBtn;
    private Modal dialog;
    private Button triggerButton;

    private ApiServerFetcher.ReleaseSummary latestRelease;
    private final Path initialDir;

    public ApiServerDownloadDialog(Context context, InsetBackgroundFactory backgroundFactory,
                                   Consumer<String> onInstalled) {
        // Title view is created late in show() because it needs a real context
        this.context = context;
        this.backgroundFactory = backgroundFactory;
        this.onInstalled = onInstalled;
        this.initialDir = resolveInitialDir();
    }

    /**
     * Builds the entry button shown in the settings page.
     */
    public Button createButton() {
        triggerButton = new Button(context);
        triggerButton.setTextColor(Theme.PRIMARY_COLOR);
        triggerButton.setTextSize(14);
        backgroundFactory.applyBackgroundTo(triggerButton);
        syncTriggerText(SESSION.getPage());
        triggerButton.setOnClickListener(v -> show());
        Runnable listener = () -> MuiModApi.postToUiThread(
                () -> syncTriggerText(SESSION.getPage()));
        SESSION.addListener(listener);
        triggerButton.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                SESSION.removeListener(listener);
            }
        });
        return triggerButton;
    }

    private void syncTriggerText(Page page) {
        if (triggerButton == null) {
            return;
        }
        switch (page) {
            case DOWNLOADING ->
                    triggerButton.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.downloading"));
            case DONE -> triggerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServerDone"));
            default -> triggerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
        }
    }

    private void show() {
        if (dialog == null) {
            buildDialog();
        }
        refreshReleaseInfo();
        syncFromSession();
        SESSION.removeListener(sessionListener);
        SESSION.addListener(sessionListener);
        dialog.show();
        dialog.setOnDismissListener(() -> SESSION.removeListener(sessionListener));
    }

    private final Runnable sessionListener = () -> MuiModApi.postToUiThread(this::syncFromSession);

    /**
     * Repaints dialog views from the shared session state.
     */
    private void syncFromSession() {
        Session.Snapshot s = SESSION.snapshot();
        setPage(s.page());
        syncTriggerText(s.page());
        switch (s.page()) {
            case DOWNLOADING -> {
                confirmBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.downloading.button1"));
                cancelBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.downloading.button2"));
                paintProgress(s.downloaded(), s.total());
            }
            case DONE -> {
                confirmBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.button1"));
                cancelBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.button2"));
                if (s.tempFile() != null) {
                    doneDesc.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.description")
                            .replace("{path}", s.tempFile().toString()));
                }
            }
            default -> {
                confirmBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button1"));
                cancelBtn.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button2"));
            }
        }
        cancelBtn.getButton().setVisibility(View.VISIBLE);
    }

    private void setPage(Page page) {
        idlePage.setVisibility(page == Page.IDLE ? View.VISIBLE : View.GONE);
        progressPage.setVisibility(page == Page.DOWNLOADING ? View.VISIBLE : View.GONE);
        donePage.setVisibility(page == Page.DONE ? View.VISIBLE : View.GONE);
        title.setText(page == Page.DONE
                ? I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.title")
                : I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.title"));
    }

    private void paintProgress(long downloaded, long total) {
        if (total > 0) {
            progressBar.setProgress((int) (downloaded * 100 / total));
            progressText.setText(formatBytes(downloaded) + " / " + formatBytes(total));
        } else {
            progressBar.setProgress(0);
            progressText.setText(formatBytes(downloaded));
        }
    }

    private void buildDialog() {
        title = new TextView(context);
        title.setTextSize(Theme.TEXT_SIZE_LARGE);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        idlePage = buildIdlePage();
        progressPage = buildProgressPage();
        progressPage.setVisibility(View.GONE);
        donePage = buildDonePage();
        donePage.setVisibility(View.GONE);
        content.addView(idlePage);
        content.addView(progressPage);
        content.addView(donePage);

        cancelBtn = new Modal.ActionButton(
                I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button2"),
                (btn, modal) -> {
                    if (SESSION.getPage() == Page.DONE) {
                        SESSION.reset();
                        syncTriggerText(Page.IDLE);
                    }
                    modal.dismiss();
                });
        confirmBtn = new Modal.ActionButton(
                I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button1"),
                (btn, modal) -> onConfirm(modal));

        dialog = new Modal(context, title, content, confirmBtn, cancelBtn);
    }

    private void onConfirm(Modal modal) {
        switch (SESSION.getPage()) {
            case IDLE -> startDownload();
            case DOWNLOADING -> SESSION.cancel();
            case DONE -> installDownload(modal);
        }
    }

    private void startDownload() {
        Path dir = Paths.get(directoryTextInput.getText().toString().trim());
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        String releaseTag = latestRelease != null ? latestRelease.tag() : "unknown";
        if (!SESSION.tryStart(dir, releaseTag)) {
            return;
        }
        setPage(Page.DOWNLOADING);
        progressBar.setProgress(0);
        progressText.setText("");

        String tempFileName = ApiServerFetcher.Platform.detect().getAssetName() + "." + releaseTag + ".temp";
        Path tempFile = dir.resolve(tempFileName);
        tempFile.toFile().deleteOnExit();

        ApiServerFetcher.DownloadProxy proxy =
                ApiServerFetcher.DownloadProxy.values()[proxySpinner.getSelectedItemPosition()];
        CompletableFuture<Path> future = ApiBinaryUpdateService.getInstance().downloadToTemp(
                dir, releaseTag, proxy,
                SESSION::reportProgress,
                SESSION.cancelFlag());
        SESSION.setFuture(future);
        future.thenAccept(path -> MuiModApi.postToUiThread(() -> {
            ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done"));
            SESSION.finishSuccess(path);
        })).exceptionally(ex -> {
            MuiModApi.postToUiThread(() -> {
                if (ex instanceof CancellationException || ex.getCause() instanceof CancellationException) {
                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.cancelled"));
                } else {
                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.error")
                            + ": " + ex.getMessage());
                }
                SESSION.finishFailure();
            });
            return null;
        });
    }

    private void installDownload(Modal modal) {
        Session.Snapshot s = SESSION.snapshot();
        ApiBinaryUpdateService updateService = ApiBinaryUpdateService.getInstance();
        Path finalPath = updateService.resolveFinalPath(s.tempFile(), s.tag());
        if (finalPath == null) {
            ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.renameFailed"));
            return;
        }
        updateService.updateMhApiJson(s.targetDir(), s.tag(),
                updateService.extractVersion(s.tag()), finalPath.getFileName().toString());
        String configPath = updateService.relativizePath(finalPath);
        onInstalled.accept(configPath);
        ApiServerManager apiServer = ApiServerManager.getInstance();
        if (apiServer != null) {
            apiServer.restartApiServer();
        }
        SESSION.reset();
        syncTriggerText(Page.IDLE);
        modal.dismiss();
    }

    private LinearLayout buildIdlePage() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);

        TextView description = new TextView(context);
        description.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.description"));
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);

        TextView descriptionUrl = new TextView(context);
        String url = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.description.url");
        String latestReleaseUrl = ApiServerFetcher.LATEST_RELEASE_URL;
        String replaced = url.replace("{url}", latestReleaseUrl);
        SpannableString spannable = new SpannableString(replaced);
        spannable.setSpan(new URLSpan(latestReleaseUrl), url.indexOf("{url}"),
                url.indexOf("{url}") + latestReleaseUrl.length(),
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        descriptionUrl.setText(spannable);
        descriptionUrl.setTextSize(Theme.TEXT_SIZE_NORMAL);
        descriptionUrl.setOnClickListener(v -> Util.getPlatform().openUri(latestReleaseUrl));

        LinearLayout directoryLayout = new LinearLayout(context);
        directoryLayout.setOrientation(LinearLayout.HORIZONTAL);

        existingVersionWarning = new TextView(context);
        existingVersionWarning.setTextSize(Theme.TEXT_SIZE_NORMAL);
        existingVersionWarning.setTextColor(Theme.WARN_TEXT_COLOR);
        existingVersionWarning.setVisibility(View.GONE);

        TextView directoryText = new TextView(context);
        directoryText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        directoryText.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir"));

        directoryTextInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        directoryTextInput.setHint(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.field.hint"));
        directoryTextInput.setTextSize(Theme.TEXT_SIZE_NORMAL);
        directoryTextInput.setTextColor(Theme.NORMAL_TEXT_COLOR);
        directoryTextInput.setText(initialDir.toString());

        Button selectDirectoryButton = new Button(context);
        selectDirectoryButton.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.button.select"));
        selectDirectoryButton.setTextColor(Theme.PRIMARY_COLOR);
        selectDirectoryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        backgroundFactory.applyBackgroundTo(selectDirectoryButton);
        selectDirectoryButton.setOnClickListener(v -> {
            String current = directoryTextInput.getText().toString().trim();
            Path fallback = current.isEmpty() ? initialDir : Paths.get(current);
            String folder = TinyFileDialogs.tinyfd_selectFolderDialog(
                    I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.dialog.title"),
                    fallback.toAbsolutePath().toString());
            if (folder != null) {
                directoryTextInput.setText(folder);
                checkExistingVersion(Paths.get(folder));
            }
        });

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
        labelParams.setMargins(0, 0, page.dp(8), 0);
        directoryLayout.addView(directoryText, labelParams);
        directoryLayout.addView(directoryTextInput, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, 0);
        buttonParams.setMargins(page.dp(4), 0, 0, 0);
        directoryLayout.addView(selectDirectoryButton, buttonParams);

        LinearLayout proxyLayout = new LinearLayout(context);
        proxyLayout.setOrientation(LinearLayout.HORIZONTAL);
        proxyLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView proxyText = new TextView(context);
        proxyText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        proxyText.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.proxy"));

        proxySpinner = new Spinner(context);
        String[] proxyLabels = Arrays.stream(ApiServerFetcher.DownloadProxy.values())
                .map(p -> I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.proxy." + p.name()))
                .toArray(String[]::new);
        ArrayAdapter<String> proxyAdapter = new ArrayAdapter<>(context, proxyLabels) {
            @Override
            @NotNull
            public View getView(int position, View convertView, @NotNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(Theme.TEXT_SIZE_NORMAL);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NotNull ViewGroup parent) {
                View dropDownView = super.getDropDownView(position, convertView, parent);
                if (dropDownView instanceof TextView tv) {
                    tv.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    return tv;
                }
                return dropDownView;
            }
        };
        proxySpinner.setAdapter(proxyAdapter);
        proxySpinner.setSelection(0);

        LinearLayout.LayoutParams proxyLabelParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
        proxyLabelParams.setMargins(0, 0, page.dp(8), 0);
        proxyLayout.addView(proxyText, proxyLabelParams);
        proxyLayout.addView(proxySpinner, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1));

        LinearLayout.LayoutParams proxyParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        proxyParams.setMargins(0, 0, 0, page.dp(8));

        LinearLayout releaseInfoLayout = new LinearLayout(context);
        releaseInfoLayout.setOrientation(LinearLayout.HORIZONTAL);
        releaseInfoLayout.setGravity(Gravity.CENTER_VERTICAL);

        releaseNameLabel = new TextView(context);
        releaseNameLabel.setTextSize(Theme.TEXT_SIZE_NORMAL);
        releaseNameLabel.setTextColor(Theme.NORMAL_TEXT_COLOR);

        Button refreshReleaseButton = new Button(context);
        refreshReleaseButton.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.refresh"));
        refreshReleaseButton.setTextColor(Theme.PRIMARY_COLOR);
        refreshReleaseButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        backgroundFactory.applyBackgroundTo(refreshReleaseButton);
        refreshReleaseButton.setOnClickListener(v -> refreshReleaseInfo());

        releaseInfoLayout.addView(releaseNameLabel, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        releaseInfoLayout.addView(refreshReleaseButton,
                new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rowParams.setMargins(0, page.dp(4), 0, page.dp(4));

        page.addView(description);
        page.addView(descriptionUrl);
        page.addView(directoryLayout, rowParams);
        page.addView(proxyLayout, proxyParams);
        page.addView(releaseInfoLayout);
        page.addView(existingVersionWarning);
        return page;
    }

    private LinearLayout buildProgressPage() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);

        TextView desc = new TextView(context);
        desc.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.downloading.description"));
        desc.setTextSize(Theme.TEXT_SIZE_NORMAL);
        desc.setOnClickListener(v -> Util.getPlatform().openUri(ApiServerFetcher.LATEST_RELEASE_URL));

        LinearLayout progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.HORIZONTAL);
        progressLayout.setGravity(Gravity.CENTER_VERTICAL);

        progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
        progressBar.setMin(0);
        progressBar.setMax(100);

        progressText = new TextView(context);
        progressText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        progressText.setTextColor(Theme.NORMAL_TEXT_COLOR);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, page.dp(24), 1);
        barParams.setMargins(0, 0, progressBar.dp(4), 0);
        progressLayout.addView(progressBar, barParams);
        progressLayout.addView(progressText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        page.addView(desc);
        page.addView(progressLayout);
        return page;
    }

    private LinearLayout buildDonePage() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setVisibility(View.GONE);

        doneDesc = new TextView(context);
        doneDesc.setTextSize(Theme.TEXT_SIZE_NORMAL);
        doneDesc.setTextColor(Theme.NORMAL_TEXT_COLOR);
        page.addView(doneDesc);
        return page;
    }

    private void refreshReleaseInfo() {
        releaseNameLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.fetching"));
        ApiBinaryUpdateService.getInstance().fetchLatestRelease().thenAccept(r -> {
            if (r != null) {
                MuiModApi.postToUiThread(() -> {
                    latestRelease = r;
                    releaseNameLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.label")
                            .replace("{tag}", r.tag()));
                    checkExistingVersion(currentDir());
                });
            } else {
                MuiModApi.postToUiThread(() -> releaseNameLabel.setText(
                        I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.failed")));
            }
        }).exceptionally(ex -> {
            MuiModApi.postToUiThread(() -> releaseNameLabel.setText(
                    I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.failed")));
            return null;
        });
    }

    private Path currentDir() {
        try {
            return Paths.get(directoryTextInput.getText().toString().trim());
        } catch (Exception e) {
            return initialDir;
        }
    }

    private void checkExistingVersion(Path dir) {
        if (latestRelease == null) {
            existingVersionWarning.setVisibility(View.GONE);
            return;
        }
        String oldVersion = ApiBinaryUpdateService.getInstance()
                .checkExistingVersion(dir, latestRelease.tag());
        if (oldVersion != null) {
            existingVersionWarning.setText(
                    I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.existingVersion")
                            .replace("{version}", oldVersion)
                            .replace("{tag}", latestRelease.tag()));
            existingVersionWarning.setVisibility(View.VISIBLE);
        } else {
            existingVersionWarning.setVisibility(View.GONE);
        }
    }

    private static Path resolveInitialDir() {
        Path path = Paths.get(ServerConfig.getInstance().getServerApiBinaryExecutablePath());
        while (!Files.isDirectory(path)) {
            path = path.getParent();
            if (path == null) {
                return Paths.get("music-hud");
            }
        }
        return path;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format("%.1f KiB", kib);
        }
        double mib = kib / 1024.0;
        if (mib < 100) {
            return String.format("%.1f MiB", mib);
        }
        return String.format("%.0f MiB", mib);
    }
}
