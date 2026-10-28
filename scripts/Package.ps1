param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$IncludeValidationSummary)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$build=[IO.Path]::GetFullPath((Join-Path $RuntimeRoot 'development/hololith/build'))
$libs=Join-Path $build 'libs'
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
if($version -notmatch '^[A-Za-z0-9][A-Za-z0-9._+-]*$'){throw 'Invalid mod version for a package directory name.'}
$dist=Join-Path $project "dist\$version"
$playerJar=Join-Path $libs "create-hololith-$version.jar"
$sourceJar=Join-Path $libs "create-hololith-$version-sources.jar"
foreach($artifact in @($playerJar,$sourceJar)){
    if(-not(Test-Path -LiteralPath $artifact -PathType Leaf)){throw "Current build artifact is missing: $artifact"}
}
$playerHash=(Get-FileHash -LiteralPath $playerJar -Algorithm SHA256).Hash.ToLowerInvariant()
function Require-Fields($Report,[string[]]$Names,[string]$Label){
    foreach($name in $Names){
        if($null -eq $Report -or $Report.PSObject.Properties.Name -notcontains $name){throw "$Label is missing required field $name."}
    }
}
function Read-Validation([string]$Relative){
    $path=Join-Path $project $Relative
    if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required validation report is missing: $Relative"}
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
function Test-GameValidation([string]$Relative,[string]$Profile,[string]$Mode=''){
    $result=Read-Validation $Relative
    Require-Fields $result @('version','profile','failures','total','checks','loadedMods') $Relative
    if($result.version -ne $version -or $result.profile -ne $Profile -or $result.failures -ne 0){throw "$Relative has failures or the wrong version/profile."}
    if($Mode){
        Require-Fields $result @('mode') $Relative
        if($result.mode -ne $Mode){throw "$Relative has the wrong fresh/resume mode."}
    }
    $checks=@($result.checks)
    if($checks.Count -lt 1 -or $result.total -ne $checks.Count){throw "$Relative has an empty or inconsistent check list."}
    foreach($entry in $checks){
        Require-Fields $entry @('test','passed') $Relative
        if($entry.passed -isnot [bool] -or -not $entry.passed -or [string]::IsNullOrWhiteSpace($entry.test)){throw "$Relative contains a failed or uncompleted check."}
        if(($entry.PSObject.Properties.Name -contains 'skipped') -and $entry.skipped){throw "$Relative counts a skipped check as completed."}
    }
    $excluded=@()
    if($result.PSObject.Properties.Name -contains 'skippedChecks'){$excluded=@($result.skippedChecks)}
    foreach($skip in $excluded){
        if($Profile -notin @('kinetic','kinetic-jade','minimal') -or $skip -cne 'Sable assembly, translation, yaw and disassembly: optional mod absent'){
            throw "$Relative contains an unsupported coverage omission: $skip"
        }
    }
    # This explicit Create-only exclusion is retained as an exclusion, never counted
    # as a successful check. The separately mandatory public-airship run covers Sable.
    foreach($id in @('minecraft','neoforge','create','hololith','hololith_probe')){
        if($result.loadedMods -notcontains $id){throw "$Relative did not load required fixture mod $id."}
    }
    if($Profile -eq 'kinetic-jade' -and $result.loadedMods -notcontains 'jade'){throw 'Jade HUD acceptance must actually load Jade.'}
    if($Profile -in @('kinetic','minimal') -and $result.loadedMods -contains 'jade'){throw "$Relative must cover Jade absence."}
    if($Profile -eq 'public-airship'){
        Require-Fields $result @('baselinePassed','schematicSha256') $Relative
        if($result.baselinePassed -isnot [bool] -or -not $result.baselinePassed -or $result.loadedMods -notcontains 'aeronautics' -or
                $result.loadedMods -notcontains 'sable' -or $result.schematicSha256 -ne 'f2d16fb6b5343cd872f0d02ec9d247cb11a48d21ea6be950963a627f297beaa2'){
            throw 'Public-airship acceptance must pass the reviewed original-craft baseline with Aeronautics and Sable.'
        }
    }
    return [ordered]@{profile=$Profile;mode=$Mode;checks=$checks.Count;passed=$true;excludedCoverage=$excluded;execution='Gradle development classpath'}
}
$gameSummaries=@()
$gameSummaries+=Test-GameValidation 'reports/kinetic/hololith-fresh.json' 'kinetic' 'fresh'
$gameSummaries+=Test-GameValidation 'reports/kinetic-jade/hololith-fresh.json' 'kinetic-jade' 'fresh'
foreach($mode in @('fresh','resume')){
    $gameSummaries+=Test-GameValidation "reports/minimal/hololith-$mode.json" 'minimal' $mode
    $gameSummaries+=Test-GameValidation "reports/contraptions/hololith-$mode.json" 'contraptions' $mode
}
$gameSummaries+=Test-GameValidation 'reports/airship-fixture/airship.json' 'public-airship'

# These runs install the actual player JAR. Their hash is meaningful artifact
# evidence; GUI probes execute source classes and must not claim that same coverage.
$requiredDedicated=@('processExitedCleanly','playerJarLoaded','jadeConfigurationMatches','unpoweredTableDoesNotScan',
    'realShaftNetworkPowersTable','poweredSurveyCompleted','motorRemovalStopsProjectionState','powerLossRetainsExactTerrain',
    'restoredMotorRejoinsNetworkWithSameTable','stationarySensorIdentityPresent','referenceConfigured','tableIdentityPresent',
    'capturedActorRejectsStaleKineticState','allActorContextsTicked','fixtureBlocksNotInWorld',
    'capturedActorReloadRetainsIdentitySettingsAndPowerOff','capturedSensorFeedsPoweredWorldTable','capturedSensorStillTicksAfterReload',
    'capturedTableRemainsOffWithoutSurvey','fixtureEntitiesCleaned','allDimensionsSaved','sharedSurveyPersisted')
$dedicatedSummaries=@()
foreach($profile in @(@{name='dedicated-minimal';minimal=$true;jade=$false},
        @{name='dedicated';minimal=$false;jade=$false},@{name='dedicated-minimal-jade';minimal=$true;jade=$true})){
    $label=$profile.name;$result=Read-Validation "reports/$label.json"
    Require-Fields $result @('version','jarSha256','failures','minimal','jade','checks') $label
    if($result.version -ne $version -or $result.jarSha256 -ne $playerHash -or $result.failures -ne 0 -or
            $result.minimal -isnot [bool] -or $result.minimal -ne $profile.minimal -or
            $result.jade -isnot [bool] -or $result.jade -ne $profile.jade){throw "$label must pass for this exact player JAR and requested profile."}
    $expected=@($requiredDedicated)
    if($profile.jade){
        $expected+='jadeCommonPluginLoaded'
        Require-Fields $result @('jadeSha256') $label
        if($result.jadeSha256 -ne '276f1f65a20d04f536989c4f311949d770b52c440c23c2b904a08860f3c93810'){throw "$label did not use the pinned optional Jade artifact."}
    }
    Require-Fields $result.checks $expected $label
    foreach($entry in $result.checks.PSObject.Properties){
        if($entry.Value -isnot [bool] -or -not $entry.Value){throw "$label contains a failed or uncompleted dedicated check: $($entry.Name)"}
    }
    $dedicatedSummaries+=[ordered]@{profile=$label;checks=@($result.checks.PSObject.Properties).Count;passed=$true;playerJarSha256=$playerHash;execution='Packaged player JAR'}
}

# Read only the active build's JUnit XML. Do not aggregate previously copied reports,
# which can retain removed suites and contain machine names or local filesystem paths.
$unitRoot=Join-Path $build 'test-results/test'
$unitFiles=@(Get-ChildItem -LiteralPath $unitRoot -Filter 'TEST-*.xml' -File)
if($unitFiles.Count -eq 0){throw 'Current build has no JUnit XML results.'}
$testRoot=Join-Path $project 'src/test/java'
$expectedSuites=@(Get-ChildItem -LiteralPath $testRoot -Filter '*Test.java' -File -Recurse | ForEach-Object {
    [IO.Path]::GetRelativePath($testRoot,$_.FullName).Replace('\','.').Replace('/','.').Replace('.java','')
})
$unitNames=@();$unitTests=0
foreach($file in $unitFiles){
    [xml]$document=Get-Content -LiteralPath $file.FullName -Raw
    $suite=$document.testsuite
    if($null -eq $suite -or $suite.name -notin $expectedSuites){throw "Unexpected or stale unit suite: $($file.Name)"}
    foreach($attribute in @('tests','failures','errors','skipped')){
        if(-not $suite.HasAttribute($attribute) -or $suite.GetAttribute($attribute) -notmatch '^\d+$'){throw "Malformed unit count in $($file.Name): $attribute"}
    }
    $cases=@($suite.testcase)
    if([int]$suite.tests -lt 1 -or [int]$suite.tests -ne $cases.Count -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or
            [int]$suite.skipped -ne 0 -or $document.SelectNodes('//testcase/skipped|//testcase/error|//testcase/failure').Count -ne 0){
        throw "Unit suite must complete with no failures, errors or skipped cases: $($file.Name)"
    }
    $unitNames+=$suite.name;$unitTests+=[int]$suite.tests
}
if($expectedSuites.Count -eq 0 -or $unitNames.Count -ne $expectedSuites.Count -or @($unitNames | Select-Object -Unique).Count -ne $unitNames.Count -or
        @(Compare-Object ($expectedSuites | Sort-Object) ($unitNames | Sort-Object)).Count -ne 0){throw 'Current JUnit output does not cover every test suite in this source checkout.'}

# No output is created until every required acceptance profile and current unit suite passes.
New-Item -ItemType Directory -Force -Path $dist | Out-Null
foreach($name in @("create-hololith-$version.jar","create-hololith-$version-sources.jar")) {
    Copy-Item -LiteralPath (Join-Path $libs $name) -Destination $dist
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceZip=Join-Path $dist "create-hololith-$version-project.zip"
if(Test-Path -LiteralPath $sourceZip){Remove-Item -LiteralPath $sourceZip}
$zip=[IO.Compression.ZipFile]::Open($sourceZip,[IO.Compression.ZipArchiveMode]::Create)
try {
    $files=@()
    foreach($folder in @('src','scripts','docs','gradle','images','.github')) {
        $files+=Get-ChildItem -LiteralPath (Join-Path $project $folder) -File -Recurse
    }
    foreach($name in @('README.md','CHANGELOG.md','LICENSE','AGENTS.md','.gitignore','.gitattributes','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat')) {
        $files+=Get-Item -LiteralPath (Join-Path $project $name)
    }
    foreach($file in $files) {
        $entry='create-hololith/'+[IO.Path]::GetRelativePath($project,$file.FullName).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entry,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {$zip.Dispose()}
Copy-Item -LiteralPath (Join-Path $project 'README.md') -Destination (Join-Path $dist 'INSTALL.md')
Copy-Item -LiteralPath (Join-Path $project 'docs') -Destination $dist -Recurse -Force
Copy-Item -LiteralPath (Join-Path $project 'images') -Destination $dist -Recurse -Force
Copy-Item -LiteralPath (Join-Path $project 'LICENSE') -Destination $dist
Copy-Item -LiteralPath (Join-Path $project 'CHANGELOG.md') -Destination $dist
if($IncludeValidationSummary){
    # Deliberate allowlist: no raw logs/XML, machine names, trial paths, entity IDs,
    # screenshots or local schematic filenames are copied from validation reports.
    [ordered]@{
        schema=1
        version=$version
        generatedUtc=[DateTime]::UtcNow.ToString('o')
        playerJarSha256=$playerHash
        sourceJarSha256=(Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
        provenance='GUI acceptance uses the Gradle development classpath and is matched by version/profile. Dedicated acceptance runs the player JAR and is matched by SHA-256. GUI evidence is not claimed as packaged-JAR execution.'
        graphical=$gameSummaries
        dedicated=$dedicatedSummaries
        unit=[ordered]@{source='Current build/test-results/test XML';suites=$unitFiles.Count;tests=$unitTests;failures=0;errors=0;skipped=0}
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $dist 'VALIDATION.json') -Encoding utf8
}elseif(Test-Path -LiteralPath (Join-Path $dist 'VALIDATION.json')){
    Remove-Item -LiteralPath (Join-Path $dist 'VALIDATION.json')
}
$hashes=foreach($file in Get-ChildItem -LiteralPath $dist -File | Where-Object {$_.Extension -in @('.jar','.zip','.mp4')}) {
    $hash=Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $($file.Name)"
}
$hashes | Set-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt') -Encoding utf8
Get-ChildItem -LiteralPath $dist | Select-Object Name,Length
