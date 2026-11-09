param([int]$Jobs = 4, [string]$OutputDirectory = "$PSScriptRoot/bin")
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$vendor = Join-Path $root '../third_party/sts_lightspeed'
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (!(Test-Path "$vendor/json/include/nlohmann/json.hpp")) { throw 'Vendored JSON headers are missing.' }
New-Item -ItemType Directory -Force $OutputDirectory, "$root/build" | Out-Null
$cmake = Get-Command cmake -ErrorAction SilentlyContinue
if ($cmake) {
    & $cmake.Source -S $root -B "$root/build/cmake" -DCMAKE_BUILD_TYPE=Release "-DBATTLE_ENGINE_OUTPUT_DIR=$OutputDirectory"
    if ($LASTEXITCODE) { throw 'CMake configure failed' }
    & $cmake.Source --build "$root/build/cmake" --config Release --parallel $Jobs
    if ($LASTEXITCODE) { throw 'CMake build failed' }
    $release = "$OutputDirectory/Release/battle-engine-worker.exe"
    if (Test-Path $release) { Copy-Item -LiteralPath $release -Destination "$OutputDirectory/battle-engine-worker.exe" }
} else {
    # Use the already installed Visual Studio toolchain; no downloads or package installation.
    $vswhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
    if (!(Test-Path $vswhere)) { throw 'Install/configure a C++17 toolchain (MSVC or CMake + MinGW) first.' }
    $vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (!$vs) { throw 'Visual Studio C++ tools were not found.' }
    $sources = @(Get-ChildItem "$vendor/src" -Filter *.cpp -Recurse | ForEach-Object { $_.FullName })
    $sources += "$root/src/Snapshot.cpp", "$root/src/SelectionSnapshot.cpp", "$root/src/TurnSearch.cpp", "$root/src/MonsterRoster.cpp", "$root/src/main.cpp"
    $flags = @('/nologo', '/std:c++17', '/EHsc', '/O2', "/MP$Jobs", '/utf-8', '/DNOMINMAX',
        "/FI`"$root/src/MsvcCompat.h`"", "/I`"$vendor/include`"", "/I`"$vendor/json/include`"", '/c')
    $flags += $sources | ForEach-Object { '"' + $_ + '"' }
    [IO.File]::WriteAllLines("$root/build/compile.rsp", $flags, [Text.Encoding]::Default)
    $batch = @"
@echo off
call "$vs\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1
cd /d "$root\build"
cl @compile.rsp
if errorlevel 1 exit /b 1
link /nologo *.obj /OUT:"$OutputDirectory\battle-engine-worker.exe"
"@
    [IO.File]::WriteAllText("$root/build/build.cmd", $batch, [Text.Encoding]::Default)
    & $env:ComSpec /d /c "$root/build/build.cmd"
    if ($LASTEXITCODE) { throw 'MSVC build failed' }
}
Write-Host "Built $OutputDirectory/battle-engine-worker.exe"
