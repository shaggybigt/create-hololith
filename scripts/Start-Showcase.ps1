param([Parameter(Mandatory)][string]$RuntimeRoot, [Parameter(Mandatory)][string]$FFmpegPath,
      [string]$ModpackRoot='', [switch]$Fresh, [switch]$PrepareOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Resolve-Runtime.ps1')
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root=[IO.Path]::GetFullPath((Join-Path $runtime 'development\hololith'))
$probe=[IO.Path]::GetFullPath((Join-Path $root 'client-showcase'))
$reports=[IO.Path]::GetFullPath((Join-Path $project 'reports\showcase'))
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
# All external inputs are checked before preserving an older trial. This is a
# Create-only recording; ModpackRoot optionally supplies the pinned Create JAR.
$createJar=Resolve-HololithCreateJar -RuntimeRoot $runtime -ModpackRoot $ModpackRoot
$encoder=[IO.Path]::GetFullPath($FFmpegPath)
Assert-HololithUnlinkedPath -Path $encoder
if(-not(Test-Path -LiteralPath $encoder -PathType Leaf)){throw 'The trusted local FFmpeg executable is missing.'}
Assert-HololithUnlinkedPath -Path $probe
Assert-HololithUnlinkedPath -Path $reports
$allowed=$root+'\'
if(-not $probe.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Showcase path outside isolated Hololith workspace.'}
if($encoder.StartsWith($probe+'\',[StringComparison]::OrdinalIgnoreCase) -or $createJar.StartsWith($probe+'\',[StringComparison]::OrdinalIgnoreCase)){
    throw 'Recording inputs must be outside the showcase trial that will be archived.'
}
if($encoder.StartsWith($reports+'\',[StringComparison]::OrdinalIgnoreCase) -or $createJar.StartsWith($reports+'\',[StringComparison]::OrdinalIgnoreCase)){
    throw 'Recording inputs must be outside the showcase reports that will be archived.'
}
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'Close project Minecraft/server/Gradle processes before staging the showcase.'}
foreach($directory in @($probe,"$probe\mods","$probe\config","$probe\validation","$probe\saves","$probe\showcase")){
    if((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Refuse linked showcase directory: $directory"}
}
if(Test-Path -LiteralPath $probe){
    if(-not $Fresh){throw 'A showcase trial already exists. Use -Fresh to archive its world and footage before recording again.'}
    if(-not(Test-Path -LiteralPath "$probe\.hololith-disposable")){throw 'Refuse to archive an unmarked showcase directory.'}
    $archive=[IO.Path]::GetFullPath((Join-Path $root ('history\showcase\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    if(-not $archive.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Showcase archive outside isolated runtime workspace.'}
    New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
    Move-Item -LiteralPath $probe -Destination $archive
}
if(Test-Path -LiteralPath $reports){
    $reportArchive=[IO.Path]::GetFullPath((Join-Path $project ('reports\history\showcase\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    $reportRoot=[IO.Path]::GetFullPath((Join-Path $project 'reports'))+'\'
    if(-not $reports.StartsWith($reportRoot,[StringComparison]::OrdinalIgnoreCase) -or -not $reportArchive.StartsWith($reportRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Report archive must remain inside project reports.'}
    New-Item -ItemType Directory -Force -Path (Split-Path $reportArchive -Parent) | Out-Null
    Move-Item -LiteralPath $reports -Destination $reportArchive
}
New-Item -ItemType Directory -Force -Path "$probe\mods","$probe\config","$probe\validation",$reports | Out-Null
Copy-Item -LiteralPath $createJar -Destination "$probe\mods"
@('renderDistance:10','simulationDistance:8','fullscreen:false','guiScale:2','gamma:0.5','resourcePacks:[]',
  'pauseOnLostFocus:false','onboardAccessibility:false','skipMultiplayerWarning:true','tutorialStep:none',
  'music:0.0','soundCategory_music:0.0','maxFps:60') | Set-Content -LiteralPath "$probe\options.txt"
Set-Content -LiteralPath "$probe\.hololith-disposable" -Value 'Disposable Hololith development fixture only.'
Set-Content -LiteralPath "$probe\.hololith-showcase" -Value 'Fresh four-minute actual Minecraft framebuffer recording fixture.'
if($PrepareOnly){Write-Output "Showcase staged without building or launching: $probe";return}
& (Join-Path $PSScriptRoot 'Build.ps1') -RuntimeRoot $runtime -ModpackRoot $ModpackRoot -Tasks @('build','probeJar')
if($LASTEXITCODE -ne 0){throw 'Showcase build failed.'}
Copy-Item -LiteralPath "$runtime\development\hololith\build\libs\hololith-probe-$version.jar" -Destination "$probe\mods"
$env:JAVA_HOME=Join-Path $runtime 'runtime\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
& (Join-Path $project 'gradlew.bat') -p $project "-PruntimeRoot=$runtime" "-PcreateJar=$createJar" runClientShowcase "-PffmpegPath=$encoder" | Tee-Object -FilePath "$reports\capture.log"
if($LASTEXITCODE -ne 0){throw 'Showcase client failed; inspect the isolated client-showcase log.'}
$capturePath=Join-Path $probe 'showcase\capture.json'
if(-not(Test-Path -LiteralPath $capturePath)){throw 'The new showcase did not produce a capture report.'}
Copy-Item -LiteralPath $capturePath,"$probe\showcase\gameplay-raw.mp4" -Destination $reports
$capture=Get-Content -LiteralPath $capturePath -Raw | ConvertFrom-Json
if($capture.version -ne $version -or $capture.profile -ne 'showcase'){throw 'Showcase report has the wrong version or profile.'}
if(-not $capture.completed -or $capture.frames -ne 7200 -or $capture.durationSeconds -ne 240 -or @($capture.checks | Where-Object {-not $_.passed}).Count){throw 'Four-minute showcase capture did not complete successfully; retain the partial footage for diagnosis.'}
Write-Output "Actual 240-second gameplay capture is ready: $reports"
Write-Output "Reusable disposable world: $probe\saves\hololith-validation"
