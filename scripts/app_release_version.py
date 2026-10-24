"""Record immutable Android releases and reject reused version codes before a build."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import re
import shutil


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def android_directory(root):
    return root if (root / "app/build.gradle.kts").is_file() else root / "android"


def app_version(root):
    text = (android_directory(root) / "app/build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code or int(code[1]) < 1:
        raise ValueError("Set versionName and a positive versionCode in android/app/build.gradle.kts")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", name[1]):
        raise ValueError("Use a semantic Android versionName, for example 0.3.1")
    return name[1], int(code[1])


def delivery_version(root):
    version_file = root / "DELIVERY_VERSION.md"
    if not version_file.is_file():
        version_file = root / "VERSION.md"
    text = version_file.read_text(encoding="utf-8-sig")
    version = re.search(r"^# .*\bVersion (\d+) \(V\d+\)", text, re.MULTILINE)
    if not version:
        raise ValueError(f"Record the current project delivery at the top of {version_file.name}")
    return int(version[1])


def history(root):
    path = root / "artifacts/android-release-history.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"schema": 1, "releases": []}


def check_next_version(root):
    name, code = app_version(root)
    releases = history(root)["releases"]
    if releases and code <= max(item["version_code"] for item in releases):
        raise ValueError(
            f"Android {name} / code {code} is already released or older. "
            "Increase versionCode before generating another APK; update versionName when app code changes. "
            "The existing final APK is preserved."
        )
    if releases and delivery_version(root) <= max(item["project_delivery"] for item in releases):
        raise ValueError("Assign the next project delivery in DELIVERY_VERSION.md (or VERSION.md) and update the sync handoff before a new app delivery")
    return name, code


def immutable_copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        if digest(source) != digest(destination):
            raise ValueError(f"Refusing to replace an existing release artifact: {destination.name}")
        return
    # Exclusive creation prevents a second publisher from overwriting this release.
    with source.open("rb") as reader, destination.open("xb") as writer:
        shutil.copyfileobj(reader, writer)
    if digest(source) != digest(destination):
        raise ValueError("Release copy verification failed: " + destination.name)


def archive_release(root, report):
    name, code = app_version(root)
    delivery = delivery_version(root)
    records = history(root)
    apk = root / "artifacts" / report["apk"]
    if digest(apk) != report["apk_sha256"]:
        raise ValueError("APK does not match its successful-build verification report")
    previous = next((item for item in records["releases"] if item["version_code"] == code), None)
    if previous:
        if previous["apk_sha256"] == report["apk_sha256"] and previous["version_name"] == name:
            return previous  # Re-recording the same bytes is harmless; it is not a new release.
        raise ValueError("This Android versionCode already identifies different APK bytes")
    if records["releases"] and code <= max(item["version_code"] for item in records["releases"]):
        raise ValueError("Release codes must increase monotonically")
    stem = f"RoomController-V{delivery}-android-{name}-code{code}"
    directory = root / "artifacts/android-releases" / f"{name}-code{code}"
    files = {
        "apk": (apk, directory / (stem + ".apk")),
        "source": (root / "artifacts" / report["source_archive"], directory / (stem + "-source.zip")),
        "verification": (root / "artifacts/RoomController-final-verification.json", directory / (stem + "-verification.json")),
    }
    if digest(files["source"][0]) != report["source_archive_sha256"]:
        raise ValueError("Source archive hash does not match the APK build report")
    for source, destination in files.values():
        immutable_copy(source, destination)
    config_path = root / "SmartRoomController_Complete_Source/SmartRoomController/Config.h"
    firmware_name = "unknown"
    if config_path.exists():
        firmware = re.search(r'version\[\]\s*=\s*"([^"]+)"', config_path.read_text(encoding="utf-8"))
        firmware_name = firmware[1] if firmware else "unknown"
    elif (root / "knowledge/firmware-baseline.json").exists():
        firmware_name = json.loads((root / "knowledge/firmware-baseline.json").read_text(encoding="utf-8"))["firmware_source"]
    entry = {
        "release_id": f"ROOM-ANDROID-{name}+{code}", "version_name": name, "version_code": code,
        "project_delivery": delivery, "apk_sha256": report["apk_sha256"],
        "original_build_snapshot_utc": report["created_utc"],
        "registered_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "firmware_source_at_registration": firmware_name,
        "firmware_installation": "not queried or changed by this app release registration",
        "test_count": report["test_count"], "lint": report["lint"],
        "artifacts": {key: dest.relative_to(root).as_posix() for key, (_, dest) in files.items()},
        "artifact_sha256": {key: digest(dest) for key, (_, dest) in files.items()},
    }
    manifest = directory / (stem + "-release.json")
    with manifest.open("x", encoding="utf-8") as stream:
        json.dump(entry, stream, indent=2); stream.write("\n")
    records["releases"].append(entry)
    path = root / "artifacts/android-release-history.json"
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)
    ledger = root / "CHANGELOG.md"
    if not ledger.exists():
        ledger = root / "versions.md"
    if not ledger.exists():
        ledger.write_text("# Project and Android release ledger\n", encoding="utf-8")
    with ledger.open("a", encoding="utf-8") as stream:
        stream.write(
            f"\n## Recorded APK: {entry['release_id']} — project delivery V{delivery}\n\n"
            f"- Android versionName: `{name}`; versionCode: `{code}`.\n"
            f"- Immutable APK: `{entry['artifacts']['apk']}`.\n"
            f"- SHA-256: `{entry['apk_sha256']}`.\n"
            f"- Frozen build snapshot: `{entry['original_build_snapshot_utc']}`; "
            f"tests: {entry['test_count']}; lint: {entry['lint']}.\n"
            f"- Firmware source when registered: `{entry['firmware_source_at_registration']}`. "
            "This does not assert installation or hardware compatibility.\n"
            f"- Source and build evidence: `{manifest.relative_to(root).as_posix()}`.\n"
        )
    return entry


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record-existing", action="store_true", help="Archive the already verified final APK without rebuilding it")
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    if args.record_existing:
        verification = json.loads((project / "artifacts/RoomController-final-verification.json").read_text(encoding="utf-8"))
        print(json.dumps(archive_release(project, verification), indent=2))
    else:
        print(json.dumps(dict(zip(("version_name", "version_code"), check_next_version(project)))))
