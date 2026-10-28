param([string]$RuntimeRoot='', [string[]]$Tasks=@('build'), [string]$ModpackRoot='')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Resolve-Runtime.ps1')
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$gradleArguments=@('-p',$project)
if($ModpackRoot -and -not $RuntimeRoot){throw '-ModpackRoot requires -RuntimeRoot for the isolated development build.'}
if($RuntimeRoot){
    $runtime=[IO.Path]::GetFullPath($RuntimeRoot)
    $bundledJava=Join-Path $runtime 'runtime/jdk-21.0.12.1+1'
    if(Test-Path -LiteralPath $bundledJava){$env:JAVA_HOME=$bundledJava}
    $env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
    $gradleArguments+="-PruntimeRoot=$runtime"
    $createJar=Resolve-HololithCreateJar -RuntimeRoot $runtime -ModpackRoot $ModpackRoot
    $gradleArguments+="-PcreateJar=$createJar"
}
& (Join-Path $project 'gradlew.bat') @gradleArguments @Tasks
if($LASTEXITCODE -ne 0){throw "Hololith Gradle failed: $LASTEXITCODE"}
