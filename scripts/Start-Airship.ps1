# SchematicPath overrides the location of the reviewed sanitized Airship V10 file only.
# This bounded fixture uses that craft's controls and does not accept arbitrary schematics.
param([Parameter(Mandatory)][string]$RuntimeRoot, [string]$SchematicPath, [switch]$Fresh, [switch]$PrepareOnly)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root=[IO.Path]::GetFullPath((Join-Path $runtime 'development\hololith'))
$probe=[IO.Path]::GetFullPath((Join-Path $root 'client-airship'))
$reports=Join-Path $project 'reports\airship-fixture'
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
if(-not $SchematicPath){$SchematicPath=Join-Path $reports 'air_ship_v10-hololith-test.nbt'}
$inputFile=[IO.Path]::GetFullPath($SchematicPath)
if(-not(Test-Path -LiteralPath $inputFile -PathType Leaf)){throw 'Sanitized local airship schematic is missing; preserve the two glue entities and omit mobs.'}
$inputHash=(Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash.ToLowerInvariant()
if($inputHash -ne 'f2d16fb6b5343cd872f0d02ec9d247cb11a48d21ea6be950963a627f297beaa2'){throw 'This fixture accepts only the exact reviewed sanitized Airship V10 file; SchematicPath changes its location, not its identity.'}
$allowed=$root+'\'
if(-not $probe.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Airship path outside isolated Hololith workspace.'}
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'Close project Minecraft/server/Gradle processes before staging this isolated probe.'}
foreach($directory in @($root,$probe,"$probe\mods","$probe\config","$probe\validation","$probe\saves","$probe\schematics")){
    if((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Refuse linked airship directory: $directory"}
}
if(Test-Path -LiteralPath $probe){
    if(-not $Fresh){throw 'An airship trial already exists. Use -Fresh to archive it before making a new disposable world.'}
    if(-not(Test-Path -LiteralPath "$probe\.hololith-disposable")){throw 'Refuse to archive an unmarked directory.'}
    $archive=[IO.Path]::GetFullPath((Join-Path $root ('history\airship\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    if(-not $archive.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive path outside isolated Hololith workspace.'}
    New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
    Move-Item -LiteralPath $probe -Destination $archive
}
New-Item -ItemType Directory -Force -Path "$probe\mods","$probe\config","$probe\validation","$probe\schematics\uploaded\hololith-airship-probe",$reports | Out-Null
foreach($file in Get-ChildItem -LiteralPath "$runtime\test-client\mods" -Filter '*.jar'){
    if($file.Name -like 'create-hololith-*' -or $file.Name -like 'hololith-probe-*'){continue}
    Copy-Item -LiteralPath $file.FullName -Destination "$probe\mods"
}
Copy-Item -Path "$runtime\test-client\config\*" -Destination "$probe\config" -Recurse
Copy-Item -LiteralPath $inputFile -Destination "$probe\schematics\air_ship_v10-hololith-test.nbt"
Copy-Item -LiteralPath $inputFile -Destination "$probe\schematics\uploaded\hololith-airship-probe\air_ship_v10-hololith-test.nbt"
@('renderDistance:8','simulationDistance:6','fullscreen:false','guiScale:2','pauseOnLostFocus:false','onboardAccessibility:false','skipMultiplayerWarning:true','tutorialStep:none','music:0.0','soundCategory_music:0.0') | Set-Content -LiteralPath "$probe\options.txt"
Set-Content -LiteralPath "$probe\.hololith-disposable" -Value 'Disposable Hololith development fixture only.'
Set-Content -LiteralPath "$probe\.hololith-airship" -Value 'Explicit local public-airship baseline and instrumented acceptance world.'
[ordered]@{source=$inputFile;sha256=$inputHash;staged=(Get-Date).ToUniversalTime().ToString('o');redistribute=$false} | ConvertTo-Json | Set-Content -LiteralPath "$probe\validation\input.json"
if($PrepareOnly){Write-Output "Airship trial staged without building or launching: $probe";return}
& (Join-Path $PSScriptRoot 'Build.ps1') -RuntimeRoot $runtime -Tasks @('build','probeJar')
if($LASTEXITCODE -ne 0){throw 'Airship fixture build failed.'}
Copy-Item -LiteralPath "$runtime\development\hololith\build\libs\hololith-probe-$version.jar" -Destination "$probe\mods"
$env:JAVA_HOME=Join-Path $runtime 'runtime\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
& (Join-Path $project 'gradlew.bat') -p $project "-PruntimeRoot=$runtime" runClientAirshipProbe | Tee-Object -FilePath "$reports\client-airship.log"
if($LASTEXITCODE -ne 0){throw 'Airship client failed; inspect the isolated client-airship log.'}
$reportPath=Join-Path $probe 'validation\airship.json'
if(-not(Test-Path -LiteralPath $reportPath)){throw 'Airship fixture did not produce a fresh report.'}
Copy-Item -LiteralPath $reportPath -Destination "$reports\airship.json"
Copy-Item -LiteralPath "$probe\logs\latest.log" -Destination "$reports\latest.log"
foreach($file in Get-ChildItem -LiteralPath "$probe\screenshots" -Filter 'hololith-airship-*.png' -ErrorAction SilentlyContinue){Copy-Item -LiteralPath $file.FullName -Destination $reports}
$report=Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
if($report.version -ne $version -or $report.profile -ne 'public-airship'){throw 'Airship report has the wrong loaded version or profile.'}
if(-not $report.baselinePassed){throw 'Original craft baseline failed; do not attribute this flight result to Hololith. See airship.json.'}
if($report.failures -ne 0){throw "Airship fixture failed $($report.failures) checks; see airship.json."}
Write-Output "Baseline and instrumented airship acceptance complete: $reports"
