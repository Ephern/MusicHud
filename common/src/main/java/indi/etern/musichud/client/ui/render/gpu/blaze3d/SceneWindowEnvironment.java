package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Samples the window/monitor state once per scene frame, so a phase spike can be blamed on the
 * environment (focus loss, monitor switch, refresh-rate or DPI change) instead of the renderer.
 */
public final class SceneWindowEnvironment {
    private static final Map<Long, Integer> MONITOR_INDEXES = new HashMap<>();

    private static boolean sampled;
    private static boolean focused;
    private static boolean iconified;
    private static boolean fullscreen;
    private static int monitorIndex = -1;
    private static int refreshRate = -1;
    private static int framebufferWidth;
    private static int framebufferHeight;
    private static int guiScale;

    private static int focusChanges;
    private static int monitorChanges;
    private static int sizeChanges;

    private SceneWindowEnvironment() {
    }

    /** Records one sample; cheap enough to run every scene frame, only called while stats are on. */
    public static void sample() {
        Minecraft minecraft = Minecraft.getInstance();
        //noinspection ConstantValue
        if (minecraft == null) return;
        Window window = minecraft.getWindow();
        //noinspection ConstantValue
        if (window == null) return;
        Monitor monitor = window.findBestMonitor();
        boolean nowFocused = minecraft.isWindowActive();
        boolean nowIconified = window.isIconified();
        boolean nowFullscreen = window.isFullscreen();
        int nowWidth = window.getWidth();
        int nowHeight = window.getHeight();
        int nowScale = window.getGuiScale();
        int nowMonitor = monitorIndex(monitor);
        int nowRefresh = refreshRate(monitor);
        if (sampled) {
            if (nowFocused != focused) focusChanges++;
            if (nowMonitor != monitorIndex) monitorChanges++;
            if (nowWidth != framebufferWidth || nowHeight != framebufferHeight
                    || nowScale != guiScale) sizeChanges++;
        }
        sampled = true;
        focused = nowFocused;
        iconified = nowIconified;
        fullscreen = nowFullscreen;
        framebufferWidth = nowWidth;
        framebufferHeight = nowHeight;
        guiScale = nowScale;
        monitorIndex = nowMonitor;
        refreshRate = nowRefresh;
    }

    /** Focus/monitor/size transitions seen since {@link #beginWindow()}. */
    public static String describe() {
        return " | env: focus=" + (focused ? 1 : 0)
                + " icon=" + (iconified ? 1 : 0)
                + " full=" + (fullscreen ? 1 : 0)
                + " mon=" + monitorIndex
                + " hz=" + refreshRate
                + " fb=" + framebufferWidth + "x" + framebufferHeight + "@" + guiScale
                + " chg=" + focusChanges + "/" + monitorChanges + "/" + sizeChanges;
    }

    /** Starts a fresh reporting window; the previous state is kept so transitions stay detectable. */
    public static void beginWindow() {
        focusChanges = 0;
        monitorChanges = 0;
        sizeChanges = 0;
    }

    private static int monitorIndex(Monitor monitor) {
        if (monitor == null) return -1;
        long handle = monitor.getMonitor();
        Integer known = MONITOR_INDEXES.get(handle);
        if (known != null) return known;
        int index = MONITOR_INDEXES.size();
        MONITOR_INDEXES.put(handle, index);
        return index;
    }

    private static int refreshRate(Monitor monitor) {
        if (monitor == null) return -1;
        VideoMode mode = monitor.getCurrentMode();
        //noinspection ConstantValue
        return mode == null ? -1 : mode.getRefreshRate();
    }
}
