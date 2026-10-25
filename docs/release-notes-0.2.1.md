# Create: Hololith 0.2.1

A mechanical surveying table that projects a textured miniature Minecraft
landscape. Includes 32/64/128-block survey areas, local sensors, remote beacons,
coordinate targeting, saved terrain and individually colored reference markers.
Aeronautics/Sable integration lets sensors and reference points follow a ship.

## Installation

- Minecraft Java **1.21.1**, **Java 21**, **NeoForge 21.1.259**, **Create 6.0.10**.
- Download **create-hololith-0.2.1.jar** and put it in the client and server
  `mods` folders. In single-player, only the client folder is needed.
- Replace older Hololith JARs. Keep only one Hololith version installed.
- The official Create NeoForge JAR supplies its bundled dependencies.
- Aeronautics **1.3.2** / Sable **2.0.6** are optional for ship functionality.
  The reference map mods and the rest of the development modpack are not needed.

The `-sources.jar` and `-project.zip` are for developers; do not install them as
mods. The project archive includes source, build tooling, documentation,
screenshots and validation summaries. Checksums are in `SHA256SUMS.txt`.

## This release

The earlier GPU-buffer helper has been replaced with an independently authored
implementation. Terrain geometry, textures, hologram lighting, marker behavior,
scan limits and save formats are preserved. See [validation](validation.md)
for framebuffer comparisons, performance measurements and actual game tests.

This is an early release. Controlled Sable assembly, translation and yaw are
covered; powered flight, arbitrary pitch/roll, shader packs and authenticated
multiplayer load still need acceptance testing. Ordinary Create contraptions
are not supported.

MIT-licensed. Developed primarily with AI coding tools under human direction.
