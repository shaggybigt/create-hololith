param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$Jade, [switch]$Fresh)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Resolve-Runtime.ps1')
$root=[IO.Path]::GetFullPath((Join-Path $runtime 'development/hololith'))
$trial=[IO.Path]::GetFullPath((Join-Path $root 'client-kinetic'))
$allowed=$root+[IO.Path]::DirectorySeparatorChar
if(-not $trial.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Kinetic trial outside isolated workspace.'}
$java=Join-Path $runtime 'runtime/jdk-21.0.12.1+1/bin/java.exe'
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'A project Java process is already running; finish it before starting a game probe.'}
foreach($path in @($root,$trial,(Join-Path $trial 'mods'),(Join-Path $trial 'saves'))){
    if((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Refuse linked trial path: $path"}
}
$create=Resolve-HololithCreateJar -RuntimeRoot $runtime
Assert-HololithUnlinkedPath -Path $trial
if(Test-Path -LiteralPath $trial){
    if(-not $Fresh -or -not(Test-Path -LiteralPath (Join-Path $trial '.hololith-disposable'))){throw 'Existing trial requires -Fresh and its disposable marker.'}
    $archive=[IO.Path]::GetFullPath((Join-Path $root ('history/kinetic/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    if(-not $archive.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive outside isolated workspace.'}
    Assert-HololithUnlinkedPath -Path $archive
    New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
    Move-Item -LiteralPath $trial -Destination $archive
}
New-Item -ItemType Directory -Force -Path "$trial/mods","$trial/validation","$trial/config" | Out-Null
Copy-Item -LiteralPath $create -Destination "$trial/mods"
Set-Content -LiteralPath "$trial/.hololith-disposable" -Value 'Disposable Hololith kinetic integration fixture only.'
@('renderDistance:8','simulationDistance:6','fullscreen:false','guiScale:2','gamma:0.5','resourcePacks:[]','pauseOnLostFocus:false','onboardAccessibility:false','skipMultiplayerWarning:true','tutorialStep:none','music:0.0','soundCategory_music:0.0') | Set-Content -LiteralPath "$trial/options.txt"
& (Join-Path $PSScriptRoot 'Build.ps1') -RuntimeRoot $runtime -Tasks @('build','probeJar')
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
Copy-Item -LiteralPath "$root/build/libs/hololith-probe-$version.jar" -Destination "$trial/mods"
$env:JAVA_HOME=Join-Path $runtime 'runtime/jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
$label=if($Jade){'kinetic-jade'}else{'kinetic'}
$reports=Join-Path $project "reports/$label"
New-Item -ItemType Directory -Force -Path $reports | Out-Null
& (Join-Path $project 'gradlew.bat') -p $project "-PruntimeRoot=$runtime" "-PcreateJar=$create" "-PjadeTest=$($Jade.IsPresent.ToString().ToLowerInvariant())" runClientKineticProbe | Tee-Object -FilePath "$reports/client.log"
if($LASTEXITCODE -ne 0){throw 'Kinetic client failed; inspect the isolated trial log.'}
$reportPath=Join-Path $trial 'validation/hololith-fresh.json'
if(-not(Test-Path -LiteralPath $reportPath)){throw 'Kinetic probe did not produce a report.'}
Copy-Item -LiteralPath $reportPath -Destination "$reports/hololith-fresh.json"
Copy-Item -LiteralPath "$trial/logs/latest.log" -Destination "$reports/latest.log"
foreach($file in Get-ChildItem -LiteralPath "$trial/screenshots" -Filter 'hololith-*.png'){Copy-Item -LiteralPath $file.FullName -Destination $reports}
$result=Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
if($result.version -ne $version -or $result.profile -ne $label){throw 'Kinetic report has the wrong version/profile.'}
if($result.failures -ne 0){throw "Kinetic acceptance failed $($result.failures) of $($result.total) checks."}
Write-Output "Kinetic acceptance passed $($result.total) checks."
