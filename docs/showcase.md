# Development showcase

The local deliverable is a **74-second, 1600×900, 30 fps MP4** showing actual
Minecraft gameplay. It demonstrates the terrain projection, instrument controls,
sensor linking, colored reference markers, brightness and a moving Create cart.
It is a development preview, not a new mod release or promotional upload.

The capture comes directly from Minecraft's rendered framebuffer in an original
creative demonstration world. A scripted player camera and normal item-use
packets make the sequence reproducible; the instrument screens, surveys and
assembled cart are running in Minecraft. The edit removes the opening six
seconds of obstructed camera footage, adds descriptive captions and fades at the
beginning/end. It does not replace gameplay with generated images or animation.
The video is **silent**: it contains no game-audio, music or synthetic audio track.

## Reproduce the capture

Use the existing Windows validation layout described in [building.md](building.md)
with Java 21, NeoForge 21.1.259 and the official Create 6.0.10 JAR. No Aeronautics
installation is required for this cart demonstration. Obtain a trusted FFmpeg
executable separately; the encoder and its dependencies are not committed to Git
or included in Hololith. FFmpeg must support `libx264` encoding and `drawtext`.

Close other Minecraft/server/Gradle processes using that validation runtime, then
run from the source directory:

```powershell
.\scripts\Start-Showcase.ps1 -RuntimeRoot C:/HololithDev -FFmpegPath C:/Tools/ffmpeg.exe
```

This compiles the mod and isolated probe, stages a disposable Create-only client,
constructs the observatory/landscape, waits for a complete real survey, and records
the 80-second sequence. A previous showcase directory is archived before a fresh
run. Do not point the harness at a normal world. The probe refuses to operate
without its explicit allowed-root property and disposable-directory marker.

Outputs are copied into the ignored `reports/showcase/` directory:

- `gameplay-raw.mp4`: the original Minecraft framebuffer capture.
- `capture.json`: frame counts and checks that a complete survey, the actual
  brightness control and real cart assembly occurred.
- `capture.log`: the development client's launch log.

The reusable world is at
`<RuntimeRoot>/development/hololith/client-showcase/saves/hololith-validation/`.
After the client closes, copy that save into another **disposable** Create/Hololith
client to explore it without the recording probe. Remove the probe JAR from the
copy's `mods` directory. The world, recordings and encoder stay outside Git; they
can be supplied as separate local deliverables without changing release downloads.

## Reproduce the edit

Use Python 3.10 or newer; no third-party Python package is needed:

```powershell
python scripts/Edit-Showcase.py --ffmpeg C:/Tools/ffmpeg.exe
```

The default input is `reports/showcase/gameplay-raw.mp4`; the output is
`reports/showcase/create-hololith-development-showcase.mp4`. Use `--input` and
`--output` to select other locations, and `--overwrite` to replace an existing
edited output. The script preserves the raw capture. On Windows it uses a locally
installed Segoe UI font; elsewhere it uses fontconfig. `--font /path/to/font.ttf`
provides an explicit local font. Fonts are not bundled or redistributed.

The editor verifies the expected input dimensions and duration, trims to 74
seconds, adds captions above the action, and writes an H.264/YUV420P MP4 suitable
for common players. It then decodes the entire result and requires **2,220 frames**.
`edit.json` records SHA-256 hashes, the caption timeline and validation result.
These checks establish file integrity and duration, not gameplay correctness.

## Demonstration and recording plan

Times below refer to the finished 74-second video; add six seconds for raw capture
times. The short unlabeled gaps leave room to see the projection between actions.

| Edited time | Shot and action |
|---|---|
| 00:00–00:09 | Close orbit of the real terrain projection: river, trees, roof and relief. |
| 00:11–00:17 | Open the actual instrument panel, change 32 to 64 blocks and rotate the map. |
| 00:19–00:23 | Show the refreshed larger region and colored reference pins. |
| 00:23–00:31 | Record the ground sensor on a Survey Card, then apply it to the table. |
| 00:35–00:42 | Link a reference to the sensor group and recolor it with a red dye. |
| 00:46–00:51 | At dusk, move the actual brightness slider and close the panel. |
| 00:54–01:10 | Assemble a real Create cart carrying the table, sensor and references; follow its movement. |
| 01:10–01:14 | Settle on the contraption with a development-preview caption and gentle fade. |

For manual recording, use clear weather, midday light for terrain, dusk for
brightness, a 50-degree field of view, no view bobbing and a 1600×900 window. Hide
the HUD during wide/close terrain shots and show it while demonstrating cards,
dyes and controls. A three-block elevated camera at a five-to-eight-block distance
keeps the table and terrain legible. Avoid the balcony's corner lamps.

## World landmarks and walkthrough

All coordinates below are in the Overworld. The terrain, river, trees and small
copper-roofed building are intentionally constructed Minecraft blocks, captured
by the ordinary Hololith sampler.

| Landmark | Position |
|---|---|
| Observatory table center | **24, 101, 0** |
| Local Survey Sensor | **0, 81, 0** |
| Remote Survey Beacon | **3, 81, 0** |
| Ground reference corners | **−6, 82, −6**; **6, 82, −6**; **6, 82, 6**; **−6, 82, 6** |
| Cart assembler / initial cart position | **−8, 100, 25** |
| Cart table before assembly | **−6, 102, 25** |
| Cart sensor before assembly | **−8, 101, 25** |

The balcony begins with a 32-block coordinate survey centered at X=0, Z=0, and
four references linked directly to that table. Record the sensor on a card and
apply it to the table to switch to a moving source. Apply a sensor card to a
reference and use a dye to demonstrate group links and independent colors. The
beacon beside the sensor is available for a manual remote-source walkthrough;
it is not operated in the finished video.

The cart's table and four reference blocks are pre-linked to its own sensor.
The recording harness assembles it through Create and drives it gently along the
east–west rail while the real contraption system performs movement, surveying
and rendering. After reopening the saved world without the probe, use normal
Create/minecart controls; the scripted drive is no longer present.

## Scope

This footage demonstrates a Create minecart contraption, not powered Aeronautics
flight. It does not show Ponder playback, survival crafting, authenticated remote
multiplayer or every contraption controller. The raw run recorded 2,400 frames
over 80 seconds, with five repeated capture frames; repetition fills occasional
capture timing gaps and is not generated motion. See [validation](validation.md)
for the separate test evidence and remaining compatibility limits.
