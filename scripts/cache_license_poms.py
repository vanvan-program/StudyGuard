import argparse
import concurrent.futures
import csv
import hashlib
import json
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument("--gradle-home", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
vendor = root / "third_party/poms"
cache = args.gradle_home / "caches/modules-2/files-2.1"
namespace = {"pom": "http://maven.apache.org/POM/4.0.0"}


def obtain(identity):
    group, artifact, version = identity
    relative = Path(group) / artifact / version / f"{artifact}-{version}.pom"
    target = vendor / relative
    base = ("https://dl.google.com/dl/android/maven2"
            if group.startswith(("androidx.", "com.android."))
            else "https://repo.maven.apache.org/maven2")
    url = f"{base}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    cached = next((cache / group / artifact / version).glob(f"*/{artifact}-{version}.pom"), None)
    if target.is_file():
        content = target.read_bytes()
        source = "preserved"
    elif cached is not None:
        content = cached.read_bytes()
        source = "Gradle cache"
    else:
        with urllib.request.urlopen(url, timeout=30) as response:
            content = response.read()
        source = "official repository"
    project = ET.fromstring(content)
    if project.tag.split("}")[-1] != "project":
        raise ValueError(f"Not a Maven POM: {identity}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
    parent = project.find("pom:parent", namespace)
    parent_identity = None
    if parent is not None:
        parent_identity = tuple(parent.findtext(f"pom:{field}", default="", namespaces=namespace)
                                for field in ("groupId", "artifactId", "version"))
        if not all(parent_identity) or any("${" in field for field in parent_identity):
            raise ValueError(f"Unresolved parent coordinates: {identity}")
    return {
        "component": ":".join(identity),
        "file": relative.as_posix(),
        "sha256": hashlib.sha256(content).hexdigest(),
        "repositoryUrl": url,
        "source": source,
    }, parent_identity


with (root / "build/studyguard-evidence/runtime-components.csv").open(
        encoding="utf-8-sig", newline="") as component_file:
    pending = {(row["group"], row["artifact"], row["version"]) for row in csv.DictReader(component_file)}
seen = set()
records = []
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    while pending:
        current = sorted(pending - seen)
        seen.update(current)
        pending = set()
        for record, parent in pool.map(obtain, current):
            records.append(record)
            if parent is not None and parent not in seen:
                pending.add(parent)
        print(f"Preserved {len(records)} exact POM files", flush=True)
vendor.mkdir(parents=True, exist_ok=True)
(vendor / "manifest.json").write_text(
    json.dumps(sorted(records, key=lambda record: record["component"]), ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
