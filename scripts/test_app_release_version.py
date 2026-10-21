"""Checks for app release identity and immutable publication; no Android rebuild required."""
from pathlib import Path
import hashlib
import json
import tempfile
import unittest
from app_release_version import app_version, archive_release, check_next_version, immutable_copy


class AppReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "android/app").mkdir(parents=True)
        (self.root / "artifacts").mkdir()
        (self.root / "SmartRoomController_Complete_Source/SmartRoomController").mkdir(parents=True)
        (self.root / "SmartRoomController_Complete_Source/SmartRoomController/Config.h").write_text('constexpr char version[]="1.1.4-music";')
        self.version("0.3.0", 5, 7)

    def version(self, name, code, delivery):
        (self.root / "android/app/build.gradle.kts").write_text(f'versionName = "{name}"\nversionCode = {code}\n')
        (self.root / "VERSION.md").write_text(f"# SmartRoom Controller — Version {delivery} (V{delivery})\n", encoding="utf-8")

    def report(self):
        apk = self.root / "artifacts/RoomController-final.apk"
        source = self.root / "artifacts/RoomController-final-source.zip"
        apk.write_bytes(b"test APK bytes, not an installable binary")
        source.write_bytes(b"test source archive bytes")
        data = {"apk": apk.name, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "source_archive": source.name, "source_archive_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "created_utc": "20261021T114733Z", "test_count": 27, "lint": {"Warning": 27}}
        (self.root / "artifacts/RoomController-final-verification.json").write_text(json.dumps(data))
        return data

    def test_archives_exact_bytes_and_repeated_registration_is_idempotent(self):
        report = self.report()
        first = archive_release(self.root, report)
        ledger_before = (self.root / "versions.md").read_bytes()
        self.assertEqual(first, archive_release(self.root, report))
        self.assertEqual(ledger_before, (self.root / "versions.md").read_bytes())
        self.assertEqual(report["apk_sha256"], hashlib.sha256((self.root / first["artifacts"]["apk"]).read_bytes()).hexdigest())
        self.assertIn("V7-android-0.3.0-code5", first["artifacts"]["apk"])

    def test_released_code_is_rejected_before_rebuild(self):
        archive_release(self.root, self.report())
        with self.assertRaisesRegex(ValueError, "already released or older"):
            check_next_version(self.root)

    def test_next_app_and_delivery_versions_are_accepted(self):
        archive_release(self.root, self.report())
        self.version("0.3.1", 6, 8)
        self.assertEqual(("0.3.1", 6), check_next_version(self.root))

    def test_app_code_advance_alone_does_not_reuse_project_delivery(self):
        archive_release(self.root, self.report())
        self.version("0.3.1", 6, 7)
        with self.assertRaisesRegex(ValueError, "next project delivery"):
            check_next_version(self.root)

    def test_existing_release_cannot_be_overwritten_with_different_bytes(self):
        old = self.root / "old.apk"; replacement = self.root / "replacement.apk"
        old.write_bytes(b"original"); replacement.write_bytes(b"different")
        with self.assertRaisesRegex(ValueError, "Refusing to replace"):
            immutable_copy(replacement, old)
        self.assertEqual(b"original", old.read_bytes())

    def test_false_apk_hash_does_not_publish_any_release(self):
        report = self.report(); report["apk_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "does not match"):
            archive_release(self.root, report)
        self.assertFalse((self.root / "artifacts/android-release-history.json").exists())

    def test_version_is_read_from_gradle_not_from_filename(self):
        self.assertEqual(("0.3.0", 5), app_version(self.root))
        self.version("unversioned-final", 5, 7)
        with self.assertRaisesRegex(ValueError, "semantic"):
            app_version(self.root)

    def test_standalone_app_root_is_supported(self):
        (self.root / "app").mkdir()
        (self.root / "app/build.gradle.kts").write_text('versionName = "0.3.1"\nversionCode = 6\n')
        self.assertEqual(("0.3.1", 6), app_version(self.root))

    def test_build_inputs_exclude_knowledge_apks_and_git(self):
        import importlib.util
        specification = importlib.util.spec_from_file_location("room_build_helper_test", Path(__file__).with_name("build-final-apk.py"))
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)
        module.ROOT = self.root
        module.ANDROID = self.root
        (self.root / "app").mkdir()
        (self.root / "app/build.gradle.kts").write_text('versionName = "0.3.0"\nversionCode = 5\n')
        (self.root / "settings.gradle.kts").write_text('include(":app")')
        before = module.inputs()
        for path in ("knowledge/doc.md", "apks/old.apk", ".git/config", "local.properties", "app/build/generated.txt"):
            target = self.root / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_text("not a compiler input")
        self.assertEqual(before, module.inputs())


if __name__ == "__main__":
    unittest.main()
