package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.warp.ProceduralWarpCoverage;
import indi.etern.musichud.client.ui.render.warp.ProceduralWarpCycle;
import indi.etern.musichud.client.ui.render.warp.ProceduralWarpTargets;
import indi.etern.musichud.client.ui.render.warp.ProceduralWarpTiming;
import indi.etern.musichud.client.ui.render.warp.WarpMesh;
import indi.etern.musichud.client.ui.render.warp.WarpMeshEvaluator;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class SceneCoverWarpProcedural implements AutoCloseable {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverWarpProcedural.class);
    private static final SceneSamplerState LINEAR_CLAMP = SceneSamplerState.linearClamp();

    private SceneGpuContext owner;
    private SceneGpuTexture target;
    private SceneMesh mesh;
    private ByteBuffer vertexData;
    private WarpMesh targetMesh;
    private ProceduralWarpTargets.Target currentTarget;
    private ProceduralWarpTargets.Target pendingTarget;
    private float startTime = Float.NaN;
    private long cycleIndex;
    private int width;
    private int height;
    private boolean closed;

    public SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture cover, float time) {
        if (closed || gpu == null || cover == null) return cover;
        try {
            ensureTarget(gpu, cover.width(), cover.height());
            ensureCycle(time);
            draw(gpu, cover, time);
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While applying the procedural cover warp", failure);
            reset();
            return cover;
        }
    }

    private void ensureTarget(SceneGpuContext gpu, int width, int height) {
        if (target != null && owner == gpu && this.width == width && this.height == height) {
            return;
        }
        releaseMesh();
        releaseTarget(gpu);
        target = gpu.createRenderTarget("MusicHud procedural cover warp", width, height,
                SceneTextureFormat.RGBA16F);
        owner = gpu;
        this.width = width;
        this.height = height;
        targetMesh = null;
        currentTarget = null;
        pendingTarget = null;
        startTime = Float.NaN;
    }

    private void ensureCycle(float time) {
        if (Float.isNaN(startTime)) {
            startTime = time;
            currentTarget = ProceduralWarpTargets.defaultTarget();
            pendingTarget = ProceduralWarpTargets.randomTarget(ThreadLocalRandom.current());
            cycleIndex = ProceduralWarpCycle.index(time, startTime);
            updateTargetMesh();
            return;
        }
        long index = ProceduralWarpCycle.index(time, startTime);
        if (index != cycleIndex) {
            currentTarget = pendingTarget != null ? pendingTarget
                    : ProceduralWarpTargets.randomTarget(ThreadLocalRandom.current());
            pendingTarget = ProceduralWarpTargets.randomTarget(ThreadLocalRandom.current());
            cycleIndex = index;
            updateTargetMesh();
        }
    }

    private void updateTargetMesh() {
        ProceduralWarpTargets.Target target = Objects.requireNonNull(currentTarget);
        WarpMesh next = WarpMeshEvaluator.evaluate(target.offsets(), target.gridSize(),
                target.interpolation(), WarpMesh.DEFAULT_RESOLUTION, target.zoom());
        targetMesh = ProceduralWarpCoverage.ensureCoverage(next);
        allocateMeshIfNeeded();
    }

    private void allocateMeshIfNeeded() {
        WarpMesh target = targetMesh;
        int indexCount = target.indices().length;
        if (mesh != null && mesh.indexCount() == indexCount) {
            return;
        }
        releaseMesh();
        int vertexBytes = target.positions().length / 2 * 20;
        vertexData = ByteBuffer.allocateDirect(vertexBytes).order(ByteOrder.nativeOrder());
        mesh = Objects.requireNonNull(owner).createMesh("Procedural cover warp mesh",
                vertexBytes, target.indices());
        targetMesh = target;
    }

    private void draw(SceneGpuContext gpu, SceneGpuTexture cover, float time) {
        if (targetMesh == null || mesh == null || vertexData == null) return;
        float gate = ProceduralWarpTiming.envelope(
                ProceduralWarpCycle.phase(time, startTime));
        float[] targetPositions = targetMesh.positions();
        float[] uv = targetMesh.uv();
        vertexData.clear();
        for (int i = 0; i < uv.length; i += 2) {
            float x = uv[i] + (targetPositions[i] - uv[i]) * gate;
            float y = uv[i + 1] + (targetPositions[i + 1] - uv[i + 1]) * gate;
            vertexData.putFloat(x).putFloat(y).putFloat(0)
                    .putFloat(uv[i]).putFloat(uv[i + 1]);
        }
        vertexData.flip();
        gpu.updateMesh(mesh, vertexData);
        ScenePipeline pipeline = gpu.pipeline(ScenePipelineKey.SCENE_COVER_WARP_PROCEDURAL,
                target.actualFormat());
        gpu.clear(target, 0xff000000);
        gpu.drawIndexed(new SceneIndexedDraw(new SceneDraw(target, pipeline,
                List.of(new SceneSamplerBinding("Sampler0", cover,
                        gpu.sampler(LINEAR_CLAMP))), List.of()), mesh));
    }

    private void releaseMesh() {
        if (mesh != null && owner != null) {
            owner.release(mesh);
        }
        mesh = null;
        vertexData = null;
        targetMesh = null;
    }

    private void releaseTarget(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
        currentTarget = null;
        pendingTarget = null;
        startTime = Float.NaN;
    }

    public void reset() {
        if (closed) return;
        releaseMesh();
        if (target != null && owner != null) {
            owner.release(target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
        currentTarget = null;
        pendingTarget = null;
        startTime = Float.NaN;
    }

    @Override
    public void close() {
        if (closed) return;
        reset();
        closed = true;
    }
}
