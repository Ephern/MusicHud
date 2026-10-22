package indi.etern.musichud.client.ui.hud.layout;

import indi.etern.musichud.client.ui.layout.JustifyContent;
import indi.etern.musichud.client.ui.layout.LayoutCompiler;
import indi.etern.musichud.client.ui.layout.LayoutNode;
import indi.etern.musichud.client.ui.layout.LayoutSlotRegistry;
import indi.etern.musichud.client.ui.layout.LayoutTree;
import indi.etern.musichud.client.ui.layout.Layouts;
import indi.etern.musichud.client.ui.layout.SizeSpec;
import indi.etern.musichud.client.ui.render.SceneStyle;
import lombok.Getter;

import static indi.etern.musichud.client.ui.layout.AlignItems.CENTER;
import static indi.etern.musichud.client.ui.layout.AlignItems.STRETCH;

@Getter
public final class HudLayouts {
    private final SceneStyle baseStyle;
    private final SceneStyle imageStyle;
    private final SceneStyle titleStyle;
    private final SceneStyle headStyle;
    private final SceneStyle statusStyle;
    private final SceneStyle lyricsStyle;
    private final SceneStyle artistsStyle;
    private final SceneStyle playTimeStyle;
    private final SceneStyle progressStyle;

    private final float contentPadding;
    private final float contentInterval;
    private final float titleSize;
    private final float infoTextSize;
    private final float lyricsSize;
    private final float subLyricsSize;
    private final float progressHeight;

    private final LayoutTree tree;
    private final LayoutNode headNode;
    private final LayoutNode statusNode;
    private final LayoutNode playTimeNode;
    private boolean lastHeadVisible;
    private boolean lastStatusVisible;
    private float lastPlayTimeWidth = Float.NaN;

    HudLayouts(SceneStyle baseStyle) {
        this.baseStyle = baseStyle;
        if (baseStyle.getRadius() > baseStyle.getHeight() / 2) {
            baseStyle.setRadius(baseStyle.getHeight() / 2);
        }
        float height = baseStyle.getHeight();
        contentPadding = Math.max(height / 10, 3);
        float imageSize = height - 2 * contentPadding;
        float contentHeight = height - 2 * contentPadding;
        float contentWidth = baseStyle.getWidth() - imageSize - 3 * contentPadding - baseStyle.getRadius() / 3;
        float contentUnit = Math.max(contentHeight / 32f, 1);
        titleSize = contentUnit * 7;
        infoTextSize = contentUnit * 5.5f;
        progressHeight = contentUnit * 2;
        lyricsSize = contentUnit * 6;
        subLyricsSize = contentUnit * 5;
        float lyricsMinHeight = lyricsSize + subLyricsSize + Math.min(contentUnit * 2.5f, 2f);
        contentInterval = Math.min(contentUnit * 2.5f, 2f);

        float imageRadius = Math.clamp(baseStyle.getRadius() - contentPadding, 0, imageSize / 2f);
        imageStyle = new SceneStyle(0, 0, imageSize, imageSize, imageRadius);
        titleStyle = SceneStyle.ofTextLayout(0, 0, contentWidth, titleSize);
        headStyle = new SceneStyle(0, 0, titleSize, titleSize, 0);
        statusStyle = new SceneStyle(0, 0, titleSize, titleSize, 0);
        lyricsStyle = new SceneStyle(0, 0, contentWidth, lyricsMinHeight, 0);
        artistsStyle = SceneStyle.ofTextLayout(0, 0, contentWidth, infoTextSize);
        playTimeStyle = SceneStyle.ofTextLayout(0, 0, 0, infoTextSize);
        progressStyle = new SceneStyle(0, 0, contentWidth, progressHeight, progressHeight / 2);
        imageStyle.setParent(baseStyle);
        titleStyle.setParent(baseStyle);
        headStyle.setParent(baseStyle);
        statusStyle.setParent(baseStyle);
        lyricsStyle.setParent(baseStyle);
        artistsStyle.setParent(baseStyle);
        playTimeStyle.setParent(baseStyle);
        progressStyle.setParent(baseStyle);

        LayoutSlotRegistry slots = new LayoutSlotRegistry();
        slots.register("cover", imageStyle);
        slots.register("title", titleStyle);
        slots.register("head", headStyle);
        slots.register("status", statusStyle);
        slots.register("lyrics", lyricsStyle);
        slots.register("artists", artistsStyle);
        slots.register("playtime", playTimeStyle);
        slots.register("progress", progressStyle);

        float headGap = Math.max(4, contentInterval);
        var spec = Layouts.hbox(root -> root
                .padding(contentPadding, contentPadding, contentPadding + baseStyle.getRadius() / 3, contentPadding)
                .gap(contentPadding)
                .alignItems(STRETCH)
                .child(Layouts.leaf("cover", leaf -> leaf.height(SizeSpec.FILL).aspectRatio(1)))
                .child(Layouts.vbox(content -> content
                        .grow(1)
                        .height(SizeSpec.FILL)
                        .alignItems(STRETCH)
                        .padding(0, 1, 0, 0)
                        .gap(0)
                        .justifyContent(JustifyContent.CENTER_IF_SINGLE)
                        .child(Layouts.hbox(titleRow -> titleRow
                                .height(titleSize)
                                .alignItems(CENTER)
                                .justifyContent(JustifyContent.END)
                                .margin(0, 0, 0, contentInterval / 2)
                                .child(Layouts.leaf("title", leaf -> leaf.grow(1).height(titleSize)))
                                .child(Layouts.leaf("head", leaf -> leaf
                                        .width(titleSize)
                                        .height(titleSize)
                                        .margin(headGap, 0, headGap, 0)))
                                .child(Layouts.leaf("status", leaf -> leaf
                                        .width(titleSize)
                                        .height(titleSize)
                                        .overflowOrder(10)
                                        .hideOnOverflow(true)
                                        .minWidth(titleSize)))
                        ))
                        .child(Layouts.vbox(middle -> middle
                                .grow(1)
                                .alignItems(STRETCH)
                                .collapseWhenEmptyAndTrailing(true)
                                .child(Layouts.leaf("lyrics", leaf -> leaf
                                        .height(SizeSpec.FILL)
                                        .hideOnOverflow(true)
                                        .minHeight(lyricsMinHeight)))
                        ))
                        .child(Layouts.hbox(infoRow -> infoRow
                                .height(infoTextSize)
                                .gap(contentInterval)
                                .overflowOrder(20)
                                .hideOnOverflow(true)
                                .minHeight(infoTextSize)
                                .child(Layouts.leaf("artists", leaf -> leaf.grow(1).height(infoTextSize)))
                                .child(Layouts.leaf("playtime", leaf -> leaf
                                        .height(infoTextSize)
                                        .shrink(1)
                                        .minWidth(0)))
                        ))
                        .child(Layouts.leaf("progress", leaf -> leaf
                                .height(progressHeight)
                                .overflowOrder(10)
                                .hideOnOverflow(true)
                                .minHeight(progressHeight)
                                .margin(0, contentInterval / 2, 0, 1)))
                )));
        tree = LayoutCompiler.compile(spec, slots);
        headNode = tree.node("head");
        statusNode = tree.node("status");
        playTimeNode = tree.node("playtime");
    }

    public static HudLayouts create(SceneStyle baseStyle) {
        return new HudLayouts(baseStyle);
    }

    public void arrange(boolean headVisible, boolean statusVisible, float playTimeWidth) {
        boolean changed = lastHeadVisible != headVisible
                || lastStatusVisible != statusVisible
                || lastPlayTimeWidth != playTimeWidth;
        if (changed) {
            lastHeadVisible = headVisible;
            lastStatusVisible = statusVisible;
            lastPlayTimeWidth = playTimeWidth;
            headNode.setVisible(headVisible);
            statusNode.setVisible(statusVisible);
            playTimeNode.setWidth(Math.max(0, playTimeWidth));
            tree.invalidate();
        }
        tree.measureAndArrange(baseStyle.getWidth(), baseStyle.getHeight());
    }
}
