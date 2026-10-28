# Public schematic acceptance tests

Use small public builds with understood controls for contraption acceptance.
A working creator demonstration is useful selection evidence, but the craft must
also pass a baseline in the exact installed modpack before attributing a flight
failure to Hololith. Keep focused developer rigs for edge cases and label those
separately from public-build acceptance.

## Procedure

1. Record the creator page, author, download hash, Minecraft/Create versions,
   required mods, controls and fuel requirements. Inspect saved entities and
   block data before placement. Prefer builds using the existing dependencies.
2. Import through Create's schematic placement, preserving machine NBT and glue.
   Use a marked disposable world. Record any removed mobs or assembly additions;
   preserve the downloaded original. Never silently repair a craft and call it
   the unchanged baseline.
3. Assemble normally and operate the original controls with physics running.
   Check lift, propulsion, turning and stopping/deceleration. If this fails,
   diagnose the craft/setup first; do not count it as a Hololith regression.
4. Repeat with the table, underside sensor and four independently colored pins.
   Verify world positions, partial-frame marker alignment, survey progress and
   real client rendering. Record the added weight and any change in handling.
5. Keep downloaded NBT, temporary worlds, raw logs and large recordings under
   ignored local directories. Commit only attribution, reproduction instructions
   and compact validation evidence. A publicly downloadable schematic is not
   automatically licensed for redistribution or covered by this project's MIT.

## Current fixtures

| Build | Provenance and compatibility | Controls / status |
|---|---|---|
| [airship v10](https://createmod.com/schematics/airship-v10) | Patrik; user-supplied original `air_ship_v10.nbt`; 25×23×26, 2,121 blocks; DataVersion 3955; creator targets Create 6.0.10 / MC 1.21.X; Create, Aeronautics and Simulated | Saved keyboard: Space/Shift adjust lift, W/S adjust speed, A/D steering. Engage the separate lever labeled “fowards” to drive forward; both direction levers off means neutral. Onboard sign limits speed to 11. Six engines contain infinite superheated creative fuel. Used by the dedicated airship harness. |
| [Basic Aeronautics Airship](https://createmod.com/schematics/basic-aeronautics-airship) | Tommobean; downloaded from creator page; 5×10×11; inspected file has 160 blocks and six honey-glue entities; DataVersion 3955; same three mod namespaces | Creator: right lever lift, left lever forward (maximum 13), steering wheel turns. Downloaded and inspected as a smaller secondary candidate; no flight validation yet. |

Original `air_ship_v10.nbt` SHA-256:
`787e4586fcb7ac51239b009368444d528933a7ed4b3624dd6598ae83e423d9c6`.

Local test copy SHA-256:
`f2d16fb6b5343cd872f0d02ec9d247cb11a48d21ea6be950963a627f297beaa2`.
Only the creeper and bat entities were removed from this file; both original glue
entities and all block/machine NBT were retained. The harness adds a Physics
Assembler to the connected hull because the download does not contain one.
Create intentionally sanitizes runtime engine fuel during schematic placement,
so both trials prepare the six engines with normal Creative Blaze Cake item-use
interactions to reproduce the saved infinite/superheated fuel. Before/after fuel
and interaction results are recorded. Keyboard frequencies survive placement.
The fixture does not set ship velocity, inject forces or teleport the ship.

The v10 download has no Honey Glue for its hull. A normal assembler initially
captured only its supporting casing and the added assembler; inspection of the
disposable saved world confirmed that the other 2,120 original blocks remained
in place. Both trials therefore add the same Honey Glue selection, from local
`(0, -1, 0)` to `(24, 22, 25)` inclusive, through Simulated's normal server
selection handler with the Honey Glue item held and its configured size checks.
This includes space for the underside sensor in both trials and preserves the
original rotor Super Glue. The baseline is the **creator's layout with required
assembly and fuel preparation**, not an unchanged import. The original download
and sanitized NBT remain untouched; the added selection and capture inventory
are recorded in the trial events. Fuel clicks target the engine body outside
Create's rotation-control hitbox, and record live getters as well as saved NBT.

The original forward selector at local `(12, 2, 23)` must be switched on after
takeoff; the backward selector at `(11, 2, 23)` stays off. W/S alone adjust the
analog transmission and do not engage either directional gearshift. The harness
uses an ordinary empty-hand lever interaction, then observes bearing input RPM,
assembled rotor blocks, sail power and actual thrust. During deceleration it
first lowers the speed setting with S, then switches the forward selector off
to return to neutral. A zero speed setting still transmits the engines' base RPM.
The report distinguishes the throttle reduction from measured physical slowing:
it compares 20-tick mean horizontal speed before and after the complete reduction
and neutral sequence. Ship velocity is never set by the fixture.

`basic_airship.nbt` SHA-256:
`2ec7d3a941cf644b4aa8abda8357db20243cbfd92b4c3994296323d7e2e67ac2`.
The website counts 166 items; the inspected file separates 160 blocks from six
glue entities. This file is retained unchanged as a local candidate.

## Reproduction

On 2026-10-28, **0.5.0-dev.1** passed all 68 checks with the prepared baseline and
instrumented ship. The table is raised to local `(12, 3, 18)` above a vertical
shaft and a 32 RPM Creative Motor occupying previously empty positions. This
separate test supply leaves the creator's propulsion system intact; it verifies
real onboard kinetic-network operation, not a survival generator design. The
added instrument assembly is glued to the deck. Power loss/recovery and the
512 SU network load are checked while assembled, followed by powered flight.

On 2026-10-25, the prepared v10 baseline and the same craft fitted with Hololith
passed all 58 acceptance checks, including powered travel, turning, deceleration
and marker/render alignment. See [measured results and actual flight images](motion-validation.md).
The smaller Basic Aeronautics Airship remains an inspected candidate, not a tested craft.

The optional full-pack script uses the existing `runtimeRoot` layout described
in [building](building.md). Download the original from the creator page, then use
Python 3's standard library to prepare the hash-checked local copy:

```powershell
python scripts/Prepare-Airship.py <path-to-air_ship_v10.nbt>
```

This writes `reports/airship-fixture/air_ship_v10-hololith-test.nbt`. The script
preserves the original and refuses unexpected inputs or conflicting output.
You can also pass a prepared local file with `-SchematicPath`.
The current harness is specific to Patrik's v10 layout and validates the exact
prepared-copy hash before placement. `-SchematicPath` changes only the input
location; a different craft needs its own controls and fixture adaptation.

```powershell
./scripts/Start-Airship.ps1 -RuntimeRoot <your-runtime-root> -Fresh
```

The script archives an existing marked trial, creates `client-airship` with
`hololith-airship`, builds the separate probe, then launches actual Minecraft.
Results and screenshots are copied to `reports/airship-fixture/`. A failed
baseline prevents the instrumented trial. The probe is never a player mod and
must not be installed in an ordinary game. Imported craft/worlds are not shipped
in the source archive.
