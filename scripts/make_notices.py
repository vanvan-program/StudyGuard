import argparse
import csv
import hashlib
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument("--gradle-home", type=Path, required=True)
parser.add_argument("--license-bundle", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
components = root / "build/studyguard-evidence/runtime-components.csv"
cache = args.gradle_home / "caches/modules-2/files-2.1"
namespace = {"pom": "http://maven.apache.org/POM/4.0.0"}


def pom_for(group, artifact, version):
    preserved = root / "third_party/poms" / group / artifact / version / f"{artifact}-{version}.pom"
    if preserved.is_file():
        return preserved
    return next((cache / group / artifact / version).glob(f"*/{artifact}-{version}.pom"), None)


def licenses_for(group, artifact, version, seen=None):
    seen = set() if seen is None else seen
    identity = (group, artifact, version)
    if identity in seen:
        return [], ""
    seen.add(identity)
    pom = pom_for(*identity)
    if pom is None:
        return [], ""
    tree = ET.parse(pom).getroot()
    licenses = []
    for license_node in tree.findall("pom:licenses/pom:license", namespace):
        name = license_node.findtext("pom:name", default="", namespaces=namespace)
        url = license_node.findtext("pom:url", default="", namespaces=namespace)
        licenses.append((name, url))
    if not licenses:
        parent = tree.find("pom:parent", namespace)
        if parent is not None:
            inherited = tuple(parent.findtext("pom:" + field, default="", namespaces=namespace)
                              for field in ("groupId", "artifactId", "version"))
            licenses, _ = licenses_for(*inherited, seen)
    return licenses, str(pom)


rows = []
with components.open(encoding="utf-8-sig", newline="") as source:
    for component in csv.DictReader(source):
        identity = (component["group"], component["artifact"], component["version"])
        licenses, pom = licenses_for(*identity)
        evidence = "POM (including parent inheritance)" if licenses else "UNVERIFIED"
        if identity == ("javax.inject", "javax.inject", "1") and not licenses:
            sources = root / "third_party/javax.inject-1-sources.jar"
            if sources.exists() and hashlib.sha256(sources.read_bytes()).hexdigest() == "c4b87ee2911c139c3daf498a781967f1eb2e75bc1a8529a2e7b328a15d0e433e":
                with zipfile.ZipFile(sources) as archive:
                    header = archive.read("javax/inject/Inject.java").decode("utf-8")
                if "Licensed under the Apache License, Version 2.0" in header:
                    licenses = [("Apache-2.0 (exact version source header)", "https://repo.maven.apache.org/maven2/javax/inject/javax.inject/1/javax.inject-1-sources.jar")]
                    evidence = "Exact version sources.jar header; SHA-256 pinned"
        rows.append({
            **component,
            "licenses": "; ".join(name for name, _ in licenses) or "UNVERIFIED",
            "license_urls": "; ".join(url for _, url in licenses),
            "pom": pom,
            "evidence": evidence,
        })
with (root / "build/studyguard-evidence/runtime-licenses.csv").open("w", encoding="utf-8-sig", newline="") as target:
    writer = csv.DictWriter(target, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
lines = [
    "# StudyGuard 第三方归属与许可证证据", "",
    "基于 FocusForcePlus，固定上游提交 f2987bc61d98bf8db42304929ed38bcba8090838。原始 LICENSE（GPLv3）与 UPSTREAM_README.md 随工程保留。",
    "StudyGuard 的改动日期与范围见 CHANGES_STUDYGUARD.md。本表来自最终 debugRuntimeClasspath、精确版本POM和补充的精确版本源码头；包含平台/BOM元数据，不是逐文件APK素材审计。", "",
    "AndroidX/Compose/Room/WorkManager 等保留相应作者版权。Kotlin/Coroutines 由 JetBrains 及贡献者提供；Dagger/Hilt 由其作者与贡献者提供。具体归属以各组件源文件与许可为准。",
    "上游 docs 中附带的 Lucide 图标及独立 ISC 许可证保持原样；新增启动图标为本工程绘制的几何图标，未引入字体、图片、音频或网络 SDK。", "",
    "POM 许可证证据不是完整 NOTICE 正文的替代；如继续分发，请同时核对各 JAR/AAR 的 META-INF/LICENSE、NOTICE 与源文件版权。未解析到的项目明确列为 UNVERIFIED。", "",
    "精确版本POM及父POM保存在third_party/poms，manifest.json记录坐标、官方仓库URL、SHA-256及取得方式；许可证生成优先读取这些副本，不依赖缓存持续存在。", "",
    "|组件|版本|许可证证据|",
    "|---|---|---|",
]
for row in rows:
    lines.append(f'|{row["group"]}:{row["artifact"]}|{row["version"]}|{row["licenses"]}|')
unverified = [row for row in rows if row["licenses"] == "UNVERIFIED"]
lines += ["", f"共 {len(rows)} 个解析组件；未取得许可证据 {len(unverified)} 个。", "",
          "javax.inject:1 的POM未声明许可，另从Maven Central取得精确版本源码包，核对Inject.java的Apache-2.0头。源码包及其原始版权头随third_party目录保留；原始SHA-256：C4B87EE2911C139C3DAF498A781967F1EB2E75BC1A8529A2E7B328A15D0E433E。", ""]
if args.license_bundle:
    bundle = args.license_bundle.read_text(encoding="utf-8")
    marker = "END OF TERMS AND CONDITIONS"
    assert "Apache License" in bundle and marker in bundle
    (root / "third_party/Apache-2.0.txt").write_text(bundle.split(marker)[0] + marker + "\n", encoding="utf-8")
    lines += ["Apache-2.0正文从本机构建所用Gradle9.3.1发行包LICENSE中的Apache条款部分提取，保存在third_party/Apache-2.0.txt；没有改写许可条款。", ""]
(root / "THIRD_PARTY_NOTICES.md").write_text("\n".join(lines), encoding="utf-8")
print(f"Resolved components: {len(rows)}; missing license evidence: {len(unverified)}")
