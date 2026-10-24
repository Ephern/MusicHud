package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.options.AdaptiveFloatOption;
import indi.etern.musichud.client.ui.hud.CoverWarpOverlayView;
import indi.etern.musichud.client.ui.render.CoverWarpSettings;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;

import java.util.Arrays;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Interactive debug screen for the cover FFD warp.
 */
public class CoverWarpDebugFragment extends Fragment implements ScreenCallback {
    private final CoverWarpSettings settings = CoverWarpSettings.getInstance();
    private CoverWarpOverlayView overlay;
    private ScrollView panel;
    private FrameLayout.LayoutParams panelParams;
    private FrameLayout root;
    private boolean panelOnRight = true;

    @Override
    public boolean hasDefaultBackground() {
        return true;
    }

    @Override
    public boolean shouldBlurBackground() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        Context context = requireContext();
        settings.setEnabled(true);
        settings.setPreviewMode(CoverWarpSettings.PreviewMode.TILED);

        FrameLayout root = new FrameLayout(context);
        this.root = root;
        overlay = new CoverWarpOverlayView(context);
        root.addView(overlay, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        panel = buildPanel(context, root);
        panelParams = new FrameLayout.LayoutParams(
                root.dp(320), MATCH_PARENT);
        panelParams.gravity = Gravity.RIGHT;
        panelParams.rightMargin = root.dp(24);
        panelParams.topMargin = root.dp(24);
        panelParams.bottomMargin = root.dp(24);
        root.addView(panel, panelParams);
        return root;
    }

    @Override
    public void onDetach() {
        settings.setPreviewMode(CoverWarpSettings.PreviewMode.OFF);
        super.onDetach();
    }

    private ScrollView buildPanel(Context context, FrameLayout root) {
        ScrollView scroll = new ScrollView(context);
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        ShapeDrawable background = new ShapeDrawable();
        background.setCornerRadius(scroll.dp(12));
        background.setColor(0xE6202020);
        scroll.setBackground(background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(scroll.dp(16), scroll.dp(12), scroll.dp(16), scroll.dp(16));
        scroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.title"));
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, content.dp(8));
        content.addView(title, titleParams);

        var category = PreferencesFragment.createCategoryList(content,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.controls"));

        Integer[] gridSizes = {3, 4, 5, 6, 7, 8, 9};
        List<Integer> gridSizeList = Arrays.asList(gridSizes);
        new PreferencesFragment.DropDownOption<>(
                context,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.gridSize"),
                gridSizes,
                gridSizeList::indexOf,
                settings::getGridSize,
                value -> {
                    settings.setGridSize(value);
                    refreshOverlay();
                })
                .create(category);

        String[] previewModes = {
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.preview.tiled"),
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.preview.layers")
        };
        List<String> previewModeList = Arrays.asList(previewModes);
        new PreferencesFragment.DropDownOption<>(
                context,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.preview"),
                previewModes,
                previewModeList::indexOf,
                () -> settings.getPreviewMode() == CoverWarpSettings.PreviewMode.LAYERS
                        ? previewModes[1] : previewModes[0],
                value -> {
                    settings.setPreviewMode(value.equals(previewModes[1])
                            ? CoverWarpSettings.PreviewMode.LAYERS
                            : CoverWarpSettings.PreviewMode.TILED);
                    refreshOverlay();
                })
                .create(category);

        new PreferencesFragment.BooleanOption(
                context,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.enabled"),
                settings::isEnabled,
                value -> {
                    settings.setEnabled(value);
                    refreshOverlay();
                })
                .setDefaultValue(true)
                .create(category);

        new AdaptiveFloatOption(
                context,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.strength"),
                () -> (double) settings.getStrength(),
                value -> {
                    settings.setStrength(value.floatValue());
                    refreshOverlay();
                })
                .setRange(0, 4, 100)
                .setDefaultValue(1.0)
                .create(category);

        new PreferencesFragment.BooleanOption(
                context,
                I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.tiledBlur"),
                settings::isTiledPreviewBlur,
                settings::setTiledPreviewBlur)
                .setDefaultValue(false)
                .create(category);

        ClientConfig clientConfig = ClientConfig.getInstance();
        new PreferencesFragment.IntegerOption(
                context,
                I18n.get(MusicHud.MOD_ID + ".config.debug.guiBackgroundFluidBlurRadius"),
                clientConfig::getGuiBackgroundFluidBlurRadius,
                clientConfig::setGuiBackgroundFluidBlurRadius)
                .setRange(0, 1024)
                .setDefaultValue(clientConfig.getDefaultGuiBackgroundFluidBlurRadius())
                .create(category);

        content.addView(category);

        Button reset = new Button(context);
        reset.setText(I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.reset"));
        reset.setTextColor(Theme.PRIMARY_COLOR);
        reset.setTextSize(14);
        reset.setOnClickListener(v -> {
            settings.resetOffsets();
            refreshOverlay();
        });
        content.addView(reset, new LinearLayout.LayoutParams(MATCH_PARENT, content.dp(40)));

        Button swapPanel = new Button(context);
        swapPanel.setText(I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.swapPanel"));
        swapPanel.setTextColor(Theme.PRIMARY_COLOR);
        swapPanel.setTextSize(14);
        swapPanel.setOnClickListener(v -> togglePanelSide());
        content.addView(swapPanel, new LinearLayout.LayoutParams(MATCH_PARENT, content.dp(40)));

        TextView hint = new TextView(context);
        hint.setText(I18n.get(MusicHud.MOD_ID + ".debug.coverWarp.hint"));
        hint.setTextSize(12);
        hint.setTextColor(Theme.HUD_FADE_COLOR);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hintParams.setMargins(0, content.dp(8), 0, 0);
        content.addView(hint, hintParams);

        return scroll;
    }

    private void refreshOverlay() {
        if (overlay != null) {
            overlay.refresh();
        }
    }

    private void togglePanelSide() {
        if (panel == null || panelParams == null || root == null) {
            return;
        }
        panelOnRight = !panelOnRight;
        panelParams.gravity = panelOnRight ? Gravity.RIGHT : Gravity.LEFT;
        panelParams.leftMargin = panelOnRight ? 0 : root.dp(24);
        panelParams.rightMargin = panelOnRight ? root.dp(24) : 0;
        panel.requestLayout();
    }
}
