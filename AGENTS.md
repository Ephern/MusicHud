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

- **Tests are DISABLED by default** (`enabled = false` in `common/build.gradle:42`). To run: edit the file and change `enabled` to `true` — there is no Gradle property to override this at runtime.
- **No CI, no linter, no formatter, no typechecker** configured. Do not look for or run these.
- **Paper module is separate** — uses `paperweight.userdev` directly, NOT Architectury Loom.
- **Build scripts are Groovy DSL** (`.gradle`), not Kotlin (`.gradle.kts`).

## Architecture

```
common/          — all shared code (network, UI, logic, data models, mixins)
fabric/          — Fabric loader adapter layer (shadow-jars common)
neoforge/        — NeoForge adapter layer (shadow-jars common)
paper/           — Paper/Bukkit plugin (shadows common independently)
```

Platform modules depend on `common`. Each produces a standalone fat jar via Shadow.

The `architectury_enabled_platforms` property in `gradle.properties` only lists `fabric,neoforge` — Paper is explicitly excluded from the Loom plugin in `build.gradle:17` (`configure(subprojects.findAll { it.name != 'paper' })`).

## Key Patterns

- **Service Locator** (not DI): `Environment.Platform.load()` uses `Class.forName()` + reflection to load platform-specific implementations. Interfaces: `ServerConfig`, `ClientConfig`, `IClientEventService`, `IServerEventService`, `INetworkRegister`, `IKeyRegistryService`.
- **Auto-Registration**: `RegistrationManager` loads classes by string array, instantiates `Register` implementors. `@RegisterMark` annotation marks registered classes. Called via `performCommonAutoRegistration()` / `performSideAutoRegistration()`.
- **Custom Network Protocol**: 30+ `CustomPacketPayload` classes in `network/payloads/`. Request/response cycle (`requestResponseCycle/`) + push messages (`pushMessages/`). Register via `INetworkRegister`.
- **Virtual Threads**: `MusicHud.EXECUTOR` = `Executors.newVirtualThreadPerTaskExecutor()`. Used for audio decoding, API server management, network I/O.
- **Mixin**: Only 2 client-side mixins in `common/` — `SoundEngineMixin` (silence vanilla music) and `MixinGuiRendererHud` (inject HUD render). Config: `music_hud.mixins.json`.
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
