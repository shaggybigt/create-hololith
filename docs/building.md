# Building and developing Hololith

## Fresh checkout

Install a 64-bit JDK 21 and Git, then clone `main` and build:

```sh
git clone --branch main https://github.com/shaggybigt/create-hololith.git
cd create-hololith
./gradlew --no-daemon build probeJar
```

The `main` branch contains the complete buildable source for the development
preview. Existing versioned release downloads remain available separately.

Use `gradlew.bat` on Windows or `bash ./gradlew` if executable permissions were not
preserved. `build` compiles the player mod, runs JUnit tests, checks the dependency
checksum and inspects the resulting JAR for accidentally bundled dependencies or
probe classes. `probeJar` also compiles the isolated gameplay harness; it does not
launch Minecraft or run graphical tests.

The default output is `build/libs/create-hololith-<version>.jar`, plus a source JAR.
The probe is a separate `hololith-probe-<version>.jar`. Never install the probe in a
normal world: it constructs and alters disposable validation scenes automatically.
Unit reports are in `build/reports/tests/test/` and `build/test-results/test/`.

The first run downloads Gradle, Minecraft/NeoForge tooling, Create and test
libraries. Later builds reuse Gradle's cache. Nothing needs to be copied from an
existing Minecraft installation for compilation and unit tests. The Gradle wrapper
pins its distribution checksum; use the committed wrapper rather than a global
Gradle installation.

## Dependencies

| Component | Pinned version / identity | Use |
|---|---|---|
| Java | 21 | Compiler and Minecraft runtime |
| Minecraft | 1.21.1 | Supplied through NeoForge development tooling |
| NeoForge | 21.1.259 | Loader and API |
| ModDevGradle | 2.0.148 | Existing development plugin |
| Gradle | 9.2.1 | Existing checked-in wrapper |
| Create | 6.0.10, official release ID `UjX6dr61` | Compile and development/test runtime |
| Registrate | MC1.21-1.3.0+67 | Extracted from the official Create JAR for compilation |
| Flywheel | 1.0.6 | Extracted from the official Create JAR for compilation |
| Ponder | 1.0.82+mc1.21.1 | Extracted from the official Create JAR for compilation |
| JUnit Jupiter | 5.11.4 | Tests only |

Create is resolved through its official
[Modrinth release](https://modrinth.com/mod/create/version/UjX6dr61), using the Maven
coordinate `maven.modrinth:create:UjX6dr61`. That repository is restricted to this
single module; no version ranges or snapshot builds select a different Create
release. This is dependency retrieval, not a Hololith publishing integration.
The distribution is the same file used by the existing modpack, verified against:

```text
create-1.21.1-6.0.10.jar
SHA-256 ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a
```

The three nested libraries are taken from that verified JAR. They are not copied
into Hololith's player or source JAR. Minecraft, NeoForge, Create, Ponder, Flywheel
and Registrate retain their respective licenses; Hololith's MIT license applies
to Hololith's own source and assets. See [provenance](references.md).

The standard Gradle wrapper scripts and wrapper JAR are Gradle project tooling,
distributed under [Gradle's Apache-2.0 license](https://github.com/gradle/gradle/blob/v9.2.1/LICENSE).
Their existing copyright and license notices are preserved. GitHub Actions are
external CI tools referenced by commit; their implementations are not included in
the addon or source tree.

Primary references:
[Create's tagged build](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/build.gradle),
[NeoForge 1.21.1 development setup](https://docs.neoforged.net/docs/1.21.1/gettingstarted/),
[ModDevGradle](https://github.com/neoforged/ModDevGradle).

## Development client and server

`./gradlew runClient` starts a separate client in `runs/client/` with Create and
Hololith. `./gradlew runServer` uses `runs/server/`; follow Minecraft's EULA prompt
before running a server. These directories are ignored by Git. Use disposable
worlds and do not point a development launch at a normal save.

Aeronautics 1.3.2 and Sable 2.0.6 are optional integration targets, not compile-time
requirements. A plain checkout can build and test without them. Optional full-pack
gameplay tests require the corresponding independently installed modpack files.

## Existing modpack validation layout

The optional `runtimeRoot` property preserves the established layout for local
validation. Example using a directory you control:

```powershell
.\gradlew.bat -PruntimeRoot=C:/HololithDev build probeJar
```

This writes build outputs below `C:/HololithDev/development/hololith/build/`, uses
game directories alongside that build directory, and reads the official Create
JAR from `C:/HololithDev/test-client/mods/create-1.21.1-6.0.10.jar`. In this mode the
gameplay launch uses the mods staged by the validation helper instead of adding a
second Create JAR to its runtime classpath. Existing full/minimal/contraption
helpers need their normal prepared disposable worlds and pack files.

The optional `Start-Airship.ps1` helper adds a fresh full-pack world for the
[public schematic acceptance test](public-schematics.md). Its downloaded input
is local-only and is not required for the ordinary build or CI.

The PowerShell gameplay and packaging helpers in `scripts/` target Windows and
the prepared modpack layout, including its Java 21 runtime and dedicated-server
installation. They are optional; the ordinary Gradle build is portable across
Windows, Linux and macOS. The full-pack probe additionally copies an existing
disposable seed world; the minimal and contraption probes construct their own
fixtures. Historical comparison/staging helpers may also require earlier local
validation evidence that is not part of a fresh checkout.

`-PcreateJar=/path/to/create-1.21.1-6.0.10.jar` overrides dependency download with a
local copy, with or without `runtimeRoot`. The same checksum is enforced. Local
paths belong in an untracked invocation or user Gradle properties, never in the
repository. Do not commit modpack JARs, saves, caches, credentials or recordings.

## Continuous integration and evidence

`.github/workflows/build.yml` runs the wrapper on pushes, pull requests and manual
dispatches with Java 21. It runs `build probeJar` and retains test reports and
development JARs for fourteen days. Actions are pinned to commit hashes, repository
permissions are read-only, and checkout credentials are not persisted. There is
no release, package, community posting or deployment step.

Automated compilation and unit tests are distinct from gameplay validation. The
recorded graphical and dedicated-server checks and their remaining limits are in
[development validation](development-validation.md), with earlier records in
[validation.md](validation.md). Updating those records requires fresh evidence;
a green CI run does not imply that the graphical probes or ship tests were run.
