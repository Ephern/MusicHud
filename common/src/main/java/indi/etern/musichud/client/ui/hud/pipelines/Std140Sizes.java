package indi.etern.musichud.client.ui.hud.pipelines;

/**
 * Dependency-free std140 size calculator replicating the exact semantics of Minecraft's
 * {@code Std140SizeCalculator}, so metadata beans stay version-neutral.
 */
public class Std140Sizes {
    private int size;

    private Std140Sizes() {
    }

    public static Std140Sizes calc() {
        return new Std140Sizes();
    }

    public int get() {
        return size;
    }

    public Std140Sizes align(int alignment) {
        size = roundToward(size, alignment);
        return this;
    }

    public Std140Sizes putFloat() {
        align(4);
        size += 4;
        return this;
    }

    public Std140Sizes putInt() {
        align(4);
        size += 4;
        return this;
    }

    public Std140Sizes putVec2() {
        align(8);
        size += 8;
        return this;
    }

    public Std140Sizes putVec3() {
        align(16);
        size += 16;
        return this;
    }

    public Std140Sizes putVec4() {
        align(16);
        size += 16;
        return this;
    }

    public Std140Sizes putMat4f() {
        align(16);
        size += 64;
        return this;
    }

    private static int roundToward(int value, int alignment) {
        return value + (value % alignment == 0 ? 0 : alignment - value % alignment);
    }
}
