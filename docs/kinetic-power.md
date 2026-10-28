# Kinetic instruments — 0.5.0-dev.1

This development version requires actual Create rotational power. An unpowered,
too-slow or overstressed table shows **no hologram**, including cached terrain,
reference pins, the datum grid and scan lights. Its saved terrain and links remain
intact. Scanning resumes when sufficient power returns.

## Connect the table

The brass-and-andesite housing under the middle tile is the drive unit. Connect
a vertical shaft to its **underside** and drive it from a normal Create network.
Either rotation direction works. The minimum speed is **16 RPM**.

Only the center accepts rotation; the eight surrounding tiles remain parts of
the same instrument. The shaft does not pass power through the table's top.
Use Create gears or a gearbox beneath the table to bring power from the side.

Look at any table tile with Engineer's Goggles for power and stress information,
or open the panel to see its operating state and selected load. An overstressed
network stops the projection; provide more capacity or reduce the area/size.

## Power demand

Create stress is the impact below multiplied by the absolute shaft speed.
Minimum speed is the same for all settings; larger maps require more capacity.

| Survey area | Impact at 100% projection | Demand at 16 RPM |
|---|---:|---:|
| 32 × 32 | 4 SU/RPM | 64 SU |
| 64 × 64 | 8 SU/RPM | 128 SU |
| 128 × 128 | 16 SU/RPM | 256 SU |

Projection magnification scales the impact by its displayed area: 125% uses
1.5625 times the load; 150% uses 2.25 times the load. For example, a 128-block,
150% projection at 16 RPM uses **576 SU**. Changes update Create's actual network
stress immediately, including recovery when a smaller map fits the network.

Brightness, subtle flicker and vertical relief are display preferences and do not
change the shared kinetic requirement. Flicker smoothly varies light output by
at most 3.6%; the panel can disable it. Neither brightness nor flicker rebuilds
the terrain mesh or requests a scan.

The projection's base sits about 0.23 blocks above the tabletop instead of 0.35.
Terrain, grid and pins move down together; relief and horizontal scale are unchanged.

## Moving instruments

An Aeronautics/Sable ship can power its table from a real onboard shaft network.
The center drive moves with the same ship as the table. A sensor's movement alone
does not supply energy.

Ordinary assembled Create contraptions do not run a normal kinetic network.
Their captured table therefore stays **off**, even if it was powered before
assembly. Saved RPM is never treated as power. The table's identity, settings and
terrain survive; after upright disassembly, reconnect real power to use it.
Sensors and reference markers on these contraptions still report to a powered
table elsewhere. There is no hidden battery or free power from movement.

## Survival crafting

Surveys also require a physical source. Select the table's own location or link
a placed sensor/beacon with a Survey Card. There is no coordinate-entry mode.
An unloaded source can leave a saved survey viewable, but cannot authorize new
scans at its recorded location. Legacy coordinate-mode tables return to local
surveying, and coordinate-only cards must be rerecorded on an instrument.

The recipe book shows all patterns after their material unlocks. Common material
tags are used where Create/NeoForge provide them; no optional addon is required.

| Instrument | Main ingredients | Progression |
|---|---|---|
| Table | 2 brass ingots, amethyst shard, 2 andesite casings, precision mechanism, 2 copper sheets, shaft | Brass-era precision machinery |
| Local sensor | Andesite alloy, 2 copper ingots, electron tube, glass | An affordable optical instrument |
| Remote beacon | Local sensor, transmitter, 2 glass, 2 andesite alloy, brass ingot | Upgrade an existing sensor |
| Survey card | Paper, brass nugget, redstone | Cheap reusable physical link |
| 4 reference markers | Brass nugget, 2 amethyst shards, redstone, copper sheet | Affordable multi-point marking |

A marker and any of the sixteen dyes can be combined in the crafting grid.
Placed markers can also be dyed directly. The crafted item carries its color
through placement and drops; its tooltip names that color.

## Ponder and Jade

Hold the configured Ponder key over the table item to open its four lessons,
including **Powering the projection**. The other lessons cover surveying, cards,
sensors, beacons and markers. The visible tutorial motor is a demonstration
source; survival tables use an ordinary Create power source.

Optional **Jade 15.10.6 for Minecraft 1.21.1 NeoForge** adds instrument context.
Install Jade on the server and clients for the authoritative custom fields.
Jade is not bundled or required to operate Hololith. The provider sends only
small status/link/color/scale/power fields, never the terrain mesh or survey NBT.
Table edges resolve to the same center instrument without loading chunks.
Create goggles and the table panel remain available without Jade.

Pasted Create schematics create fresh instruments; they do not clone saved survey
identities or cached terrain. Normal world saves preserve both.

This branch uses network protocol 5: update Hololith on the server and every
client together. Existing published downloads keep their previous behavior.
