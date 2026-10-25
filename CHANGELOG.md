# Changelog

## 0.4.0-dev.2 — movement testing preview

- Resolve linked markers from the exact tracked Create contraption or Sable ship
  render pose each frame, with identity/link validation and authoritative fallback
  when the source is unavailable. Pins leaving known terrain are hidden.
- Check sensor positions and survey progress every five ticks. A completed scan
  hands off to the newest region in the same poll, preserving bounded scan work
  and the last useful projection during motion.
- Refresh marker membership after adopting a new survey, preventing pins from
  disappearing when the survey region changes rapidly. Keep the previous terrain
  mesh visible until its replacement is ready, mapping pins onto the displayed
  terrain's own origin and surface heights.
- Add public-schematic provenance and a disposable powered-flight acceptance
  harness for Patrik's airship v10, with baseline and instrumented trials.
- Keep dependencies and saved terrain formats unchanged. Clients and servers
  must both use this preview because marker packets now include source identity.

## 0.4.0-dev.1 — development preview

- Added three original Create Ponder tutorials covering tables, survey cards,
  sensors, remote beacons and colored reference markers.
- Added immediate, localized link/unlink feedback with a quiet chime and a small
  visual cue, plus contextual item and recorded-card tooltips.
- Added an in-game hologram brightness slider (40–180%) with immediate preview
  and a saved per-client setting. Changing brightness reuses the terrain mesh.
- Clarified live, cached, incomplete and unavailable survey feedback; restored
  terrain is only described as live after a fresh scan has been checked.
- Moved complete buildable source into the repository, pinned automatic Create
  dependency retrieval, organized documentation and screenshots, and added a
  Java 21 compile/test workflow. The MIT license and old releases are preserved.
- Added a reproducible creative showcase world and actual gameplay capture/edit
  tools. The local development showcase is 74 seconds long; no release or
  community upload is part of this preview.
- Kept existing dependency versions, terrain formats and scanning budgets.

## 0.3.0

- Added live surveying, transformed sensors and colored reference points on
  ordinary Create contraptions, with entity-tracking synchronization.
- Added card linking, dye colors, wrench controls and the instrument panel while
  assembled. All nine table tiles attach as one table during Create assembly.
- Preserve instrument identities, settings and cached terrain through captured
  entity persistence and upright disassembly.
- Added an isolated Create contraption game fixture and packet contract tests.
- Requires the same Minecraft, NeoForge and Create versions as 0.2.1. Clients
  and servers must both update because the network protocol now includes mounted
  instrument messages. Aeronautics/Sable remains optional.

## 0.2.1

- Replaced the earlier GPU-buffer helper with an independent implementation
  using Minecraft's existing vertex-buffer API. The terrain meshes, textures,
  lighting, marker rendering, scanning and persistence formats are unchanged.
- Added defensive cleanup for failed mesh creation and off-thread disposal.
- Added a clean Create-only graphical test profile and a dedicated-server
  profile to verify that the rest of the development modpack is optional.
- Added an external-baseline framebuffer comparison and GPU timing probe.
- Prepared standalone installation instructions, source, screenshots and
  validation reports for sharing. See [validation](docs/validation.md).

## 0.2.0 — earlier local build

- Added colored reference blocks linked to tables or survey sensors, including
  transformed marker positions aboard Sable ships.
- Added subtle terrain-edge lighting and configurable hologram intensity.

## 0.1.0 — initial local build

- Added the surveying table, 3D surface projection, survey cards, sensors,
  beacons, coordinate targeting, bounded caching and optional Sable integration.
