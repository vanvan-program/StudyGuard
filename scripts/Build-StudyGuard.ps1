param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'),
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = Join-Path (Split-Path (Split-Path $projectRoot -Parent) -Parent) '.tools'
if (-not $env:JAVA_HOME) {
    $jdkCandidates = @('C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot', 'C:\Program Files\Android\Android Studio\jbr')
    foreach ($candidate in $jdkCandidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}
if (-not $env:ANDROID_HOME -and (Test-Path -LiteralPath (Join-Path $toolsRoot 'android-sdk'))) {
    $env:ANDROID_HOME = Join-Path $toolsRoot 'android-sdk'
}
if (-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath (Join-Path $toolsRoot 'gradle-home'))) {
    $env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-home'
}
if (-not $env:ANDROID_USER_HOME -and (Test-Path -LiteralPath (Join-Path $toolsRoot 'gradle-home\android-home'))) {
    $env:ANDROID_USER_HOME = Join-Path $toolsRoot 'gradle-home\android-home'
}
$localGradle = Join-Path $toolsRoot 'gradle-9.3.1\bin\gradle.bat'
$launcher = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { Join-Path $projectRoot 'gradlew.bat' }
$arguments = @('-p', $projectRoot) + $Tasks + @('--no-daemon', '--console=plain', '--max-workers=2')
if ($Offline) { $arguments += '--offline' }
& $launcher @arguments
exit $LASTEXITCODE
