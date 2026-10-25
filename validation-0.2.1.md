# Validation — 0.2.1

## 0.2.1 renderer equivalence

The replacement buffer helper was implemented independently from Minecraft's
API sources. The old 0.2.0 JAR was loaded only by a separate validation class
loader; it is not included in the 0.2.1 release or source archive.

The final 0.2.1 renderer and the old renderer drew the same live 32×32 and
64×64 snapshots into a 512×512 offscreen framebuffer. Four rotations and
intensities 0.4, 1.0 and 1.8 produced **24 pixel-identical pairs**, with zero
differing pixels and visible nonempty terrain in every pair. Buffer counts,
storage layouts and index counts matched. GPU handles, sizes and contents
remained unchanged across repeated draws. Source inspection confirms that the
new draw method performs no terrain uploads or explicit per-frame allocation;
the probe does not trace every GL call.

Measured on an RTX 4090 with driver 617.42, using 21 alternating paired samples
of eight warmed draws per sample:

| Survey | Old GPU median | 0.2.1 GPU median | Old CPU median | 0.2.1 CPU median |
|---|---|---|---|---|
| 32×32 | 25.600 µs | 25.472 µs | 37.575 µs | 35.988 µs |
| 64×64 | 49.152 µs | 49.024 µs | 37.975 µs | 37.275 µs |

These observations show no material rendering-cost regression in this setup.
They are not a general FPS guarantee or a claim of a meaningful speedup. CPU
timing includes reflection and render-state setup. Raw comparison summaries are
in `docs/test-results/0.2.1/render-comparison-32.json` and `render-comparison-64.json`.

The release-entry comparison found changes only to the GPU helper, its
`SurveyMesh` caller, version metadata and obsolete helper notices. All other
runtime classes and assets are byte-for-byte identical to 0.2.0. See
`docs/test-results/0.2.1/binary-comparison.json`. Gameplay, network payload and saved
survey formats are unchanged.

## 0.2.1 gameplay and compatibility validation

Validated on 2026-10-24 against the installed pack: Minecraft 1.21.1,
NeoForge 21.1.259, Create 6.0.10, Aeronautics 1.3.2, Sable 2.0.6 and
Sable Companion 1.6.0, using Java 21.0.12 on Windows 11.

## Results

| Layer | Result | Evidence |
|---|---|---|
| Java/resources/release build | Successful | `scripts/Build.ps1`; release JAR and sources JAR |
| NeoForge JUnit boot | 14 tests passed, 0 failed/skipped | `docs/test-results/0.2.1/unit-tests.json` |
| Graphical full-pack client + integrated server | 84 checks passed, including renderer comparisons | `docs/test-results/0.2.1/hololith-fresh.json` |
| Full-pack saved world reopened in a fresh JVM | 9 checks passed | `docs/test-results/0.2.1/hololith-resume.json` |
| Clean Create-only client + integrated server | 48 checks passed | `docs/test-results/0.2.1/minimal-fresh.json` |
| Clean saved world reopened in a fresh JVM | 11 checks passed | `docs/test-results/0.2.1/minimal-resume.json` |
| Actual release JAR on full-pack dedicated server | 8 checks passed | `docs/test-results/0.2.1/dedicated.json` |
| Actual release JAR on Create-only dedicated server | 8 checks passed | `docs/test-results/0.2.1/dedicated-minimal.json` |
| Visual inspection | Terrain, markers, dark interior, brightness and transformed ship screenshots | `images/` |
| Historical 0.1.0 world opened with 0.2.0 | 7 checks passed in the earlier run; not repeated for 0.2.1 | `docs/test-results/0.2.1/historical-0.1-to-0.2.json` |

Both dedicated-server reports record the SHA-256 of the actual release JAR:
`32b404f26cfd0d55652777c8912b0c91b603b9dd9ccbdce680704ac1b953f155`.
The distributed source archive contains curated JSON evidence; raw local logs
and private development-world paths are excluded.

The clean profile creates a new vanilla flat world with fresh configuration.
Its only external mod JAR is official Create 6.0.10; Hololith and the separate
test harness are supplied by the development build. The loaded mod inventory
contains Minecraft, NeoForge, Create, Flywheel, Ponder, Hololith and the harness.
There is no Sable, Aeronautics, Forgotten Knowledge, shader mod or reference map
mod. It exercises the regular controls, all survey sizes, marker linking and
recoloring, hidden references, caches and rendering, skipping only Sable-specific
assembly/transform phases. The clean dedicated profile loads exactly the
official Create JAR and the release Hololith JAR.

The full-pack game tests used an offline development identity and a copy of the
existing disposable validation world. They exercised normal client item-use packets for
placing the nine-tile table and recording/applying sensor and beacon cards.
Reference interactions also used normal client item-use packets for sensor
linking, dye recoloring, recording a table card, direct table linking and
sneak-wrench unlinking.
The instrument panel opened through the server and its buttons changed the
authoritative survey size and rotation. These are actual game runs, beyond a
successful compilation.

The terrain fixture contains hills, a water channel, leaf canopies and a roof.
Assertions checked 1,024 known columns at 32×32, full 64×64 sampling, the capped
64×64 sampling grid at 128×128, palette/NBT round trips, client synchronization,
and a terrain edit changing height, material and revision. An unloaded target
20 million blocks away stayed unloaded and produced no invented terrain.
Coordinate mode and beacon locations acquired the expected regions.

A moving source changed position every second for eight seconds; completed
surveys advanced and the previous projection remained available. Then the
installed Sable engine assembled all nine table tiles, an underside sensor and
four completely enclosed references into a real sublevel, joined by a connected
frame. Checks covered retained blocks and sensor/table identities,
server ticking, client synchronization, world coordinate conversion, translation
by (8, 0, -4), 90-degree yaw (including an off-axis table tile), and engine
disassembly. Screenshots show the projection on translated and rotated geometry.
All four marker positions matched independently computed transformed world
coordinates within 0.004 blocks after assembly, translation and yaw. Their
height came from surveyed terrain, regardless of the references' altitude.
Physics was paused for this controlled fixture and its previous state restored.
This validates the engine bridge; it does not establish powered-airship flight
or player-operated glue/assembler behavior.

The marker fixture also uses four buried references surrounded on all six faces
by solid blocks, with two direct table links and two sensor links. It checks
distinct colors, no terrain revision caused by marker registration, the initial
chunk update tag, removal, saved-NBT restoration and the block-entity removal /
reload lifecycle. An overhead reference does not lift its own terrain sample.
Dark-room and daylight captures use the default intensity; a separate capture
uses 1.6. Changing intensity retains the same cached GPU terrain mesh.

The separate JVM restart checks the table, sensor UUID, table-local snapshot,
shared region cache, all four reference links/colors and reconstructed runtime
marker registry. The 0.1.0 migration run preserved the old table/survey and gave
it a new persistent table identity in the earlier 0.2.0 run. The full-pack dedicated
smoke uses the actual release JAR with copied server mods/config and a new flat
world; the clean server uses fresh config and only Create plus Hololith. Each checks
release loading, a complete 32×32 survey, sensor NBT, a colored linked reference,
table UUID, saved shared data, graceful shutdown and exit code zero. Its console
places the center block directly; full multiblock placement was covered by the
graphical test. Four chunks were
explicitly loaded by the test fixture, not by Hololith.

JUnit cases cover negative-coordinate alignment, the large-area sampling bound,
malformed control settings and invalid snapshot schemas/arrays, half-open marker
bounds, unknown terrain, water/canopy heights, the shared vertical-fit formula,
completed-survey centers and marker payload round trips. A 64-marker packet is
under 4 KiB; decoding rejects excess counts before allocation and nonfinite
coordinates. Empty updates and bounded initial chunk data are covered. The final
runtime JAR was checked for mod metadata, the independent helper and its MIT license;
the test-only probe is a separate JAR and is excluded from the release.

## Practical limits and remaining acceptance work

- No authenticated remote client or multi-player load test was performed.
  Dedicated-server execution and integrated client/server networking passed.
- No powered flight, passenger interactions, docking, arbitrary pitch/roll or
  ship save/reload test was performed. Controlled translation and yaw passed.
- No third-party shader-pack or lower-end GPU performance benchmark was run.
  Visual checks used an RTX 4090 at 1600×900. Reported FPS is an observation,
  not a minimum hardware or performance guarantee.
- Marker sneak-wrench unlinking passed through the actual client. Survival dye
  consumption, mining/drop color retention, cross-dimension links and natural
  chunk-unload/cache-eviction scenarios have code/schema review coverage but not
  dedicated end-to-end acceptance cases in this run. The explicit block-entity
  removal/reload test is not a natural chunk-unload test.
- Four distinct colors were exercised in game. All 16 model variants and dye
  mappings were checked in resources/code. No multi-table stress test was run.
- This is a surface-height reconstruction: no cave interiors, undercuts,
  individual branches or full voxel buildings. 128×128 surveys omit alternate
  columns. Tall terrain is fitted vertically; narrow details can disappear.
- Sensors/beacons do not load chunks or scan independently. An active table
  must capture loaded terrain before it can be viewed from cache elsewhere.
- Ordinary Create contraptions are not supported. Hololith-specific Ponder
  scenes and CC:Tweaked integration are not included in 0.2.1.

The pack emits existing optional-mod mixin warnings and Gadgets & Gizmos
virtual-model warnings. Sable also logged an early movement packet before the
client sublevel existed during assembly; the subsequent sublevel, terrain and
marker synchronization checks passed. No Hololith missing-model errors appeared in the
successful graphical runs. Java compilation reports legacy API deprecation
warnings but no errors.

## Repeat locally

Close project Minecraft/server JVMs before running these tests. They have fixed
guards for the established local runtime paths and must not be aimed at a live
world.

```powershell
.\scripts\Build.ps1
.\scripts\Start-Probe.ps1 -Fresh -Compare
.\scripts\Start-Probe.ps1 -Resume
.\scripts\Start-Minimal.ps1
.\scripts\Start-Minimal.ps1 -Resume
.\scripts\Test-Dedicated.ps1
.\scripts\Test-Dedicated.ps1 -Minimal
.\scripts\Stage-Public.ps1
.\scripts\Package.ps1
```

`-Compare` needs the private earlier 0.2.0 baseline JAR in `dist/0.2.0`; that
baseline is deliberately excluded from this release. Omit the flag to run
the gameplay checks without the old-versus-new framebuffer comparison.

Fresh graphical tests archive previous Hololith trial directories. Dedicated
tests create a new timestamped directory and listen only on localhost:25576.
Both stop their Minecraft process at completion. Neither test replaces the
playable pack's worlds, mods, settings or Forgotten Knowledge source.
