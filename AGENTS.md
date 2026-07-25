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

- **ModernUI** (icyllis.modernui) — GUI framework, NOT vanilla MC widgets. Flat JARs in `libs/`.
- **Lombok** heavily used — annotation processing is already configured.
- **JLayer** (MP3) + **JFLAC** (FLAC) — audio decoders, shaded into output jars.
- **Mojang mappings** — `loom.officialMojangMappings()`.

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
