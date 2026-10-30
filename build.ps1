# Keep Gradle's cache local to this checkout on Windows.
# Forward task names and flags unchanged to the standard Gradle wrapper.
$previousGradleUserHome = $env:GRADLE_USER_HOME
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user'
Push-Location $PSScriptRoot
try {
    if ($args.Count -eq 0) {
        & "$PSScriptRoot\gradlew.bat" :app:assembleDebug
    } else {
        & "$PSScriptRoot\gradlew.bat" @args
    }
    $buildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    $env:GRADLE_USER_HOME = $previousGradleUserHome
}
exit $buildExitCode
