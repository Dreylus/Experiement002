#!/usr/bin/env python3
"""Copies the UI test screenshots into the repo, shrunk to 600 px wide.

    python3 collect_screenshots.py <raw dir> <result bundle .xcresult> <destination dir>

The test writes PNGs straight into <raw dir>. If it couldn't, they're exported from the result bundle.
The destination is only replaced when there are new screenshots.
"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

raw, result, dest = (Path(arg) for arg in sys.argv[1:4])
raw.mkdir(parents=True, exist_ok=True)

if not any(raw.glob("*.png")) and result.exists():
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(result),
                        "--output-path", tmp], check=False)
        manifest = Path(tmp) / "manifest.json"
        if manifest.exists():
            for test in json.loads(manifest.read_text()):
                for item in test.get("attachments", []):
                    name = item.get("suggestedHumanReadableName") or ""
                    match = re.match(r"(\d\d-[a-z0-9-]+)", name)
                    if match and item.get("exportedFileName"):
                        shutil.copy(Path(tmp) / item["exportedFileName"], raw / f"{match.group(1)}.png")

shots = sorted(raw.glob("*.png"))
if not shots:
    print(f"::warning::No screenshots in {raw}")
    sys.exit(0)

if dest.exists():
    shutil.rmtree(dest)
dest.mkdir(parents=True)
for path in shots:
    with Image.open(path) as image:
        image = image.convert("RGB")
        width, height = image.size
        if width > 600:
            image = image.resize((600, round(height * 600 / width)), Image.LANCZOS)
        image.save(dest / path.name, optimize=True)
print(f"{len(shots)} screenshots -> {dest}")
