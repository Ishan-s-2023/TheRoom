"""Restore the ignored Room identity from the user's verified personal APK; print no secrets."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

EXPECTED = "917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4"


def restore(apk, root):
    if hashlib.sha256(apk.read_bytes()).hexdigest() != EXPECTED:
        raise ValueError("Choose the verified 0.3.0/code 5 personal APK; its SHA-256 does not match")
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo("assets/room-device.config")
        if entry.file_size > 8192:
            raise ValueError("Unexpected configuration size")
        data = archive.read(entry)
    config = json.loads(data)
    if not all(config.get(key) for key in ("ssid", "ap_password", "token", "hostname", "setup_ip", "certificate_sha256")):
        raise ValueError("Incomplete controller configuration")
    destination = root / "app/src/main/assets/room-device.config"
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        if json.loads(destination.read_bytes()) != config:
            raise ValueError("A different local identity exists; it has not been overwritten")
        return "Matching private configuration already exists; no changes made."
    with destination.open("xb") as output:
        output.write(data)
    return "Matching controller configuration restored to the ignored local asset."


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    print(restore(args.apk, Path(__file__).resolve().parents[1]))
