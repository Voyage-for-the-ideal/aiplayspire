$ProjectDir = $PSScriptRoot
$SteamPath = "D:\Program Files\Slay the Spire"
$ModsPath = "D:\Program Files\Slay the Spire\mods"
$JavaHome = "D:\Program Files\Java\jdk-25.0.2"
$Javac = "$JavaHome\bin\javac.exe"
$Jar = "$JavaHome\bin\jar.exe"

# Paths to dependencies
$DesktopJar = "$SteamPath\desktop-1.0.jar"
$ModTheSpireJar = "$SteamPath\ModTheSpire.jar"
$BaseModJar = "$ModsPath\BaseMod.jar"

# Output directories
$TargetDir = "$ProjectDir\target"
$ClassesDir = "$TargetDir\classes"

# Clean
if (Test-Path $TargetDir) {
    Remove-Item -Path $TargetDir -Recurse -Force
}
New-Item -ItemType Directory -Path $ClassesDir -Force | Out-Null

# Source files
$SourceFiles = Get-ChildItem -Path "$ProjectDir\src\main\java" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName

# Classpath
$Classpath = "$DesktopJar;$ModTheSpireJar;$BaseModJar"

Write-Host "Compiling..."
# Compile
& $Javac --release 8 -d $ClassesDir -cp $Classpath $SourceFiles

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

# Copy resources
Write-Host "Copying resources..."
Copy-Item -Path "$ProjectDir\src\main\resources\*" -Destination $ClassesDir -Recurse

# Create JAR
Write-Host "Packaging JAR..."
$JarFile = "$TargetDir\CommunicationMod.jar"
& $Jar cf $JarFile -C $ClassesDir .

if ($LASTEXITCODE -ne 0) {
    Write-Error "Packaging failed!"
    exit 1
}

Write-Host "Build successful! JAR created at: $JarFile"

# Deploy JAR directly to mods folder
$DeployJar = "$ModsPath\CommunicationMod.jar"

Write-Host "Deploying JAR to: $DeployJar"

Copy-Item -Path $JarFile -Destination $DeployJar -Force

Write-Host "Mod deployed successfully!"
