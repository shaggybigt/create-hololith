param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$Minimal, [switch]$Jade, [string]$JadeJar='')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Resolve-Runtime.ps1')
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
New-Item -ItemType Directory -Force -Path (Join-Path $project 'reports') | Out-Null
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$server=Join-Path $runtime 'test-server'
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'Close project JVMs before this isolated dedicated test.'}
if($JadeJar -and -not $Jade){throw '-JadeJar requires -Jade.'}
$resolvedJade=$null
$jadeHash=$null
if($Jade){
    $resolvedJade=if($JadeJar){[IO.Path]::GetFullPath($JadeJar)}else{
        Join-Path $runtime 'gradle-cache/caches/modules-2/files-2.1/maven.modrinth/jade/eYz2YBGT/88ee316e68900080b017f60c12162e2731924cf8/jade-eYz2YBGT.jar'
    }
    if(-not(Test-Path -LiteralPath $resolvedJade -PathType Leaf)){throw 'Pinned Jade 15.10.6 is not cached. Supply -JadeJar with the official NeoForge 1.21.1 artifact.'}
    Assert-HololithUnlinkedPath -Path $resolvedJade
    $jadeHash=(Get-FileHash -LiteralPath $resolvedJade -Algorithm SHA256).Hash.ToLowerInvariant()
    if($jadeHash -ne '276f1f65a20d04f536989c4f311949d770b52c440c23c2b904a08860f3c93810'){throw 'Jade JAR does not match the pinned official 15.10.6 artifact.'}
}
$createJar=Resolve-HololithCreateJar -RuntimeRoot $runtime -ModpackRoot $server
$jar=Join-Path $runtime "development\hololith\build\libs\create-hololith-$version.jar"
if(-not(Test-Path -LiteralPath $jar -PathType Leaf)){throw "Build the current player JAR before running dedicated acceptance: $jar"}
$isolatedRoot=[IO.Path]::GetFullPath((Join-Path $runtime 'development/hololith'))
$trial=[IO.Path]::GetFullPath((Join-Path $isolatedRoot ('dedicated-smoke-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
if(-not $trial.StartsWith($isolatedRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Dedicated trial is outside the isolated development directory.'}
Assert-HololithUnlinkedPath -Path $trial
Assert-HololithUnlinkedPath -Path $server
$reportName=if($Minimal){'dedicated-minimal'}else{'dedicated'}
if($Jade){$reportName+='-jade'}
New-Item -ItemType Directory -Path $trial | Out-Null
Set-Content -LiteralPath (Join-Path $trial '.hololith-disposable') -Value 'Disposable Hololith dedicated acceptance world only.'
$folders=if($Minimal){@('libraries')}else{@('libraries','config','defaultconfigs')}
foreach($folder in $folders){
    $source=Join-Path $server $folder
    if($folder -eq 'libraries' -or (Test-Path -LiteralPath $source -PathType Container)){
        Assert-HololithUnlinkedPath -Path $source
        Copy-Item -LiteralPath $source -Destination $trial -Recurse
    }
}
New-Item -ItemType Directory -Path (Join-Path $trial 'mods') | Out-Null
foreach($file in Get-ChildItem -LiteralPath (Join-Path $server 'mods') -Filter '*.jar'){
    if($Minimal -and $file.Name -ne 'create-1.21.1-6.0.10.jar'){continue}
    if($file.Name -like 'fantasy-validation*' -or $file.Name -like 'hololith-probe*' -or $file.Name -like 'create-hololith*'){continue}
    # Jade and Jade Addons are never inherited implicitly; -Jade tests only the pinned core.
    if($file.Name -like 'jade*.jar'){continue}
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $trial 'mods')
}
if($Jade){Copy-Item -LiteralPath $resolvedJade -Destination (Join-Path $trial 'mods/Jade-1.21.1-NeoForge-15.10.6.jar')}
$testedJar=Join-Path $trial "mods/create-hololith-$version.jar"
Copy-Item -LiteralPath $jar -Destination $testedJar
$testedJarHash=(Get-FileHash -LiteralPath $testedJar -Algorithm SHA256).Hash
Copy-Item -LiteralPath (Join-Path $server 'eula.txt') -Destination $trial
@('-Xms1G','-Xmx6G') | Set-Content -LiteralPath (Join-Path $trial 'user_jvm_args.txt')
@('server-ip=127.0.0.1','server-port=25576','online-mode=true','view-distance=2','simulation-distance=2','level-type=minecraft:flat','generate-structures=false','level-name=world-smoke','enable-query=false','enable-rcon=false','max-players=1') | Set-Content -LiteralPath (Join-Path $trial 'server.properties')
$info=[Diagnostics.ProcessStartInfo]::new()
$info.FileName=$java;$info.WorkingDirectory=$trial;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
foreach($arg in @('@user_jvm_args.txt','@libraries/net/neoforged/neoforge/21.1.259/win_args.txt','nogui','--world','world-smoke')){$info.ArgumentList.Add($arg)}
$process=[Diagnostics.Process]::new();$process.StartInfo=$info
$null=$process.Start();$process.StandardInput.AutoFlush=$true
$outputTask=$process.StandardOutput.ReadToEndAsync();$errorTask=$process.StandardError.ReadToEndAsync()
Write-Output "Isolated dedicated validation: $trial"
# These IDs belong only to this disposable fixture. Never select all contraptions or carts.
$actorUuid=[guid]::NewGuid();$cartUuid=[guid]::NewGuid()
$tableUuid=[guid]::NewGuid();$sensorUuid=[guid]::NewGuid();$markerUuid=[guid]::NewGuid()
$fixtureStarted=$false;$fixtureCleaned=$false
function ConvertTo-SnbtUuid([guid]$Value){
    $hex=$Value.ToString('N');$parts=for($i=0;$i -lt 4;$i++){
        $part=[Convert]::ToInt64($hex.Substring($i*8,8),16)
        if($part -ge 2147483648){$part-=4294967296}
        $part.ToString([Globalization.CultureInfo]::InvariantCulture)
    }
    '[I;'+($parts -join ',')+']'
}
function Test-DedicatedAssertion([string]$Condition,[string]$Token,[int]$TimeoutSeconds=10){
    $until=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $pattern='(?m)\[Server\] '+[regex]::Escape($Token)+'\r?$'
    do {
        if($process.HasExited){throw "Dedicated JVM exited while checking $Token"}
        $process.StandardInput.WriteLine("execute $Condition run say $Token")
        Start-Sleep -Milliseconds 400
        if((Get-Content -LiteralPath "$trial\logs\latest.log" -Raw) -match $pattern){return $true}
    }while([DateTime]::UtcNow -lt $until)
    Write-Warning "Timed out waiting for $Token"
    return $false
}
function Remove-ActorFixture {
    if(-not $fixtureStarted -or $process.HasExited){return}
    # The actor is killed before its vehicle so it cannot dismount and restore blocks.
    $process.StandardInput.WriteLine("kill $actorUuid")
    $process.StandardInput.WriteLine("kill $cartUuid")
    $process.StandardInput.WriteLine('setblock 0 84 0 minecraft:air')
    $process.StandardInput.WriteLine('setblock 0 83 0 minecraft:air')
}
function Reconnect-ObserverPower {
    # Vanilla /data reloads the block entity and can detach Create's live network.
    # Reconnect physical components after fixture-only NBT edits, just as a player
    # can replace the drive. Never repair the observer with fabricated RPM/NBT.
    foreach($command in @('setblock 0 78 0 minecraft:air','setblock 0 79 0 minecraft:air',
            'setblock 0 79 0 create:shaft[axis=y]','setblock 0 78 0 create:creative_motor[facing=up]')){
        $process.StandardInput.WriteLine($command)
    }
}
try {
    $deadline=[DateTime]::UtcNow.AddMinutes(8)
    do {
        if($process.HasExited){throw 'Dedicated JVM exited before readiness'}
        $log=if(Test-Path -LiteralPath "$trial\logs\latest.log"){Get-Content -LiteralPath "$trial\logs\latest.log" -Raw}else{''}
        if($log -match 'Done \([\d.,]+s\)! For help'){break}
        if([DateTime]::UtcNow -gt $deadline){throw 'Dedicated startup timeout'}
        Start-Sleep -Seconds 1
    }while($true)
    foreach($command in @('forceload add -16 -16 15 15','scoreboard objectives add hololith_probe dummy',
            'setblock 0 80 0 hololith:surveying_table{Span:32}','setblock 0 79 0 create:shaft[axis=y]',
            'setblock 2 80 0 hololith:local_sensor')){$process.StandardInput.WriteLine($command)}
    $null=Test-DedicatedAssertion 'if data block 0 80 0 {HololithPower:"UNPOWERED",Speed:0.0f} unless data block 0 80 0 Survey' 'HOLOLITH_DEDICATED_UNPOWERED_OK'
    # The motor's normal default is 16 RPM. Only its shaft supplies table power;
    # this fixture never assigns speed or a network directly to the world table.
    $process.StandardInput.WriteLine('setblock 0 78 0 create:creative_motor[facing=up]')
    $powered='if block 0 78 0 create:creative_motor[facing=up] if block 0 79 0 create:shaft[axis=y] if data block 0 80 0 {HololithPower:"RUNNING",Speed:16.0f} if data block 0 80 0 Source if data block 0 80 0 Network'
    $null=Test-DedicatedAssertion $powered 'HOLOLITH_DEDICATED_KINETIC_POWER_OK' 20
    $null=Test-DedicatedAssertion ($powered+' if data block 0 80 0 Survey{Format:1,Span:32,X:0,Z:0,Loaded:1024}') 'HOLOLITH_DEDICATED_SURVEY_OK' 30
    $process.StandardInput.WriteLine('data modify storage hololith:dedicated_fixture CachedSurvey set from block 0 80 0 Survey')
    $process.StandardInput.WriteLine('data modify storage hololith:dedicated_fixture TableId set from block 0 80 0 TableId')
    $process.StandardInput.WriteLine('setblock 0 78 0 minecraft:air')
    $null=Test-DedicatedAssertion 'if data block 0 80 0 {HololithPower:"UNPOWERED",Speed:0.0f,Survey:{Format:1,Span:32,Loaded:1024}}' 'HOLOLITH_DEDICATED_STOPPED_OK'
    Start-Sleep -Seconds 2
    # /data modify returns zero when the entire saved terrain compound is unchanged.
    $process.StandardInput.WriteLine('execute store success score cache_changed hololith_probe run data modify storage hololith:dedicated_fixture CachedSurvey set from block 0 80 0 Survey')
    $null=Test-DedicatedAssertion 'if score cache_changed hololith_probe matches 0 if data block 0 80 0 Survey{Format:1,Span:32,Loaded:1024} if data storage hololith:dedicated_fixture CachedSurvey' 'HOLOLITH_DEDICATED_STOPPED_CACHE_OK'
    $process.StandardInput.WriteLine('setblock 0 78 0 create:creative_motor[facing=up]')
    $process.StandardInput.WriteLine('execute store success score identity_changed hololith_probe run data modify storage hololith:dedicated_fixture TableId set from block 0 80 0 TableId')
    $null=Test-DedicatedAssertion ($powered+' if score identity_changed hololith_probe matches 0 if data block 0 80 0 Survey{Format:1,Span:32,Loaded:1024}') 'HOLOLITH_DEDICATED_POWER_RESTORED_OK'
    $process.StandardInput.WriteLine('setblock 4 80 0 hololith:reference_marker[color=magenta]')
    $process.StandardInput.WriteLine('data modify block 4 80 0 TargetId set from block 0 80 0 TableId')
    Start-Sleep -Seconds 2
    $process.StandardInput.WriteLine('execute if block 4 80 0 hololith:reference_marker[color=magenta] if data block 4 80 0 TargetId run say HOLOLITH_DEDICATED_REFERENCE_OK')
    $process.StandardInput.WriteLine('execute if data block 0 80 0 TableId run say HOLOLITH_DEDICATED_TABLE_ID_OK')
    $process.StandardInput.WriteLine('execute if data block 2 80 0 SensorId run say HOLOLITH_DEDICATED_SENSOR_OK')

    # Server-only actor lifecycle regression: deserialize captured instrument blocks onto a
    # minecart passenger, tick them, and use vanilla /data to save and reload the entity.
    # This deliberately does not claim physical Create assembly or client rendering coverage.
    $actorId=ConvertTo-SnbtUuid $actorUuid;$cartId=ConvertTo-SnbtUuid $cartUuid
    $tableId=ConvertTo-SnbtUuid $tableUuid;$sensorId=ConvertTo-SnbtUuid $sensorUuid;$markerId=ConvertTo-SnbtUuid $markerUuid
    $tablePath='Contraption.Blocks.BlockList[{Pos:2L}].Data'
    $sensorPath='Contraption.Blocks.BlockList[{Pos:0L}].Data'
    $markerPath='Contraption.Blocks.BlockList[{Pos:274877906945L}].Data'
    # Deliberately stale saved kinetic state must never power an ordinary captured table.
    # It has no initial survey: acquiring one while mounted would be a regression.
    $tableData='{id:"hololith:surveying_table",TableId:'+ $tableId +',SensorId:'+ $sensorId +',Span:32,Rotation:1,Zoom:1,Relief:2,Mode:1,Dimension:"minecraft:overworld",X:-512,Z:-512,HololithPower:"RUNNING",Speed:256.0f,Source:[I;0,1,0],Network:{Id:918273645L,Stress:1.0f,Capacity:1048576.0f,Size:7}}'
    $sensorData='{id:"hololith:survey_sensor",SensorId:'+ $sensorId +'}'
    $markerData='{id:"hololith:reference_marker",MarkerId:'+ $markerId +',TargetId:'+ $tableId +',TargetKind:0}'
    $blocks='{Palette:[{Name:"hololith:surveying_table",Properties:{part:"4",facing:"north"}},{Name:"hololith:local_sensor"},{Name:"hololith:reference_marker",Properties:{color:"red"}}],BlockList:[{Pos:2L,State:0,Data:'+ $tableData +'},{Pos:0L,State:1,Data:'+ $sensorData +'},{Pos:274877906945L,State:2,Data:'+ $markerData +'}]}'
    $actors='[{Pos:[I;0,2,0],Motion:[0.0d,0.0d,0.0d],RelativeMotion:[0.0d,0.0d,0.0d],Data:{},FirstMovement:1b},{Pos:[I;0,0,0],Motion:[0.0d,0.0d,0.0d],RelativeMotion:[0.0d,0.0d,0.0d],Data:{},FirstMovement:1b},{Pos:[I;1,1,0],Motion:[0.0d,0.0d,0.0d],RelativeMotion:[0.0d,0.0d,0.0d],Data:{},FirstMovement:1b}]'
    $contraption='{Type:"create:mounted",RotationMode:"ROTATION_LOCKED",Anchor:[I;0,0,0],BoundsFront:[-1.0f,0.0f,-1.0f,2.0f,3.0f,1.0f],Blocks:'+ $blocks +',Actors:'+ $actors +',items:[],fluids:[]}'
    $passenger='{id:"create:contraption",UUID:'+ $actorId +',Initialized:1b,Placed:1b,InitialOrientation:"SOUTH",Yaw:0.0f,Pitch:0.0f,Contraption:'+ $contraption +'}'
    $fixtureStarted=$true
    $process.StandardInput.WriteLine('setblock 0 83 0 minecraft:stone')
    $process.StandardInput.WriteLine('setblock 0 84 0 minecraft:rail[shape=north_south]')
    $process.StandardInput.WriteLine('summon minecraft:minecart 0.5 84.1 0.5 {UUID:'+ $cartId +',Passengers:['+ $passenger +']}')
    $identityCondition="if data entity $actorUuid $tablePath"+'{TableId:'+ $tableId +',SensorId:'+ $sensorId +',Span:32,Rotation:1,Zoom:1,Relief:2,Mode:1}'+" if data entity $actorUuid $sensorPath"+'{SensorId:'+ $sensorId +'}'+" if data entity $actorUuid $markerPath"+'{MarkerId:'+ $markerId +',TargetId:'+ $tableId +',TargetKind:0}'
    $mountedCondition="if data entity $actorUuid $tablePath"+'{HololithPower:"MOUNTED",Speed:0.0f}'+" unless data entity $actorUuid $tablePath.Survey unless data entity $actorUuid $tablePath.Source unless data entity $actorUuid $tablePath.Network"
    $actorReady=Test-DedicatedAssertion ($identityCondition+' '+$mountedCondition) 'HOLOLITH_DEDICATED_ACTOR_POWER_OFF_OK' 30
    if($actorReady){
        $null=Test-DedicatedAssertion ("if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;0,2,0]}].Position'+" if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;0,0,0]}].Position'+" if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;1,1,0]}].Position') 'HOLOLITH_DEDICATED_ACTOR_CONTEXTS_OK'
        $null=Test-DedicatedAssertion 'unless block 0 2 0 hololith:surveying_table unless block 0 0 0 hololith:local_sensor unless block 1 1 0 hololith:reference_marker' 'HOLOLITH_DEDICATED_ACTOR_CAPTURED_ONLY_OK'
        # A powered stationary table consumes the moving sensor's heartbeats. A distant
        # fallback and cleared snapshot prevent a previous local survey from passing.
        $process.StandardInput.WriteLine('data remove block 0 80 0 Survey')
        $process.StandardInput.WriteLine('data merge block 0 80 0 {Mode:1,SensorId:'+ $sensorId +',Dimension:"minecraft:overworld",X:-512,Z:-512,Status:"Awaiting captured sensor"}')
        Reconnect-ObserverPower
        $sensorLive='if data block 0 80 0 {HololithPower:"RUNNING",Mode:1,SensorId:'+ $sensorId +',Status:"Live survey",Survey:{Format:1,Span:32,X:0,Z:0,Loaded:1024}}'
        $null=Test-DedicatedAssertion $sensorLive 'HOLOLITH_DEDICATED_MOUNTED_SENSOR_LIVE_OK' 30
        # /data merge performs EntityDataAccessor.getData/save followed by setData/load.
        $process.StandardInput.WriteLine("data merge entity $actorUuid {Silent:1b}")
        $null=Test-DedicatedAssertion ($identityCondition+' '+$mountedCondition+" if data entity $actorUuid {Silent:1b}") 'HOLOLITH_DEDICATED_ACTOR_RELOAD_OFF_OK'
        # Longer than SurveyStore's five-second online interval: old heartbeats cannot
        # alone satisfy the next live survey, and the captured table must remain inert.
        Start-Sleep -Seconds 6
        $process.StandardInput.WriteLine('data remove block 0 80 0 Survey')
        $process.StandardInput.WriteLine('data merge block 0 80 0 {Status:"Awaiting fresh mounted heartbeat"}')
        Reconnect-ObserverPower
        $null=Test-DedicatedAssertion $sensorLive 'HOLOLITH_DEDICATED_MOUNTED_SENSOR_RETICK_OK' 20
        $null=Test-DedicatedAssertion ($identityCondition+' '+$mountedCondition) 'HOLOLITH_DEDICATED_ACTOR_REMAINS_OFF_OK'
    }
    Remove-ActorFixture
    $fixtureCleaned=Test-DedicatedAssertion "unless entity $actorUuid unless entity $cartUuid" 'HOLOLITH_DEDICATED_ACTOR_CLEANUP_OK'
    $process.StandardInput.WriteLine('save-all flush');$process.StandardInput.WriteLine('stop')
    if(-not $process.WaitForExit(60000)){throw 'Dedicated shutdown timeout'}
}finally{
    if(-not $fixtureCleaned){Remove-ActorFixture}
    if(-not $process.HasExited){$process.StandardInput.WriteLine('stop');if(-not $process.WaitForExit(30000)){$process.Kill($true);$process.WaitForExit()}}
    $outputTask.GetAwaiter().GetResult() | Set-Content -LiteralPath "$trial\stdout.log"
    $errorTask.GetAwaiter().GetResult() | Set-Content -LiteralPath "$trial\stderr.log"
    if(Test-Path -LiteralPath "$trial\logs\latest.log"){Copy-Item -LiteralPath "$trial\logs\latest.log" -Destination "$project\reports\$reportName.log"}
}
$log=Get-Content -LiteralPath "$trial\logs\latest.log" -Raw
$successes=@{}
foreach($match in [regex]::Matches($log,'(?m)\[Server\] (HOLOLITH_DEDICATED_[A-Z_]+)\r?$')){$successes[$match.Groups[1].Value]=$true}
$checks=@{
    processExitedCleanly=($process.ExitCode -eq 0)
    playerJarLoaded=($log -match [regex]::Escape("Create: Hololith $version"))
    jadeConfigurationMatches=(($log -match 'Jade 15\.10\.6\+neoforge \(jade\)') -eq $Jade.IsPresent)
    unpoweredTableDoesNotScan=$successes.ContainsKey('HOLOLITH_DEDICATED_UNPOWERED_OK')
    realShaftNetworkPowersTable=$successes.ContainsKey('HOLOLITH_DEDICATED_KINETIC_POWER_OK')
    poweredSurveyCompleted=$successes.ContainsKey('HOLOLITH_DEDICATED_SURVEY_OK')
    motorRemovalStopsProjectionState=$successes.ContainsKey('HOLOLITH_DEDICATED_STOPPED_OK')
    powerLossRetainsExactTerrain=$successes.ContainsKey('HOLOLITH_DEDICATED_STOPPED_CACHE_OK')
    restoredMotorRejoinsNetworkWithSameTable=$successes.ContainsKey('HOLOLITH_DEDICATED_POWER_RESTORED_OK')
    stationarySensorIdentityPresent=$successes.ContainsKey('HOLOLITH_DEDICATED_SENSOR_OK')
    referenceConfigured=$successes.ContainsKey('HOLOLITH_DEDICATED_REFERENCE_OK')
    tableIdentityPresent=$successes.ContainsKey('HOLOLITH_DEDICATED_TABLE_ID_OK')
    capturedActorRejectsStaleKineticState=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_POWER_OFF_OK')
    allActorContextsTicked=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CONTEXTS_OK')
    fixtureBlocksNotInWorld=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CAPTURED_ONLY_OK')
    capturedActorReloadRetainsIdentitySettingsAndPowerOff=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_RELOAD_OFF_OK')
    capturedSensorFeedsPoweredWorldTable=$successes.ContainsKey('HOLOLITH_DEDICATED_MOUNTED_SENSOR_LIVE_OK')
    capturedSensorStillTicksAfterReload=$successes.ContainsKey('HOLOLITH_DEDICATED_MOUNTED_SENSOR_RETICK_OK')
    capturedTableRemainsOffWithoutSurvey=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_REMAINS_OFF_OK')
    fixtureEntitiesCleaned=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CLEANUP_OK')
    allDimensionsSaved=($log -match 'ThreadedAnvilChunkStorage: All dimensions are saved')
    sharedSurveyPersisted=(Test-Path -LiteralPath "$trial\world-smoke\data\hololith_surveys.dat")
}
if($Jade){
    # Jade 15.10.6 logs discovery before common registration; successful server
    # startup and shutdown above establish that registration did not abort loading.
    $checks.jadeCommonPluginLoaded=($log -match 'Start loading plugin from dev\.hololith\.compat\.jade\.HololithJadePlugin' -and
        $checks.processExitedCleanly -and $checks.realShaftNetworkPowersTable)
}
$failures=@($checks.Values | Where-Object {$_ -eq $false}).Count
@{timestamp=[DateTime]::UtcNow.ToString('o');version=$version;trial=$trial;minimal=$Minimal.IsPresent;jade=$Jade.IsPresent;jadeSha256=$jadeHash;scope='Dedicated player JAR startup, actual motor/shaft power gating and cache retention, powered stationary table following a captured sensor, and captured actor power-off/save/reload. No physical assembly, flight or client rendering assertions.';fixture=@{actor=$actorUuid.ToString();cart=$cartUuid.ToString();table=$tableUuid.ToString();sensor=$sensorUuid.ToString();marker=$markerUuid.ToString()};mods=@(Get-ChildItem -LiteralPath "$trial\mods" -Filter '*.jar' | Select-Object -ExpandProperty Name);jarSha256=$testedJarHash;checks=$checks;failures=$failures} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$project\reports\$reportName.json"
if($failures -ne 0){throw "Dedicated smoke failed $failures checks; logs preserved at $trial"}
Write-Output "Dedicated $reportName player JAR passed shaft-power gating, cached terrain, captured sensor updates and actor reload checks."
