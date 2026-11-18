#!/usr/bin/env python3
"""Builds the app icon, the in-app icon picker and the app name from ios/Branding.

    python3 ios/scripts/prepare_branding.py      (needs: pip install pillow; pillow-heif for HEIC)

ios/Branding/AppIcon.png     The icon on the Home Screen and on every order alert.
                             PNG, JPG, WEBP or HEIC, any size: it's center-cropped to a square.
ios/Branding/Icons/*.png     Extra icons to switch between inside the app (Customize tab).
                             The file name is the title, e.g. "Gold Bag.png".
ios/Branding/branding.json   {"appName": "Store"}: the name under the icon.

Everything this writes is git-ignored and rebuilt on every CI run:
    ios/OrderNotifier/Resources/Assets.xcassets/AppIcon*.appiconset
    ios/OrderNotifier/Resources/Assets.xcassets/BrandIcon.imageset, IconPreview-*.imageset
    ios/OrderNotifier/Resources/Icons.json
    ios/Support/Info.plist
"""
import json
import plistlib
import re
import shutil
from pathlib import Path

from PIL import Image, ImageOps

try:  # Photos straight from an iPhone are often HEIC.
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:
    pass

IOS = Path(__file__).resolve().parent.parent
BRANDING = IOS / "Branding"
RESOURCES = IOS / "OrderNotifier" / "Resources"
ASSETS = RESOURCES / "Assets.xcassets"
INFO_PLIST = IOS / "Support" / "Info.plist"

PICTURES = {".png", ".jpg", ".jpeg", ".webp", ".heic", ".heif"}
DEFAULT_NAME = "Store"
BLANK = (0x9A, 0x9F, 0xA6)  # the blank grey slate, same as the Android app
REFRESH_TASK = "app.ordernotifier.refresh"  # must match refreshTaskID in AppModel.swift
PREVIEW_SIZE = 384  # in-app pictures are at most 104 pt (312 px on a 3x screen)


def warn(message, path=None):
    """Prints a warning that also shows up on the GitHub Actions run page."""
    where = f" file={path.relative_to(IOS.parent)}" if path else ""
    print(f"::warning{where}::{message}")


def square(path, size):
    """Center-crops a picture to a square, flattens transparency onto white."""
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGBA")
    side = min(image.size)
    left = (image.width - side) // 2
    top = (image.height - side) // 2
    image = image.crop((left, top, left + side, top + side)).resize((size, size), Image.LANCZOS)
    flat = Image.new("RGB", (size, size), (255, 255, 255))
    flat.paste(image, mask=image.getchannel("A"))
    return flat


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def add_app_icon(name, image):
    folder = ASSETS / f"{name}.appiconset"
    folder.mkdir(parents=True)
    image.save(folder / "Icon.png", optimize=True)
    write_json(folder / "Contents.json", {
        "images": [{"filename": "Icon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        "info": {"author": "xcode", "version": 1},
    })


def add_preview(name, image):
    folder = ASSETS / f"{name}.imageset"
    folder.mkdir(parents=True)
    image.resize((PREVIEW_SIZE, PREVIEW_SIZE), Image.LANCZOS).save(folder / f"{name}.png", optimize=True)
    write_json(folder / "Contents.json", {
        "images": [{"filename": f"{name}.png", "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
    })


def words(stem):
    return re.findall(r"[A-Za-z0-9]+", stem)


def title_for(stem):
    text = " ".join(words(stem)) or "Icon"
    return text if any(c.isupper() for c in text) else text.title()


def pictures_in(folder):
    if not folder.is_dir():
        return []
    return sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in PICTURES)


def app_name():
    path = BRANDING / "branding.json"
    if not path.exists():
        return DEFAULT_NAME
    try:
        config = json.loads(path.read_text(encoding="utf-8-sig"))
    except (ValueError, OSError) as error:
        warn(f"Couldn't read branding.json ({error}); using the name {DEFAULT_NAME!r}.", path)
        return DEFAULT_NAME
    name = " ".join(str(config.get("appName") or "").split())
    return name or DEFAULT_NAME


def write_info_plist(name):
    info = {
        "CFBundleDevelopmentRegion": "en",
        "CFBundleDisplayName": name,
        "CFBundleExecutable": "$(EXECUTABLE_NAME)",
        "CFBundleIdentifier": "$(PRODUCT_BUNDLE_IDENTIFIER)",
        "CFBundleInfoDictionaryVersion": "6.0",
        "CFBundleName": "$(PRODUCT_NAME)",
        "CFBundlePackageType": "$(PRODUCT_BUNDLE_PACKAGE_TYPE)",
        "CFBundleShortVersionString": "$(MARKETING_VERSION)",
        "CFBundleVersion": "$(CURRENT_PROJECT_VERSION)",
        "CFBundleURLTypes": [{"CFBundleURLName": "ordernotifier", "CFBundleURLSchemes": ["ordernotifier"]}],
        "LSRequiresIPhoneOS": True,
        "ITSAppUsesNonExemptEncryption": False,
        # "audio" lets Always On keep running; "fetch" lets iOS wake the app to top up the queue.
        "UIBackgroundModes": ["audio", "fetch"],
        "BGTaskSchedulerPermittedIdentifiers": [REFRESH_TASK],
        "UIApplicationSceneManifest": {"UIApplicationSupportsMultipleScenes": False},
        "UIApplicationSupportsIndirectInputEvents": True,
        "UILaunchScreen": {},
        "UIRequiredDeviceCapabilities": ["arm64"],
        "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait"],
    }
    INFO_PLIST.parent.mkdir(parents=True, exist_ok=True)
    with INFO_PLIST.open("wb") as file:
        plistlib.dump(info, file)


def main():
    for pattern in ("AppIcon*.appiconset", "BrandIcon.imageset", "IconPreview-*.imageset"):
        for old in ASSETS.glob(pattern):
            shutil.rmtree(old)

    icon = None
    candidates = [p for p in pictures_in(BRANDING) if p.stem.lower() == "appicon"]
    for path in candidates:
        try:
            icon = square(path, 1024)
            print(f"App icon: {path.name}")
            break
        except Exception as error:  # a broken or unsupported picture shouldn't stop the build
            warn(f"Couldn't use {path.name} as the app icon ({error}).", path)
    if icon is None:
        print("App icon: blank grey slate")
        icon = Image.new("RGB", (1024, 1024), BLANK)
    add_app_icon("AppIcon", icon)
    add_preview("BrandIcon", icon)
    options = [{"iconName": None, "title": "Default", "preview": "BrandIcon"}]

    used = set()
    for path in pictures_in(BRANDING / "Icons"):
        key = "".join(w[:1].upper() + w[1:] for w in words(path.stem)) or "Icon"
        base, n = key, 2
        while key in used:
            key, n = f"{base}{n}", n + 1
        try:
            image = square(path, 1024)
        except Exception as error:
            warn(f"Skipped {path.name} ({error}).", path)
            continue
        used.add(key)
        add_app_icon(f"AppIcon-{key}", image)
        add_preview(f"IconPreview-{key}", image)
        options.append({"iconName": f"AppIcon-{key}", "title": title_for(path.stem), "preview": f"IconPreview-{key}"})
        print(f"Extra icon: {path.name} -> {title_for(path.stem)}")

    write_json(RESOURCES / "Icons.json", options)
    name = app_name()
    write_info_plist(name)
    print(f"App name: {name}")


if __name__ == "__main__":
    main()
