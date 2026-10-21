"""Copy main workspace knowledge into this standalone app checkout, with hashes."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil


def sync(workspace, repository):
    workspace = workspace.resolve(); repository = repository.resolve()
    if workspace == repository or not (workspace / "ESP32_APP_SYNC_CONTEXT.md").is_file():
        raise ValueError("Choose the original combined Room workspace")
    groups = [(workspace, "project"), (workspace / "docs", "docs"),
              (workspace / "SmartRoomController_Complete_Source/SmartRoomController", "firmware"),
              (workspace / "artifacts", "artifacts")]
    copied = []
    for directory, label in groups:
        for source in sorted(directory.glob("*.md")):
            destination = repository / "knowledge" / label / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            expected = hashlib.sha256(source.read_bytes()).hexdigest()
            if hashlib.sha256(destination.read_bytes()).hexdigest() != expected:
                raise ValueError("Knowledge copy verification failed: " + source.name)
            copied.append({"source": source.relative_to(workspace).as_posix(),
                           "copy": destination.relative_to(repository).as_posix(), "sha256": expected})
    index = repository / "knowledge/README.md"
    lines = ["# TheRoom knowledge files", "", "Copied reference documents from the combined Room workspace. Original document bytes are preserved.",
             "The source paths in the manifest identify their original context; these copies do not include the live firmware checkout.", "",
             "Main app/ESP32 handoff: [ESP32_APP_SYNC_CONTEXT.md](project/ESP32_APP_SYNC_CONTEXT.md).", "",
             "| Source document | Repository copy |", "| --- | --- |"]
    for entry in copied:
        lines.append(f"| `{entry['source']}` | [{Path(entry['copy']).name}]({entry['copy'].removeprefix('knowledge/')}) |")
    index.write_text("\n".join(lines) + "\n", encoding="utf-8")
    (repository / "knowledge/manifest.json").write_text(json.dumps({"project_delivery": "V8", "files": copied}, indent=2) + "\n", encoding="utf-8")
    return len(copied)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, required=True)
    args = parser.parse_args()
    print(f"Copied and verified {sync(args.workspace, Path(__file__).resolve().parents[1])} knowledge documents.")
