# Package previously built artifacts. Does not touch the game or install the mods.
param([string]$DependencyDirectory = "$PSScriptRoot/../_ModTheSpire/mods",
      [string]$BattleJar = "$PSScriptRoot/../scumthespire/target/BattleAiMod.jar",
      [string]$OutputZip = "$PSScriptRoot/out/autobattle-windows-x64.zip")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$mod = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $BattleJar))
try {
    $dll = $mod.GetEntry('native/windows-x86_64/battle_jni.dll')
    if (!$dll -or $dll.Length -lt 1) { throw 'Build and package the JNI library first.' }
    if ($mod.GetEntry('battleaimod/networking/AiServer.class')) { throw 'Stale Java server classes; run a clean Maven package.' }
} finally { $mod.Dispose() }
$inputs = @((Join-Path $DependencyDirectory 'SaveStateMod.jar'),(Join-Path $DependencyDirectory 'LudicrousSpeed.jar'),$BattleJar)
$inputs = @($inputs | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
$dest = [IO.Path]::GetFullPath($OutputZip)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
if (Test-Path -LiteralPath $dest) { throw "Choose a new output ZIP: $dest" }
$zip = [IO.Compression.ZipFile]::Open($dest,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $inputs) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file,[IO.Path]::GetFileName($file)) | Out-Null
    }
} finally { $zip.Dispose() }
Get-FileHash -Algorithm SHA256 -LiteralPath $dest
