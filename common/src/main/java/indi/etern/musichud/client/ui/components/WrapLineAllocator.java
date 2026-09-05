package indi.etern.musichud.client.ui.components;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure line-wrapping allocator used by {@link FlexWrapLayout}. It decides which
 * row index a child of a given width should be placed into for a fixed container
 * width, keeping the accumulated width of every row within the container.
 */
final class WrapLineAllocator {
    private final int containerWidth;
    private final List<Integer> assignedWidths = new ArrayList<>();

    WrapLineAllocator(int containerWidth) {
        this.containerWidth = containerWidth;
    }

    /**
     * Allocate a row index for a child occupying {@code childWidth} pixels
     * (including its horizontal margins).
     *
     * <p>A child is placed on the first row that still fits it. If no existing
     * row fits, it is placed on the first empty row (a single over-wide child is
     * still kept on its own row instead of spawning a new overflowing row for
     * every such child); if there is no empty row either, a fresh row is created.</p>
     *
     * @param childWidth the measured width of the child plus its margins, in pixels
     * @return the index of the target row
     */
    int allocate(int childWidth) {
        for (int i = 0; i < assignedWidths.size(); i++) {
            int currentWidth = assignedWidths.get(i);
            if (currentWidth == 0 || containerWidth - currentWidth >= childWidth) {
                assignedWidths.set(i, currentWidth + childWidth);
                return i;
            }
        }
        assignedWidths.add(childWidth);
        return assignedWidths.size() - 1;
    }

    /**
     * Number of rows currently known to the allocator. Exposed for tests and for
     * {@link FlexWrapLayout} to keep its row list in sync.
     */
    int rowCount() {
        return assignedWidths.size();
    }

    int getRowWidth(int index) {
        return assignedWidths.get(index);
    }
}