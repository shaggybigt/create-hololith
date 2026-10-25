# Validation — 0.4.0-dev.1

Tested on 2026-10-24 (America/Chicago; report timestamps are UTC) using Minecraft
1.21.1, NeoForge 21.1.259, Create 6.0.10 and Java 21.0.12.1+1. Graphical runs used
Windows 11, an RTX 4090 and a 1600×900 window. The full pack includes Aeronautics
1.3.2 and Sable 2.0.6; the minimal profiles contain Create, Hololith and bundled
dependencies, plus the separate disposable validation harness.

## Recorded results

| Check | Result | Evidence |
|---|---|---|
| Fresh checkout, documented build with automatic dependencies | Build and probe compilation succeeded; 32 tests passed | [Build record](test-results/0.4.0-dev.1/fresh-checkout.json), [unit tests](test-results/0.4.0-dev.1/unit-tests.json) |
| Full-pack graphical survey/ship run | 102 checks passed | [Fresh world](test-results/0.4.0-dev.1/full-pack-fresh.json) |
| Full-pack process restart and final QoL checks | 45 checks passed | [Reload](test-results/0.4.0-dev.1/full-pack-resume.json) |
| Create-only graphical survey run | 78 checks passed | [Fresh world](test-results/0.4.0-dev.1/minimal-fresh.json) |
| Create-only process restart and final QoL checks | 47 checks passed | [Reload](test-results/0.4.0-dev.1/minimal-resume.json) |
| Real Create bearing and cart assembly/interactions | 59 checks passed | [Contraptions](test-results/0.4.0-dev.1/contraptions-fresh.json) |
| Captured contraptions reopened in a new JVM | 13 checks passed | [Contraption reload](test-results/0.4.0-dev.1/contraptions-resume.json) |
| Player JAR on minimal and full-pack dedicated servers | 15 checks passed in each profile | [Minimal server](test-results/0.4.0-dev.1/dedicated-minimal.json), [full server](test-results/0.4.0-dev.1/dedicated.json) |

The fresh-world runs cover complete surveying and movement flows. The subsequent
reload runs include the corrected caption assertions and final instrument-panel
rendering. Reports list every assertion; counts are not counts of unique tests
across profiles. The earlier [0.3.0 validation](validation.md) remains preserved.

## What the new checks establish

- The actual brightness slider previews and saves a bounded client preference.
  It reuses the existing terrain GPU mesh and leaves shared survey settings alone.
- All five instrument/card items have their intended Ponder associations. Three
  native Ponder scenes build a real terrain GPU mesh from their own tutorial
  structure. Tests inspect displayed captions, including the last caption, and
  verify marker recoloring, unlinking and timeline replay.
- Screenshots were visually inspected for caption placement, visible markers and
  sharp panel labels/status. The menu background blur is applied before labels.
- Unit tests cover complete/partial/offline/unavailable survey descriptions and
  recent-scan boundaries. A restored or long-idle snapshot does not by itself
  justify a live status. This presentation check does not add scans or packets.
- Existing game checks exercise real client item-use packets, changes to terrain,
  cache persistence, concealed markers, transformed ship/contraption positions,
  and retained state through assembly, disassembly and process restart.
- Dedicated servers load the actual player JAR, finish stationary surveys and
  tick/save/reload deserialized captured Create actors without client classes.
  Both server records include the tested JAR's SHA-256.

## Build and repository checks

The source was cloned into a separate checkout and built with
`gradlew.bat --no-daemon build probeJar`, without `runtimeRoot` or `createJar`
overrides. Public pinned dependencies were used, with the local Gradle dependency
cache reused. The resulting compiled classes match the tested runtime build;
the build record identifies text-only line-ending differences between archives.

The official Create checksum and distribution contents check passed. The player
JAR excludes the probe and dependency classes. The MIT license is unchanged from
the existing repository. Tracked files were checked for local machine paths,
credentials, worlds and generated artifacts; recordings, launch logs, encoders,
runtime JARs and temporary worlds remain excluded.

GitHub Actions passed [Build and test](https://github.com/shaggybigt/create-hololith/actions/runs/40161192964)
for commit `0dc2b270bfc9a863efda8f7a0aad49f09c50b135` on Ubuntu with Java 21,
compiling the addon/probe and passing all 32 automated tests. The downloaded test
reports have no failures, errors or skipped cases; [hosted build evidence](test-results/0.4.0-dev.1/hosted-ci.json)
records the commit, checks and player JAR checksum. The workflow uses pinned
actions, read-only repository permissions and no release step. Graphical and
dedicated-server validation above was performed locally. Main and the existing
0.3.0 release remain unchanged.

## Demonstration and limits

The [showcase guide](showcase.md) describes the actual gameplay capture, reusable
world, editing commands and manual recording plan. The local finished video is
74 seconds at 1600×900/30 fps, silent, with captions and fades. Its refreshed capture
uses the final readable instrument panel; [capture/edit evidence](test-results/0.4.0-dev.1/showcase.json)
records frame counts and checksums.

Graphical tests use an offline development identity and integrated server. They
do not establish authenticated remote multiplayer, many simultaneous viewers,
low-end hardware performance or compatibility with every shader pack. Dedicated
tests establish server startup/actor lifecycle, not remote-client acceptance.

Sable translation/yaw/disassembly and Create bearing/cart motion are exercised.
The demonstration shows a real assembled cart with test-driven movement, not
piloted Aeronautics flight. Trains, gantries, arbitrary controller extensions,
natural chunk-unload travel and player glue assembly need separate acceptance.
These limitations also apply to the previous compatibility claims; this update
does not add lift detection, clearance analysis or safety warnings.
