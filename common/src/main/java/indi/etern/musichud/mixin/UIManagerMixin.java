package indi.etern.musichud.mixin;

import icyllis.modernui.core.Core;
import icyllis.modernui.mc.UIManager;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.gpu.MidFrameCommit;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneFrameClock;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Runs the partial recordings the UI thread committed before the main UI recording. Each segment is
 * submitted and then blurred, so compositing inside the next segment already samples a fresh result.
 * <p>
 * They are only submitted together with the same frame's main recording. The UI thread can be slower
 * than the render thread, and displaying a partially recorded frame leaves most of the layer missing
 * or showing the previous frame.
 */
@SuppressWarnings("UnstableApiUsage")
@Mixin(UIManager.class)
public abstract class UIManagerMixin {
    @Unique
    private static final Logger music_hud$LOGGER = MusicHud.getLogger(UIManagerMixin.class);

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Licyllis/arc3d/engine/ImmediateContext;addTask" +
                            "(Licyllis/arc3d/engine/Task;)Z"))
    private void music_hud$runMidFrames(GuiGraphics graphics, int mouseX, int mouseY,
                                        float deltaTick, CallbackInfo ci) {
        List<MidFrameCommit.Commit> commits = MidFrameCommit.takeAll();
        if (commits.isEmpty()) {
            return;
        }
        var context = Core.requireImmediateContext();
        for (MidFrameCommit.Commit commit : commits) {
            boolean added = context.addTask(commit.recording());
            commit.recording().close();
            if (!added) {
                // A failed segment leaves its region cleared but undrawn, and the rest of the
                // frame's commands were already recorded, so the loss is visible for one frame.
                music_hud$LOGGER.error("Failed to add a partial UI recording, batch={}", commit.batch());
            }
            SceneInterop.flushArc3d(context);
            if (SceneInterop.requiresMidFrameSubmit()) {
                if (!context.submit()) {
                    music_hud$LOGGER.error("Failed to submit a partial UI recording, batch={}", commit.batch());
                }
                SceneInterop.prepareGameGraphics();
            }
            try {
                SceneBlurSlot.runAll(RenderRuntime.getInstance().gpu(), commit.batch());
            } finally {
                SceneInterop.restoreArc3d();
            }
        }
    }

    /** Retired textures are released only after this frame's recordings have been submitted. */
    @Inject(method = "render", at = @At("RETURN"))
    private void music_hud$endFrame(GuiGraphics graphics, int mouseX, int mouseY,
                                    float deltaTick, CallbackInfo ci) {
        SceneFrameClock.tick();
    }
}
