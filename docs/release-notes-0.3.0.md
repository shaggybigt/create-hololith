# Create: Hololith 0.3.0

Adds ordinary Create contraption integration: moving surveying tables, sensors
and reference markers, with cards, dyes, wrench controls and the instrument
panel available while assembled. The projection retains its existing terrain,
textures, lighting, scan budgets and mesh cache. Aeronautics/Sable support remains.

## Installation

- Minecraft Java **1.21.1**, **Java 21**, **NeoForge 21.1.259**, **Create 6.0.10**.
- Put **create-hololith-0.3.0.jar** in both client and server `mods` directories.
  For single-player, install only in the client directory. Replace the older
  Hololith JAR; all players and the server must use the new version together.
- The official Create NeoForge JAR includes its required bundled libraries.
- Aeronautics **1.3.2** / Sable **2.0.6** remain optional for ships. No additional
  mod is needed for ordinary Create contraption support.

The `-sources.jar` and `-project.zip` files are for development, not installation.
The complete project ZIP contains the source, Gradle wrapper, resources, tests,
documentation and validation evidence. Checksums are in `SHA256SUMS.txt`.

## Contraption use

Connect the instruments to a contraption with Create's normal attachment rules.
Matching table tiles attach as a single 3×3 table. Configure and link before
assembly, or use cards, dyes, wrench controls and the table panel while moving.
A stationary table can follow a moving sensor, and a moving table can view a
fixed beacon or coordinate survey.

Park tables upright before disassembly. Sideways or upside-down placed tables
are unsupported. Offline/unloaded entities and Create portal transitions suspend
live updates; cached terrain remains available and no chunks are force-loaded.
The shared API covers several controller types; the exact tested fixtures and
remaining acceptance work are listed in [validation](validation.md).

## Validation

Passed 19 unit tests, 59 graphical bearing/minecart checks and 13 mounted reload
checks. Full-pack and Create-only graphical regressions and fresh-process reloads
also passed, as did 15 checks on each dedicated-server profile using the final JAR.
Bearings and minecarts were exercised physically; other shared Create controller
types still need individual in-game acceptance checks.

MIT-licensed. Developed primarily with AI coding tools under human direction.
