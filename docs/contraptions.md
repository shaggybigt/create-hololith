# Ordinary Create contraptions

Hololith 0.3.0 targets the installed Create 6.0.10 for Minecraft 1.21.1 NeoForge.
Its integration uses Create's `MovementBehaviour`, `MovingInteractionBehaviour`
and `BlockMovementChecks` APIs. No Create implementation or library is bundled
in Hololith. The official Create JAR remains a required external dependency.

## Runtime model

Create captures assembled blocks as an entity rather than continuing ordinary
server block-entity ticks. Hololith gives its captured instruments a small
runtime block entity, loaded from their existing NBT. These objects are never
installed in world chunks. Actor ticks feed the existing scanner and marker
registry using Create's transformed block centers, followed by the optional
Sable world transform. Configuration and snapshots are persisted back into the
captured block data for entity saves and disassembly.

The client uses Create's virtual render world and existing Hololith block-entity
renderer. The terrain mesh, hologram materials and bounded GPU cache are reused.
Updates address a contraption entity ID, entity UUID and local block position;
ordinary chunk packets cannot address a captured block. Full state is sent when
it changes or a player starts tracking, with marker movement sent separately.
The existing 64-marker bound, quantization and throttling remain in place.

The shared actor APIs serve mechanical bearings, pistons, pulleys, elevators,
gantries, minecart contraptions and train carriages. This is architectural
coverage, not a claim that every controller, track layout or third-party addon
has passed an acceptance test. See [validation](validation.md) for actual runs.

## Use and limits

Connect the instruments using Create's normal structural/glue rules. Matching
table tiles attach to one another during assembly; separate adjacent tables do
not attach merely because their sides touch. Vanilla pistons still cannot shear
the table apart. Cards, dyes, wrench controls and the panel work while assembled.

Park the table upright before disassembly. Its placed model is a horizontal 3×3
table; sideways/upside-down parked tables would need a different orientation
model. Do not use tilted disassembly to place one vertically.

Sensors and markers update only while their entity is ticking. Offline train
simulation, unloaded contraptions and Create's portal transitions may suspend
actors. Hololith retains the last terrain and allows marker entries to expire;
it does not load chunks, force trains online or extrapolate unknown positions.
Create may also pause minecart actors during a configured rotation pause.

Arbitrary third-party replacements for Create's moving-entity system need their
own validation. Sable ships retain the separate integration documented in
[Aeronautics integration](aeronautics.md).
