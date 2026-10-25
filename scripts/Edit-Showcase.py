"""Edit the actual 80-second Minecraft capture into a captioned 74-second preview.

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
    (0, 9, "Create: Hololith", "A real three-dimensional survey, projected above your workbench"),
    (11, 17, "Survey controls", "Change survey area and map rotation from the instrument panel"),
    (19, 23, "A larger view", "32 / 64 / 128-block survey areas - terrain refreshes gradually"),
    (23, 31, "Link a survey sensor", "Record it on a Survey Card, then apply the card to a table"),
    (35, 42, "Colored reference points", "Link each marker, then choose from Minecraft's sixteen dye colors"),
    (46, 51, "Brightness, in game", "Adjust projection intensity instantly - each client keeps its own setting"),
    (54, 70, "Surveying on the move", "A real assembled Create cart carries the table, sensor and reference markers"),
    (70, 74, "Create: Hololith - development preview", "Minecraft Java 1.21.1 / NeoForge / Create 6.0.10 - work in progress"),
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
                        default=ROOT / "reports/showcase/create-hololith-development-showcase.mp4")
    parser.add_argument("--font", help="Optional local TTF/OTF font; otherwise Segoe UI or fontconfig")
    parser.add_argument("--overwrite", action="store_true", help="Replace the specified output video")
    args = parser.parse_args()
    executable = str(Path(shutil.which(args.ffmpeg) or args.ffmpeg).expanduser().resolve())
    source = args.input.expanduser().resolve()
    output = args.output.expanduser().resolve()
    if not source.is_file():
        parser.error(f"Input capture is missing: {source}")
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
    if duration is None or (int(duration[1])*3600 + int(duration[2])*60 + float(duration[3])) < 80:
        parser.error("Expected an 80-second original capture.")
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
            filters.extend([
                f"drawbox=x=48:y=32:w=1504:h=102:color=0x102226@0.79:t=fill:{active}",
                f"drawbox=x=48:y=32:w=5:h=102:color=0xd8b978@1:t=fill:{active}",
                f"drawtext={font_argument}:textfile=title-{index}.txt:expansion=none:"
                f"fontsize=32:fontcolor=0xf8edda:x=78:y=46:{active}",
                f"drawtext={font_argument}:textfile=subtitle-{index}.txt:expansion=none:"
                f"fontsize=23:fontcolor=0xe0efec:x=79:y=91:{active}",
            ])
        filters += ["fade=t=in:st=0:d=0.55", "fade=t=out:st=73.35:d=0.65"]
        (work / "edit.ffscript").write_text(",\n".join(filters), encoding="utf-8")
        command = [executable, "-hide_banner", "-y" if args.overwrite else "-n",
                   "-ss", "6", "-i", str(source), "-t", "74", "-map", "0:v:0",
                   "-an", "-sn", "-dn", "-filter_script:v", "edit.ffscript", "-r", "30",
                   "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-threads", "8",
                   "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(output)]
        subprocess.run(command, cwd=work, check=True)
    # Decode the entire result and count frames, rather than trusting an encode exit alone.
    validation = subprocess.run([executable, "-v", "error", "-nostats", "-progress", "pipe:1",
                                 "-i", str(output), "-map", "0:v:0", "-f", "null", "-"],
                                capture_output=True, text=True, check=True)
    frame_counts = re.findall(r"^frame=(\d+)$", validation.stdout, flags=re.MULTILINE)
    if not frame_counts or int(frame_counts[-1]) != 2220 or validation.stderr.strip():
        raise RuntimeError("Edited capture failed its 2220-frame decode check: " + validation.stderr)
    report = {
        "source": source.name, "output": output.name,
        "sourceSha256": sha256(source), "outputSha256": sha256(output),
        "sourceSeconds": 80, "trimmedStartSeconds": 6, "durationSeconds": 74,
        "width": 1600, "height": 900, "fps": 30, "decodedFrames": 2220,
        "audio": "Silent; no recorded or synthetic audio track",
        "editing": "Opening trim, descriptive top captions, gentle opening/closing fades",
        "gameplay": "Actual Minecraft framebuffer capture with only the documented edits; no generated gameplay",
        "captions": [dict(start=start, end=end, title=title, subtitle=subtitle)
                     for start, end, title, subtitle in CAPTIONS],
        "font": selected_font.name if selected_font else "sans-serif via fontconfig",
        "validation": "Full output decoded successfully with exactly 2220 frames",
    }
    (output.parent / "edit.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "durationSeconds": 74, "frames": 2220,
                      "bytes": output.stat().st_size, "sha256": report["outputSha256"]}, indent=2))


if __name__ == "__main__":
    main()
