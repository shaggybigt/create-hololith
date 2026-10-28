# Shared read-only resolution for the isolated development launchers.
function Assert-HololithUnlinkedPath {
    param([Parameter(Mandatory)][string]$Path)
    $current=[IO.Path]::GetFullPath($Path)
    while($current){
        if(Test-Path -LiteralPath $current){
            if((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){
                throw "Refuse linked runtime input: $current"
            }
        }
        $parent=[IO.Directory]::GetParent($current)
        $current=if($null -eq $parent){$null}else{$parent.FullName}
    }
}

function Resolve-HololithCreateJar {
    param([Parameter(Mandatory)][string]$RuntimeRoot, [string]$ModpackRoot='')
    $name='create-1.21.1-6.0.10.jar'
    $candidates=if($ModpackRoot){
        @(Join-Path ([IO.Path]::GetFullPath($ModpackRoot)) "mods/$name")
    }else{
        @((Join-Path ([IO.Path]::GetFullPath($RuntimeRoot)) "test-client/mods/$name"),
          (Join-Path ([IO.Path]::GetFullPath($RuntimeRoot)) "downloads/mods/$name"))
    }
    foreach($candidate in $candidates){
        if(-not(Test-Path -LiteralPath $candidate -PathType Leaf)){continue}
        Assert-HololithUnlinkedPath -Path $candidate
        $actual=(Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
        $expected='ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a'
        if($actual -ne $expected){throw "Expected the pinned official Create 6.0.10 JAR ($expected), got $actual at $candidate."}
        return [IO.Path]::GetFullPath($candidate)
    }
    throw "Create 6.0.10 is missing. Expected: $($candidates -join ' or ')."
}

function Resolve-HololithModpackRoot {
    param([Parameter(Mandatory)][string]$RuntimeRoot, [string]$ModpackRoot='',
          [Parameter(Mandatory)][string]$TrialRoot)
    if(-not $ModpackRoot){
        $ModpackRoot=Join-Path ([IO.Path]::GetFullPath($RuntimeRoot)) 'test-client'
        if(-not(Test-Path -LiteralPath $ModpackRoot -PathType Container)){
            throw 'The legacy test-client profile is absent. Supply -ModpackRoot with an existing modpack directory containing mods/ (and optionally config/).'
        }
    }
    $source=[IO.Path]::GetFullPath($ModpackRoot)
    $sourceComparison=$source.TrimEnd([IO.Path]::DirectorySeparatorChar,[IO.Path]::AltDirectorySeparatorChar)
    $trial=[IO.Path]::GetFullPath($TrialRoot).TrimEnd([IO.Path]::DirectorySeparatorChar,[IO.Path]::AltDirectorySeparatorChar)
    if($sourceComparison.Equals($trial,[StringComparison]::OrdinalIgnoreCase) -or $sourceComparison.StartsWith($trial+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
        throw 'The modpack source must be outside the airship trial that will be archived and staged.'
    }
    Assert-HololithUnlinkedPath -Path $source
    $mods=Join-Path $source 'mods'
    if(-not(Test-Path -LiteralPath $mods -PathType Container)){throw "Modpack source has no mods directory: $source"}
    Assert-HololithUnlinkedPath -Path $mods
    $config=Join-Path $source 'config'
    if(Test-Path -LiteralPath $config){
        if(-not(Test-Path -LiteralPath $config -PathType Container)){throw "Modpack config is not a directory: $config"}
        Assert-HololithUnlinkedPath -Path $config
        foreach($entry in Get-ChildItem -LiteralPath $config -Force -Recurse){
            if($entry.Attributes -band [IO.FileAttributes]::ReparsePoint){throw "Refuse linked config input: $($entry.FullName)"}
        }
    }
    return $source
}
