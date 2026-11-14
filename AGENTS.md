# Music HUD - Agent Guide

Multi-loader Minecraft mod (Fabric + NeoForge + Paper), specific Minecraft version should be determined by gradle.properties and git branch name. A GUI-based full-server song request system powered by Netease Cloud Music API.

## Build & Run

```bash
# Build specific loader (produces shadowed fat jars):
./gradlew fabric:build          # build/libs/music_hud-fabric-<version>.jar
./gradlew neoforge:build        # build/libs/music_hud-neoforge-<version>.jar
./gradlew paper:build           # build/libs/music_hud-paper-<version>.jar

# Run (Fabric/NeoForge via Loom):
./gradlew fabric:runClient
./gradlew neoforge:runClient
./gradlew fabric:runServer
./gradlew neoforge:runServer
```

## Critical Quirks

- **Paper module is separate** — uses `paperweight.userdev` directly, NOT Architectury Loom. Paper is NOT in `settings.gradle` on this branch.
- **Build scripts are Groovy DSL** (`.gradle`), not Kotlin (`.gradle.kts`).
- **`core` module is a plain `java-library` (NO Loom)** — excluded by `build.gradle:16`: `configure(subprojects.findAll { it.name != 'core' })`.
  - Platform modules (`fabric`/`neoforge`) add `core` via a dedicated `coreLib` configuration that extends `compileClasspath` + `runtimeClasspath` but **NOT** `developmentFabric`/`developmentNeoForge` — this prevents Architectury Transformer from applying unnecessary transforms (`GenerateFakeFabricMod`, `RemapInjectables`).
- **`-Dfabric.dli.config` must be overridden in Fabric `loom.runs`** — Architectury Loom 1.17.487 generates DLI config at `.gradle/loom-cache/projects/<subproject>/launch.cfg` but the injector property points to `<subproject>/.gradle/loom-cache/launch.cfg`. Without the override, `dev-launch-injector` enters pass-through mode → mods and Minecraft assets won't load.
- **ModernUI 3.13+ is required for the client UI** — this branch compiles and runs against the Lumen fork (`github.com/MOPELotus/ModernUI-MC-Lumen`), because 1.21.9+ has no official build. The fork's universal jars live in `libs/` and are picked by `modernUiLumenJar('<Fabric|NeoForge>')` in the root `build.gradle` (`ModernUI-Lumen-<platform>-<minecraft_version>-*-universal.jar`); they are declared as a **mod dependency** (`modImplementation`), so the jar is on the compile *and* dev runtime classpath. The transformer runs before mod loading and resolves ModernUI classes from the classpath, so `compileOnly` alone breaks the dev launch. The universal jar keeps `arc3d-compiler`/`commonmark` in `META-INF/jars`, which dev classpath mods do not expand, so `modernUiLumenLibraries('<platform>')` unpacks them into `build/modernui-libs/` and they are added with `implementation files(...)`. Do not copy the fork jar into `run/*/mods` (duplicate mod) and do not add ModernUI Maven coordinates (their core/markflow would shadow the fork). Fabric declares the dependency under `recommends` because the same jar runs on servers; the client probes `icyllis.arc3d.engine.ImageProxy` at runtime and falls back to a vanilla `ModernUiRequiredScreen` with the HUD left unregistered.
- **Dev run classpath must contain one ASM version** — Fabric loader 0.18.x ships ASM 9.9 while NeoForge 21.9 ships 9.8, and `Knot` aborts on duplicates. Root `build.gradle` forces `org.ow2.asm:*:9.9` in every configuration; keep that block when adding dependencies.
- **Fork version requirements** — the fork's `fabric.mod.json` needs `mixinextras >= 0.5.5`, which is only bundled from **fabric-loader 0.19.5** onwards (0.19.4 still ships 0.5.4); its `neoforge.mods.toml` needs **NeoForge `[21.9.16-beta, 21.10)`**.
- **Lumen fork text API differs from upstream 3.13** — `ModernTextGeometry` uses the fork's `icyllis.modernui.mc.text.ModernBakedGlyph` (upstream calls it `GLBakedGlyph`) and gets textures through `AbstractTexture.getTextureView()`.

## Architecture

```
core/            — platform-independent Java library (no Minecraft deps)
                   network codecs, payload interfaces, data beans, JMTC (SMTC/MPRIS),
                   server API interfaces, utility classes (RegistrationManager, etc.)
common/          — Architectury common module with Minecraft deps
                   UI (ModernUI), audio (stream decoders), mixins, platform service impls
fabric/          — Fabric loader adapter layer (shadow-jars common + core)
neoforge/        — NeoForge adapter layer (shadow-jars common + core)
paper/           — Paper/Bukkit plugin (on a separate branch, not in settings here)
```

`common` depends on `core` (`api project(':core')` in `common/build.gradle:24`). Platform modules shadow both `common` and `core` via `shadowBundle`.

The `configure(subprojects.findAll { it.name != 'core' })` block in `build.gradle:16` applies Loom to `common`, `fabric`, `neoforge` — but NOT `core`.

## Key Patterns

- **Service Locator** (not DI): `Environment.Platform.load()` uses `Class.forName()` + reflection to load platform-specific implementations. Interfaces: `ServerConfig`, `ClientConfig`, `IClientEventService`, `IServerEventService`, `INetworkRegister`, `IKeyRegistryService`.
- **Auto-Registration**: `RegistrationManager` loads classes by string array, instantiates `Register` implementors. `@RegisterMark` annotation marks registered classes. Called via `performCommonAutoRegistration()` / `performSideAutoRegistration()`.
- **Custom Network Protocol**: 30+ `CustomPacketPayload` classes in `network/payloads/`. Request/response cycle (`requestResponseCycle/`) + push messages (`pushMessages/`). Register via `INetworkRegister`.
- **Virtual Threads**: `MusicHud.EXECUTOR` = `Executors.newVirtualThreadPerTaskExecutor()`. Used for audio decoding, API server management, network I/O.
- **Mixin**: Config `music_hud.mixins.json`.
- **Config**: Forge Config API Port (Fabric, shared with NeoForge) / native NeoForge `ModConfig` / Paper `config.yml`.

## Render Backend Boundary

- `common/.../client/ui/render/gpu/` is the version-neutral scene layer. It must not import `com.mojang.blaze3d.*`, `com.mojang.renderpearl.*`, `org.lwjgl.*`, `net.neoforged.*`, or `net.minecraft.*`.
- `gpu/blaze3d/` is the 1.21.6–1.21.10 blaze3d command adapter. `gpu/blaze3d/gl/` is the only place allowed to use raw OpenGL and NeoForge `ValidationGpuDevice` reflection.
- Effects use `SceneGpuContext` plus `SceneTextureFormat`/`SceneSamplerBinding`/`SceneUniform`; they must not cast to backend types or probe GL directly. Filtering/address mode live on the texture (set at allocation), not on the binding.
- `SceneGpuContext` has no generic readback API. Add readback only with a backend capability and a real implementation; the native RGBA16F shim advertises RGBA8 metadata and must never be used for CPU transfer.
- Scene-layer GPU objects are released through `SceneRetirement` (at least 3 frames **and** 50 ms), never immediately: VulkanMod keeps several frames in flight and reuses image memory at once, so a few frames at a high frame rate are not enough wall time (OpenGL's driver hides this).
- Offscreen uniforms live in MC's `DynamicUniformStorage` — a **3-buffer ring** that assumes **one rotation per rendered frame**. `RenderRuntime.endSceneFrame()` rotates at most once per frame (stamped in `GuiRenderer.prepare`). Rotating per scene render (the HUD and the screen are separate scenes, 2+ per frame) shortens the ring below the frames VulkanMod keeps in flight, so a later draw overwrites a uniform still being read — the blur reads another pass's sigma and the fluid background flickers between blur radii.
- The scene chain is deliberately **single-buffered**: each stage writes a texture that later passes sample in the **same** submission (VulkanMod inserts the layout barriers at pass boundaries), and the published GUI background is written before the UI recording that samples it. Ping-pong buffers plus a one-frame publish delay were tried as a "safe default" and fixed nothing — the flicker came from the uniform ring above — while costing a frame of latency per stage and ~10 MB of targets. Do not re-add them without a reproduced failure.
- `SceneBlur` keeps a pyramid level once it was allocated and caches one gaussian scratch per level, so an animating radius (lyric rows) never reallocates while it sweeps across levels. `SceneBlur.prepare(..., resultScale)` may run the blur at `1/2` or `1/4` of the output size; the published texture is then smaller and **only consumers that upscale may ask for it** (`LineBlurRenderer.drawResult` uses the image's own size). `SceneBlurPainter` (tooltips, modal and layer backdrops) maps the image 1:1 in device pixels, so it must always get `resultScale == 1`.
- The lyric view builds one `LyricLineView` per line for the whole song, and each row allocates its blur/bloom targets lazily. Rows are therefore released after staying out of the viewport (`LyricLineView.releaseBlurTargetsWhenIdle`, 1.5 s) — without that, GPU memory grows with song length instead of with the visible rows.
- `interop/` bridges scene textures to and from arc3d. `SceneInteropBackends` picks `GlInteropBackend` (OpenGL), `VkInteropBackend` (Fabric + direct `VkGpuDevice`), or a no-op; override with `-Dmusic_hud.debug.sceneBackend=auto|gl|vk|none`.
- Vulkan is **delayed**: VulkanMod's frame order is untouched, so arc3d samples textures written at least one UI frame earlier (`SceneInteropBackend.delaysExportedTextures()`, gated by `SceneFrameClock`, which ticks at the end of `UIManager.render`). Do not add `Renderer.flushCmds()` or VulkanMod mixins to that path. For the same reason partial UI recordings are **not** submitted mid-frame on Vulkan (`SceneInteropBackend.requiresMidFrameSubmit()`): the blur passes read the previous frame's sources, and an extra submission per frame tears VulkanMod's buffer swap.
- Vulkan interop goes through ModernUI's `VulkanModIntegration` and arc3d `VulkanImage.wrap`. It requires a direct `VkGpuDevice` (NeoForge validation wrappers are not supported), falls back to RGBA8 targets, and `VkInteropBackend` is loaded reflectively because VulkanMod is absent on other clients.
- VulkanMod is compile-only (`modCompileOnly files(vulkanModJar())`, jar in `libs/`); dev runs load the mod from `fabric/run/client/mods`. Its GLSL parser rejects `//` comments inside UBO blocks.
- VulkanMod renders with a GL-style negative-height viewport (`Renderer.setViewport`), so every full-screen pass would mirror its sampled image; pass pipelines therefore use `core/fullscreen_flip` (and the warp mesh flips its sampled row through `MHWarp.y`) whenever `SceneGpuContext.passOutputMirrored()` is true, keeping every chain orientation-neutral like OpenGL. `scene_capture.fsh` flips only when `MainColorSource.topDown()` is false (bottom-up main target), so the capture stays a plain copy on VulkanMod.
- arc3d's Vulkan caps never implement `Caps.getImageDescForSampledCopy`, which makes `Surface.makeImageSnapshot()` return null; `VulkanSampledCopyMixin` patches the base `Caps` (so `GLCaps`' own override still wins). Keep the lyric edge fade's "skip when no snapshot" fallback: its `DST_OUT` bands cover the whole viewport and would erase the background under the lyric column.

## Frameworks & Dependencies

- **ModernUI** (icyllis.modernui) — GUI framework, NOT vanilla MC widgets. Resolved from Gradle caches / Loom remap cache (may have flat JARs in `libs/` in some branches). See `ModernUI Library JARs` below for exact sources-JAR paths.
- **Lombok** heavily used — annotation processing is already configured.
- **JLayer** (MP3) + **JFLAC** (FLAC) — audio decoders, shaded into output jars.
- **Mojang mappings** — `loom.officialMojangMappings()`.

## ModernUI Library JARs

`read_file` (IDE MCP, shared by the AI Assistant / Air plugins) can read files inside these sources JARs with `--file_path "<jar path>!/<entry>"`. `search_symbol` / `search_text` do NOT index external libraries — never use them to locate classes inside these JARs; use the paths below directly.

ModernUI itself is compiled from the Lumen fork jar in `{projectRoot}\libs\` (no sources jar); the table below lists the official 3.13.0 artifacts, still useful as an API reference for classes the fork keeps unchanged.

### Path stability rules

- Hash directories in `Gradle home\caches\modules-2\files-2.1` are content SHA-1s: stable as long as the dependency version is unchanged; they change only when a version is upgraded.
- `-c1c451a1` / `-b5e3e3a6` remap hashes under the project `.gradle\loom-cache` are computed by Loom from MC version + mappings + dependency versions — they can change after a re-build or upgrade. If a path below fails, re-locate with the command at the bottom of this section.
- Path variables below: `{projectRoot}` = this repository root (where this AGENTS.md lives); `{gradleHome}` = `H:\Dev\.gradle` on this machine (default `~/.gradle` elsewhere). Substitute them before calling `read_file` — it needs an absolute path.

ModernUI is `compileOnly` on this branch, so both modules use the plain jars under `{gradleHome}\caches` (the runtime Lumen fork jar is not resolvable from here).

1.21.9/10 example (compiled against the 1.21.8 artifacts)

| Library | Sources JAR path template (use with `read_file --file_path "…!/path/to/Class.java"`) |
|---|---|
| modernui-core 3.13.0 (plain) | `{gradleHome}\caches\modules-2\files-2.1\dev.icyllis\modernui-core\3.13.0\bced1daf870a3ede277593ea26a72e70e571c052\modernui-core-3.13.0-sources.jar` |
| ModernUI-Fabric 1.21.8-3.13.0.3 (plain, compileOnly) | `{gradleHome}\caches\modules-2\files-2.1\icyllis.modernui\ModernUI-Fabric\1.21.8-3.13.0.3\a231f87090bd6d9723cabce0db99c61d466d2b2f\ModernUI-Fabric-1.21.8-3.13.0.3-sources.jar` |
| ModernUI-NeoForge 1.21.8-3.13.0.3 (plain, compileOnly) | `{gradleHome}\caches\modules-2\files-2.1\icyllis.modernui\ModernUI-NeoForge\1.21.8-3.13.0.3\7a6e28682e8e33552076d37e3177ed513b22d309\ModernUI-NeoForge-1.21.8-3.13.0.3-sources.jar` |
| ModernUI-Markflow 3.13.0 (plain) | `{gradleHome}\caches\modules-2\files-2.1\icyllis.modernui\ModernUI-Markflow\3.13.0\acadc19e2ddad7647d1defd74e129d1fa68c85ce\ModernUI-Markflow-3.13.0-sources.jar` |
| arc3d-* 2026.2.0 (compiler/core/engine/granite/opengl/sketch/vulkan) | Pattern: `{gradleHome}\caches\modules-2\files-2.1\dev.icyllis\arc3d-<artifact>\2026.2.0\<hash>\arc3d-<artifact>-2026.2.0-sources.jar` — locate `<hash>` with `Get-ChildItem -Recurse "{gradleHome}\caches\modules-2\files-2.1\dev.icyllis" -Filter "arc3d-*-sources.jar"` |

Fallback if a path is missing (e.g. after re-build, upgrade, or in another working tree): locate the JAR first, then pass the full path to `read_file`. Do NOT extract JARs with PowerShell — `read_file` reads JAR entries directly.

```powershell
Get-ChildItem -Recurse "{gradleHome}\caches","{projectRoot}\.gradle\loom-cache" -Filter "*<library>*sources*"
```

## External Dependencies

- **NCM API Enhanced** — external process (`api`/`api.exe`) managed by `ApiServerManager`. Lifecycle: auto-start on launch, manages process I/O via virtual threads. Deploy to `{corepath}/music-hud/`.

## Testing

- JUnit Jupiter 6.0.0.
- Single test class: `common/src/test/java/indi/etern/musichud/MainTest.java`.
- Tests call live NCM API (network-dependent, may fail offline).
- **Must enable manually** before running tests.
- Debug switch `-Dmusic_hud.debug.forceModernUiMissing=true` forces the client into the ModernUI-missing fallback (warning screen, HUD unregistered, ModernUI mixins skipped) without uninstalling MODERNUI.

## Misc

- Languages: `en_us`, `zh_cn`, `zh_hk`, `zh_tw`.
- Custom GL shaders (`.vsh`/`.fsh`) for album cover, progress bar, fluid background.
- `gradle.bac/` is a backup of a previous Gradle wrapper — not the active one (`gradle/` is live).
