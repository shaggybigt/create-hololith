# Aeronautics compatibility

Target: Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10, Aeronautics bundled
1.3.2, Sable 2.0.6. These were checked in the pack lock and installed JARs.
Sable bundles Sable Companion 1.6.0.

Ship blocks live in distant plot chunks, separate from their physical world
pose. `ShipCoordinates` calls Companion's `projectOutOfSubLevel(Level, Vec3)`;
reflection is resolved once and cached. The exact signature was verified in
installed bytecode. Conversion errors return an empty result and log once;
callers retain cached terrain rather than scanning unconverted plot coordinates.
Without Companion, ordinary world coordinates are used. No companion code or
extra library is bundled.

Installed `ServerLevelPlot` registers normal block entities and tick containers
with `ENTITY_TICKING` status. `VanillaSubLevelBlockEntityRenderer` supplies a
ship-transformed PoseStack to the normal renderer dispatcher. The projection
therefore stays table-local and does not transform its vertices twice. Sensor
links use persistent UUIDs rather than relocation-sensitive block positions.
Pending surveys finish before the next moving target is selected, keeping the
last completed terrain visible during motion.

Reference markers use the same physical-world conversion, including the full
ship rotation, on each five-tick location update. Their world X/Z coordinates
are mapped against the displayed completed survey; their ship or world Y is
discarded. The renderer uses the actual cached terrain mesh's height mapping,
then applies the same table rotation, magnification and relief to the pins.
This keeps markers registered to terrain while a newer survey is pending.
Enclosure and line of sight never enter the marker lookup.

Table, sensor and reference identities are saved UUIDs. A runtime registry of
loaded references indexes those links without searching chunks. Removal checks
the owning block entity so an old pre-assembly instance cannot unregister its
replacement. Initial chunk data includes the current marker frame; changed
frames use a bounded clientbound payload sent to chunk-tracking players. The
installed Sable `ChunkMapMixin.getPlayers` maps plot chunks to the sublevel's
tracking players, so this path also delivers updates aboard ships. Reference
updates do not resend terrain geometry or trigger terrain scans.

Table teardown occurs only on player/explosion destruction, not generic
`onRemove`, so assembly cannot accidentally delete the other eight tiles.
Rotation and mirror operations update tile indices. The installed Sable assembly
helper also sets `isMoving=true`. Simulated's assembly predicate permits blocks
with finite hardness outside its `non_movable` tag; Hololith's piston-blocking
reaction therefore does not inherently prohibit physics assembly.

Ordinary Create bearing, train and piston contraptions are a different system
and are not supported by this bridge. Glue/assemble all nine table tiles as one
ship structure. See `validation.md` for exactly which live integration tests ran.
The 0.2.1 graphical probe assembled the actual table, underside sensor and four
fully enclosed reference blocks through Sable. It checked all four marker
coordinates against an independently transformed fixture after assembly,
translation by (8, 0, -4), and a 90-degree yaw. Actual client marker frames and
terrain remained synchronized; the table identity survived relocation and the
engine disassembled the structure successfully. Marker height came from the
surveyed ground rather than the reference blocks' altitude.

This was a controlled paused-physics fixture using the installed engine. Its
components are joined by a physical frame: Sable's disconnected-body splitting
runs even with physics paused, so disconnected enclosures are not one airship.
Powered flight, passengers, docking, shader packs and authenticated multiplayer
require their own acceptance tests.

Sources: [Companion API](https://github.com/ryanhcode/sable-companion),
[Sable documentation](https://github.com/ryanhcode/sable/wiki),
[Sable plot implementation](https://github.com/ryanhcode/sable/blob/main/common/src/main/java/dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot.java),
[Simulated assembly](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/simulated/common/src/main/java/dev/simulated_team/simulated/util/assembly/SimAssemblyContraption.java).
