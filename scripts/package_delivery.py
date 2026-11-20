"""Package built APKs and the clean committed source, without local QA folders."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path


root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, default=root.parent / "deliverables")
args = parser.parse_args()
delivery = args.output.resolve()
if delivery == root or root in delivery.parents:
    raise ValueError("Delivery output must be outside the source checkout")


def git(*arguments):
    return subprocess.check_output(["git", "-C", str(root), *arguments])


if git("status", "--porcelain").strip():
    raise RuntimeError("Commit reviewed source changes before packaging")
commit = git("rev-parse", "HEAD").decode().strip()
version_match = re.search(
    r'versionName\s*=\s*"([^"]+)"',
    (root / "app/build.gradle.kts").read_text(encoding="utf-8"),
)
if version_match is None:
    raise ValueError("Cannot read app version")
version = version_match.group(1)
delivery.mkdir(parents=True, exist_ok=True)
artifacts = {
    f"StudyGuard-{version}-release.apk": root / "app/build/outputs/apk/release/app-release.apk",
    f"StudyGuard-{version}-debug.apk": root / "app/build/outputs/apk/debug/app-debug.apk",
    f"StudyGuard-{version}-debug-androidTest.apk": root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
}
for name, source in artifacts.items():
    if not source.is_file():
        raise FileNotFoundError(source)
    shutil.copy2(source, delivery / name)

archive_path = delivery / f"StudyGuard-{version}-source.zip"
git("archive", "--format=zip", "--output", str(archive_path), commit)
manifest_lines = []
with zipfile.ZipFile(archive_path) as archive:
    if archive.testzip() is not None:
        raise RuntimeError("Source archive CRC verification failed")
    for name in sorted(archive.namelist()):
        if name.endswith("/"):
            continue
        digest = hashlib.sha256(archive.read(name)).hexdigest()
        manifest_lines.append(f"{digest}  {name}")
(delivery / "SOURCE_MANIFEST.sha256").write_text(
    "\n".join(manifest_lines) + "\n", encoding="utf-8"
)
artifacts[archive_path.name] = archive_path
metadata = []
for name in artifacts:
    path = delivery / name
    metadata.append({
        "name": name,
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    })
(delivery / "SHA256SUMS.txt").write_text(
    "".join(f"{item['sha256']}  {item['name']}\n" for item in metadata),
    encoding="utf-8",
)
result = {
    "version": version,
    "sourceCommit": commit,
    "upstreamCommit": "f2987bc61d98bf8db42304929ed38bcba8090838",
    "sourceFileCount": len(manifest_lines),
    "artifacts": metadata,
}
(delivery / "artifacts.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(result, ensure_ascii=False, indent=2))
