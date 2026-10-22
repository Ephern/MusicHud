package indi.etern.musichud.client.ui.layout;

import java.util.ArrayList;
import java.util.List;

public class FlexBox extends LayoutNode {
    private static final float EPSILON = 0.01f;

    private final FlexDirection direction;
    private final List<LayoutNode> children = new ArrayList<>();
    private final List<LayoutNode> visibleChildren = new ArrayList<>();

    public FlexBox(FlexDirection direction) {
        this.direction = direction;
    }

    public FlexBox add(LayoutNode child) {
        children.add(child);
        return this;
    }

    public List<LayoutNode> children() {
        return children;
    }

    @Override
    public void measure(float width, float height) {
        setMeasuredSize(width, height);
        measureChildren();
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        super.setCollapsed(collapsed);
        for (LayoutNode child : children) {
            child.setCollapsed(collapsed);
        }
    }

    @Override
    public void resetCollapse() {
        super.resetCollapse();
        for (LayoutNode child : children) {
            child.resetCollapse();
        }
    }

    private void measureChildren() {
        boolean row = direction == FlexDirection.ROW;
        float innerWidth = Math.max(0, measuredWidth - padding.left() - padding.right());
        float innerHeight = Math.max(0, measuredHeight - padding.top() - padding.bottom());
        float mainAvailable = row ? innerWidth : innerHeight;
        float crossAvailable = row ? innerHeight : innerWidth;

        collectVisibleChildren();
        if (visibleChildren.isEmpty()) {
            return;
        }

        float crossParent = row ? crossAvailable : crossAvailable;
        for (LayoutNode child : visibleChildren) {
            child.resolvedMain = row
                    ? resolveMainWidth(child, innerWidth, crossAvailable)
                    : resolveMainHeight(child, innerHeight, crossAvailable);
        }

        resolveOverflow(row, mainAvailable);

        collectVisibleChildren();
        float usedMain = usedMain(row);
        float free = mainAvailable - usedMain;
        if (free > EPSILON) {
            float totalGrow = 0;
            for (LayoutNode child : visibleChildren) {
                totalGrow += child.flexGrow;
            }
            if (totalGrow > 0) {
                for (LayoutNode child : visibleChildren) {
                    if (child.flexGrow > 0) {
                        child.resolvedMain += free * (child.flexGrow / totalGrow);
                    }
                }
            }
        }

        boolean hidden = false;
        for (LayoutNode child : visibleChildren) {
            if (!child.isLayoutVisible()) {
                continue;
            }
            float minMain = row ? child.minWidth : child.minHeight;
            if (child.hideOnOverflow && child.resolvedMain + EPSILON < minMain) {
                child.setCollapsed(true);
                hidden = true;
                continue;
            }
            float crossMarginStart = row ? child.margin.top() : child.margin.left();
            float crossMarginEnd = row ? child.margin.bottom() : child.margin.right();
            float cross = resolveCross(child, child.resolvedMain, crossAvailable,
                    crossMarginStart, crossMarginEnd);
            float minCross = row ? child.minHeight : child.minWidth;
            if (child.hideOnOverflow && cross + EPSILON < minCross) {
                child.setCollapsed(true);
                hidden = true;
                continue;
            }
            if (row) {
                child.measure(child.resolvedMain, cross);
            } else {
                child.measure(cross, child.resolvedMain);
            }
        }
        if (hidden) {
            measureChildren();
            return;
        }
        if (collapseTrailingEmptyContainers()) {
            measureChildren();
        }
    }

    private boolean collapseTrailingEmptyContainers() {
        collectVisibleChildren();
        boolean changed = false;
        for (int i = 0; i < visibleChildren.size(); i++) {
            LayoutNode child = visibleChildren.get(i);
            if (!child.isCollapseWhenEmptyAndTrailing() || !(child instanceof FlexBox box)) {
                continue;
            }
            if (!box.isEffectivelyEmpty()) {
                continue;
            }
            boolean hasFollowing = false;
            for (int j = i + 1; j < visibleChildren.size(); j++) {
                if (visibleChildren.get(j).isLayoutVisible()) {
                    hasFollowing = true;
                    break;
                }
            }
            if (!hasFollowing) {
                child.setCollapsed(true);
                changed = true;
            }
        }
        return changed;
    }

    public boolean isEffectivelyEmpty() {
        for (LayoutNode child : children) {
            if (child.isLayoutVisible()) {
                return false;
            }
        }
        return true;
    }

    private void resolveOverflow(boolean row, float mainAvailable) {
        List<Integer> orders = new ArrayList<>();
        for (LayoutNode child : visibleChildren) {
            if (!orders.contains(child.overflowOrder)) {
                orders.add(child.overflowOrder);
            }
        }
        orders.sort((a, b) -> Integer.compare(b, a));

        for (int order : orders) {
            while (true) {
                collectVisibleChildren();
                float overflow = usedMain(row) - mainAvailable;
                if (overflow <= EPSILON) {
                    return;
                }
                if (!shrinkGroupOnce(row, order, overflow) && !hideGroupOnce(order)) {
                    break;
                }
            }
        }
    }

    private boolean shrinkGroupOnce(boolean row, int order, float overflow) {
        float totalWeight = 0;
        for (LayoutNode child : visibleChildren) {
            if (child.overflowOrder != order || !child.isLayoutVisible()) {
                continue;
            }
            float minMain = row ? child.minWidth : child.minHeight;
            if (child.flexShrink > 0 && child.resolvedMain > minMain + EPSILON) {
                totalWeight += child.flexShrink * child.resolvedMain;
            }
        }
        if (totalWeight <= EPSILON) {
            return false;
        }

        boolean changed = false;
        for (LayoutNode child : visibleChildren) {
            if (child.overflowOrder != order || !child.isLayoutVisible()) {
                continue;
            }
            float minMain = row ? child.minWidth : child.minHeight;
            if (child.flexShrink <= 0 || child.resolvedMain <= minMain + EPSILON) {
                continue;
            }
            float weight = child.flexShrink * child.resolvedMain;
            float share = overflow * (weight / totalWeight);
            float capacity = child.resolvedMain - minMain;
            if (share > capacity + EPSILON) {
                if (child.hideOnOverflow) {
                    child.setCollapsed(true);
                    return true;
                }
                if (child.resolvedMain != minMain) {
                    child.resolvedMain = minMain;
                    changed = true;
                }
            } else if (share > EPSILON) {
                child.resolvedMain -= share;
                changed = true;
            }
        }
        return changed;
    }

    private boolean hideGroupOnce(int order) {
        LayoutNode victim = null;
        for (LayoutNode child : visibleChildren) {
            if (child.overflowOrder != order || !child.isLayoutVisible() || !child.hideOnOverflow) {
                continue;
            }
            if (victim == null || child.flexShrink > victim.flexShrink) {
                victim = child;
            }
        }
        if (victim == null) {
            return false;
        }
        victim.setCollapsed(true);
        return true;
    }

    private float usedMain(boolean row) {
        float used = visibleChildren.isEmpty() ? 0 : gap * (visibleChildren.size() - 1);
        for (LayoutNode child : visibleChildren) {
            if (child.isLayoutVisible()) {
                used += child.resolvedMain + (row
                        ? child.margin.left() + child.margin.right()
                        : child.margin.top() + child.margin.bottom());
            }
        }
        return used;
    }

    private float resolveMainWidth(LayoutNode child, float parentWidth, float parentHeight) {
        float width;
        if (child.flexGrow > 0 && (child.widthMode == SizeSpec.Mode.AUTO || child.widthMode == SizeSpec.Mode.FILL)) {
            width = 0;
        } else if (child.widthMode == SizeSpec.Mode.AUTO && child.heightMode == SizeSpec.Mode.FILL && child.aspectRatio > 0) {
            width = parentHeight * child.aspectRatio;
        } else {
            width = resolveSize(child.widthMode, child.widthValue, parentWidth,
                    child.intrinsicWidth(parentWidth, parentHeight));
        }
        return width;
    }

    private float resolveMainHeight(LayoutNode child, float parentHeight, float parentWidth) {
        float height;
        if (child.flexGrow > 0 && (child.heightMode == SizeSpec.Mode.AUTO || child.heightMode == SizeSpec.Mode.FILL)) {
            height = 0;
        } else if (child.heightMode == SizeSpec.Mode.AUTO && child.widthMode == SizeSpec.Mode.FILL && child.aspectRatio > 0) {
            height = parentWidth / child.aspectRatio;
        } else {
            height = resolveSize(child.heightMode, child.heightValue, parentHeight,
                    child.intrinsicHeight(parentWidth, parentHeight));
        }
        return height;
    }

    private float resolveCross(LayoutNode child, float main, float parentCross,
                               float marginStart, float marginEnd) {
        AlignItems align = switch (child.alignSelf) {
                    case START -> AlignItems.START;
                    case CENTER -> AlignItems.CENTER;
                    case END -> AlignItems.END;
                    case STRETCH -> AlignItems.STRETCH;
                    case AUTO -> alignItems;
                };
        boolean row = direction == FlexDirection.ROW;
        SizeSpec.Mode crossMode = row ? child.heightMode : child.widthMode;
        float crossValue = row ? child.heightValue : child.widthValue;
        float available = Math.max(0, parentCross - marginStart - marginEnd);
        float cross;
        if (align == AlignItems.STRETCH && (crossMode == SizeSpec.Mode.AUTO || crossMode == SizeSpec.Mode.FILL)) {
            cross = available;
        } else if (crossMode == SizeSpec.Mode.AUTO && child.aspectRatio > 0 && main > 0) {
            cross = row ? main / child.aspectRatio : main * child.aspectRatio;
        } else {
            cross = resolveSize(crossMode, crossValue, parentCross,
                    row ? child.intrinsicHeight(main, parentCross) : child.intrinsicWidth(parentCross, main));
        }
        return Math.min(cross, available);
    }

    @Override
    public void arrange(float x, float y) {
        this.x = x;
        this.y = y;
        writeBounds();

        boolean row = direction == FlexDirection.ROW;
        float innerWidth = Math.max(0, measuredWidth - padding.left() - padding.right());
        float innerHeight = Math.max(0, measuredHeight - padding.top() - padding.bottom());
        float mainSize = row ? innerWidth : innerHeight;
        float crossSize = row ? innerHeight : innerWidth;

        collectVisibleChildren();
        float usedMain = visibleChildren.isEmpty() ? 0 : gap * (visibleChildren.size() - 1);
        for (LayoutNode child : visibleChildren) {
            if (child.isLayoutVisible()) {
                usedMain += child.outerMain(row);
            }
        }
        float remaining = mainSize - usedMain;
        float cursor = switch (justifyContent) {
            case START -> 0;
            case CENTER -> Math.max(0, remaining) / 2;
            case END -> remaining;
            case CENTER_IF_SINGLE -> visibleChildren.size() == 1
                    ? Math.max(0, remaining) / 2
                    : 0;
        };

        for (LayoutNode child : visibleChildren) {
            if (!child.isLayoutVisible()) {
                continue;
            }
            float mainMarginStart = row ? child.margin.left() : child.margin.top();
            float crossMarginStart = row ? child.margin.top() : child.margin.left();
            float crossMarginEnd = row ? child.margin.bottom() : child.margin.right();
            AlignItems align = switch (child.alignSelf) {
                        case START -> AlignItems.START;
                        case CENTER -> AlignItems.CENTER;
                        case END -> AlignItems.END;
                        case STRETCH -> AlignItems.STRETCH;
                        case AUTO -> alignItems;
                    };
            float childCross = row ? child.measuredHeight : child.measuredWidth;
            float crossStart = switch (align) {
                case START, STRETCH -> crossMarginStart;
                case CENTER -> crossMarginStart + (crossSize - crossMarginStart - crossMarginEnd - childCross) / 2;
                case END -> crossSize - crossMarginEnd - childCross;
            };
            if (row) {
                child.arrange(this.x + padding.left() + cursor + mainMarginStart,
                        this.y + padding.top() + crossStart);
            } else {
                child.arrange(this.x + padding.left() + crossStart,
                        this.y + padding.top() + cursor + mainMarginStart);
            }
            cursor += child.outerMain(row) + gap;
        }
    }

    private void collectVisibleChildren() {
        visibleChildren.clear();
        for (LayoutNode child : children) {
            if (child.isLayoutVisible()) {
                visibleChildren.add(child);
            }
        }
    }
}
