param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'adb.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
if (-not (Test-Path -LiteralPath $apk) -or -not (Test-Path -LiteralPath $testApk)) {
    throw '请先构建 :app:assembleDebug :app:assembleDebugAndroidTest'
}
$output = Join-Path $projectRoot 'build\device-evidence'
New-Item -ItemType Directory -Path $output -Force | Out-Null
& $Adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK 安装失败' }
& $Adb -s $Serial install -r $testApk
if ($LASTEXITCODE -ne 0) { throw '测试 APK 安装失败' }
$result = & $Adb -s $Serial shell am instrument -w -r 'io.studyguard.app.test/androidx.test.runner.AndroidJUnitRunner'
$result | Set-Content -LiteralPath (Join-Path $output "$Serial-instrumentation.txt") -Encoding utf8
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=') {
    throw '设备测试失败，请查看保存的原始输出'
}
if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)') {
    throw '未找到明确的设备测试成功结果，不能判定通过'
}
