$ProjectDir = $PSScriptRoot
$SteamPath = "D:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire"
$WorkshopPath = "D:\Program Files (x86)\Steam\steamapps\workshop\content\646570"

# Use system javac/jar
$Javac = "javac"
$Jar = "jar"

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
& $Javac -d $ClassesDir -cp $Classpath $SourceFiles

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
