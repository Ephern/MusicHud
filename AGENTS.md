# Music HUD - Agent Guide

Multi-loader Minecraft mod (Fabric + NeoForge + Paper) for 1.21.8 (Java 21). A GUI-based full-server song request system powered by Netease Cloud Music API.

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

- **Tests are DISABLED by default** (`enabled = false` in `common/build.gradle:46`). To run: edit the file and change `enabled` to `true` — there is no Gradle property to override this at runtime.
- **No CI, no linter, no formatter, no typechecker** configured. Do not look for or run these.
- **Paper module is separate** — uses `paperweight.userdev` directly, NOT Architectury Loom. Paper is NOT in `settings.gradle` on this branch.
- **Build scripts are Groovy DSL** (`.gradle`), not Kotlin (`.gradle.kts`).
- **`core` module is a plain `java-library` (NO Loom)** — excluded by `build.gradle:16`: `configure(subprojects.findAll { it.name != 'core' })`.
  - Platform modules (`fabric`/`neoforge`) add `core` via a dedicated `coreLib` configuration that extends `compileClasspath` + `runtimeClasspath` but **NOT** `developmentFabric`/`developmentNeoForge` — this prevents Architectury Transformer from applying unnecessary transforms (`GenerateFakeFabricMod`, `RemapInjectables`).
- **`-Dfabric.dli.config` must be overridden in Fabric `loom.runs`** — Architectury Loom 1.17.487 generates DLI config at `.gradle/loom-cache/projects/<subproject>/launch.cfg` but the injector property points to `<subproject>/.gradle/loom-cache/launch.cfg`. Without the override, `dev-launch-injector` enters pass-through mode → mods and Minecraft assets won't load.
- **Mixin count: 6** — `SoundEngineMixin`, `GuiRendererHudMixin`, `ScreenMixin`, `SpanSetMixin`, `ReactiveMusicCompatMixin` + `MusicHudMixinPlugin` (plugin class).

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
- **Mixin**: Config `music_hud.mixins.json`. 6 classes: `SoundEngineMixin`, `GuiRendererHudMixin`, `ScreenMixin`, `SpanSetMixin`, `ReactiveMusicCompatMixin` + `MusicHudMixinPlugin`.
- **Config**: Forge Config API Port (Fabric, shared with NeoForge) / native NeoForge `ModConfig` / Paper `config.yml`.

## Frameworks & Dependencies

- **ModernUI** (icyllis.modernui) — GUI framework, NOT vanilla MC widgets. Resolved from Gradle caches / Loom remap cache (may have flat JARs in `libs/` in some branches). See `ModernUI Library JARs` below for exact sources-JAR paths.
- **Lombok** heavily used — annotation processing is already configured.
- **JLayer** (MP3) + **JFLAC** (FLAC) — audio decoders, shaded into output jars.
- **Mojang mappings** — `loom.officialMojangMappings()`.

## ModernUI Library JARs

`idea_execute_tool read_file` can read files inside these sources JARs with `--file_path "<jar path>!/<entry>"`. `search_symbol` / `search_text` / `skill_search` do NOT index external libraries — never use them to locate classes inside these JARs; use the paths below directly.

### Path stability rules

- Hash directories in `Gradle home\caches\modules-2\files-2.1` are content SHA-1s: stable as long as the dependency version is unchanged; they change only when a version is upgraded.
- `-c1c451a1` / `-b5e3e3a6` remap hashes under the project `.gradle\loom-cache` are computed by Loom from MC version + mappings + dependency versions — they can change after a re-build or upgrade. If a path below fails, re-locate with the command at the bottom of this section.
- Path variables below: `{projectRoot}` = this repository root (where this AGENTS.md lives); `{gradleHome}` = `H:\Dev\.gradle` on this machine (default `~/.gradle` elsewhere). Substitute them before calling `read_file` — it needs an absolute path.

Fabric module uses the Loom-remapped jars (`-c1c451a1` / `-b5e3e3a6` suffixes) under the project `.gradle\loom-cache`; NeoForge module uses the plain jars under `{gradleHome}\caches`.

| Library | Sources JAR path template (use with `read_file --file_path "…!/path/to/Class.java"`) |
|---|---|
| modernui-core 3.13.0 (remapped, fabric) | `{projectRoot}\.gradle\loom-cache\remapped_mods\remapped\dev\icyllis\modernui-core-b5e3e3a6\3.13.0\modernui-core-b5e3e3a6-3.13.0-sources.jar` |
| modernui-core 3.13.0 (plain) | `{gradleHome}\caches\modules-2\files-2.1\dev.icyllis\modernui-core\3.13.0\bced1daf870a3ede277593ea26a72e70e571c052\modernui-core-3.13.0-sources.jar` |
| ModernUI-Fabric 1.21.8-3.13.0.3 (remapped, used by fabric) | `{projectRoot}\.gradle\loom-cache\remapped_mods\remapped\icyllis\modernui\ModernUI-Fabric-c1c451a1\1.21.8-3.13.0.3\ModernUI-Fabric-c1c451a1-1.21.8-3.13.0.3-sources.jar` |
| ModernUI-NeoForge 1.21.8-3.13.0.3 (used by neoforge) | `{gradleHome}\caches\modules-2\files-2.1\icyllis.modernui\ModernUI-NeoForge\1.21.8-3.13.0.3\7a6e28682e8e33552076d37e3177ed513b22d309\ModernUI-NeoForge-1.21.8-3.13.0.3-sources.jar` |
| ModernUI-Markflow 3.13.0 (remapped) | `{projectRoot}\.gradle\loom-cache\remapped_mods\remapped\icyllis\modernui\ModernUI-Markflow-b5e3e3a6\3.13.0\ModernUI-Markflow-b5e3e3a6-3.13.0-sources.jar` |
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

## Misc

- Languages: `en_us`, `zh_cn`, `zh_hk`, `zh_tw`.
- Custom GL shaders (`.vsh`/`.fsh`) for album cover, progress bar, fluid background.
- `gradle.bac/` is a backup of a previous Gradle wrapper — not the active one (`gradle/` is live).
