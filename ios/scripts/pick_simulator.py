#!/usr/bin/env python3
"""Prints the UDID of the iPhone simulator to use in CI: the newest iOS runtime, preferring an iPhone 17 Pro.

    xcrun simctl list devices available -j | python3 pick_simulator.py
"""
import json
import re
import sys

best = None
for runtime, devices in json.load(sys.stdin)["devices"].items():
    match = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not match:
        continue
    version = (int(match.group(1)), int(match.group(2)))
    for device in devices:
        name = device["name"]
        if not name.startswith("iPhone"):
            continue
        score = (version, name == "iPhone 17 Pro", "Pro" in name and "Max" not in name, name)
        if best is None or score > best[0]:
            best = (score, device["udid"], f"{name}, iOS {version[0]}.{version[1]}")
if best is None:
    sys.exit("No iPhone simulator found")
print(f"Using {best[2]}", file=sys.stderr)
print(best[1])
