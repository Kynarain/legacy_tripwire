param([string]$Root = "C:\Users\kynar\IdeaProjects\legacy_tripwire")

$ErrorActionPreference = 'Continue'
$mv   = "$Root\multiversion"
$mdks = "$Root\.ref4\mdks"
New-Item -ItemType Directory -Force -Path "$mv\logs" | Out-Null
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

$targets = @(
    @{ mc = '1.21';    fv = '51.0.33' },
    @{ mc = '1.21.1';  fv = '52.1.16' },
    @{ mc = '1.21.3';  fv = '53.1.12' },
    @{ mc = '1.21.4';  fv = '54.1.18' },
    @{ mc = '1.21.5';  fv = '55.1.13' },
    @{ mc = '1.21.6';  fv = '56.0.9'  },
    @{ mc = '1.21.7';  fv = '57.0.3'  },
    @{ mc = '1.21.8';  fv = '58.1.22' },
    @{ mc = '1.21.9';  fv = '59.0.5'  },
    @{ mc = '1.21.10'; fv = '60.1.15' },
    @{ mc = '1.21.11'; fv = '61.2.1'  }
)

$desc = 'Restores the pre-1.21.2 tripwire hook behaviour (reverting the MC-129055 / MC-59471 fix) so classic string duper machines work again.'

foreach ($t in $targets) {
    $p = "$mv\forge-$($t.mc)"
    Remove-Item -Recurse -Force $p -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path "$p\gradle\wrapper", "$p\src\main\java", "$p\src\main\resources\META-INF" | Out-Null

    Copy-Item "$mdks\$($t.mc)\gradlew"     $p -Force
    Copy-Item "$mdks\$($t.mc)\gradlew.bat" $p -Force
    Copy-Item "$mdks\$($t.mc)\gradle\wrapper" "$p\gradle" -Recurse -Force

    # ranges come from that version's own MDK, so they stay authoritative
    $mgp    = Get-Content "$mdks\$($t.mc)\gradle.properties" -Raw
    $major  = ($t.fv -split '\.')[0]
    $loader = if ($mgp -match '(?m)^loader_version_range\s*=\s*(.+)$')    { $Matches[1].Trim() } else { "[$major,)" }
    $mcr    = if ($mgp -match '(?m)^minecraft_version_range\s*=\s*(.+)$') { $Matches[1].Trim() } else { "[$($t.mc),1.22)" }
    $fr     = if ($mgp -match '(?m)^forge_version_range\s*=\s*(.+)$')     { $Matches[1].Trim() } else { "[$major,)" }

    # gradle.properties: mine, with this version's numbers
    $gp = Get-Content "$Root\gradle.properties" -Raw
    $gp = $gp -replace '(?m)^minecraft_version=.*$',       "minecraft_version=$($t.mc)"
    $gp = $gp -replace '(?m)^forge_version=.*$',           "forge_version=$($t.fv)"
    $gp = $gp -replace '(?m)^minecraft_version_range=.*$', "minecraft_version_range=$mcr"
    $gp = $gp -replace '(?m)^forge_version_range=.*$',     "forge_version_range=$fr"
    $gp = $gp -replace '(?m)^loader_version_range=.*$',    "loader_version_range=$loader"
    [System.IO.File]::WriteAllText("$p\gradle.properties", $gp, (New-Object System.Text.UTF8Encoding($false)))

    # build.gradle: mine, with a per-version jar name
    $bg = (Get-Content "$Root\build.gradle" -Raw).Replace('base.archivesName = mod_id', 'base.archivesName = "${mod_id}-${minecraft_version}"')
    [System.IO.File]::WriteAllText("$p\build.gradle", $bg, (New-Object System.Text.UTF8Encoding($false)))

    $sg = "plugins {`n    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'`n}`n`nrootProject.name = 'legacy_tripwire-forge-$($t.mc)'`n"
    [System.IO.File]::WriteAllText("$p\settings.gradle", $sg, (New-Object System.Text.UTF8Encoding($false)))

    Copy-Item "$Root\src\main\java\*" "$p\src\main\java" -Recurse -Force
    Copy-Item "$Root\src\main\resources\legacy_tripwire.mixins.json" "$p\src\main\resources" -Force
    if (Test-Path "$mdks\$($t.mc)\src\main\resources\pack.mcmeta") {
        $pm = (Get-Content "$mdks\$($t.mc)\src\main\resources\pack.mcmeta" -Raw).Replace('${mod_id}', 'legacy_tripwire')
        [System.IO.File]::WriteAllText("$p\src\main\resources\pack.mcmeta", $pm, (New-Object System.Text.UTF8Encoding($false)))
    }

    $toml = @"
modLoader="javafml"
loaderVersion="$loader"
license="MIT"

[[mods]]
modId="legacy_tripwire"
version="1.0.0"
displayName="Legacy Tripwire"
authors="Kynarain"
description='''$desc'''

[[dependencies.legacy_tripwire]]
    modId="forge"
    mandatory=true
    versionRange="$fr"
    ordering="NONE"
    side="BOTH"

[[dependencies.legacy_tripwire]]
    modId="minecraft"
    mandatory=true
    versionRange="$mcr"
    ordering="NONE"
    side="BOTH"
"@
    [System.IO.File]::WriteAllText("$p\src\main\resources\META-INF\mods.toml", $toml, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "prepared forge-$($t.mc)  (loader $loader, mc $mcr)"
}

foreach ($t in $targets) {
    $p   = "$mv\forge-$($t.mc)"
    $log = "$mv\logs\forge-$($t.mc).log"
    Push-Location $p
    & "$p\gradlew.bat" build --console=plain --no-daemon *> $log
    $code = $LASTEXITCODE
    Pop-Location
    $jar = '-'
    $libs = Get-ChildItem "$p\build\libs\*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($libs) { $jar = $libs.Name }
    Write-Output ("BUILD {0,-8} exit={1,-3} jar={2}" -f $t.mc, $code, $jar)
}
Write-Output "ALL DONE"
