"""Build a frozen Android snapshot and publish only after checks and source verification."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from app_release_version import android_directory, app_version, archive_release, check_next_version, delivery_version

ROOT = Path(__file__).resolve().parents[1]
ANDROID = android_directory(ROOT)
ARTIFACTS = ROOT / "artifacts"
SKIP = {"build", ".gradle", ".kotlin"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs():
    return {
        p.relative_to(ANDROID).as_posix(): digest(p)
        for p in sorted(ANDROID.rglob("*"))
        if p.is_file()
        and (p.relative_to(ANDROID).parts[0] in {"app", "gradle"}
             or p.relative_to(ANDROID).as_posix() in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"})
        and not any(part.startswith(".gradle") or part in SKIP for part in p.relative_to(ANDROID).parts)
        and p.name != "local.properties"
    }


def main():
    check_next_version(ROOT)  # Fail before building or replacing any released APK.
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run = ARTIFACTS / "final-apk-verification" / stamp
    frozen = run / "android"
    before = inputs()
    for relative, expected in before.items():
        target = frozen / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ANDROID / relative, target)
        if digest(target) != expected:
            raise RuntimeError("Source changed during snapshot: " + relative)
    version_name, version_code = app_version(ROOT)
    manifest = {"created_utc": stamp, "android_source_sha256": before,
                "app_version": version_name, "version_code": version_code,
                "project_delivery": f"V{delivery_version(ROOT)}"}
    embedded = frozen / "app/src/main/assets/final-build-manifest.json"
    embedded.parent.mkdir(parents=True, exist_ok=True)
    embedded.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or
               next((str(p) for p in (ROOT / ".tools/android-sdk", ROOT.parent / ".tools/android-sdk") if p.is_dir()), "missing-android-sdk"))
    if not sdk.is_dir():
        raise RuntimeError("Set ANDROID_HOME to an installed Android SDK")
    (frozen / "local.properties").write_text("sdk.dir=" + sdk.as_posix() + "\n", encoding="utf-8")
    env = os.environ.copy()
    if not env.get("JAVA_HOME"):
        java_home = next((p for parent in (ROOT, ROOT.parent) for p in (parent / ".tools/jdk").glob("jdk-*")), None)
        if java_home is not None: env["JAVA_HOME"] = str(java_home)
    env["ANDROID_HOME"] = str(sdk)
    command = [str(frozen / "gradlew.bat"), "-p", str(frozen), "--no-daemon",
               "assembleDebug", "testDebugUnitTest", "lintDebug", "--console=plain"]
    print("Building frozen snapshot: " + str(run), flush=True)
    with (run / "build.log").open("w", encoding="utf-8") as log:
        result = subprocess.run(command, env=env, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError("Build/checks failed; see " + str(run / "build.log"))
    if inputs() != before:
        raise RuntimeError("Working source changed during build; rerun before publishing a final APK.")
    built = frozen / "app/build/outputs/apk/debug/app-debug.apk"
    with zipfile.ZipFile(built) as apk:
        if json.loads(apk.read("assets/final-build-manifest.json")) != manifest:
            raise RuntimeError("APK source manifest mismatch")
        if apk.testzip() is not None:
            raise RuntimeError("APK archive corrupt")
    tests = [ET.parse(p).getroot().attrib for p in sorted((frozen / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))]
    if not tests or any(int(t.get("failures", 0)) or int(t.get("errors", 0)) for t in tests):
        raise RuntimeError("Missing or failing test results")
    lint = ET.parse(frozen / "app/build/reports/lint-results-debug.xml").getroot()
    severities = {}
    for issue in lint.findall("issue"):
        level = issue.attrib["severity"]
        severities[level] = severities.get(level, 0) + 1
    if severities.get("Error", 0) or severities.get("Fatal", 0):
        raise RuntimeError("Lint errors")
    source_zip = ARTIFACTS / "RoomController-final-source.zip"
    with zipfile.ZipFile(source_zip, "w", zipfile.ZIP_DEFLATED) as bundle:
        for relative in before:
            bundle.write(frozen / relative, "android/" + relative)
        bundle.write(embedded, "android/app/src/main/assets/final-build-manifest.json")
    final = ARTIFACTS / "RoomController-final.apk"
    shutil.copy2(built, final)
    if digest(final) != digest(built):
        raise RuntimeError("Published APK mismatch")
    built.unlink()  # Keep one installable APK; retain build logs, reports and source.
    report = {
        **manifest, "apk": final.name, "apk_sha256": digest(final),
        "source_archive": source_zip.name, "source_archive_sha256": digest(source_zip),
        "tests": tests, "test_count": sum(int(t["tests"]) for t in tests),
        "lint": severities, "source_unchanged_after_build": True,
        "build_log": (run / "build.log").relative_to(ROOT).as_posix(),
        "limits": ["Covers completed changes present in the shared Android source at the recorded snapshot.",
                   "Other chats and unsaved or future edits cannot be certified by this build.",
                   "Physical ESP32 and live Spotify account acceptance are not verified by JVM/lint checks.",
                   "Firmware, Python backend and React reference files are separate deliverables, not APK contents."],
    }
    (ARTIFACTS / "RoomController-final-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    archive_release(ROOT, report)  # Keep a versioned APK/source/evidence copy and append versions.md.
    (ARTIFACTS / "RoomController-final.apk.sha256").write_text(digest(final) + "  " + final.name + "\n", encoding="ascii")
    checksum = ARTIFACTS / "checksums.sha256"
    entries = {}
    if checksum.exists():
        for line in checksum.read_text().splitlines():
            if "  " in line:
                value, name = line.split("  ", 1)
                entries[name] = value
    entries = {name: value for name, value in entries.items() if (ARTIFACTS / name).is_file()}
    for name in (final.name, source_zip.name, "RoomController-final-verification.json"):
        entries[name] = digest(ARTIFACTS / name)
    checksum.write_text("".join(f"{value}  {name}\n" for name, value in sorted(entries.items())), encoding="ascii")
    print(json.dumps({"apk": str(final), "sha256": digest(final), "tests": report["test_count"], "lint": severities, "run": str(run)}, indent=2), flush=True)


if __name__ == "__main__":
    main()
