param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$Resume)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root=[IO.Path]::GetFullPath((Join-Path $runtime 'development\hololith'))
$probe=[IO.Path]::GetFullPath((Join-Path $root 'client-minimal'))
$reports=Join-Path $project 'reports\minimal'
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$allowed=$root+'\'
if(-not $probe.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Minimal probe path outside isolated Hololith workspace.'}
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'Close project Minecraft/server/Gradle processes before staging this isolated probe.'}
foreach($directory in @($probe,"$probe\mods","$probe\config","$probe\validation","$probe\saves","$probe\saves\hololith-validation")){
    if((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Refuse linked probe directory: $directory"}
}
if($Resume){
    if(-not(Test-Path -LiteralPath "$probe\saves\hololith-validation\level.dat")){throw 'No minimal Hololith trial exists to resume.'}
}else{
    if(Test-Path -LiteralPath $probe){
        $archive=[IO.Path]::GetFullPath((Join-Path $root ('history\minimal\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
        if(-not $archive.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive path outside isolated Hololith workspace.'}
        New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
        Move-Item -LiteralPath $probe -Destination $archive
    }
    New-Item -ItemType Directory -Force -Path "$probe\mods","$probe\config","$probe\validation" | Out-Null
    @('renderDistance:8','simulationDistance:6','fullscreen:false','guiScale:2','gamma:0.5','resourcePacks:[]','pauseOnLostFocus:false','onboardAccessibility:false','skipMultiplayerWarning:true','tutorialStep:none','music:0.0','soundCategory_music:0.0') | Set-Content -LiteralPath "$probe\options.txt"
}
New-Item -ItemType Directory -Force -Path $reports | Out-Null
foreach($file in Get-ChildItem -LiteralPath "$probe\mods" -Filter '*.jar'){
    if($file.Name -ne 'create-1.21.1-6.0.10.jar' -and $file.Name -notlike 'hololith-probe-*.jar'){throw "Unexpected mod in minimal trial: $($file.Name)"}
}
Copy-Item -LiteralPath "$runtime\test-client\mods\create-1.21.1-6.0.10.jar" -Destination "$probe\mods"
Set-Content -LiteralPath (Join-Path $probe '.hololith-disposable') -Value 'Disposable Hololith development fixture only.'
& (Join-Path $PSScriptRoot 'Build.ps1') -RuntimeRoot $runtime -Tasks @('build','probeJar')
foreach($old in Get-ChildItem -LiteralPath "$probe\mods" -Filter 'hololith-probe-*.jar'){Remove-Item -LiteralPath $old.FullName}
Copy-Item -LiteralPath "$runtime\development\hololith\build\libs\hololith-probe-$version.jar" -Destination "$probe\mods"
$env:JAVA_HOME=Join-Path $runtime 'runtime\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
$mode=if($Resume){'resume'}else{'fresh'}
$reportPath=Join-Path $probe "validation\hololith-$mode.json"
if(Test-Path -LiteralPath $reportPath){Remove-Item -LiteralPath $reportPath}
& (Join-Path $project 'gradlew.bat') -p $project "-PruntimeRoot=$runtime" runClientMinimalProbe "-PprobeResume=$($Resume.IsPresent.ToString().ToLowerInvariant())" | Tee-Object -FilePath "$reports\client-$mode.log"
if($LASTEXITCODE -ne 0){throw 'Minimal Hololith client launch failed; inspect client-minimal/logs/latest.log.'}
if(-not(Test-Path -LiteralPath $reportPath)){throw 'Minimal probe did not produce a fresh report.'}
$report=Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
Copy-Item -LiteralPath $reportPath -Destination $reports
foreach($file in Get-ChildItem -LiteralPath "$probe\screenshots" -Filter 'hololith-*.png' -ErrorAction SilentlyContinue){Copy-Item -LiteralPath $file.FullName -Destination $reports}
if($report.version -ne $version -or $report.profile -ne 'minimal'){throw 'Minimal report has the wrong loaded version or test profile.'}
if($report.failures -ne 0){throw "Minimal Hololith probe failed $($report.failures) of $($report.total) checks."}
Write-Output "Minimal Hololith $mode probe passed $($report.total) checks. Reports: $reports"
