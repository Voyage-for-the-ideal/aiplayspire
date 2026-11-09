param(
    [int]$Port = 5125,
    [int]$FallbackPort = 5126,
    [ValidateRange(0,90000)][int]$MaxTimeMs = 0,
    [string]$LogDir = (Join-Path $PSScriptRoot 'logs\cpp'),
    [switch]$EnableJavaFallback,
    [switch]$NoFallback
)
$ErrorActionPreference = 'Stop'
if ($NoFallback -and $EnableJavaFallback) { throw 'Choose C++ only or EnableJavaFallback, not both' }
$LogDir = [System.IO.Path]::GetFullPath($LogDir)
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $arguments = @('run', '--no-capture-output', '-n', 'spire', 'python', '-m', 'battle_engine.server',
        '--port', "$Port", '--log-dir', $LogDir,
        '--telemetry', (Join-Path $LogDir 'requests.jsonl'))
    if ($MaxTimeMs -gt 0) { $arguments += @('--max-time-ms', "$MaxTimeMs") }
    if ($EnableJavaFallback) {
        $arguments += @('--enable-java-fallback', '--fallback-port', "$FallbackPort")
    } else {
        $arguments += '--no-fallback'
    }
    & 'D:\miniconda3\Scripts\conda.exe' @arguments
    if ($LASTEXITCODE) { throw "Battle gateway exited $LASTEXITCODE" }
} finally { Pop-Location }
