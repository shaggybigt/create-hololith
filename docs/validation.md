# Validation — 0.3.0

This is the preserved release record. See [development validation](development-validation.md)
for the 0.4.0 development branch.

Tested on 2026-10-24 (America/Chicago; report timestamps are UTC) with
Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10 and Java 21.0.12.1+1.
Graphical runs used Windows 11, an RTX 4090, driver 617.42 and 1600×900.
The full pack includes Aeronautics 1.3.2 / Sable 2.0.6; the minimal and
contraption profiles load only Create, Hololith and bundled dependencies,
plus the separate disposable validation harness.

## Results

All final runs passed. Compilation is separate from the actual game checks below.

| Run | Result | Evidence |
|---|---|---|
| NeoForge JUnit boot | 19 tests; no failures or skips | [Unit tests](test-results/unit-tests.json) |
| Full-pack graphical client and integrated server | 72 checks passed | [Fresh run](test-results/hololith-fresh.json) |
| Full-pack saved world reopened in a fresh JVM | 9 checks passed | [Reload](test-results/hololith-resume.json) |
| Create-only graphical client and integrated server | 48 checks passed | [Fresh run](test-results/minimal-fresh.json) |
| Create-only saved world reopened in a fresh JVM | 11 checks passed | [Reload](test-results/minimal-resume.json) |
| Real bearing and minecart contraption fixture | 59 checks passed | [Contraptions](test-results/contraptions-fresh.json) |
| Still-assembled bearing reopened in a fresh JVM | 13 checks passed | [Mounted reload](test-results/contraptions-resume.json) |
| Final JAR on full-pack dedicated server | 15 checks passed | [Dedicated](test-results/dedicated.json) |
| Final JAR on Create-only dedicated server | 15 checks passed | [Minimal dedicated](test-results/dedicated-minimal.json) |

Both dedicated reports record the SHA-256 of the final runtime JAR. Graphical
runs use the same compiled production classes through NeoForge's development
launcher, with an offline development player. They are not authenticated remote
multiplayer acceptance tests.

## New contraption coverage

The fixture creates a real upward-facing mechanical bearing and uses Create's
assembly/disassembly methods. Only the table's center tile is explicitly glued
to its platform; all nine matching tiles are captured by Hololith's attachment
check. Create's normal keep-assembled bearing setting holds the fixture while
the test changes its angle to 90° and 180°.

At each tested pose, the mounted table receives live terrain, the sensor
reports the engine-transformed world position, and four distinct marker UUIDs
match independently queried Create transforms. A second stationary table follows
the moving sensor. The actual client virtual block entity receives the terrain
and markers, and its Hololith renderer builds a GPU mesh. Screenshots were
visually inspected: [bearing](../images/contraption-bearing.png) and
[minecart](../images/contraption-cart.png).

Create interaction packets exercise the mounted panel, its authoritative area
and rotation settings, a dye change, recording the mounted sensor on a card,
and relinking a reference marker. The dye operation also exercises a client
contraption structure rebuild without losing the table's projection.

The cart fixture uses the real cart assembler, minecart and contraption
passenger. The test supplies minecart velocity along a straight rail and verifies
physical translation, transformed sensors/markers, the live client projection,
and disassembly into an intact nine-tile table with its identity and cache.

The fresh run saves the bearing while still assembled. A separate JVM reloads
the world and verifies table/sensor identities, settings, dye color, marker
links, live sensor and marker updates, and client rendering. It then disassembles
the upright table and verifies the restored world block entity continues to
receive terrain and all four markers.

## Existing features and dedicated servers

The full-pack and minimal regressions exercise normal item placement, survey
cards, remote beacons, coordinate targets, 32/64/128 areas, terrain edits,
bounded unloaded-region requests, persistence, marker colors and linking,
reference removal/reload, actual controls and hologram intensity changes.
Changing intensity still reuses the existing GPU mesh.

The full-pack run also repeats Sable engine assembly, controlled translation,
90° yaw and disassembly of the table, underside sensor and four enclosed
reference blocks. Its paused-physics fixture checks transformed reference
coordinates and actual client synchronization. It does not establish powered
flight, docking or arbitrary ship pitch/roll behavior.

Each dedicated run loads the production JAR, exercises stationary surveys,
then deserializes a captured table/sensor/marker on a minecart contraption.
The actor ticks acquire live terrain at the sensor, preserve identities/settings
through entity save/load, and resume scanning after the captured cache is
cleared. Captured local block positions are not installed in world chunks.
These dedicated fixtures are lifecycle checks; physical assembly and rendering
are covered by the separate graphical tests.

The first minimal dedicated attempt exposed an invalid test command:
`unless data block` errors when there is no block entity. The assertion was
corrected to `unless block`; both final dedicated runs passed.

## Scope and remaining acceptance work

- Bearings and minecart contraptions were exercised in game. Pistons, pulleys,
  elevators, gantries and train carriages use the same actor integration but
  have not each received a separate physical fixture. Train portal transitions,
  offline train simulation and third-party contraption replacements need their
  own acceptance checks.
- Park tables upright before disassembly. The placed table is a horizontal 3×3
  multiblock; sideways or upside-down placement is not supported.
- The fixture places glue volumes programmatically and drives motion through
  engine APIs. It does not test a player's complete construction workflow.
  Mounted panel/card/dye packets passed; mounted sneak-wrench interaction and
  survival dye consumption have implementation review rather than separate
  end-to-end coverage. Normal stationary sneak-wrench interaction passed.
- Natural chunk unloading, cache eviction, cross-dimension links, many-player
  load and large train networks were not tested end to end. Explicit reference
  removal/reload passed. No chunks are force-loaded by Hololith; fixture chunk
  loading is test setup.
- No shader-pack or lower-end GPU benchmark was performed. The existing scan
  budgets, mesh cache and marker limits remain. This is not an FPS guarantee.
- Hololith remains a surface survey; it does not reconstruct cave interiors or
  complete voxel volumes. Ponder scenes and CC:Tweaked integration are not
  included.

## Release integrity and history

[The runtime entry comparison](test-results/runtime-diff-0.2.1-to-0.3.0.json)
confirms that the terrain renderer, mesh, GPU buffer and render-type classes are
byte-for-byte unchanged from 0.2.1. The new build adds contraption integration
and modifies its connection points, controls and version metadata. No existing
runtime entries were removed. The release excludes the probe and bundled
Minecraft/Create implementation classes or dependency JARs.

The [0.2.1 validation](validation-0.2.1.md), including its historical framebuffer
and GPU timing comparisons, remains under `test-results/0.2.1/`. Those performance
measurements were not repeated for 0.3.0. The older 0.1-to-0.2 migration evidence
is historical, not a new 0.3.0 migration run.

## Repeat in the established local environment

Use `scripts/Build.ps1`, `Start-Probe.ps1 -Fresh` then `-Resume`,
`Start-Minimal.ps1` then `-Resume`, `Start-Contraption.ps1` then `-Resume`,
and `Test-Dedicated.ps1` with and without `-Minimal`.
Run them sequentially with normal Minecraft/server processes closed.
The scripts guard their disposable runtime directories and preserve prior worlds.
`Stage-Public.ps1` stages reports and screenshots; `Package.ps1` requires the
matching release reports and dedicated JAR hashes before producing artifacts.
For a portable source build, use the Gradle command in the [README](../README.md).

