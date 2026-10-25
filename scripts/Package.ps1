param([Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
${libs}=Join-Path $RuntimeRoot 'development\hololith\build\libs'
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$dist=Join-Path $project "dist\$version"
foreach($mode in @('fresh','resume')) {
    $result=Get-Content -LiteralPath (Join-Path $project "reports\hololith-$mode.json") -Raw | ConvertFrom-Json
    if($result.failures -ne 0 -or $result.version -ne $version -or $result.profile -ne 'full-pack'){throw "The $mode game validation has failures or is for the wrong release/profile."}
}
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$dedicated=Get-Content -LiteralPath (Join-Path $project 'reports\dedicated.json') -Raw | ConvertFrom-Json
$releaseHash=(Get-FileHash -LiteralPath (Join-Path $libs "create-hololith-$version.jar") -Algorithm SHA256).Hash
if($dedicated.failures -ne 0 -or $dedicated.jarSha256 -ne $releaseHash){throw 'Dedicated result must pass and match the release JAR.'}
$minimalDedicated=Get-Content -LiteralPath (Join-Path $project 'reports\dedicated-minimal.json') -Raw | ConvertFrom-Json
if($minimalDedicated.failures -ne 0 -or $minimalDedicated.jarSha256 -ne $releaseHash){throw 'Minimal dedicated result must pass and match the release JAR.'}
foreach($mode in @('fresh','resume')){
    $result=Get-Content -LiteralPath (Join-Path $project "reports\minimal\hololith-$mode.json") -Raw | ConvertFrom-Json
    if($result.failures -ne 0 -or $result.version -ne $version -or $result.profile -ne 'minimal'){throw "Minimal $mode validation has failures or is for the wrong release/profile."}
}
foreach($mode in @('fresh','resume')){
    $result=Get-Content -LiteralPath (Join-Path $project "reports\contraptions\hololith-$mode.json") -Raw | ConvertFrom-Json
    if($result.failures -ne 0 -or $result.version -ne $version -or $result.profile -ne 'contraptions'){throw "Contraption $mode validation has failures or is for the wrong release/profile."}
}
New-Item -ItemType Directory -Force -Path (Join-Path $project 'reports\unit-tests') | Out-Null
Copy-Item -Path (Join-Path $RuntimeRoot 'development\hololith\build\test-results\test\TEST-*.xml') -Destination (Join-Path $project 'reports\unit-tests')
foreach($file in Get-ChildItem -LiteralPath (Join-Path $project 'reports\unit-tests') -Filter 'TEST-*.xml'){
    [xml]$suite=Get-Content -LiteralPath $file.FullName
    if([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0 -or [int]$suite.testsuite.skipped -ne 0){throw 'Unit tests must pass without skipped cases before packaging.'}
}
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
$hashes=foreach($file in Get-ChildItem -LiteralPath $dist -File | Where-Object {$_.Extension -in @('.jar','.zip','.mp4')}) {
    $hash=Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $($file.Name)"
}
$hashes | Set-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt') -Encoding utf8
Get-ChildItem -LiteralPath $dist | Select-Object Name,Length
