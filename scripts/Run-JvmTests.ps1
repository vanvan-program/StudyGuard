$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$classpathFile = Join-Path $projectRoot 'build\studyguard-evidence\test-classpath.txt'
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw '请先运行 Gradle :app:studyGuardEvidence --init-script scripts/evidence.init.gradle'
}
$testClasses = @(
    'com.focusforceplus.app.compliance.BlockDecisionTest',
    'com.focusforceplus.app.compliance.RoutineDurationValidatorTest',
    'com.focusforceplus.app.compliance.TamperProtectionTest',
    'com.focusforceplus.app.compliance.TodoLimitsTest',
    'com.focusforceplus.app.service.ActiveRoutineRegistryTest',
    'com.focusforceplus.app.ExampleUnitTest',
    'com.focusforceplus.app.study.StudyLedgerTest',
    'com.focusforceplus.app.study.StudyPlannerTest',
    'com.focusforceplus.app.study.StudyTimelineTest',
    'com.focusforceplus.app.study.StudyBackupCodecTest',
    'com.focusforceplus.app.study.StudyStatisticsTest'
)
$resolvedClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim().Split(';')
$classpath = ($resolvedClasspath | Where-Object {
    $_.StartsWith((Join-Path $projectRoot 'app\build')) -or
    $_ -match '\\(junit-[\d.]+|hamcrest-core-[\d.]+|kotlin-stdlib(?:-jdk[78])?-[\d.]+|kotlinx-coroutines-core-jvm-[\d.]+|kotlinx-serialization-(?:core|json)-jvm-[\d.]+|javax.inject-[\d.]+|annotations-[\d.]+)\.jar$'
}) -join ';'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
& $java '-Dfile.encoding=UTF-8' '-cp' $classpath 'org.junit.runner.JUnitCore' @testClasses
exit $LASTEXITCODE
