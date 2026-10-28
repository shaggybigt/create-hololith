# Kinetic integration validation — 0.5.0-dev.1

Test date: 2026-10-28. Targets remain Minecraft 1.21.1, NeoForge 21.1.259,
Create 6.0.10 and Java 21. Optional ship targets remain Aeronautics 1.3.2 and
Sable 2.0.6. Optional Jade acceptance uses 15.10.6 NeoForge.

## Build and gameplay evidence

The Gradle build, distribution checks and **58 JUnit tests** pass with no skipped
unit cases. Graphical probes run actual Minecraft with the source classes on the
development classpath and a separate disposable-world harness. Dedicated tests
load the packaged player JAR without that harness. These are different evidence
types; compilation alone does not establish gameplay behavior.

The Create-only kinetic run passed **357 checks**, and the optional Jade run
passed **373 checks**. Real motors, shafts and a
finite-capacity hand crank verify initial blackout, the 16 RPM threshold, reverse
rotation, source removal, cache retention, reconnection and network overload.
Changing span or magnification updates the real stress network, and lowering the
load recovers an overstressed network. Fixed-camera screenshots verify stopped
and overloaded projections are absent.

All 21 recipes load through Minecraft's recipe manager. The five base recipes are
matched against their real ingredients; all sixteen marker dye recipes are
crafted, placed, broken and placed again with their colors intact. Recipe-book
unlocks, ingredient tags, Create goggles and table-edge routing are checked.

All four Ponder scenes play in the real Ponder UI. The power lesson checks slow,
running, overloaded, disconnected and recovered states, cached terrain and
visible captions. Literal percentages render correctly. The in-game flicker
toggle updates and saves the client preference while reusing the same GPU mesh.
A unit test checks continuous frame-to-frame modulation within 96.4–100% output.

The full Create-only surveying regression passed **155 checks** in a fresh world
and **106 checks** after a separate JVM reload. This includes actual sensor/beacon
card use, saved links and colored markers, live terrain edits, scan throttling,
coordinate-packet/card rejection and legacy coordinate-save migration. Removing
a real surveyed beacon immediately invalidates its heartbeat; the powered table
retains the identical cached snapshot afterward. A fake offline sensor with
recorded coordinates cannot queue or acquire a new survey of loaded terrain.

The projection's shared render origin is lowered from 1.35 blocks to about 1.23
above the block base. Actual captures show terrain and pins lowered together;
terrain scale and height mapping are unchanged.

The ordinary Create contraption regression passed **140 checks** in a fresh
world and **24 checks** after a separate JVM reload. Actual bearing and cart
assemblies keep captured tables dark, even with a captured Creative Motor.
Moving sensors and reference markers feed a separately powered table. Tests
also cover card interactions, marker colors, client rendering, saved settings,
disassembly, reconnection to a real shaft and subsequent assembly.

The actual packaged player JAR passed **22 dedicated-server checks** with Create
only, **22** with the existing modpack, and **23** with Create and Jade. These
verify startup, real shaft power, stopped survey retention, captured sensor
updates and actor save/reload. All three runs used player JAR SHA-256
`9b9ba73a554675cc5f13a155233769eb1522bc13c5dd38fdf9af4894675d92ad`.
The optional Jade run also verifies discovery of Hololith's common plugin and
clean server operation; the graphical Jade run separately checks its visible HUD.

## Public airship acceptance

The [credited Airship V10 fixture](public-schematics.md) passed **68 checks**,
first with the prepared creator layout and then with Hololith instruments.
Both trials use actual unpaused physics and the original flight controls.
The fitted table receives 32 RPM through a real onboard shaft network and applies
512 SU for its 128-block survey. Stopping the source while assembled blanks the
projection without deleting terrain; restarting restores it. The table remains
powered through translation and turning, with four transformed reference pins.

The fitted craft adds a Creative Motor as an explicitly separate test supply.
This establishes onboard network compatibility, not the capacity of a particular
survival generator. The creator's propulsion system is retained. No downloaded
schematic or temporary world is redistributed with Hololith.

## Practical limits

- Ordinary assembled Create contraptions do not maintain normal live kinetic
  networks. Their tables stay off; moving sensors and markers remain usable by
  a powered table elsewhere. Disassembly and real power restore projection.
- No authenticated multiplayer session, multiple simultaneous human players,
  passenger camera, long-duration server soak or large fleet benchmark is claimed.
- The small flicker variation uses the existing draw-intensity value and adds no
  scans, mesh rebuilds or packets. A new hardware-wide FPS benchmark was not run.
- Jade's custom fields are validated for ordinary world instruments. Hovering
  captured Create contraptions may require a separate compatible Jade accessor;
  ship-specific Jade hovering is not part of this acceptance.
- Historical release screenshots predate the strict power requirement.
  Existing released JARs retain their previous behavior.

Raw reports, logs, captures and test worlds remain in ignored local development
directories. Only reviewed screenshots and this compact evidence belong in Git.
