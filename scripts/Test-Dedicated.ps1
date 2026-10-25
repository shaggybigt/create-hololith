param([Parameter(Mandatory)][string]$RuntimeRoot, [switch]$Minimal)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
New-Item -ItemType Directory -Force -Path (Join-Path $project 'reports') | Out-Null
$version=((Get-Content (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}) -split '=',2)[1]
$server=Join-Path $runtime 'test-server'
$java=Join-Path $runtime 'runtime\jdk-21.0.12.1+1\bin\java.exe'
if(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.ExecutablePath -eq $java}){throw 'Close project JVMs before this isolated dedicated test.'}
$trial=Join-Path $runtime ('development\hololith\dedicated-smoke-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $trial | Out-Null
$folders=if($Minimal){@('libraries')}else{@('libraries','config','defaultconfigs')}
foreach($folder in $folders){Copy-Item -LiteralPath (Join-Path $server $folder) -Destination $trial -Recurse}
New-Item -ItemType Directory -Path (Join-Path $trial 'mods') | Out-Null
foreach($file in Get-ChildItem -LiteralPath (Join-Path $server 'mods') -Filter '*.jar'){
    if($Minimal -and $file.Name -ne 'create-1.21.1-6.0.10.jar'){continue}
    if($file.Name -like 'fantasy-validation*' -or $file.Name -like 'hololith-probe*' -or $file.Name -like 'create-hololith*'){continue}
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $trial 'mods')
}
${jar}=Join-Path $runtime "development\hololith\build\libs\create-hololith-$version.jar"
$reportName=if($Minimal){'dedicated-minimal'}else{'dedicated'}
Copy-Item -LiteralPath $jar -Destination (Join-Path $trial 'mods')
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
try {
    $deadline=[DateTime]::UtcNow.AddMinutes(8)
    do {
        if($process.HasExited){throw 'Dedicated JVM exited before readiness'}
        $log=if(Test-Path -LiteralPath "$trial\logs\latest.log"){Get-Content -LiteralPath "$trial\logs\latest.log" -Raw}else{''}
        if($log -match 'Done \([\d.,]+s\)! For help'){break}
        if([DateTime]::UtcNow -gt $deadline){throw 'Dedicated startup timeout'}
        Start-Sleep -Seconds 1
    }while($true)
    foreach($command in @('forceload add -16 -16 15 15','setblock 0 80 0 hololith:surveying_table{Span:32}','setblock 2 80 0 hololith:local_sensor')){$process.StandardInput.WriteLine($command)}
    Start-Sleep -Seconds 8
    $process.StandardInput.WriteLine('setblock 4 80 0 hololith:reference_marker[color=magenta]')
    $process.StandardInput.WriteLine('data modify block 4 80 0 TargetId set from block 0 80 0 TableId')
    Start-Sleep -Seconds 2
    $process.StandardInput.WriteLine('execute if block 4 80 0 hololith:reference_marker[color=magenta] if data block 4 80 0 TargetId run say HOLOLITH_DEDICATED_REFERENCE_OK')
    $process.StandardInput.WriteLine('execute if data block 0 80 0 TableId run say HOLOLITH_DEDICATED_TABLE_ID_OK')
    $process.StandardInput.WriteLine('execute if data block 0 80 0 Survey{Format:1,Span:32,Loaded:1024} run say HOLOLITH_DEDICATED_SURVEY_OK')
    $process.StandardInput.WriteLine('execute if data block 2 80 0 SensorId run say HOLOLITH_DEDICATED_SENSOR_OK')

    # Server-only actor lifecycle regression: deserialize captured instrument blocks onto a
    # minecart passenger, tick them, and use vanilla /data to save and reload the entity.
    # This deliberately does not claim physical Create assembly or client rendering coverage.
    $actorId=ConvertTo-SnbtUuid $actorUuid;$cartId=ConvertTo-SnbtUuid $cartUuid
    $tableId=ConvertTo-SnbtUuid $tableUuid;$sensorId=ConvertTo-SnbtUuid $sensorUuid;$markerId=ConvertTo-SnbtUuid $markerUuid
    $tablePath='Contraption.Blocks.BlockList[{Pos:2L}].Data'
    $sensorPath='Contraption.Blocks.BlockList[{Pos:0L}].Data'
    $markerPath='Contraption.Blocks.BlockList[{Pos:274877906945L}].Data'
    # A distant fallback target makes a live survey at (0,0) evidence of sensor heartbeats.
    $tableData='{id:"hololith:surveying_table",TableId:'+ $tableId +',SensorId:'+ $sensorId +',Span:32,Rotation:1,Zoom:1,Relief:2,Mode:1,Dimension:"minecraft:overworld",X:-512,Z:-512}'
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
    $liveCondition="if data entity $actorUuid $tablePath"+'{Status:"Live survey",Survey:{Format:1,Span:32,X:0,Z:0,Loaded:1024}}'
    $actorReady=Test-DedicatedAssertion ($identityCondition+' '+$liveCondition) 'HOLOLITH_DEDICATED_ACTOR_SURVEY_OK' 30
    if($actorReady){
        $null=Test-DedicatedAssertion ("if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;0,2,0]}].Position'+" if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;0,0,0]}].Position'+" if data entity $actorUuid "+'Contraption.Actors[{Pos:[I;1,1,0]}].Position') 'HOLOLITH_DEDICATED_ACTOR_CONTEXTS_OK'
        $null=Test-DedicatedAssertion 'unless block 0 2 0 hololith:surveying_table unless block 0 0 0 hololith:local_sensor unless block 1 1 0 hololith:reference_marker' 'HOLOLITH_DEDICATED_ACTOR_CAPTURED_ONLY_OK'
        # /data merge performs EntityDataAccessor.getData/save followed by setData/load.
        $process.StandardInput.WriteLine("data merge entity $actorUuid {Silent:1b}")
        $null=Test-DedicatedAssertion ($identityCondition+' '+$liveCondition+" if data entity $actorUuid {Silent:1b}") 'HOLOLITH_DEDICATED_ACTOR_RELOAD_OK'
        # Remove only this captured table's snapshot, then require actor ticks to refill it.
        $process.StandardInput.WriteLine("data remove entity $actorUuid $tablePath.Survey")
        $process.StandardInput.WriteLine("execute unless data entity $actorUuid $tablePath.Survey run say HOLOLITH_DEDICATED_ACTOR_CACHE_CLEARED_OK")
        $null=Test-DedicatedAssertion ($identityCondition+' '+$liveCondition) 'HOLOLITH_DEDICATED_ACTOR_RETICK_OK'
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
    releaseJarLoaded=($log -match [regex]::Escape("Create: Hololith $version"))
    fullSurveyCompleted=$successes.ContainsKey('HOLOLITH_DEDICATED_SURVEY_OK')
    sensorTicked=$successes.ContainsKey('HOLOLITH_DEDICATED_SENSOR_OK')
    referenceConfigured=$successes.ContainsKey('HOLOLITH_DEDICATED_REFERENCE_OK')
    tableIdentityPresent=$successes.ContainsKey('HOLOLITH_DEDICATED_TABLE_ID_OK')
    capturedActorSurveyAndIdentities=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_SURVEY_OK')
    allActorContextsTicked=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CONTEXTS_OK')
    fixtureBlocksNotInWorld=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CAPTURED_ONLY_OK')
    capturedActorReloadPreservedState=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_RELOAD_OK')
    capturedActorCacheCleared=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CACHE_CLEARED_OK')
    capturedActorRetickedAfterReload=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_RETICK_OK')
    fixtureEntitiesCleaned=$successes.ContainsKey('HOLOLITH_DEDICATED_ACTOR_CLEANUP_OK')
    allDimensionsSaved=($log -match 'ThreadedAnvilChunkStorage: All dimensions are saved')
    sharedSurveyPersisted=(Test-Path -LiteralPath "$trial\world-smoke\data\hololith_surveys.dat")
}
$failures=@($checks.Values | Where-Object {$_ -eq $false}).Count
@{timestamp=[DateTime]::UtcNow.ToString('o');trial=$trial;minimal=$Minimal.IsPresent;scope='Dedicated startup, stationary surveys, and deserialized minecart actor ticking/save/reload; no physical assembly or rendering assertions';fixture=@{actor=$actorUuid.ToString();cart=$cartUuid.ToString();table=$tableUuid.ToString();sensor=$sensorUuid.ToString();marker=$markerUuid.ToString()};mods=@(Get-ChildItem -LiteralPath "$trial\mods" -Filter '*.jar' | Select-Object -ExpandProperty Name);jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;checks=$checks;failures=$failures} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$project\reports\$reportName.json"
if($failures -ne 0){throw "Dedicated smoke failed $failures checks; logs preserved at $trial"}
Write-Output 'Dedicated release JAR passed startup, surveys, captured actor ticks and entity save/reload checks.'
