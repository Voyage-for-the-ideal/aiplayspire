$ProjectDir = $PSScriptRoot
$SteamPath = "D:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire"
$WorkshopPath = "D:\Program Files (x86)\Steam\steamapps\workshop\content\646570"
$JavaHome = "D:\Program Files\Java\jdk-25.0.2"
$Javac = "$JavaHome\bin\javac.exe"
$Jar = "$JavaHome\bin\jar.exe"

# Paths to dependencies
$DesktopJar = "$SteamPath\desktop-1.0.jar"
$ModTheSpireJar = "$WorkshopPath\1605060445\ModTheSpire.jar"
$BaseModJar = "$WorkshopPath\1605833019\BaseMod.jar"

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

# Prepare Mod folder
$ModSourceDir = "$ProjectDir\CommunicationMod"
$ModContentDir = "$ModSourceDir\content"

if (!(Test-Path $ModContentDir)) {
    New-Item -ItemType Directory -Path $ModContentDir -Force | Out-Null
}

Write-Host "Copying JAR to Mod content folder..."
Copy-Item -Path $JarFile -Destination "$ModContentDir\CommunicationMod.jar" -Force

# Copy Mod folder to Game directory
$GameDir = $SteamPath
$GameModDir = "$GameDir\CommunicationMod"

Write-Host "Deploying Mod to game directory: $GameModDir"

if (Test-Path $GameModDir) {
    Write-Host "Removing old Mod folder..."
    Remove-Item -Path $GameModDir -Recurse -Force
}

Copy-Item -Path $ModSourceDir -Destination $GameDir -Recurse -Force

Write-Host "Mod deployed successfully!"
