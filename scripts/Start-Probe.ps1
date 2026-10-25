param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$Resume,[switch]$Fresh,[switch]$Compare)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
New-Item -ItemType Directory -Force -Path (Join-Path $project 'reports') | Out-Null
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$probe=Join-Path $runtime 'development\hololith\client-probe'
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}) { throw 'Close project Minecraft/server/Gradle processes before staging this isolated probe.' }
if($Resume){
    if(-not(Test-Path -LiteralPath "$probe\saves\hololith-validation\level.dat")){throw 'No Hololith trial exists to resume.'}
}else{
    if(Test-Path -LiteralPath $probe){
        if(-not $Fresh){throw 'A trial already exists. Use -Resume or -Fresh (archives the previous trial).'}
        $priorResume=Join-Path $probe 'validation\hololith-resume.json'
        if(Test-Path -LiteralPath $priorResume){
            $prior=Get-Content -LiteralPath $priorResume -Raw | ConvertFrom-Json
            if($prior.failures -eq 0 -and $prior.checks.test -contains 'legacy table obtains persistent table identity'){
                Copy-Item -LiteralPath $priorResume -Destination "$project\reports\hololith-migration.json"
                Copy-Item -LiteralPath "$project\reports\client-resume.log" -Destination "$project\reports\client-migration.log"
            }
        }
        $resolved=[IO.Path]::GetFullPath($probe)
        $archive=[IO.Path]::GetFullPath((Join-Path $runtime ('development\hololith\history\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
        $allowed=[IO.Path]::GetFullPath((Join-Path $runtime 'development\hololith'))+'\'
        if(-not $resolved.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or -not $archive.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive paths outside isolated Hololith workspace.'}
        if((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refuse linked probe directory.'}
        New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
        Move-Item -LiteralPath $resolved -Destination $archive
    }
    $source=Join-Path $runtime 'development\client-probe\saves\client-validation'
    if(-not(Test-Path -LiteralPath "$source\level.dat")){throw 'Existing disposable source world is missing.'}
    New-Item -ItemType Directory -Force -Path "$probe\saves\hololith-validation","$probe\mods","$probe\config","$probe\validation" | Out-Null
    $lock=$null
    try {
        if(Test-Path -LiteralPath "$source\session.lock"){$lock=[IO.File]::Open("$source\session.lock",[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
        foreach($file in Get-ChildItem -LiteralPath $source -File -Recurse){
            if($file.Name -eq 'session.lock'){continue}
            if($file.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refuse linked world data.'}
            $target=Join-Path "$probe\saves\hololith-validation" ([IO.Path]::GetRelativePath($source,$file.FullName))
            New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $target
        }
    }finally{if($lock){$lock.Dispose()}}
    foreach($file in Get-ChildItem -LiteralPath "$runtime\test-client\mods" -Filter '*.jar'){
        if($file.Name -like 'create-hololith-*' -or $file.Name -like 'hololith-probe-*'){continue}
        Copy-Item -LiteralPath $file.FullName -Destination "$probe\mods"
    }
    Copy-Item -Path "$runtime\test-client\config\*" -Destination "$probe\config" -Recurse
    @('renderDistance:8','simulationDistance:6','fullscreen:false','guiScale:2','pauseOnLostFocus:false','onboardAccessibility:false','skipMultiplayerWarning:true','tutorialStep:none','music:0.0','soundCategory_music:0.0') | Set-Content -LiteralPath "$probe\options.txt"
}
Set-Content -LiteralPath (Join-Path $probe '.hololith-disposable') -Value 'Disposable Hololith development fixture only.'
& (Join-Path $PSScriptRoot 'Build.ps1') -RuntimeRoot $runtime -Tasks @('build','probeJar')
foreach($old in Get-ChildItem -LiteralPath "$probe\mods" -Filter 'hololith-probe-*.jar'){Remove-Item -LiteralPath $old.FullName}
Copy-Item -LiteralPath "$runtime\development\hololith\build\libs\hololith-probe-$version.jar" -Destination "$probe\mods"
$env:JAVA_HOME=Join-Path $runtime 'runtime\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
$mode=if($Resume){'resume'}else{'fresh'}
$reportPath=Join-Path $probe "validation\hololith-$mode.json"
if(Test-Path -LiteralPath $reportPath){Remove-Item -LiteralPath $reportPath}
$comparisonArguments=@()
if($Compare){
    $baseline=Join-Path $project 'dist\0.2.0\create-hololith-0.2.0.jar'
    if(-not(Test-Path -LiteralPath $baseline)){throw 'The local 0.2.0 baseline JAR is required for comparison.'}
    $comparisonArguments+="-PbaselineJar=$baseline"
}
& (Join-Path $project 'gradlew.bat') -p $project "-PruntimeRoot=$runtime" runClientProbe "-PprobeResume=$($Resume.IsPresent.ToString().ToLowerInvariant())" @comparisonArguments | Tee-Object -FilePath "$project\reports\client-$mode.log"
if($LASTEXITCODE -ne 0){throw 'Hololith client launch failed; inspect client-probe/logs/latest.log.'}
if(-not(Test-Path -LiteralPath $reportPath)){throw 'Full-pack probe did not produce a fresh report.'}
$report=Get-Content -LiteralPath "$probe\validation\hololith-$mode.json" -Raw | ConvertFrom-Json
Copy-Item -LiteralPath "$probe\validation\hololith-$mode.json" -Destination "$project\reports"
foreach($file in Get-ChildItem -LiteralPath "$probe\validation" -Filter 'render-comparison-*.json'){Copy-Item -LiteralPath $file.FullName -Destination "$project\reports"}
foreach($file in Get-ChildItem -LiteralPath "$probe\screenshots" -Filter 'hololith-*.png' -ErrorAction SilentlyContinue){Copy-Item -LiteralPath $file.FullName -Destination "$project\reports"}
if(${report}.version -ne $version -or $report.profile -ne 'full-pack'){throw 'Full-pack report has the wrong loaded version or profile.'}
if($report.failures -ne 0){throw "Hololith probe failed $($report.failures) of $($report.total) checks."}
Write-Output "Hololith $mode probe passed $($report.total) checks. Reports: $project\reports"
