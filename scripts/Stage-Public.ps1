param([Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$images=Join-Path $project 'images'
New-Item -ItemType Directory -Force -Path $images | Out-Null
foreach($pair in @(@('hololith-markers-daylight.png','daylight.png'),@('hololith-dark-interior.png','interior.png'),@('hololith-ship-yaw.png','ship.png'))){
    Copy-Item -LiteralPath (Join-Path $project "reports\$($pair[0])") -Destination (Join-Path $images $pair[1])
}
foreach($name in @('bearing','cart')){
    Copy-Item -LiteralPath (Join-Path $project "reports\contraptions\hololith-contraption-$name.png") -Destination (Join-Path $images "contraption-$name.png")
}
# Build tooling is separate from the runtime mod and retains its upstream notices.
$gradle=Join-Path $runtime 'gradle-cache\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1'
Copy-Item -LiteralPath (Join-Path $gradle 'LICENSE') -Destination (Join-Path $project 'gradle\wrapper\LICENSE')
Copy-Item -LiteralPath (Join-Path $gradle 'NOTICE') -Destination (Join-Path $project 'gradle\wrapper\NOTICE')
$validation=Join-Path $project 'docs\test-results'
New-Item -ItemType Directory -Force -Path $validation | Out-Null
foreach($name in @('hololith-fresh.json','hololith-resume.json')){
    Copy-Item -LiteralPath (Join-Path $project "reports\$name") -Destination (Join-Path $validation $name)
}
foreach($mode in @('fresh','resume')){
    Copy-Item -LiteralPath (Join-Path $project "reports\minimal\hololith-$mode.json") -Destination (Join-Path $validation "minimal-$mode.json")
    Copy-Item -LiteralPath (Join-Path $project "reports\contraptions\hololith-$mode.json") -Destination (Join-Path $validation "contraptions-$mode.json")
}
Copy-Item -LiteralPath (Join-Path $project 'reports\hololith-migration.json') -Destination (Join-Path $validation 'historical-0.1-to-0.2.json')
foreach($name in @('dedicated','dedicated-minimal')){
    $report=Get-Content -LiteralPath (Join-Path $project "reports\$name.json") -Raw | ConvertFrom-Json
    $report.PSObject.Properties.Remove('trial')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $validation "$name.json") -Encoding utf8
}
Write-Output 'Public screenshots, build-tool notices and validation summaries staged; raw logs and local trial paths excluded.'
$suites=foreach($file in Get-ChildItem -LiteralPath (Join-Path $runtime 'development\hololith\build\test-results\test') -Filter 'TEST-*.xml'){
    [xml]$document=Get-Content -LiteralPath $file.FullName
    $suite=$document.testsuite
    @{suite=[string]$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped}
}
$suites | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $validation 'unit-tests.json') -Encoding utf8
# Renderer equivalence evidence belongs to 0.2.1 and remains under test-results/0.2.1.
# 0.3.0 intentionally changes gameplay integration and is validated by the game fixtures.
