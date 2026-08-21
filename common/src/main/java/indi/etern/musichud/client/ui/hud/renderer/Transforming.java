package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.function.Consumer;

public class Transforming {
    private final PoseStack pose;

    private Transforming(PoseStack pose) {
        this.pose = pose;
        pose.pushPose();
    }

    public static Transforming on(PoseStack pose) {
        return new Transforming(pose);
    }

    public Transforming translate(float x, float y) {
        pose.translate(x, y, 0);
        return this;
    }

    public Transforming rotate(float angle) {
        pose.mulPose(Axis.ZP.rotation(angle));
        return this;
    }

    public Transforming scale(float scale) {
        pose.scale(scale, scale, 0);
        return this;
    }

    public Transforming then(Consumer<Transforming> task) {
        task.accept(this);
        return this;
    }

    public void end(Consumer<Transforming> task) {
        task.accept(this);
        pose.popPose();
    }

    public void end() {
        pose.popPose();
    }

    public Transforming subTransform(Consumer<Transforming> consumer) {
        pose.pushPose();
        consumer.accept(this);
        pose.popPose();
        return this;
    }
}
