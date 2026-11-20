import json
import xml.etree.ElementTree as ET
from pathlib import Path


root = Path(__file__).resolve().parents[1]
report_paths = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
if not report_paths:
    raise RuntimeError("No standard Gradle test XML reports found")

suites = []
for report_path in report_paths:
    suite = ET.parse(report_path).getroot()
    suites.append({
        "name": suite.attrib["name"],
        "timestamp": suite.attrib.get("timestamp"),
        **{field: int(suite.attrib[field]) for field in ("tests", "failures", "errors", "skipped")},
        "seconds": float(suite.attrib["time"]),
    })

totals = {field: sum(suite[field] for suite in suites)
          for field in ("tests", "failures", "errors", "skipped")}
if totals["tests"] == 0:
    raise RuntimeError("Standard Gradle reports contain no tests")
summary = {
    "task": ":app:testDebugUnitTest",
    "scope": "Host JVM tests; no Android device execution",
    **totals,
    "suites": suites,
}
output = root / "build/studyguard-evidence/standard-tests.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(totals))
if totals["failures"] or totals["errors"]:
    raise SystemExit(1)
