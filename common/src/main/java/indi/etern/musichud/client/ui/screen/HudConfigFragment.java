package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.AdaptiveFloatOption;
import indi.etern.musichud.client.ui.components.DynamicIntegerOption;
import indi.etern.musichud.client.ui.components.SignedIntegerOption;
import indi.etern.musichud.client.ui.hud.HudEditOverlayView;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Main fragment of {@link HudConfigScreen}
 */
public class HudConfigFragment extends Fragment implements ScreenCallback {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private final int MARGIN = 24;

    private FrameLayout root;
    private HudEditOverlayView overlay;
    private ScrollView panel;
    private FrameLayout.LayoutParams panelParams;
    private int margin;

    private SignedIntegerOption offsetXOption;
    private SignedIntegerOption offsetYOption;
    private DynamicIntegerOption widthOption;
    private DynamicIntegerOption heightOption;
    private DynamicIntegerOption radiusOption;

    @Override
    public boolean hasDefaultBackground() {
        return false;
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
        root = new FrameLayout(context);
        margin = root.dp(MARGIN);

        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        root.setLayoutTransition(transition);

        overlay = new HudEditOverlayView(context);
        overlay.setOnConfigChanged(this::onHudConfigChanged);
        root.addView(overlay, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        panel = buildPanel(context);
        panelParams = new FrameLayout.LayoutParams(panelWidth(4), WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        panelParams.leftMargin = margin;
        root.addView(panel, panelParams);

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> onRootSizeChanged());
        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updatePanelPosition());
        root.post(this::updatePanelPosition);
        return root;
    }

    private void onRootSizeChanged() {
        if (root == null || panel == null) {
            return;
        }
        margin = root.dp(MARGIN);
        if (panel instanceof MaxHeightScrollView mhs) {
            mhs.setMaxHeight(Math.max(0, root.getHeight() - 2 * margin));
        }
        panel.requestLayout();
        updatePanelPosition();
    }

    private ScrollView buildPanel(Context context) {
        MaxHeightScrollView panel = new MaxHeightScrollView(context);
        panel.setMaxHeight(Math.max(0, windowHeight() - 2 * margin));
        panel.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(panel.dp(12));
        TypedValue value = new TypedValue();
        var theme = context.getTheme();
        if (theme.resolveAttribute(R.ns, R.attr.colorSurface, value, true)) {
            bg.setColor(theme.getResources().loadColorStateList(value, null, theme));
        } else {
            bg.setColor(0xFF2D2D30);
        }
        bg.setStroke(panel.dp(1), new ColorStateList(
                new int[][]{StateSet.WILD_CARD}, new int[]{0x20FFFFFF}));
        panel.setBackground(bg);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(panel.dp(16), panel.dp(12), panel.dp(16), panel.dp(24));
        panel.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".config.hudScreenTitle"));
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, panel.dp(8));
        content.addView(title, titleParams);

        var layoutCategory = PreferencesFragment.createCategoryList(content,
                I18n.get(MusicHud.MOD_ID + ".config.category.layout"));
        buildLayoutOptions(context, layoutCategory);
        content.addView(layoutCategory);

        var behaviorCategory = PreferencesFragment.createCategoryList(content,
                I18n.get(MusicHud.MOD_ID + ".config.category.hudBehavior"));
        new PreferencesFragment.BooleanOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.common.enableHud"),
                clientConfig::getEnableHud,
                clientConfig::setEnableHud)
                .setDefaultValue(clientConfig.getDefaultEnableHud())
                .create(behaviorCategory);
        new PreferencesFragment.BooleanOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.common.autoHide"),
                clientConfig::getHideHudWhenNotPlaying,
                clientConfig::setHideHudWhenNotPlaying)
                .setDefaultValue(clientConfig.getDefaultHideHudWhenNotPlaying())
                .create(behaviorCategory);
        new PreferencesFragment.BooleanOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.common.enableMarqueeText"),
                clientConfig::getEnableMarqueeText,
                clientConfig::setEnableMarqueeText)
                .setDefaultValue(clientConfig.getDefaultEnableMarqueeText())
                .create(behaviorCategory);
        content.addView(behaviorCategory);

        var appearanceCategory = PreferencesFragment.createCategoryList(content,
                I18n.get(MusicHud.MOD_ID + ".config.category.hudAppearance"));
        new AdaptiveFloatOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.common.hudBackgroundMixAlpha"),
                clientConfig::getHudBackgroundMixAlpha,
                clientConfig::setHudBackgroundMixAlpha)
                .setRange(0, 1)
                .setDefaultValue(clientConfig.getDefaultHudBackgroundMixAlpha())
                .create(appearanceCategory);
        content.addView(appearanceCategory);

        return panel;
    }

    private void buildLayoutOptions(Context context, ViewGroup category) {
        new PreferencesFragment.DropDownOption<>(
                context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.verticalAlign"),
                VerticalAlign.values(),
                VerticalAlign::ordinal,
                () -> VerticalAlign.valueOf(clientConfig.getHudVerticalPosition()),
                (vp) -> clientConfig.setHudVerticalPosition(vp.name()))
                .setDefaultValue(VerticalAlign.valueOf(clientConfig.getDefaultHudVerticalPosition()))
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);
        new PreferencesFragment.DropDownOption<>(
                context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.horizontalAlign"),
                HorizontalAlign.values(),
                HorizontalAlign::ordinal,
                () -> HorizontalAlign.valueOf(clientConfig.getHudHorizontalPosition()),
                (hp) -> clientConfig.setHudHorizontalPosition(hp.name()))
                .setDefaultValue(HorizontalAlign.valueOf(clientConfig.getDefaultHudHorizontalPosition()))
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);

        offsetXOption = new SignedIntegerOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.offsetX"),
                clientConfig::getHudOffsetX,
                clientConfig::setHudOffsetX);
        offsetXOption.setRange(-1920, 1920)
                .setDefaultValue(clientConfig.getDefaultHudOffsetX())
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);

        offsetYOption = new SignedIntegerOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.offsetY"),
                clientConfig::getHudOffsetY,
                clientConfig::setHudOffsetY);
        offsetYOption.setRange(-1920, 1920)
                .setDefaultValue(clientConfig.getDefaultHudOffsetY())
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);

        widthOption = new DynamicIntegerOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.hudWidth"),
                clientConfig::getHudWidth,
                clientConfig::setHudWidth);
        widthOption.setRange(clientConfig.getHudHeight(), 800, 4)
                .setDefaultValue(clientConfig.getDefaultHudWidth())
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);

        heightOption = new DynamicIntegerOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.hudHeight"),
                clientConfig::getHudHeight,
                clientConfig::setHudHeight);
        heightOption.setRange(16, 256, 2)
                .setDefaultValue(clientConfig.getDefaultHudHeight())
                .setOnChanged(() -> {
                    onLayoutOptionChanged();
                    radiusOption.updateRange(0, clientConfig.getHudHeight() / 2, 1);
                    widthOption.updateRange(clientConfig.getHudHeight(), 800, 4);
                });
        heightOption.create(category);

        radiusOption = new DynamicIntegerOption(context,
                I18n.get(MusicHud.MOD_ID + ".config.layout.hudCornerRadius"),
                clientConfig::getHudCornerRadius,
                clientConfig::setHudCornerRadius);
        radiusOption.setRange(0, clientConfig.getHudHeight() / 2)
                .setDefaultValue(clientConfig.getDefaultHudCornerRadius())
                .setOnChanged(this::onLayoutOptionChanged)
                .create(category);
    }

    private void onLayoutOptionChanged() {
        HudRendererManager hudRendererManager = HudRendererManager.getInstance();
        hudRendererManager.updateLayoutFromConfig();
        hudRendererManager.refreshStyle();
        if (overlay != null) {
            overlay.refresh();
        }
        updatePanelPosition();
    }

    private void onHudConfigChanged() {
        if (offsetXOption != null) offsetXOption.refresh();
        if (offsetYOption != null) offsetYOption.refresh();
        if (widthOption != null) widthOption.refresh();
        if (heightOption != null) heightOption.refresh();
        if (radiusOption != null) radiusOption.refresh();
        updatePanelPosition();
    }

    private void updatePanelPosition() {
        if (root == null || panel == null || panelParams == null) {
            return;
        }
        if (panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }
        RectF hud = overlay.getHudGuiRect();
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectF hudPx = new RectF(hud.left * scale, hud.top * scale, hud.right * scale, hud.bottom * scale);
        int slack = root.dp(24);
        hudPx.inset(-slack, -slack);

        int screenW = windowWidth();
        int screenH = windowHeight();
        int panelH = panel.getHeight();
        int top = (screenH - panelH) / 2;
        int bottom = top + panelH;

        int width = panelWidth(4);
        if (!intersects(hudPx, margin, top, margin + width, bottom)) {
            applyDock(Gravity.LEFT, margin, width);
            return;
        }
        int rightPos = screenW - margin - width;
        if (!intersects(hudPx, rightPos, top, rightPos + width, bottom)) {
            applyDock(Gravity.RIGHT, margin, width);
            return;
        }
        int slimW = Math.clamp(screenW - 2L * margin, 0, windowWidth() / 6);
        if (slimW < width) {
            if (!intersects(hudPx, margin, top, margin + slimW, bottom)) {
                applyDock(Gravity.LEFT, margin, slimW);
                return;
            }
            int slimRightPos = screenW - margin - slimW;
            if (!intersects(hudPx, slimRightPos, top, slimRightPos + slimW, bottom)) {
                applyDock(Gravity.RIGHT, margin, slimW);
            }
        }
        // cannot satisfy the constraints: ignore and keep the current position
    }

    private void applyDock(int side, int leftMargin, int width) {
        boolean dockRight = side == Gravity.RIGHT;
        int gravity = dockRight ? Gravity.CENTER_VERTICAL | Gravity.RIGHT : Gravity.CENTER_VERTICAL | Gravity.LEFT;
        int rightMargin = dockRight ? margin : 0;
        if (panelParams.width == width && panelParams.gravity == gravity
                && panelParams.leftMargin == leftMargin && panelParams.rightMargin == rightMargin) {
            return;
        }
        panelParams.width = width;
        panelParams.gravity = gravity;
        panelParams.leftMargin = leftMargin;
        panelParams.rightMargin = rightMargin;
        panel.requestLayout();
    }

    private int panelWidth(int divisor) {
        int screenW = windowWidth();
        return Math.clamp(screenW - 2L * margin, 0, screenW / divisor);
    }

    private static int windowWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    private static int windowHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    private static boolean intersects(RectF a, float left, float top, float right, float bottom) {
        return a.left < right && a.right > left && a.top < bottom && a.bottom > top;
    }

    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeight = Integer.MAX_VALUE;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentSize = MeasureSpec.getSize(heightMeasureSpec);
            int capped = Math.min(parentSize > 0 ? parentSize : maxHeight, maxHeight);
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST));
        }
    }
}
