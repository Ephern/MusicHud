package indi.etern.musichud.client.ui.render.interop;

/**
 * What the current backend can bridge. Consumers must degrade instead of failing when a direction
 * is unavailable (e.g. disable a backdrop blur).
 */
public record InteropCapabilities(boolean canImport, boolean canExport) {
    public static final InteropCapabilities NONE = new InteropCapabilities(false, false);
    public static final InteropCapabilities FULL = new InteropCapabilities(true, true);
}
