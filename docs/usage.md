# Using Create: Hololith

These instructions describe **0.5.0-dev.1**, the kinetic-powered development
preview. Hold **Shift** over an instrument in your inventory for its controls.
Recorded Survey Cards show their target type, short identity and recorded location
so that cards for tables and sensors are easier to distinguish.

## Learn with Ponder

Hover over a Hololith item in your inventory and hold the configured **Ponder**
key (default **W**). Use Ponder's scene navigation to move between the tutorials
attached to that item.

| Item | Available tutorials |
|---|---|
| Surveying Table | **A landscape on your workbench**, **Surveying somewhere else**, **Marking places in the landscape**, **Powering the projection** |
| Local Survey Sensor or Remote Survey Beacon | **Surveying somewhere else** |
| Survey Card | **Surveying somewhere else**, **Marking places in the landscape** |
| Reference Marker | **Marking places in the landscape** |

The table tutorial explains placement, map area, rotation, magnification and
relief. The source tutorial demonstrates cards, sensors, beacons and
cached terrain. The marker tutorial shows direct table links, sensor groups, dye
colors and unlinking with a wrench.

Ponder uses its own small tutorial model. Its projection represents that model,
not a live scan of your current world; tutorial actions do not change your placed
instruments or links. Close Ponder to try the same controls on your own table.

## Use the instrument

- Place the **Hololith Surveying Table** in a clear, dry 3×3 space. Aim at the
  intended center tile. Connect a shaft beneath the center and supply at least
  **16 RPM**. It scans its own surroundings by default once powered.
- Right-click a table tile with an empty hand to open its instrument panel.
  Choose a **32×32**, **64×64** or **128×128** block area, quarter-turn rotation,
  projection size and normal/double relief. Press **Apply survey settings**.
  Projection size enlarges the hologram; Area changes the world coverage.
- A Create wrench rotates the map. Sneak-wrench cycles the survey area.
- Put a **Local Survey Sensor** under your vessel or elsewhere in the world.
  Use a **Survey Card** on the sensor, then use that card on the table. The table
  follows the sensor's world position; the link survives ordinary save/reload and
  is based on a UUID rather than the block's current coordinates.
  Breaking and replacing a sensor gives it a new identity: record and link it again.
- A **Remote Survey Beacon** uses the same linking method. Place it at a base,
  record it on a card, and link the table elsewhere. These links can cross
  dimensions when the target dimension is present on the server.
- Reuse a card on another sensor or beacon to overwrite its target. Reapplying
  a card changes the table's target. **Source: table** restores local scanning.

Recording and applying cards reports which instrument was recorded or linked and
what to do next. A sensor card's listed coordinates are its position when recorded;
the UUID link follows its current position. Cards cannot select arbitrary terrain. Short
identities distinguish nearby instruments; they are labels, not security credentials.

Leave the table a moment to acquire its first survey. A projection can be partial
if only some target chunks are loaded. Unknown columns remain blank. Previously
surveyed columns remain visible when their chunks unload; the panel reports the
live/cached state. A beacon is **not a chunk loader**. Visit distant terrain or
keep it naturally loaded to obtain new data. Links are instruments, not access
controls; players who can reach a table can operate it.

Sensors and beacons report locations; an active linked table performs the scan.
Capture a region while it is loaded before leaving it. Cached surveys belong to
a particular center and area setting, and old shared surveys can be evicted.
Changing targets or area requires loaded terrain unless that survey is cached.
Live surveying requires a physical source: the table itself, or a linked sensor
or beacon that is currently loaded and reporting. An offline link retains saved
terrain but cannot scan its old coordinates, even if those chunks are loaded by
another player. Old coordinate-only cards cannot retarget a table; existing
coordinate-mode tables return to surveying their own surroundings.
The table requires rotational power; its sensors, beacons, cards and reference
markers are passive. No CC:Tweaked computer or separate table fuel is required.
When power stops, all projected light switches off and surveys pause. Saved
terrain and links remain intact. Larger survey areas and projection sizes
increase Create stress demand. See [kinetic power and survival recipes](kinetic-power.md)
for costs, moving-platform limits, Ponder and optional Jade support.

## Read the survey status

The instrument panel explains the current display and what can update. These
labels describe the most recent completed scan; terrain refreshes gradually.

| Status | Meaning |
|---|---|
| **Live survey** | All samples were loaded during the latest completed scan. |
| **Live + cached terrain** | Every sample is known, but some terrain is currently supplied by the saved survey. |
| **Incomplete survey** | Some samples have never been captured. The panel shows known and loaded sample counts; unknown columns remain blank. |
| **Cached survey** | A saved projection is being checked after loading, or its terrain chunks are unloaded. The label identifies which. |
| **Surveying** | A scan is pending and no completed projection is available yet. |
| **Unavailable** | No usable terrain is known. Visit the target so its chunks can load naturally. |

**Sensor offline**, **Dimension unavailable**, **Position unavailable** and
**Updating location** identify the reason a live target cannot yet replace the
display. The panel says whether a saved survey is still shown or none is
available. While a moving source acquires its next region, retained terrain and
its marker positions continue to refer to the displayed region.

The counts are survey samples, not always individual blocks: a 128-block area
uses a 64×64 sample grid. A cached label does not imply the remote beacon is
loading chunks. Loading or reloading a world does not immediately mark old
terrain live; it must be checked again.

## Reference markers

Craft **Reference Markers** in batches of four. They are small brass-and-copper
lamps with a colored lens. The blocks can be exposed, enclosed or buried; they
do not need a view of the sky, terrain, table or player.

1. For markers following a sensor survey, record the sensor or beacon on a
   Survey Card as usual, then use that card on each reference block. Link the
   table to the same sensor. Every table viewing that sensor can show its markers.
2. For a direct table link, **sneak-use a Survey Card on any table tile**, then
   use that card on each reference block. Direct links stay with that table when
   its survey source changes. A table card does not retarget another table.
3. Use any of Minecraft's **16 dyes** on a placed reference to set its individual
   color. Survival recoloring consumes one dye only when the color changes.
   Mining preserves the dye color; replacing a block creates a new reference
   identity and needs a new link.
4. **Sneak-use a Create wrench** on a reference to unlink it. Empty-hand use
   reports its link status and color.

For the cargo-lift use case, mount four references at the opening's corners,
color them individually, and apply the same sensor card to all four. Assemble
them with the sensor and table on the ship. Their world X/Z positions, including
ship rotation, become small colored pins over the surveyed ground. Their actual
elevation does not set pin height. Reference blocks themselves are excluded from
the top of a terrain sample so a free-standing overhead reference cannot lift
its own pin; normal roofs and terrain remain part of the surface survey.

Markers follow the **currently displayed completed survey**. Out-of-area,
other-dimension and unknown-terrain locations are hidden. Movement is sampled
four times per second, with 1/256-block position quantization. Markers use the
same map rotation, magnification, relief and cached mesh height scale as terrain.
At most **64** in-area markers appear per table, ordered stably by identity.
They require no additional terrain scans. Changed positions travel separately
from the terrain snapshot; stationary markers send no recurring position packet.

Reference blocks must be in ticking, loaded chunks, an active Create contraption,
or an active Sable sublevel.
An unloaded/removed reference disappears promptly (within roughly one second)
instead of leaving an outdated pin. Its saved link and color return when it loads.
No marker chunk is force-loaded. A missing table or replaced sensor can be
resolved by recording the new target and reapplying the card. Links and table
identities survive save/reload, Create assembly and Sable relocation; old 0.1.0 tables gain an
identity automatically when loaded.

## Hologram lighting

The projection combines surface textures, biome colors and geometry with
emissive shading and thin additive highlights on exposed
terrain edges. It uses Minecraft's existing shaders; no shader pack or
postprocessing bloom is required. Colored pins have white cores and subtle
ground halos to remain distinguishable on both bright and dark terrain.

Open the table's instrument panel and move **Hologram brightness** from **40% to
180%** (default **100%**). The projection previews the change immediately, and the
setting is saved when the panel closes. It affects every Hololith on this client;
each player can choose their own intensity. You do not need to apply new survey
settings or restart the game. The underlying value remains `hologramIntensity` in
`config/hololith-client.toml` for players who prefer configuration files.

This changes local terrain, edge and marker brightness without new surveys or
GPU terrain rebuilds. The original mesh rebuild/cache limits remain in place;
the added cached glow buffer is capped at 4,096 small edge quads per survey.

## Survey fidelity and cost

The projection is actual 3D geometry: sampled ground and roofs, exposed cliff
faces, raised leaf canopies and a separate water surface. It uses world block
materials and biome colors. It is a surface survey, not a complete voxel copy:
interiors, cave systems, overhang undersides and individual branches are not
reconstructed. Trees and deep water are probed at most 32 blocks below the top.
The Nether survey shows the heightmap surface, including the roof.
Tall terrain is uniformly compressed vertically to fit the projection, preserving
relative heights. Relief doubles that fitted height; it is not an absolute altitude scale.

32/64-block areas sample every column. The 128-block area samples every other
column (64×64 samples); narrow details can disappear at this scale. Moving targets
are aligned to four-block increments to avoid rebuilding on every small movement.
Scans are spread across ticks and check loaded chunks only. Changes normally appear
after a five-second refresh plus queue/scan time. There is no continuous full scan.

Server settings are in the world's `serverconfig/hololith-server.toml`:
`columnsPerTick` (512), `refreshTicks` (100), `cachedRegions` (64). All tables share
the scan budget; the scanner also yields after approximately 2 ms per tick
(checked between small batches). A maximum of 128 pending regions and 512 recent
request keys bounds queue memory. Saved regions use block-state palettes, not
unstable runtime registry IDs. The world file `data/hololith_surveys.dat` holds
the bounded shared cache and sensor directory; tables also save their own last
snapshot in their block entity NBT.

## Ordinary Create contraptions

Sensors on Create's assembled contraptions report their transformed world
positions, and colored references follow the moving platform. A powered table
elsewhere can follow them with the same bounded scanning and marker updates.
Captured tables themselves stay off: ordinary Create contraptions do not maintain
live shaft networks. Their settings and terrain remain saved for disassembly.

- Configure and link instruments before assembly, or use their normal cards,
  dyes, wrench and instrument panel while assembled.
- Connect a table tile to your contraption using Create's normal assembly rules.
  All nine matching table tiles stay attached to each other. Sensors and markers
  still need their own normal structural/glue connections.
- A powered stationary table can follow a moving sensor. A table aboard an
  Aeronautics/Sable ship can use a fixed beacon if it has real onboard shaft
  power. Reference positions always use world X/Z.
- Park the table **upright before disassembling**. Its placed form is a horizontal
  3×3 multiblock; sideways or upside-down parked tables are not supported.
- A contraption that unloads or stops ticking stops live updates. The powered
  observing table retains its cached terrain. Captured tables need upright
  disassembly and a real power connection before projecting again. Hololith
  does not keep trains, carts or chunks loaded.

The bridge uses Create's shared actor APIs for controlled contraptions, gantries,
cart contraptions and train carriages. See [contraption compatibility](contraptions.md)
and [current validation](kinetic-validation.md) for the types actually exercised in game and
known limits. Mods that replace Create's contraption engine need separate testing.
