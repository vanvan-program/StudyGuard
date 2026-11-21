<div align="center">

[简体中文](README.md) | 繁體中文 | [English](README.en.md)

# StudyGuard

**面向自主學習的離線 Android 學習監督、專注計時與任務計畫應用程式**

[![Release](https://img.shields.io/github/v/release/vanvan-program/StudyGuard?include_prereleases&style=flat-square)](https://github.com/vanvan-program/StudyGuard/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square)](#安裝與更新)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10%20%7C%20Compose-7F52FF?style=flat-square)](#技術棧與從原始碼建置)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=flat-square)](LICENSE)

[下載安裝包（v0.3.5-dev）](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.5-dev) · [使用與安裝指南](docs/studyguard/使用与安装指南.md) · [更新日誌](CHANGELOG.md) · [驗證報告](docs/studyguard/VALIDATION-0.3.5-dev.md) · [隱私權政策](PRIVACY.md) · [問題回報](https://github.com/vanvan-program/StudyGuard/issues)

</div>

---

## 專案定位

StudyGuard 將**學習任務計畫、專注計時、應用程式使用提醒與學習統計**整合於一款原生 Android 應用程式中。支援**紙本學習**與**手機學習**雙模式，資料預設儲存於本機，**無帳號、無廣告、不申請網路權限**。

> [!NOTE]
> 目前版本為 **0.3.5-dev**（支援 Android 8.0 及以上），屬於開發預覽版：計畫草案與每日總結採用本機規則，尚未接入線上大型語言模型；任務關聯修復已於 Android 16 官方模擬器完成驗證，各廠商實機與長期背景耗電表現仍待驗證。

## 核心功能

- **雙模式學習計時**：支援手動開始、暫停與恢復。**紙本模式**允許鎖定螢幕持續計時；**手機模式**僅累計本應用程式及自選學習白名單應用程式內的有效時間。
- **計畫與任務管理**：支援建立、編輯、改期與完成學習任務；可依本機規則產生未來最多 7 天的計畫草案，並支援透過日曆選取日期。
- **任務與計時連動**：可直接從任務啟動專注計時，達成目標時長自動完成任務；提前結束時亦可選擇同步標記完成；程序遭系統中斷後仍保留任務關聯。
- **減少分心提醒**：自訂受限娛樂應用程式與學習白名單。基礎模式透過系統使用情況監測提醒，亦可自願開啟無障礙增強；始終放行通訊、桌面、系統設定與解除安裝畫面。
- **學習進度與統計**：依有效學習時間觸發進度檢查，提供科目時長、近 7/30 日趨勢、打卡紀錄與本機每日總結。
- **JSON 本機備份與還原**：於設定中將完整學習資料匯出為 JSON，還原前自動檢查格式、關聯完整性與校驗碼，確認後覆寫還原。
- **隨時自由退出**：提前結束無須填寫原因，緊急解除無須密碼或倒數等待。

> 計時以可確認的觀察區間為準。程序中斷、長觀測缺口與明顯時鐘跳變不會補計未知時間；一般應用程式的限制能力亦受 Android 系統機制約束。

## 安裝與更新

1. 前往 [GitHub Releases](https://github.com/vanvan-program/StudyGuard/releases) 下載 `StudyGuard-0.3.5-dev-release.apk`（`debug` 與 `androidTest` 封裝僅供開發測試，日常試用請選取 `release`）。
2. 於手機檔案管理員開啟 APK，依系統提示授權並安裝。
3. 應用程式套件名稱為 `io.studyguard.app`，**不會覆蓋上游 FocusForcePlus**。本版沿用 StudyGuard 開發簽章，可直接覆蓋同簽章舊開發版；憑證與安裝包雜湊值請見驗證報告與 Release 中的 `SHA256SUMS.txt`。自行建置的版本簽章不同，更新前請先匯出備份。

## 權限與隱私邊界

| 權限 | 用途 | 是否必需 |
| :--- | :--- | :--- |
| 使用情況存取 | 識別前景應用程式並計算手機模式有效學習時間 | 依需求開啟 |
| 通知權限 | 顯示計時運作狀態與分心提醒 | 依需求開啟 |
| 無障礙服務 | 僅讀取視窗套件名稱以強化提醒，**不讀取文字、輸入或頁面內容** | 選用 |
| 精確鬧鐘 / 勿擾存取 | 保留原版工具的選用提醒輔助功能 | 選用 |

- 應用程式 **未宣告 `INTERNET` 權限**，不接入雲端服務或第三方分析 SDK，並排除系統雲端備份與裝置移轉。
- 使用者主動匯出的 JSON 備份檔**未加密**；若儲存至雲端硬碟目錄，可能由該文件提供者同步。解除安裝會刪除應用程式內部資料，詳見 [隱私權政策](PRIVACY.md)。

## 技術棧與從原始碼建置

- **技術棧**：Kotlin `2.2.10` · Jetpack Compose · Material 3 · Room · Gradle `9.3.1` · AGP `9.1.1`
- **環境需求**：JDK `21` · Android SDK Platform `36.1`（`minSdk 26` / `targetSdk 36`）

```sh
git clone https://github.com/vanvan-program/StudyGuard.git
cd StudyGuard
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

- Windows 可使用 `gradlew.bat` 或 `scripts/Build-StudyGuard.ps1`；如遇中文路徑編碼問題請見 [BUILD-ENCODING.md](docs/studyguard/BUILD-ENCODING.md)。
- Release 建置啟用 R8 與資源縮減；自行簽章可透過環境變數設定 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（儲存庫不含私鑰）。

### 目前驗證摘要（0.3.5-dev）

| 驗證項目 | 結果 |
| :--- | :--- |
| JVM 單元測試 | 182 項通過（0 失敗 / 0 略過） |
| Android 16 `StudyPersistenceTest` | 17 項通過 |
| Release 實測流程 | 提前結束完成任務、強制停止後恢復關聯、達標自動完成皆通過 |
| 資料庫遷移 | Room 10→12、11→12 及 0.3.4→0.3.5 資料保留驗證通過 |
| Lint Debug | 0 errors / 31 warnings / 10 hints |

完整驗證證據與已知待處理事項請見 [VALIDATION-0.3.5-dev.md](docs/studyguard/VALIDATION-0.3.5-dev.md)。

## 來源與開源授權

- 本專案基於 [karOS555/FocusForcePlus](https://github.com/karOS555/FocusForcePlus)（鎖定上游提交 [`f2987bc`](https://github.com/karOS555/FocusForcePlus/commit/f2987bc61d98bf8db42304929ed38bcba8090838)）二次開發，保留上游版權及 **[GNU GPLv3](LICENSE)** 授權條款。
- 相關文件：[上游原始說明](UPSTREAM_README.md) · [StudyGuard 修改紀錄](CHANGES_STUDYGUARD.md) · [第三方元件與授權聲明](THIRD_PARTY_NOTICES.md) · [文件目錄](docs/README.md)。
