# Reference investigation

Investigated 2026-10-24. These map mods informed the design; they are not runtime
dependencies, and their source code and artwork are not included in the current addon.

| Project | Findings | Decision |
|---|---|---|
| [Frames Map 3D](https://www.curseforge.com/minecraft/mc-mods/frames-map-3d), [source](https://github.com/codex-skd/frames-map-3d) | Investigated full-volume palettes, bounded capture, revision synchronization, greedy meshes and persistent GPU buffers at commit `30eaf75c6a325f4da5ac243e2366ebc726e91d01`. | Conceptual reference for terrain capture and cached rendering. No project source retained in 0.2.1; see provenance below. |
| [Holographique Map](https://www.curseforge.com/minecraft/mc-mods/holographique-map) | NeoForge 1.21.1; textured voxels, biome colors, water and coordinates. No public source found during this investigation. | Feature and visual reference; no copied code or artwork. Ship behavior was not established. |
| [HB's Satellites](https://www.curseforge.com/minecraft/mc-mods/hbs-satellites), [source](https://github.com/jhw2167/MCM_005_HBs-Satellites/tree/1.21.1) | The investigated 1.21.1 implementation uses Chisels & Bits mini-blocks and Foundation/Balm infrastructure and loads remote chunks. | Conceptual reference for separate surveying sources and displays. No dependency or copied code. |

## Provenance and the 0.2.1 replacement

Earlier internal builds through 0.2.0 included a small GPU-buffer helper
attributed to Frames Map 3D. Its provenance remained ambiguous during preparation
for publication. For 0.2.1, that helper was removed and replaced with the
independently authored `ProjectionBuffer`, implemented from Minecraft/NeoForge
API sources. No source code from the reference map projects is retained
in this release. The project links above remain as conceptual credits; this
records the development history without making a legal judgment about those
projects.

Hololith's terrain sampler, palette persistence, bounded fair queue, surface
mesh, models, controls and sensor system are original addon implementations.
The scanner uses loaded chunks only, a requirement that differs from the remote
chunk-loading behavior found in some references. GPU transforms combine the
Minecraft camera matrix with the block-entity renderer's supplied local pose,
including Sable's ship transform when present.

## Rendering and API choices

The surface representation preserves cliffs, rooflines, treetops and water
without storing all underground voxels. It cannot reconstruct cave interiors,
undersides of overhangs or individual branches. Meshes use at most 64×64 cells
and an eight-entry GPU cache. Mesh rebuilds are limited to one per client tick
and at most twice per second per table. Resource reload/disconnect/expiry dispose
buffers. Cyan tint, emissive texture shading, datum grid and scan light give
the hologram its appearance using Minecraft shaders. Iris shadow passes are
skipped through its optional public API; no shader pack is required.

Create and Minecraft textures are referenced through the existing resource
atlas; no raw Minecraft/Create texture assets are bundled in the addon. The table, sensor, beacon, card and
reference-marker models are original. APIs were checked against Minecraft
1.21.1 and NeoForge 21.1.259. The addon source is available under its own
[MIT license](../LICENSE).

Primary API documentation: [NeoForge networking](https://docs.neoforged.net/docs/1.21.1/networking/),
[Sable Companion](https://github.com/ryanhcode/sable-companion).

## Tutorials and demonstration

The four Ponder timelines, their text and the small survey workshop are original
Hololith content. `scripts/Generate-Ponder.py` creates the workshop NBT from an
explicit block layout; no upstream Ponder scene source or schematic is copied.
The tutorials use the Ponder API bundled with the installed Create distribution.

Kinetic integration uses Create's public kinetic block/entity, network stress,
goggles and renderer APIs, verified against the exact installed 6.0.10 binary.
Optional Jade integration uses its `IWailaPlugin`, block-data and tooltip APIs
from the official 15.10.6 NeoForge distribution. No Create or Jade implementation
source is copied or bundled. References:
[Create kinetic entity](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/kinetics/base/KineticBlockEntity.java),
[Jade source and API](https://github.com/Snownee/Jade/tree/1.21-neoforge).

The showcase landscape, camera path and captions are original. Its video contains
actual Minecraft framebuffer captures from the disposable development world, with
trimming, captions and fades applied afterward. It has no music or generated
gameplay footage. FFmpeg is an external editing tool and is not bundled in the
mod or source repository. See [the showcase guide](showcase.md).

## Public craft used for acceptance work

Patrik's [airship v10](https://createmod.com/schematics/airship-v10), supplied by
the user, and Tommobean's
[Basic Aeronautics Airship](https://createmod.com/schematics/basic-aeronautics-airship)
were inspected as independent test inputs. Their downloads are not bundled or
relicensed under Hololith's MIT license. See [public schematic provenance and
reproduction](public-schematics.md) for hashes, controls, local preparation and
the distinction between inspected candidates and completed flight acceptance.
