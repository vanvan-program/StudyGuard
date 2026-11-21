<div align="center">

[简体中文](README.md) | [繁體中文](README.zh-TW.md) | English

# StudyGuard

**An offline-first Android study timer, task planner, and distraction guard for self-directed learning.**

[![Release](https://img.shields.io/github/v/release/vanvan-program/StudyGuard?include_prereleases&style=flat-square)](https://github.com/vanvan-program/StudyGuard/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square)](#installation--updates)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10%20%7C%20Compose-7F52FF?style=flat-square)](#tech-stack--building-from-source)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=flat-square)](LICENSE)

[Download APK (v0.3.5-dev)](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.5-dev) · [User Guide (ZH)](docs/studyguard/使用与安装指南.md) · [Changelog](CHANGELOG.md) · [Validation Report](docs/studyguard/VALIDATION-0.3.5-dev.md) · [Privacy Policy](PRIVACY.md) · [Issues](https://github.com/vanvan-program/StudyGuard/issues)

</div>

---

## Overview

StudyGuard brings **study task planning, focus timing, app-distraction reminders, and study analytics** together in a native Android app. It supports both **Paper Study** and **Phone Study** modes, stores all data locally by default, and requires **no account, no ads, and no `INTERNET` permission**.

> [!NOTE]
> Current version **0.3.5-dev** (Android 8.0+) is a developer preview: study plan drafts and daily summaries use local heuristics rather than online LLMs. The latest task-linking fixes are verified on the official Android 16 emulator; OEM physical devices and long-term background battery behavior remain to be validated.

## Key Features

- **Dual-Mode Study Timer**: Manual start, pause, and resume. **Paper Mode** continues counting while the screen is locked; **Phone Mode** counts only time spent in StudyGuard and your whitelisted study apps.
- **Task & Plan Management**: Create, edit, reschedule, and complete study tasks; generate local rule-based plan drafts up to 7 days ahead with calendar date picking.
- **Task-Timer Linking**: Start a study session directly from a task to auto-complete it upon reaching the target duration, optionally mark complete on early finish, and preserve task links across process death.
- **Distraction Reminders**: Configure restricted entertainment apps and a study whitelist. Basic mode monitors via Usage Stats and reminds you; optional Accessibility enhancement is available. Always allows phone/messaging, launcher, system settings, and uninstallation.
- **Progress & Analytics**: Track subject durations, 7/30-day trends, check-in streaks, and local daily summaries.
- **Local JSON Backup & Restore**: Export study data to JSON and restore with schema, referential integrity, and checksum validation.
- **No Lock-In Exit**: End sessions early without writing excuses, and trigger emergency release without passwords or wait timers.

> Timing only counts verifiable observation intervals. Process interruptions, long observation gaps, and obvious clock jumps are never backfilled. Standard Android app-restriction capabilities are subject to OS limitations.

## Installation & Updates

1. Download `StudyGuard-0.3.5-dev-release.apk` from [GitHub Releases](https://github.com/vanvan-program/StudyGuard/releases) (`debug` and `androidTest` APKs are for development testing).
2. Open the APK in your file manager and follow the system prompt to install.
3. Package name is `io.studyguard.app` (will not overwrite upstream FocusForcePlus). Signed with the StudyGuard dev certificate for in-place upgrades over matching dev builds; see `SHA256SUMS.txt` in Releases. Self-built APKs use a different signature—export a JSON backup before switching.

## Permissions & Privacy

| Permission | Purpose | Requirement |
| :--- | :--- | :--- |
| Usage Access | Detect foreground app and track valid Phone Mode study time | On-demand |
| Notifications | Show active timer status and distraction reminders | On-demand |
| Accessibility Service | Reads window package names only for stronger reminders; **never reads text, input, or screen content** | Optional |
| Exact Alarms / DND | Optional reminder utilities retained from upstream | Optional |

- Declares **no `INTERNET` permission**, uses no cloud or analytics SDKs, and opts out of Android cloud backup and device transfer.
- User-exported JSON backups are **unencrypted**; uninstalling deletes internal app data. See [PRIVACY.md](PRIVACY.md) for details.

## Tech Stack & Building from Source

- **Stack**: Kotlin `2.2.10` · Jetpack Compose · Material 3 · Room · Gradle `9.3.1` · AGP `9.1.1`
- **Requirements**: JDK `21` · Android SDK Platform `36.1` (`minSdk 26` / `targetSdk 36`)

```sh
git clone https://github.com/vanvan-program/StudyGuard.git
cd StudyGuard
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

- On Windows, use `gradlew.bat` or `scripts/Build-StudyGuard.ps1`. See [BUILD-ENCODING.md](docs/studyguard/BUILD-ENCODING.md) for non-ASCII path notes.
- Release builds enable R8 and resource shrinking. Provide `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` for custom signing (no private keys are stored in the repository).

### Current Verification Summary (0.3.5-dev)

| Check | Result |
| :--- | :--- |
| JVM Unit Tests | 182 passed (0 failed / 0 skipped) |
| Android 16 `StudyPersistenceTest` | 17 passed |
| Release E2E Flows | Early-finish completion, force-stop link recovery, target auto-complete passed |
| Database Migration | Room 10→12, 11→12, and 0.3.4→0.3.5 data retention verified |
| Lint Debug | 0 errors / 31 warnings / 10 hints |

See [VALIDATION-0.3.5-dev.md](docs/studyguard/VALIDATION-0.3.5-dev.md) for full evidence and known caveats.

## Attribution & License

- Derived from [karOS555/FocusForcePlus](https://github.com/karOS555/FocusForcePlus) (pinned upstream commit [`f2987bc`](https://github.com/karOS555/FocusForcePlus/commit/f2987bc61d98bf8db42304929ed38bcba8090838)), retaining upstream copyright under **[GNU GPLv3](LICENSE)**.
- Related docs: [UPSTREAM_README.md](UPSTREAM_README.md) · [CHANGES_STUDYGUARD.md](CHANGES_STUDYGUARD.md) · [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) · [docs/README.md](docs/README.md).
