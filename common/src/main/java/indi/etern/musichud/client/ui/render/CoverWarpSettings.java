package indi.etern.musichud.client.ui.render;

import java.util.Arrays;

/**
 * Shared, in-memory state for the cover FFD warp and its debug screen.
 * <p>
 * The control grid is square and supports 3x3 up to 9x9 control points. Offsets are
 * stored in normalized cover UV units.
 */
public final class CoverWarpSettings {
    public static final int MIN_GRID = 3;
    public static final int MAX_GRID = 9;
    public static final int DEFAULT_GRID = 4;
    public static final int MAX_CONTROL_POINTS = MAX_GRID * MAX_GRID;

    public enum PreviewMode {
        OFF,
        TILED,
        LAYERS
    }

    public record Snapshot(int gridSize, boolean enabled, long revision, float[] offsets, float strength) {
    }

    private static final CoverWarpSettings INSTANCE = new CoverWarpSettings();

    private boolean enabled;
    private boolean tiledPreviewBlur;
    private PreviewMode previewMode = PreviewMode.OFF;
    private int gridSize = DEFAULT_GRID;
    private float strength = 1f;
    private final float[] offsets = new float[MAX_CONTROL_POINTS * 2];
    private long revision;

    private CoverWarpSettings() {
    }

    public static CoverWarpSettings getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            revision++;
        }
    }

    public synchronized PreviewMode getPreviewMode() {
        return previewMode;
    }

    public synchronized boolean isTiledPreviewBlur() {
        return tiledPreviewBlur;
    }

    public synchronized void setTiledPreviewBlur(boolean tiledPreviewBlur) {
        this.tiledPreviewBlur = tiledPreviewBlur;
    }

    public synchronized void setPreviewMode(PreviewMode previewMode) {
        PreviewMode mode = previewMode == null ? PreviewMode.OFF : previewMode;
        if (this.previewMode != mode) {
            this.previewMode = mode;
            revision++;
        }
    }

    public synchronized int getGridSize() {
        return gridSize;
    }

    public synchronized void setGridSize(int gridSize) {
        int clamped = Math.clamp(gridSize, MIN_GRID, MAX_GRID);
        if (this.gridSize != clamped) {
            this.gridSize = clamped;
            Arrays.fill(offsets, 0f);
            revision++;
        }
    }

    public synchronized float getStrength() {
        return strength;
    }

    public synchronized void setStrength(float strength) {
        float clamped = Math.clamp(strength, 0f, 4f);
        if (this.strength != clamped) {
            this.strength = clamped;
            revision++;
        }
    }

    public synchronized float getOffsetX(int x, int y) {
        return offsets[(y * gridSize + x) * 2];
    }

    public synchronized float getOffsetY(int x, int y) {
        return offsets[(y * gridSize + x) * 2 + 1];
    }

    public synchronized void setOffset(int x, int y, float dx, float dy) {
        if (x < 0 || y < 0 || x >= gridSize || y >= gridSize) {
            return;
        }
        int index = (y * gridSize + x) * 2;
        offsets[index] = dx;
        offsets[index + 1] = dy;
        revision++;
    }

    public synchronized void resetOffsets() {
        Arrays.fill(offsets, 0f);
        revision++;
    }

    public synchronized long getRevision() {
        return revision;
    }

    public synchronized Snapshot snapshot() {
        int count = gridSize * gridSize * 2;
        return new Snapshot(gridSize, enabled, revision,
                Arrays.copyOf(offsets, count), strength);
    }
}
