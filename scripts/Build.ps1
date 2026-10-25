param([string]$RuntimeRoot='', [string[]]$Tasks=@('build'))
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$gradleArguments=@('-p',$project)
if($RuntimeRoot){
    $runtime=[IO.Path]::GetFullPath($RuntimeRoot)
    $bundledJava=Join-Path $runtime 'runtime/jdk-21.0.12.1+1'
    if(Test-Path -LiteralPath $bundledJava){$env:JAVA_HOME=$bundledJava}
    $env:GRADLE_USER_HOME=Join-Path $runtime 'gradle-cache'
    $gradleArguments+="-PruntimeRoot=$runtime"
    $createJar=Join-Path $runtime 'test-client/mods/create-1.21.1-6.0.10.jar'
    if(Test-Path -LiteralPath $createJar){$gradleArguments+="-PcreateJar=$createJar"}
}
& (Join-Path $project 'gradlew.bat') @gradleArguments @Tasks
if($LASTEXITCODE -ne 0){throw "Hololith Gradle failed: $LASTEXITCODE"}