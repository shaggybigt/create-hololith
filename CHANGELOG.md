# Changelog

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
