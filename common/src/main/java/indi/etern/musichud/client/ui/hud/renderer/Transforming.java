package indi.etern.musichud.client.ui.hud.renderer;

import org.joml.Matrix3x2fStack;

import java.util.function.Consumer;

/**
 * Version-neutral 2D transform helper backed by a JOML {@link Matrix3x2fStack}.
 */
public class Transforming {
    private final Matrix3x2fStack pose;

    private Transforming(Matrix3x2fStack pose) {
        this.pose = pose;
        pose.pushMatrix();
    }

    public static Transforming on(Matrix3x2fStack pose) {
        return new Transforming(pose);
    }

    public Transforming translate(float x, float y) {
        pose.translate(x, y);
        return this;
    }

    public Transforming rotate(float angle) {
        pose.rotate(angle);
        return this;
    }

    public Transforming scale(float scale) {
        pose.scale(scale);
        return this;
    }

    public Transforming then(Consumer<Transforming> task) {
        task.accept(this);
        return this;
    }

    public void end(Consumer<Transforming> task) {
        task.accept(this);
        pose.popMatrix();
    }

    public void end() {
        pose.popMatrix();
    }

    public Transforming subTransform(Consumer<Transforming> consumer) {
        pose.pushMatrix();
        consumer.accept(this);
        pose.popMatrix();
        return this;
    }
}
