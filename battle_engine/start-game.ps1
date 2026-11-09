# Starts the real game client only. The C++ gateway is a separate start.ps1 process.
param(
    [string]$GameDir = 'D:\Program Files\Slay the Spire',
    [string]$Profile = '<Default>',
    [ValidateRange(1, 65535)][int]$Port = 5125,
    [ValidateRange(0, 90000)][int]$MaxTimeMs = 0,
    [string]$RunSeed = ''
)
$ErrorActionPreference = 'Stop'
$GameDir = (Resolve-Path -LiteralPath $GameDir).Path
$java = Join-Path $GameDir 'jre\bin\java.exe'
foreach ($relative in @('jre\bin\java.exe', 'ModTheSpire.jar', 'mods\BattleAiMod.jar', 'mods\SaveStateMod.jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $GameDir $relative) -PathType Leaf)) {
        throw "Missing game file: $relative in $GameDir"
    }
}
$arguments = @('-Dbattleai.backend=external', "-Dbattleai.clientPort=$Port")
if ($MaxTimeMs -gt 0) { $arguments += "-Dbattleai.clientBudgetMs=$MaxTimeMs" }
if ($RunSeed) { $arguments += "-Dbattleai.runSeed=$RunSeed" }
$arguments += @('-Xmx2G', '-jar', 'ModTheSpire.jar', '--profile', $Profile,
    '--skip-launcher', '--skip-intro', '--jre51')
Push-Location $GameDir
try {
    & $java @arguments
    if ($LASTEXITCODE) { throw "Game client exited $LASTEXITCODE" }
} finally { Pop-Location }
