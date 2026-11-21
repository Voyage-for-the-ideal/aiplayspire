param([int]$Jobs = 4, [string]$OutputDirectory = "$PSScriptRoot/bin")
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$vendor = Join-Path $root '../third_party/sts_lightspeed'
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (!(Test-Path "$vendor/json/include/nlohmann/json.hpp")) { throw 'Vendored JSON headers are missing.' }
New-Item -ItemType Directory -Force $OutputDirectory, "$root/build" | Out-Null
if (!(Test-Path "$env:JAVA_HOME/include/win32/jni_md.h")) { throw 'Set JAVA_HOME to a Windows JDK (JNI headers required).' }
$cmake = Get-Command cmake -ErrorAction SilentlyContinue
if ($cmake) {
    & $cmake.Source -S $root -B "$root/build/cmake" -DCMAKE_BUILD_TYPE=Release "-DBATTLE_ENGINE_OUTPUT_DIR=$OutputDirectory"
    if ($LASTEXITCODE) { throw 'CMake configure failed' }
    & $cmake.Source --build "$root/build/cmake" --config Release --parallel $Jobs
    if ($LASTEXITCODE) { throw 'CMake build failed' }
    $release = "$OutputDirectory/Release/battle-engine-worker.exe"
    foreach ($name in @('battle-engine-worker.exe','battle_jni.dll')) {
        $artifact = Join-Path "$OutputDirectory/Release" $name
        if (Test-Path $artifact) { Copy-Item -LiteralPath $artifact -Destination (Join-Path $OutputDirectory $name) }
    }
} else {
    # Use the already installed Visual Studio toolchain; no downloads or package installation.
    $vswhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
    if (!(Test-Path $vswhere)) { throw 'Install/configure a C++17 toolchain (MSVC or CMake + MinGW) first.' }
    $vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (!$vs) { throw 'Visual Studio C++ tools were not found.' }
    $sources = @(Get-ChildItem "$vendor/src" -Filter *.cpp -Recurse | ForEach-Object { $_.FullName })
    $sources += "$root/src/Snapshot.cpp", "$root/src/SelectionSnapshot.cpp", "$root/src/TurnSearch.cpp", "$root/src/MonsterRoster.cpp", "$root/src/main.cpp", "$root/src/NativeBindings.cpp"
    $flags = @('/nologo', '/std:c++17', '/EHsc', '/O2', '/MT', "/MP$Jobs", '/utf-8', '/DNOMINMAX',
        "/FI`"$root/src/MsvcCompat.h`"", "/I`"$vendor/include`"", "/I`"$vendor/json/include`"", "/I`"$env:JAVA_HOME/include`"", "/I`"$env:JAVA_HOME/include/win32`"", '/c')
    $flags += $sources | ForEach-Object { '"' + $_ + '"' }
    [IO.File]::WriteAllLines("$root/build/compile.rsp", $flags, [Text.Encoding]::Default)
    $coreObjects = @($sources | Where-Object { (Split-Path $_ -Leaf) -notin @('main.cpp','NativeBindings.cpp') } | ForEach-Object { '"' + [IO.Path]::GetFileNameWithoutExtension($_) + '.obj"' })
    [IO.File]::WriteAllLines("$root/build/core.rsp", $coreObjects, [Text.Encoding]::Default)
    $batch = @"
@echo off
call "$vs\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1
cd /d "$root\build"
cl @compile.rsp
if errorlevel 1 exit /b 1
lib /nologo @core.rsp /OUT:battle_core.lib
if errorlevel 1 exit /b 1
link /nologo /DLL NativeBindings.obj battle_core.lib /OUT:"$OutputDirectory\battle_jni.dll"
if errorlevel 1 exit /b 1
link /nologo main.obj battle_core.lib /OUT:"$OutputDirectory\battle-engine-worker.exe"
"@
    [IO.File]::WriteAllText("$root/build/build.cmd", $batch, [Text.Encoding]::Default)
    & $env:ComSpec /d /c "$root/build/build.cmd"
    if ($LASTEXITCODE) { throw 'MSVC build failed' }
}
Write-Host "Built $OutputDirectory/battle-engine-worker.exe"
