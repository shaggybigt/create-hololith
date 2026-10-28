"""Caption the complete four-minute actual Minecraft walkthrough.

Requires Python 3.10+ and an external FFmpeg with libx264 and drawtext. This script
does not generate or replace gameplay, add audio, or upload anything.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parent.parent
CAPTIONS = [
    (0, 18, "Create: Hololith", "Four minutes of actual Minecraft - real terrain, textures and colored reference points"),
    (18, 25, "The projection needs kinetic power", "Stopping the real Create drive turns the light off; the completed survey stays saved"),
    (25, 31, "At least 16 RPM", "This 8 RPM shaft turns, but the table cannot project yet"),
    (31, 35, "Power restored", "A Creative Motor supplies this demonstration's genuine 32 RPM kinetic network"),
    (35, 48, "64-block survey and map rotation", "Use the physical table's instrument panel - settings apply to the real survey"),
    (48, 71, "128 blocks of terrain", "A larger area and projection use more Create stress capacity"),
    (71, 90, "An enlarged 150-percent projection", "Adjust map size independently of survey area; the stress requirement updates with it"),
    (90, 114, "Link a placed survey sensor", "Record the sensor on a Survey Card, then use that same card on the table"),
    (114, 136, "A remote beacon is another physical source", "Record the placed beacon and return its card to the table to view that location"),
    (136, 160, "Reference markers and sixteen dye colors", "Link a reference to the sensor, then dye it - its pin appears over the surveyed terrain"),
    (160, 182, "Brightness and subtle flicker", "Preview both settings in the real panel; each client saves its own display preferences"),
    # Let the native Ponder timeline and controls remain unobscured after the introduction.
    (182, 186, "Create Ponder tutorials", "The in-game tutorial explains the instrument with Create's familiar interactive scenes"),
    (193, 210, "Prepare a moving survey", "Link the powered stationary table to a sensor mounted on a real Create cart"),
    (210, 219, "A cart-mounted sensor and four references", "Powered rails move this assembled demonstration rig after one initial push"),
    (219, 232, "Watch the terrain follow the moving source", "The stationary table keeps its own kinetic drive while the sensor and references travel"),
    (232, 240, "Create: Hololith", "Minecraft Java 1.21.1 / NeoForge / Create 6.0.10 - actual gameplay, no generated footage"),
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def select_font(explicit: str | None) -> Path | None:
    if explicit:
        font = Path(explicit).expanduser().resolve()
        if not font.is_file():
            raise SystemExit(f"Font is not a file: {font}")
        return font
    windows_font = Path("C:/Windows/Fonts/segoeui.ttf")
    if windows_font.is_file():
        return windows_font
    fontconfig = shutil.which("fc-match")
    if fontconfig:
        match = subprocess.run([fontconfig, "-f", "%{file}", "sans-serif"],
                               capture_output=True, text=True, check=True).stdout
        if Path(match).is_file():
            return Path(match)
    return None  # Ask FFmpeg's own fontconfig integration for sans-serif.


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", required=True, help="Path to a trusted FFmpeg executable")
    parser.add_argument("--input", type=Path, default=ROOT / "reports/showcase/gameplay-raw.mp4")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "reports/showcase/create-hololith-showcase.mp4")
    parser.add_argument("--font", help="Optional local TTF/OTF font; otherwise Segoe UI or fontconfig")
    parser.add_argument("--overwrite", action="store_true", help="Replace the specified output video")
    args = parser.parse_args()
    executable = str(Path(shutil.which(args.ffmpeg) or args.ffmpeg).expanduser().resolve())
    source = args.input.expanduser().resolve()
    output = args.output.expanduser().resolve()
    if not source.is_file():
        parser.error(f"Input capture is missing: {source}")
    capture_path = source.parent / "capture.json"
    if not capture_path.is_file():
        parser.error("The matching capture.json evidence is required beside the raw video.")
    capture = json.loads(capture_path.read_text(encoding="utf-8-sig"))
    if (capture.get("completed") is not True or capture.get("frames") != 7200
            or capture.get("durationSeconds") != 240
            or any(check.get("passed") is not True for check in capture.get("checks", []))):
        parser.error("The raw capture must be a completed, validated four-minute showcase.")
    if source == output:
        parser.error("Output must not replace the raw capture.")
    if output.exists() and not args.overwrite:
        parser.error("Output already exists; use --overwrite to replace it.")
    output.parent.mkdir(parents=True, exist_ok=True)
    probe = subprocess.run([executable, "-hide_banner", "-i", str(source)],
                           capture_output=True, text=True)
    if not re.search(r"Video:.*1600x900.*30 fps", probe.stderr):
        parser.error("Expected the original 1600x900, 30 fps Minecraft capture.")
    duration = re.search(r"Duration: (\d+):(\d+):([\d.]+)", probe.stderr)
    if duration is None or (int(duration[1])*3600 + int(duration[2])*60 + float(duration[3])) < 240:
        parser.error("Expected the complete 240-second original capture.")
    selected_font = select_font(args.font)
    with tempfile.TemporaryDirectory(prefix="hololith-edit-", dir=output.parent) as temporary:
        work = Path(temporary)
        if selected_font:
            shutil.copyfile(selected_font, work / "caption-font.ttf")
            font_argument = "fontfile=caption-font.ttf"
        else:
            font_argument = "font=sans-serif"
        filters = ["setpts=PTS-STARTPTS"]
        for index, (start, end, title, subtitle) in enumerate(CAPTIONS):
            (work / f"title-{index}.txt").write_text(title, encoding="utf-8")
            (work / f"subtitle-{index}.txt").write_text(subtitle, encoding="utf-8")
            active = f"enable='gte(t,{start})*lt(t,{end})'"
            # Keep Create's native Ponder title and upper tutorial controls visible.
            banner_y = 752 if start == 182 else 32
            filters.extend([
                f"drawbox=x=48:y={banner_y}:w=1504:h=102:color=0x102226@0.79:t=fill:{active}",
                f"drawbox=x=48:y={banner_y}:w=5:h=102:color=0xd8b978@1:t=fill:{active}",
                f"drawtext={font_argument}:textfile=title-{index}.txt:expansion=none:"
                f"fontsize=32:fontcolor=0xf8edda:x=78:y={banner_y+14}:{active}",
                f"drawtext={font_argument}:textfile=subtitle-{index}.txt:expansion=none:"
                f"fontsize=23:fontcolor=0xe0efec:x=79:y={banner_y+59}:{active}",
            ])
        filters += ["fade=t=in:st=0:d=0.55", "fade=t=out:st=239.2:d=0.8"]
        (work / "edit.ffscript").write_text(",\n".join(filters), encoding="utf-8")
        command = [executable, "-hide_banner", "-y" if args.overwrite else "-n",
                   "-i", str(source), "-t", "240", "-map", "0:v:0",
                   "-an", "-sn", "-dn", "-filter_script:v", "edit.ffscript", "-r", "30",
                   "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-threads", "8",
                   "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(output)]
        subprocess.run(command, cwd=work, check=True)
    # Decode the entire result and count frames, rather than trusting an encode exit alone.
    validation = subprocess.run([executable, "-v", "error", "-nostats", "-progress", "pipe:1",
                                 "-i", str(output), "-map", "0:v:0", "-f", "null", "-"],
                                capture_output=True, text=True, check=True)
    frame_counts = re.findall(r"^frame=(\d+)$", validation.stdout, flags=re.MULTILINE)
    if not frame_counts or int(frame_counts[-1]) != 7200 or validation.stderr.strip():
        raise RuntimeError("Edited capture failed its 7200-frame decode check: " + validation.stderr)
    report = {
        "source": source.name, "output": output.name,
        "sourceSha256": sha256(source), "outputSha256": sha256(output),
        "sourceSeconds": 240, "trimmedStartSeconds": 0, "durationSeconds": 240,
        "version": capture.get("version"),
        "width": 1600, "height": 900, "fps": 30, "decodedFrames": 7200,
        "audio": "Silent; no recorded or synthetic audio track",
        "editing": "Complete capture, readable chapter captions, gentle opening/closing fades",
        "gameplay": "Actual Minecraft framebuffer capture with only the documented edits; no generated gameplay",
        "captions": [dict(start=start, end=end, title=title, subtitle=subtitle)
                     for start, end, title, subtitle in CAPTIONS],
        "font": selected_font.name if selected_font else "sans-serif via fontconfig",
        "validation": "Full output decoded successfully with exactly 7200 frames",
    }
    (output.parent / "edit.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "durationSeconds": 240, "frames": 7200,
                      "bytes": output.stat().st_size, "sha256": report["outputSha256"]}, indent=2))


if __name__ == "__main__":
    main()
