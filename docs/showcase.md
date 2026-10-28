# Gameplay showcase

The new showcase accompanies **0.5.0-dev.1**. Its four-minute walkthrough covers
terrain, shaft power, survey settings, physical sensor/beacon links, colored
reference markers, moving sensors, brightness, flicker and Ponder.

Watch the current video from the repository README or
[download the full-quality copy](https://github.com/shaggybigt/create-hololith/releases/download/v0.5.0-dev.1/create-hololith-showcase.mp4).

| Time | Feature |
|---|---|
| 0:00 | Textured terrain and colored pins |
| 0:18 | Power loss, minimum speed and restoration |
| 0:35 | Survey area, rotation and magnification |
| 1:30 | Physical sensor and beacon links |
| 2:16 | Reference linking and dye colors |
| 2:40 | Brightness and subtle flicker |
| 3:02 | Create Ponder |
| 3:13 | Linking a cart sensor and following its motion |

## How it is recorded

The footage comes directly from Minecraft's rendered framebuffer in an original
creative demonstration world. A scripted player camera and real item-use packets
make the sequence reproducible. Terrain scanning, controls and contraption motion
run in Minecraft; no generated images or simulated gameplay replace the footage.
The edit adds readable captions and opening/closing fades. The video is silent.

Creative equipment provides repeatable power for the demonstration. Ordinary
assembled Create carts carry sensors and reference markers; a separate powered
table observes them. This is distinct from an Aeronautics ship's live onboard
kinetic network, which has its own [public-airship acceptance test](public-schematics.md).

## Reproduce the capture

Use Java 21 and the prepared Windows development layout from [building](building.md).
Obtain an FFmpeg executable supporting `libx264` and `drawtext`; FFmpeg is not
bundled with the mod or source archive.

```powershell
./scripts/Start-Showcase.ps1 -RuntimeRoot C:/HololithDev -FFmpegPath C:/Tools/ffmpeg.exe -Fresh
python scripts/Edit-Showcase.py --ffmpeg C:/Tools/ffmpeg.exe
```

The helper stages a marked disposable world, builds the separate probe and records
actual gameplay. Keep its probe JAR out of ordinary saves. Raw video, capture
checks, the edited video and editing evidence belong in ignored `reports/showcase/`;
worlds and encoder binaries are not committed to Git. Preserve raw footage when
making a new edit. The editing script fully decodes its result to check frame count.
