package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
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

    private SceneGpuContext owner;
    private SceneGpuTexture target;
    private SceneMesh mesh;
    /** Topology currently uploaded on the render thread. */
    private WarpMesh targetMesh;
    private ProceduralWarpTargets.Target currentTarget;
    /** Prefetched next-cycle target, built off the render thread. */
    private volatile ProceduralWarpTargets.Target preparedTarget;
    private boolean targetRequestInFlight;
    private float startTime = Float.NaN;
    private long cycleIndex;
    /** Bumped whenever the expected mesh changes, so stale worker results are dropped. */
    private long generation;
    private volatile PreparedMesh prepared;
    private int width;
    private int height;
    private boolean closed;

    /**
     * Result of the off-thread mesh build: untouched by GL until uploaded.
     */
    private record PreparedMesh(long generation, WarpMesh mesh, ByteBuffer vertices) {
    }

    public SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture cover, float time) {
        if (closed || gpu == null || cover == null) return cover;
        try {
            ensureTarget(gpu, cover.width(), cover.height());
            ensureCycle(time);
            // Building the mesh takes several milliseconds (evaluate + coverage + metrics), so it
            // runs on a worker thread; until it lands the un-warped cover is used. The cycle starts
            // at gate = 0, so skipping the warp for a frame or two is invisible.
            if (!uploadPreparedMesh(gpu)) return cover;
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
        preparedTarget = null;
        startTime = Float.NaN;
        generation++;
        prepared = null;
        try {
            // Only on (re)allocation: the mesh covers the whole viewport, so the per-frame clear is
            // gone, but freshly allocated storage must never be sampled as stale memory.
            gpu.clear(target, 0xff000000);
        } catch (RuntimeException failure) {
            LOGGER.debug("While clearing a fresh procedural cover warp target", failure);
        }
    }

    private void ensureCycle(float time) {
        if (Float.isNaN(startTime)) {
            startTime = time;
            currentTarget = ProceduralWarpTargets.defaultTarget();
            cycleIndex = ProceduralWarpCycle.index(time, startTime);
            requestTarget();
            requestMesh(currentTarget);
            return;
        }
        long index = ProceduralWarpCycle.index(time, startTime);
        if (index != cycleIndex) {
            // Never build the next preset here: fitting it costs a full set of mesh evaluations
            // (hundreds of microseconds to several milliseconds). It is prefetched one cycle ahead.
            ProceduralWarpTargets.Target next = preparedTarget;
            preparedTarget = null;
            currentTarget = next != null ? next : ProceduralWarpTargets.defaultTarget();
            cycleIndex = index;
            requestTarget();
            requestMesh(currentTarget);
        }
    }

    /** Builds the next cycle's preset off the render thread; a late result just costs one cycle. */
    private void requestTarget() {
        if (targetRequestInFlight) return;
        targetRequestInFlight = true;
        try {
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    preparedTarget = ProceduralWarpTargets.randomTarget(ThreadLocalRandom.current());
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.debug("While building the next procedural cover warp target", failure);
                } finally {
                    targetRequestInFlight = false;
                }
            });
        } catch (RuntimeException rejected) {
            targetRequestInFlight = false;
            LOGGER.debug("Could not queue the next procedural cover warp target", rejected);
        }
    }

    /** Evaluates the target mesh off the render thread and publishes it for upload. */
    private void requestMesh(ProceduralWarpTargets.Target request) {
        if (request == null) return;
        long requestGeneration = ++generation;
        try {
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    WarpMesh evaluated = WarpMeshEvaluator.evaluate(request.offsets(), request.gridSize(),
                            request.interpolation(), WarpMesh.DEFAULT_RESOLUTION, request.zoom());
                    WarpMesh covered = ProceduralWarpCoverage.ensureCoverage(evaluated);
                    float[] positions = covered.positions();
                    float[] uv = covered.uv();
                    ByteBuffer vertices = ByteBuffer.allocateDirect(positions.length / 2 * 20)
                            .order(ByteOrder.nativeOrder());
                    for (int i = 0; i < uv.length; i += 2) {
                        // Position carries the undeformed canvas uv, UV0 the coverage-expanded target.
                        vertices.putFloat(uv[i]).putFloat(uv[i + 1]).putFloat(0f)
                                .putFloat(positions[i]).putFloat(positions[i + 1]);
                    }
                    vertices.flip();
                    prepared = new PreparedMesh(requestGeneration, covered, vertices);
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.debug("While building the procedural cover warp mesh", failure);
                }
            });
        } catch (RuntimeException rejected) {
            // Executor is shutting down: keep the previous mesh (or the un-warped cover).
            LOGGER.debug("Could not queue the procedural cover warp mesh build", rejected);
        }
    }

    /** @return true when a mesh is uploaded and the pass can run. */
    private boolean uploadPreparedMesh(SceneGpuContext gpu) {
        PreparedMesh ready = prepared;
        if (ready == null) {
            return targetMesh != null;
        }
        prepared = null;
        if (ready.generation() != generation) {
            return targetMesh != null;
        }
        WarpMesh meshData = ready.mesh();
        if (mesh == null || mesh.indexCount() != meshData.indices().length) {
            releaseMesh();
            mesh = Objects.requireNonNull(owner).createMesh("Procedural cover warp mesh",
                    ready.vertices().remaining(), meshData.indices());
        }
        targetMesh = meshData;
        gpu.updateMesh(mesh, ready.vertices());
        return true;
    }

    private void draw(SceneGpuContext gpu, SceneGpuTexture cover, float time) {
        if (targetMesh == null || mesh == null) return;
        float gate = ProceduralWarpTiming.envelope(
                ProceduralWarpCycle.phase(time, startTime));
        ScenePipeline pipeline = gpu.pipeline(ScenePipelineKey.SCENE_COVER_WARP_PROCEDURAL,
                target.actualFormat());
        // No pre-clear: ProceduralWarpCoverage guarantees the mesh spans the whole viewport, even
        // at gate = 0 where the mesh maps exactly onto [0, 1].
        gpu.drawIndexed(new SceneIndexedDraw(new SceneDraw(target, pipeline,
                List.of(new SceneSamplerBinding("Sampler0", cover)),
                List.of(new SceneUniformBinding(WarpUniform.UBO_NAME,
                        new WarpUniform(gate)))), mesh));
    }

    private void releaseMesh() {
        if (mesh != null && owner != null) {
            owner.release(mesh);
        }
        mesh = null;
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
        preparedTarget = null;
        startTime = Float.NaN;
    }

    public void reset() {
        if (closed) return;
        // Invalidate any in-flight worker result before dropping the GL objects.
        generation++;
        prepared = null;
        releaseMesh();
        if (target != null && owner != null) {
            owner.release(target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
        currentTarget = null;
        preparedTarget = null;
        startTime = Float.NaN;
    }

    @Override
    public void close() {
        if (closed) return;
        reset();
        closed = true;
    }

    /** Deformation amount of the running cycle; 0 is the undeformed mesh, 1 the cycle target. */
    private record WarpUniform(float gate) implements SceneUniform {
        private static final String UBO_NAME = "MHWarp";
        private static final int UBO_SIZE = Std140Sizes.calc().putVec4().get();

        @Override
        public String getUBOName() {
            return UBO_NAME;
        }

        @Override
        public int getUBOSize() {
            return UBO_SIZE;
        }

        @Override
        public void write(Std140Writer writer) {
            writer.putVec4(gate, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
