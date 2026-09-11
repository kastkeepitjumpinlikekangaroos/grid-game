<#
.SYNOPSIS
  Package the Grid Game client (or map editor) as a native Windows .exe via jpackage.

.DESCRIPTION
  A plain `bazel run` / `java -jar` launches through the shared java.exe, so Windows
  shows a generic Java icon/name in the taskbar, Alt+Tab, and Start menu, and users
  would need a JRE installed separately. jpackage bundles a private Java runtime
  image plus a real Windows launcher .exe (with our icon baked into the PE resource
  table) so the result runs standalone and looks like a native app everywhere.

  jpackage does not cross-compile: this script must be run ON Windows (it produces
  a Windows PE executable and links against the local JDK's runtime image). Building
  the deploy jar itself is fine cross-platform via Bazel, but the actual .exe has to
  be produced here.

.PARAMETER Target
  "client" (default) or "mapeditor".

.PARAMETER Installer
  Also build a WiX-based .exe installer (Start Menu entry, desktop shortcut,
  uninstaller) instead of just an app-image folder. Requires the WiX Toolset v3
  (candle.exe/light.exe) on PATH — https://wixtoolset.org/releases/ (v3, not v4/v5;
  jpackage on JDK 17-21 targets the v3 toolchain).

.EXAMPLE
  .\scripts\build_windows_exe.ps1
  .\scripts\build_windows_exe.ps1 -Target mapeditor -Installer
#>
param(
    [ValidateSet("client", "mapeditor")]
    [string]$Target = "client",
    [switch]$Installer
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

switch ($Target) {
    "client" {
        $AppName    = "Grid Game"
        $MainClass  = "com.gridgame.client.Main"
        $BazelLabel = "//src/main/scala/com/gridgame/client:client_windows_deploy.jar"
        $DeployJar  = "bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar"
        # Heap sizing, as the client's jvm_flags in BUILD.bazel (a deploy jar carries none)
        $JavaOptions = @("-Xms64m", "-Xmx768m")
    }
    "mapeditor" {
        $AppName    = "Grid Game Map Editor"
        $MainClass  = "com.gridgame.mapeditor.Main"
        $BazelLabel = "//src/main/scala/com/gridgame/mapeditor:mapeditor_windows_deploy.jar"
        $DeployJar  = "bazel-bin/src/main/scala/com/gridgame/mapeditor/mapeditor_windows_deploy.jar"
        $JavaOptions = @()
    }
}

$Icon = "sprites/icon_wizard.ico"
$Dest = "dist/windows"

if (-not (Test-Path $Icon)) {
    Write-Error "Missing $Icon — run: python3 scripts/generate_icon.py"
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Error "jpackage not found. Install a JDK 14+ (e.g. https://adoptium.net) and ensure its bin/ is on PATH."
}

if ($Installer -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    Write-Error "WiX Toolset v3 not found (candle.exe missing). Install it from https://wixtoolset.org/releases/ and ensure it's on PATH, or omit -Installer for a plain app-image build."
}

Write-Host "Building $BazelLabel..."
bazel build $BazelLabel
if ($LASTEXITCODE -ne 0) { throw "bazel build failed" }

$WorkDir = Join-Path $env:TEMP ([System.IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path "$WorkDir\input" | Out-Null
try {
    Copy-Item $DeployJar "$WorkDir\input\app.jar"

    $DestApp = Join-Path $Dest $AppName
    if (Test-Path $DestApp) { Remove-Item -Recurse -Force $DestApp }
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null

    $RuntimeHome = if ($env:JPACKAGE_RUNTIME_HOME) { $env:JPACKAGE_RUNTIME_HOME } else { $env:JAVA_HOME }
    if (-not $RuntimeHome) {
        throw "JAVA_HOME is not set. Point it at a JDK 14+ install (or set JPACKAGE_RUNTIME_HOME) so jpackage can bundle a runtime image."
    }

    $jpackageArgs = @(
        "--type", $(if ($Installer) { "exe" } else { "app-image" }),
        "--name", $AppName,
        "--input", "$WorkDir\input",
        "--main-jar", "app.jar",
        "--main-class", $MainClass,
        "--icon", $Icon,
        "--runtime-image", $RuntimeHome,
        "--dest", $Dest
    )
    foreach ($opt in $JavaOptions) {
        $jpackageArgs += @("--java-options", $opt)
    }
    if ($Installer) {
        $jpackageArgs += @("--win-shortcut", "--win-menu", "--win-dir-chooser")
    }

    jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

    if ($Installer) {
        Write-Host "Built installer in $Dest — look for '$AppName-*.exe'"
    } else {
        Write-Host "Built $DestApp\$AppName.exe"
    }
} finally {
    Remove-Item -Recurse -Force $WorkDir
}
